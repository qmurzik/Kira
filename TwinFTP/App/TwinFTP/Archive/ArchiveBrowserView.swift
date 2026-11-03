import SwiftUI
import TwinCore

/// Просмотр и распаковка ZIP-архива.
struct ArchiveBrowserView: View {
    let request: ArchiveRequest
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var reader: ZipArchiveReader?
    @State private var localURL: URL?
    @State private var loadError: String?
    @State private var folder = ""
    @State private var password = ""
    @State private var askPassword = false
    @State private var pendingAction: PendingAction?
    @State private var working = false
    @State private var progress: Double = 0
    @State private var progressName = ""
    @State private var cancelFlag = Locked(false)
    @State private var previewURL: URL?
    @State private var resultMessage: String?
    @State private var policy: ZipExistingFilePolicy = .rename

    enum PendingAction: Equatable {
        case extractHere, extractToOther, uploadToFTP
        case preview(String)
    }

    private struct Node: Identifiable {
        let name: String
        let path: String
        let isDirectory: Bool
        let entry: ZipEntry?
        let size: Int64
        var id: String { path }
    }

    var body: some View {
        NavigationStack {
            Group {
                if let reader {
                    content(reader)
                } else if let loadError {
                    StateView(symbol: "doc.zipper", title: "Не удалось открыть архив", message: loadError, actionTitle: "Закрыть") { dismiss() }
                } else {
                    VStack(spacing: 10) {
                        ProgressView()
                        Text(request.item.path.hasPrefix("/") && app.pane(request.paneID).source.isRemote ? "Скачивание архива…" : "Чтение архива…")
                            .font(.system(size: 13)).foregroundStyle(Theme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .navigationTitle(request.item.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Закрыть") { cancelFlag.withValue { $0 = true }; dismiss() } }
            }
            .overlay { if working { progressOverlay } }
            .alert("Пароль архива", isPresented: $askPassword) {
                SecureField("Пароль", text: $password)
                Button("OK") { if let a = pendingAction { run(a) } }
                Button("Отмена", role: .cancel) { pendingAction = nil }
            } message: {
                Text("Архив зашифрован. Поддерживаются ZipCrypto и AES-256 (WinZip).")
            }
            .alert("Готово", isPresented: Binding(get: { resultMessage != nil }, set: { if !$0 { resultMessage = nil } })) {
                Button("OK") { resultMessage = nil }
            } message: { Text(resultMessage ?? "") }
            .sheet(item: Binding(get: { previewURL.map { IdentifiedURL(url: $0) } }, set: { if $0 == nil { previewURL = nil } })) { wrapper in
                QuickLookView(url: wrapper.url, title: wrapper.url.lastPathComponent).ignoresSafeArea()
            }
        }
        .task { await load() }
        .onAppear { if request.uploadAfterExtract { pendingAction = .uploadToFTP } }
    }

    struct IdentifiedURL: Identifiable { let url: URL; var id: String { url.path } }

    // MARK: - Загрузка

    private func load() async {
        let pane = app.pane(request.paneID)
        do {
            var url = URL(fileURLWithPath: request.item.path)
            if case .remote(let pid) = pane.source {
                let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                    .appendingPathComponent("Archives/\(UUID().uuidString)", isDirectory: true)
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                url = dir.appendingPathComponent(request.item.name)
                FileManager.default.createFile(atPath: url.path, contents: nil)
                guard let handle = FileHandle(forWritingAtPath: url.path) else { throw ZipError.io("Нет места для архива.") }
                let pool = try app.sessions.pool(for: pid)
                let conn = await pool.lease()
                defer { Task { await pool.giveBack(conn) } }
                try await conn.download(request.item.path) { try handle.write(contentsOf: $0) }
                try handle.close()
            }
            let target = url
            let r = try await Task.detached { try ZipArchiveReader(url: target) }.value
            localURL = url
            reader = r
            if request.uploadAfterExtract { run(.uploadToFTP) }
        } catch {
            loadError = PaneModel.describe(error)
        }
    }

    // MARK: - Содержимое

    private func children(_ reader: ZipArchiveReader) -> [Node] {
        var dirs: [String: Int64] = [:]
        var files: [Node] = []
        let prefix = folder.isEmpty ? "" : folder + "/"
        for e in reader.entries {
            guard let rel = ZipArchiveReader.sanitize(e.path), rel.hasPrefix(prefix) else { continue }
            let rest = String(rel.dropFirst(prefix.count))
            if rest.isEmpty { continue }
            if let slash = rest.firstIndex(of: "/") {
                let name = String(rest[..<slash])
                dirs[name, default: 0] += e.uncompressedSize
            } else if e.isDirectory {
                dirs[rest, default: 0] += 0
            } else {
                files.append(Node(name: rest, path: prefix + rest, isDirectory: false, entry: e, size: e.uncompressedSize))
            }
        }
        let dirNodes = dirs.map { Node(name: $0.key, path: prefix + $0.key, isDirectory: true, entry: nil, size: $0.value) }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        return dirNodes + files.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    @ViewBuilder
    private func content(_ reader: ZipArchiveReader) -> some View {
        let pane = app.pane(request.paneID)
        let other = app.otherPane(of: pane)
        List {
            Section {
                HStack(spacing: 12) {
                    Image(systemName: "doc.zipper").font(.system(size: 28)).foregroundStyle(.brown)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("\(reader.entries.filter { !$0.isDirectory }.count) \(RussianPlural.files(reader.entries.filter { !$0.isDirectory }.count)) · \(ByteFormatter.string(reader.totalUncompressedSize))")
                            .font(.system(size: 14, weight: .semibold))
                        if reader.hasEncryptedEntries {
                            Label("Защищён паролем", systemImage: "lock.fill").font(.system(size: 12)).foregroundStyle(Theme.warning)
                        }
                    }
                }
                Picker("Если файл существует", selection: $policy) {
                    ForEach(ZipExistingFilePolicy.allCases, id: \.self) { Text($0.title).tag($0) }
                }
                if pane.source.isLocal {
                    Button { start(.extractHere) } label: {
                        Label("Распаковать в папку «\(PathUtils.splitExtension(request.item.name).base)»", systemImage: "folder.badge.plus")
                    }
                }
                if other.source.isLocal && (other.path != pane.path || pane.source.isRemote) {
                    Button { start(.extractToOther) } label: {
                        Label("Распаковать в соседнюю панель", systemImage: "arrow.right.doc.on.clipboard")
                    }
                }
                if other.source.isRemote && pane.source.isLocal {
                    Button { start(.uploadToFTP) } label: {
                        Label("Распаковать и загрузить на FTP", systemImage: "icloud.and.arrow.up")
                    }
                }
            }
            Section(folder.isEmpty ? "Содержимое" : "/" + folder) {
                if !folder.isEmpty {
                    Button {
                        folder = folder.contains("/") ? String(folder[..<folder.lastIndex(of: "/")!]) : ""
                    } label: {
                        Label("Назад", systemImage: "arrow.turn.left.up")
                    }
                }
                ForEach(children(reader)) { node in
                    HStack(spacing: 10) {
                        let item = FileItem(name: node.name, path: node.path, kind: node.isDirectory ? .directory : .file, size: node.size)
                        FileIconView(item: item, size: 22)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(node.name).font(.system(size: 14)).lineLimit(2)
                            Text(ByteFormatter.string(node.size) + (node.entry?.isEncrypted == true ? " · 🔒" : ""))
                                .font(.system(size: 11)).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        if node.isDirectory { Image(systemName: "chevron.right").font(.caption).foregroundStyle(Theme.textTertiary) }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if node.isDirectory { folder = node.path } else { start(.preview(node.path)) }
                    }
                }
            }
            Section {
                Text("Распаковка выполняется на iPhone. Пути вида «../» и символьные ссылки блокируются, размер проверяется заранее (защита от zip-бомб).")
                    .font(.system(size: 11.5)).foregroundStyle(Theme.textSecondary)
            }
        }
        .listStyle(.insetGrouped)
    }

    private var progressOverlay: some View {
        ZStack {
            Color.black.opacity(0.25).ignoresSafeArea()
            VStack(spacing: 12) {
                ProgressView(value: progress).frame(width: 220)
                Text(progressName.isEmpty ? "Распаковка…" : progressName).font(.system(size: 12)).lineLimit(1).frame(maxWidth: 240)
                Button("Отмена") { cancelFlag.withValue { $0 = true } }
            }
            .padding(20)
            .background(RoundedRectangle(cornerRadius: 16).fill(Theme.surfaceRaised))
        }
    }

    // MARK: - Действия

    private func start(_ action: PendingAction) {
        pendingAction = action
        if reader?.hasEncryptedEntries == true && password.isEmpty {
            askPassword = true
        } else {
            run(action)
        }
    }

    private func run(_ action: PendingAction) {
        guard let reader, let localURL else {
            pendingAction = action
            return
        }
        if reader.hasEncryptedEntries && password.isEmpty {
            pendingAction = action
            askPassword = true
            return
        }
        let pane = app.pane(request.paneID)
        let other = app.otherPane(of: pane)
        let pw = password.isEmpty ? nil : password
        let policy = self.policy
        cancelFlag = Locked(false)
        let flag = cancelFlag
        switch action {
        case .preview(let path):
            guard let entry = reader.entries.first(where: { ZipArchiveReader.sanitize($0.path) == path }) else { return }
            working = true
            Task {
                do {
                    let dir = FileManager.default.temporaryDirectory.appendingPathComponent("zip-preview-\(UUID().uuidString)")
                    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                    let out = dir.appendingPathComponent(entry.name)
                    let data = try await Task.detached { try ZipArchiveReader(url: localURL).data(for: entry, password: pw) }.value
                    try data.write(to: out)
                    working = false
                    previewURL = out
                } catch {
                    working = false
                    handle(error, retry: action)
                }
            }
        case .extractHere, .extractToOther:
            let baseDir: String
            if action == .extractHere {
                let parent = PathUtils.parent(request.item.path)
                let existing = Set((try? FileManager.default.contentsOfDirectory(atPath: parent)) ?? [])
                baseDir = PathUtils.child(parent, PathUtils.uniqueName(for: PathUtils.splitExtension(request.item.name).base, existing: existing))
            } else {
                baseDir = other.path
            }
            working = true
            Task {
                do {
                    let report = try await Task.detached {
                        try ZipArchiveReader(url: localURL).extractAll(to: baseDir, password: pw, policy: policy,
                                                                       isCancelled: { flag.current }) { done, total, name in
                            let f = total > 0 ? Double(done) / Double(total) : 0
                            Task { @MainActor in progress = f; progressName = name }
                        }
                    }.value
                    working = false
                    pane.refresh(); other.refresh()
                    var msg = "Распаковано файлов: \(report.extractedFiles) в «\(PathUtils.lastComponent(baseDir))»."
                    if !report.skipped.isEmpty { msg += "\nПропущено небезопасных или существующих: \(report.skipped.count)." }
                    if !report.renamed.isEmpty { msg += "\nПереименовано: \(report.renamed.count)." }
                    resultMessage = msg
                    Haptics.success()
                } catch {
                    working = false
                    handle(error, retry: action)
                }
            }
        case .uploadToFTP:
            guard other.source.isRemote else {
                app.show("Откройте FTP-сервер в соседней панели.", style: .warning)
                return
            }
            dismiss()
            app.extractAndUpload(archive: FileItem(name: request.item.name, path: localURL.path, kind: .file), password: pw, from: pane)
        }
    }

    private func handle(_ error: Error, retry: PendingAction) {
        if let z = error as? ZipError, z == .wrongPassword || z == .passwordRequired {
            password = ""
            pendingAction = retry
            askPassword = true
            app.show(z.errorDescription ?? "Неверный пароль", style: .error)
        } else {
            app.showError(error)
        }
    }
}
