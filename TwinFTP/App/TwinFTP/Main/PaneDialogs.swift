import SwiftUI
import TwinCore

/// Обработка диалогов панелей: ввод имён, удаление, свойства, права, упаковка.
struct PaneDialogsHost: ViewModifier {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @Binding var dialog: PaneDialog?
    @State private var input = ""

    private var textDialogActive: Bool {
        switch dialog {
        case .newFolder, .newFile, .rename, .jump, .zip: return true
        default: return false
        }
    }

    private var deleteTargets: [FileItem]? {
        if case .delete(_, let items) = dialog { return items }
        return nil
    }

    func body(content: Content) -> some View {
        content
            .alert(textTitle, isPresented: Binding(get: { textDialogActive }, set: { if !$0 { dialog = nil } })) {
                TextField(textPlaceholder, text: $input)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button(textConfirm) { confirmText() }
                Button("Отмена", role: .cancel) { dialog = nil }
            } message: {
                Text(textMessage)
            }
            .onChange(of: dialog?.id) { _, _ in
                prefill()
                if deleteTargets != nil && !settings.confirmDelete { performDelete() }
            }
            .confirmationDialog(deleteTitle, isPresented: Binding(get: { deleteTargets != nil && settings.confirmDelete }, set: { if !$0 { dialog = nil } }),
                                titleVisibility: .visible) {
                Button("Удалить", role: .destructive) { performDelete() }
                Button("Отмена", role: .cancel) { dialog = nil }
            } message: {
                Text(deleteMessage)
            }
            .sheet(item: Binding(get: { infoItem }, set: { if $0 == nil { dialog = nil } })) { wrapper in
                FileInfoView(item: wrapper.item, pane: app.pane(wrapper.paneID))
            }
            .sheet(item: Binding(get: { chmodItem }, set: { if $0 == nil { dialog = nil } })) { wrapper in
                ChmodView(item: wrapper.item, pane: app.pane(wrapper.paneID))
            }
    }

    // MARK: - Текстовые диалоги

    private var textTitle: String {
        switch dialog {
        case .newFolder: return "Новая папка"
        case .newFile: return "Новый файл"
        case .rename(_, let item): return item.isDirectory ? "Переименовать папку" : "Переименовать файл"
        case .jump: return "Перейти по пути"
        case .zip: return "Сжать в ZIP"
        default: return ""
        }
    }

    private var textPlaceholder: String {
        switch dialog {
        case .newFolder: return "Имя папки"
        case .newFile: return "index.php"
        case .jump: return "/public_html"
        case .zip: return "archive.zip"
        default: return "Новое имя"
        }
    }

    private var textConfirm: String {
        switch dialog {
        case .newFolder, .newFile: return "Создать"
        case .rename: return "Переименовать"
        case .jump: return "Перейти"
        case .zip: return "Сжать"
        default: return "OK"
        }
    }

    private var textMessage: String {
        switch dialog {
        case .jump(let pid, _):
            return app.pane(pid).source.isLocal ? "Путь от корня «\(app.pane(pid).sourceTitle)»" : "Абсолютный путь на сервере или относительно текущей папки"
        case .zip(_, let items): return "Будет упаковано: \(items.count) \(RussianPlural.objects(items.count))"
        case .rename(_, let item): return item.name
        default: return ""
        }
    }

    private func prefill() {
        switch dialog {
        case .rename(_, let item): input = item.name
        case .jump(_, let path): input = path
        case .zip(_, let items):
            input = items.count == 1 ? PathUtils.splitExtension(items[0].name).base + ".zip" : "archive.zip"
        case .newFile: input = ""
        case .newFolder: input = ""
        default: break
        }
    }

    private func confirmText() {
        guard let d = dialog else { return }
        let text = input.trimmingCharacters(in: .whitespaces)
        dialog = nil
        let pane = app.pane(d.paneID)
        Task {
            do {
                switch d {
                case .newFolder:
                    try await pane.makeDirectory(named: text)
                    Haptics.success()
                case .newFile:
                    let item = try await pane.createFile(named: text)
                    Haptics.success()
                    if SyntaxLanguage.isEditable(fileName: item.name) { app.openInEditor(item, from: pane) }
                case .rename(_, let item):
                    try await pane.rename(item, to: text)
                    Haptics.success()
                case .jump:
                    pane.jump(to: text)
                case .zip(_, let items):
                    await createZip(items, name: text, in: pane)
                default: break
                }
            } catch {
                app.showError(error)
            }
        }
    }

