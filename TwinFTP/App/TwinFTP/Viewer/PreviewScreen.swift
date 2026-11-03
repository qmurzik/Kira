import SwiftUI
import TwinCore

/// Экран просмотра файла: локальный файл открывается сразу, удалённый — скачивается во временную папку.
struct PreviewScreen: View {
    let request: PreviewRequest
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var localURL: URL?
    @State private var error: String?
    @State private var progress: Int64 = 0
    @State private var showShare = false
    @State private var downloadTask: Task<Void, Never>?

    var body: some View {
        Group {
            if let url = localURL {
                QuickLookView(url: url, title: request.item.name)
                    .ignoresSafeArea()
                    .overlay(alignment: .bottomTrailing) {
                        HStack(spacing: 10) {
                            if SyntaxLanguage.isEditable(fileName: request.item.name) || isProbablyText {
                                Button {
                                    dismiss()
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                                        app.openInEditor(request.item, from: app.pane(request.paneID))
                                    }
                                } label: {
                                    Label("Редактировать", systemImage: "pencil")
                                }
                                .buttonStyle(.borderedProminent)
                            }
                            Button { showShare = true } label: { Image(systemName: "square.and.arrow.up") }
                                .buttonStyle(.bordered)
                                .accessibilityLabel("Поделиться")
                        }
                        .padding(20)
                    }
            } else if let error {
                NavigationStack {
                    StateView(symbol: "exclamationmark.triangle", title: "Не удалось открыть", message: error, actionTitle: "Закрыть") { dismiss() }
                }
            } else {
                NavigationStack {
                    VStack(spacing: 14) {
                        ProgressView(value: Double(progress), total: Double(max(1, request.item.size ?? 1)))
                            .frame(maxWidth: 240)
                        Text("Загрузка с сервера… \(ByteFormatter.string(progress))")
                            .font(.system(size: 13)).foregroundStyle(Theme.textSecondary)
                        Button("Отмена") { downloadTask?.cancel(); dismiss() }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .navigationTitle(request.item.name)
                    .navigationBarTitleDisplayMode(.inline)
                }
            }
        }
        .sheet(isPresented: $showShare) {
            if let localURL { ShareSheet(items: [localURL]) }
        }
        .task { await prepare() }
        .onDisappear { downloadTask?.cancel() }
    }

    private var isProbablyText: Bool {
        ["txt", "log", "csv", "md"].contains(request.item.fileExtension)
    }

    private func prepare() async {
        let pane = app.pane(request.paneID)
        switch pane.source {
        case .local:
            localURL = URL(fileURLWithPath: request.item.path)
        case .remote(let pid):
            let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("Preview", isDirectory: true)
                .appendingPathComponent(UUID().uuidString, isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let target = dir.appendingPathComponent(request.item.name)
            FileManager.default.createFile(atPath: target.path, contents: nil)
            guard let handle = FileHandle(forWritingAtPath: target.path) else { error = "Нет места для временного файла."; return }
            let task = Task {
                do {
                    let pool = try app.sessions.pool(for: pid)
                    let conn = await pool.lease()
                    defer { Task { await pool.giveBack(conn) } }
                    let counter = Locked(Int64(0))
                    try await conn.download(request.item.path) { chunk in
                        try handle.write(contentsOf: chunk)
                        let total = counter.withValue { $0 += Int64(chunk.count); return $0 }
                        await MainActor.run { progress = total }
                    }
                    try handle.close()
                    localURL = target
                } catch {
                    try? handle.close()
                    self.error = PaneModel.describe(error)
                }
            }
            downloadTask = task
            await task.value
        case .serverPicker:
            error = "Нет источника."
        }
    }
}
