import Foundation
import SwiftUI
import TwinCore

/// Открытый в редакторе документ.
@MainActor
final class EditorDocument: ObservableObject, Identifiable {
    enum Origin: Equatable {
        case local(path: String)
        case remote(profileID: UUID, serverName: String, path: String)
    }

    enum SaveState: Equatable {
        case idle
        case saving(String)
        case saved
        case failed(String)
    }

    let id = UUID()
    let origin: Origin
    let name: String
    @Published var language: SyntaxLanguage
    @Published var isLoading = true
    @Published var loadError: String?
    @Published var isDirty = false { didSet { if oldValue != isDirty { onDirtyChange?() } } }
    var onDirtyChange: (() -> Void)?
    @Published var saveState: SaveState = .idle
    @Published var encoding: TextEncodingKind = .utf8
    @Published var lineEnding: LineEnding = .lf
    @Published var hadMixedLineEndings = false
    @Published var cursorLine = 1
    @Published var cursorColumn = 1
    @Published var lineCount = 1
    @Published var readOnly = false

    /// Текст на момент последней загрузки/сохранения.
    private(set) var savedText = ""
    /// Актуальный текст (синхронизируется из UITextView).
    var text = ""
    /// Счётчик версий для принудительной перезагрузки текста во view.
    @Published private(set) var reloadToken = UUID()

    // Для удалённых файлов — базовая версия на сервере (защита от перезаписи чужих изменений).
    var remoteBaselineSize: Int64?
    var remoteBaselineDate: Date?
    var remoteMode: Int?
    var workingCopyURL: URL?

    init(origin: Origin, name: String) {
        self.origin = origin
        self.name = name
        self.language = SyntaxLanguage.detect(fileName: name)
    }

    var isRemote: Bool { if case .remote = origin { return true }; return false }

    var locationDescription: String {
        switch origin {
        case .local(let p): return p
        case .remote(_, let server, let p): return "\(server): \(p)"
        }
    }

    func setLoaded(_ decoded: DecodedText) {
        text = decoded.text
        savedText = decoded.text
        encoding = decoded.encoding
        lineEnding = decoded.lineEnding
        hadMixedLineEndings = decoded.hadMixedLineEndings
        isDirty = false
        isLoading = false
        lineCount = decoded.text.reduce(1) { $1 == "\n" ? $0 + 1 : $0 }
        reloadToken = UUID()
    }

    func textDidChange(_ newText: String) {
        text = newText
        isDirty = newText != savedText
        if case .saved = saveState { saveState = .idle }
        if case .failed = saveState { saveState = .idle }
    }

    func markSaved() {
        savedText = text
        isDirty = false
        saveState = .saved
    }
}

/// Управление вкладками редактора, загрузкой и сохранением (локально и на FTP).
@MainActor
final class EditorManager: ObservableObject {
    @Published private(set) var documents: [EditorDocument] = []
    @Published var activeID: UUID?
    @Published var remoteConflict: EditorDocument?

    private let sessions: SessionManager
    private let servers: ServerStore

    init(sessions: SessionManager, servers: ServerStore) {
        self.sessions = sessions
        self.servers = servers
    }

    var active: EditorDocument? { documents.first { $0.id == activeID } ?? documents.first }
    var hasUnsaved: Bool { documents.contains { $0.isDirty } }

    private static var cacheDir: URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("EditorWorkingCopies")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    // MARK: - Открытие

    func openLocal(path: String) {
        if let existing = documents.first(where: { $0.origin == .local(path: path) }) {
            activeID = existing.id
            return
        }
        let doc = EditorDocument(origin: .local(path: path), name: PathUtils.lastComponent(path))
        doc.onDirtyChange = { [weak self] in self?.objectWillChange.send() }
        documents.append(doc)
        activeID = doc.id
        Task {
            do {
                let data = try await Task.detached { () throws -> Data in
                    let size = (try? FileManager.default.attributesOfItem(atPath: path)[.size] as? NSNumber)?.int64Value ?? 0
                    if size > TextDocumentCodec.maxEditableSize { throw TextDocumentError.tooLarge(size) }
                    return try Data(contentsOf: URL(fileURLWithPath: path))
                }.value
                doc.setLoaded(try TextDocumentCodec.decode(data))
            } catch {
                doc.isLoading = false
                doc.loadError = PaneModel.describe(error)
            }
        }
    }

    func openRemote(item: FileItem, profile: ServerProfile) {
        let origin = EditorDocument.Origin.remote(profileID: profile.id, serverName: profile.displayName, path: item.path)
        if let existing = documents.first(where: { $0.origin == origin }) {
            activeID = existing.id
            return
        }
        let doc = EditorDocument(origin: origin, name: item.name)
        doc.remoteMode = item.unixMode
        doc.onDirtyChange = { [weak self] in self?.objectWillChange.send() }
        documents.append(doc)
        activeID = doc.id
        if let size = item.size, size > TextDocumentCodec.maxEditableSize {
            doc.isLoading = false
            doc.loadError = TextDocumentError.tooLarge(size).errorDescription
            return
        }
        Task { await loadRemote(doc) }
    }