    private func createZip(_ items: [FileItem], name: String, in pane: PaneModel) async {
        var fileName = name.isEmpty ? "archive.zip" : name
        if !fileName.lowercased().hasSuffix(".zip") { fileName += ".zip" }
        if let problem = PathUtils.validateName(fileName) { app.show(problem, style: .error); return }
        let existing = Set(pane.items.map(\.name))
        fileName = PathUtils.uniqueName(for: fileName, existing: existing)
        let target = URL(fileURLWithPath: PathUtils.child(pane.path, fileName))
        let paths = items.map(\.path)
        app.busyMessage = "Упаковка в ZIP…"
        let progress = BusyProgress.shared
        progress.reset()
        do {
            try await Task.detached(priority: .userInitiated) {
                try ZipArchiveWriter.create(at: target, from: paths, isCancelled: { progress.isCancelled }) { done, total, _ in
                    progress.update(done: done, total: total)
                }
            }.value
            app.busyMessage = nil
            pane.refresh()
            app.show("Создан архив «\(fileName)»", style: .success)
            Haptics.success()
        } catch {
            app.busyMessage = nil
            app.showError(error)
        }
    }

    // MARK: - Удаление

    private var deleteTitle: String {
        guard let items = deleteTargets else { return "" }
        if items.count == 1 { return "Удалить «\(items[0].name)»?" }
        return "Удалить \(items.count) \(RussianPlural.objects(items.count))?"
    }

    private var deleteMessage: String {
        guard let d = dialog, case .delete(let pid, let items) = d else { return "" }
        let remote = app.pane(pid).source.isRemote
        let hasDirs = items.contains { $0.isDirectory }
        var msg = remote ? "Файлы будут удалены с сервера без возможности восстановления." : "Файлы будут удалены с iPhone без возможности восстановления."
        if hasDirs { msg += " Папки удаляются вместе со всем содержимым." }
        return msg
    }

    private func performDelete() {
        guard let d = dialog, case .delete(let pid, let items) = d else { return }
        dialog = nil
        let pane = app.pane(pid)
        app.busyMessage = "Удаление…"
        Task {
            let errors = await pane.delete(items) { name in
                app.busyMessage = "Удаление: \(name)"
            }
            app.busyMessage = nil
            if errors.isEmpty {
                app.show("Удалено: \(items.count) \(RussianPlural.objects(items.count))", style: .success)
                Haptics.success()
            } else {
                app.show(errors.joined(separator: "\n"), style: .error)
            }
            pane.exitSelectionMode()
        }
    }

    // MARK: - Листы

    struct ItemWrapper: Identifiable {
        let item: FileItem
        let paneID: String
        var id: String { paneID + item.path }
    }

    private var infoItem: ItemWrapper? {
        if case .info(let pid, let item) = dialog { return ItemWrapper(item: item, paneID: pid) }
        return nil
    }

    private var chmodItem: ItemWrapper? {
        if case .chmod(let pid, let item) = dialog { return ItemWrapper(item: item, paneID: pid) }
        return nil
    }
}

/// Прогресс длительной операции (упаковка, распаковка) для оверлея.
final class BusyProgress: ObservableObject, @unchecked Sendable {
    static let shared = BusyProgress()
    @Published var fraction: Double = 0
    private let lock = NSLock()
    private var cancelled = false
    private var lastPublish = Date.distantPast

    var isCancelled: Bool { lock.lock(); defer { lock.unlock() }; return cancelled }

    func cancel() { lock.lock(); cancelled = true; lock.unlock() }

    func reset() {
        lock.lock(); cancelled = false; lock.unlock()
        DispatchQueue.main.async { self.fraction = 0 }
    }

    func update(done: Int64, total: Int64) {
        let now = Date()
        lock.lock()
        let shouldPublish = now.timeIntervalSince(lastPublish) > 0.1 || done == total
        if shouldPublish { lastPublish = now }
        lock.unlock()
        guard shouldPublish else { return }
        let f = total > 0 ? Double(done) / Double(total) : 0
        DispatchQueue.main.async { self.fraction = f }
    }
}