    func reload(_ doc: EditorDocument) {
        doc.isLoading = true
        doc.loadError = nil
        switch doc.origin {
        case .local(let path):
            documents.removeAll { $0.id == doc.id }
            openLocal(path: path)
        case .remote:
            Task { await loadRemote(doc) }
        }
    }

    private func loadRemote(_ doc: EditorDocument) async {
        guard case .remote(let pid, _, let path) = doc.origin else { return }
        do {
            let pool = try sessions.pool(for: pid)
            let conn = await pool.lease()
            defer { Task { await pool.giveBack(conn) } }
            let (data, baseline) = try await RemoteTextFile.load(conn, path: path)
            doc.remoteBaselineSize = baseline.size
            doc.remoteBaselineDate = baseline.modified
            // Рабочая копия во временном хранилище.
            let copy = Self.cacheDir.appendingPathComponent(pid.uuidString + "-" + String(path.hashValue, radix: 16) + "-" + doc.name)
            try? data.write(to: copy)
            doc.workingCopyURL = copy
            doc.setLoaded(try TextDocumentCodec.decode(data))
        } catch {
            doc.isLoading = false
            doc.loadError = PaneModel.describe(error)
        }
    }

    // MARK: - Закрытие

    func close(_ doc: EditorDocument) {
        documents.removeAll { $0.id == doc.id }
        if activeID == doc.id { activeID = documents.last?.id }
        if let copy = doc.workingCopyURL { try? FileManager.default.removeItem(at: copy) }
    }

    // MARK: - Сохранение

    /// Сохраняет документ. Для FTP: проверка изменений на сервере → загрузка во временный файл →
    /// проверка размера → переименование поверх оригинала. Успех показывается только после подтверждения сервера.
    func save(_ doc: EditorDocument, force: Bool = false) async {
        guard !doc.readOnly, !doc.isLoading, doc.loadError == nil else { return }
        let data: Data
        do {
            data = try TextDocumentCodec.encode(doc.text, encoding: doc.encoding, lineEnding: doc.lineEnding)
        } catch {
            doc.saveState = .failed(PaneModel.describe(error))
            Haptics.error()
            return
        }
        switch doc.origin {
        case .local(let path):
            doc.saveState = .saving("Сохранение…")
            do {
                try await Task.detached {
                    let url = URL(fileURLWithPath: path)
                    try data.write(to: url, options: .atomic)
                }.value
                doc.markSaved()
                Haptics.success()
            } catch {
                doc.saveState = .failed(PaneModel.describe(error))
                Haptics.error()
            }
        case .remote(let pid, _, let path):
            doc.saveState = .saving("Проверка сервера…")
            if let copy = doc.workingCopyURL { try? data.write(to: copy, options: .atomic) }
            do {
                let pool = try sessions.pool(for: pid)
                let conn = await pool.lease()
                defer { Task { await pool.giveBack(conn) } }
                let baseline = RemoteFileBaseline(size: doc.remoteBaselineSize, modified: doc.remoteBaselineDate)
                let newBaseline = try await RemoteTextFile.save(conn, path: path, data: data, baseline: baseline,
                                                                mode: doc.remoteMode, force: force) { message in
                    Task { @MainActor in doc.saveState = .saving(message) }
                }
                doc.remoteBaselineSize = newBaseline.size
                doc.remoteBaselineDate = newBaseline.modified
                doc.markSaved()
                Haptics.success()
            } catch FTPError.remoteFileChanged {
                doc.saveState = .idle
                remoteConflict = doc
                Haptics.warning()
            } catch {
                doc.saveState = .failed("Не сохранено на сервере: \(PaneModel.describe(error)). Изменения остаются в редакторе — повторите сохранение.")
                Haptics.error()
            }
        }
    }

    /// Сохраняет копию рядом с оригиналом (при конфликте с изменениями на сервере).
    func saveAsCopy(_ doc: EditorDocument) async {
        guard case .remote(let pid, _, let path) = doc.origin else { return }
        do {
            let data = try TextDocumentCodec.encode(doc.text, encoding: doc.encoding, lineEnding: doc.lineEnding)
            let pool = try sessions.pool(for: pid)
            let conn = await pool.lease()
            defer { Task { await pool.giveBack(conn) } }
            let (base, ext) = PathUtils.splitExtension(doc.name)
            let stamp = Int(Date().timeIntervalSince1970)
            let target = PathUtils.child(PathUtils.parent(path), "\(base).twinftp-\(stamp)\(ext)")
            var sent = false
            try await conn.upload(target) {
                defer { sent = true }
                return sent ? nil : data
            }
            doc.saveState = .failed("Файл на сервере изменился. Ваша версия сохранена как «\(PathUtils.lastComponent(target))».")
        } catch {
            doc.saveState = .failed(PaneModel.describe(error))
        }
    }
}