/// Свойства файла.
struct FileInfoView: View {
    let item: FileItem
    @ObservedObject var pane: PaneModel
    @Environment(\.dismiss) private var dismiss
    @State private var folderSize: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        FileIconView(item: item, size: 40)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.name).font(.headline).textSelection(.enabled)
                            Text(item.isDirectory ? "Папка" : (item.fileExtension.isEmpty ? "Файл" : item.fileExtension.uppercased() + "-файл"))
                                .font(.subheadline).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    .padding(.vertical, 6)
                }
                Section {
                    row("Расположение", pane.source.isRemote ? "\(pane.sourceTitle): \(PathUtils.parent(item.path))" : PathUtils.parent(item.path))
                    if let size = item.size, !item.isDirectory {
                        row("Размер", "\(ByteFormatter.string(size)) (\(size.formatted()) байт)")
                    }
                    if item.isDirectory && pane.source.isLocal {
                        row("Размер", folderSize ?? "Подсчёт…")
                    }
                    if let date = item.modified { row("Изменён", DateText.long.string(from: date)) }
                    if let perms = item.permissions {
                        row("Права", perms + (item.unixMode.map { " (\(String($0, radix: 8)))" } ?? ""))
                    }
                    if let target = item.linkTarget { row("Ссылка на", target) }
                }
            }
            .navigationTitle("Свойства")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Готово") { dismiss() } } }
            .task {
                guard item.isDirectory, pane.source.isLocal else { return }
                let path = item.path
                let total = await Task.detached { () -> (Int64, Int) in
                    let all = (try? LocalFileSystem().walk(path)) ?? []
                    return (all.reduce(Int64(0)) { $0 + ($1.isDirectory ? 0 : ($1.size ?? 0)) }, all.filter { !$0.isDirectory }.count)
                }.value
                folderSize = "\(ByteFormatter.string(total.0)), \(total.1) \(RussianPlural.files(total.1))"
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(title).foregroundStyle(Theme.textSecondary)
            Spacer()
            Text(value).multilineTextAlignment(.trailing).textSelection(.enabled)
        }
        .font(.system(size: 14))
    }
}

/// Изменение прав доступа на сервере (SITE CHMOD).
struct ChmodView: View {
    let item: FileItem
    @ObservedObject var pane: PaneModel
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var mode: Int = 0o644
    @State private var saving = false

    private func octal(_ v: Int) -> String {
        let s = String(v, radix: 8)
        return String(repeating: "0", count: max(0, 3 - s.count)) + s
    }

    private let groups = [("Владелец", 6), ("Группа", 3), ("Остальные", 0)]
    private let bits = [("Чтение", 4), ("Запись", 2), ("Выполнение", 1)]

    var body: some View {
        NavigationStack {
            Form {
                ForEach(groups, id: \.0) { group in
                    Section(group.0) {
                        ForEach(bits, id: \.0) { bit in
                            Toggle(bit.0, isOn: Binding(
                                get: { mode & (bit.1 << group.1) != 0 },
                                set: { on in if on { mode |= bit.1 << group.1 } else { mode &= ~(bit.1 << group.1) } }))
                        }
                    }
                }
                Section {
                    HStack {
                        Text("Восьмеричный вид")
                        Spacer()
                        Text(octal(mode)).font(.system(.body, design: .monospaced))
                        Text(UnixPermissions.string(fromMode: mode)).font(.system(.caption, design: .monospaced)).foregroundStyle(Theme.textSecondary)
                    }
                    HStack {
                        ForEach([0o644, 0o755, 0o600, 0o777], id: \.self) { preset in
                            Button(octal(preset)) { mode = preset }.buttonStyle(.bordered)
                        }
                    }
                }
            }
            .navigationTitle(item.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    if saving { ProgressView() } else {
                        Button("Применить") {
                            saving = true
                            Task {
                                do {
                                    try await pane.chmod(item, mode: mode)
                                    Haptics.success()
                                    dismiss()
                                } catch {
                                    app.showError(error)
                                    saving = false
                                }
                            }
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
            .onAppear { mode = item.unixMode ?? (item.isDirectory ? 0o755 : 0o644) }
        }
    }
}
