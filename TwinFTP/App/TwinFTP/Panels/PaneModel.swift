import Foundation
import SwiftUI
import TwinCore

enum PaneSource: Equatable, Hashable {
    case local(rootID: String)
    case remote(profileID: UUID)
    /// Панель FTP без активного подключения (показывается список серверов).
    case serverPicker

    var isRemote: Bool { if case .remote = self { return true }; return false }
    var isLocal: Bool { if case .local = self { return true }; return false }
}

/// Состояние одной панели: источник, путь, история, выделение, сортировка, поиск.
@MainActor
final class PaneModel: ObservableObject, Identifiable {
    let id: String
    @Published private(set) var source: PaneSource
    @Published private(set) var path: String = "/"
    @Published private(set) var items: [FileItem] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?
    @Published private(set) var lastLoaded: Date?
    @Published var selection: Set<String> = []
    @Published var selectionMode = false
    @Published var sort = FileSortOrder()
    @Published var searchText = ""
    @Published var isSearching = false
    @Published private(set) var canGoBack = false
    @Published private(set) var canGoForward = false

    private var backStack: [String] = []
    private var forwardStack: [String] = []
    private var loadToken = UUID()
    private var lastPathBySource: [PaneSource: String] = [:]
    weak var app: AppModel?

    init(id: String, source: PaneSource) {
        self.id = id
        self.source = source
        if let data = UserDefaults.standard.data(forKey: "sort.\(id)"),
           let s = try? JSONDecoder().decode(FileSortOrder.self, from: data) {
            sort = s
        }
    }

    // MARK: - Производные свойства

    var settings: AppSettings? { app?.settings }

    var visibleItems: [FileItem] {
        var list = items
        if !(settings?.showHiddenFiles ?? true) { list = list.filter { !$0.isHidden } }
        // Временные файлы передачи не показываем.
        list = list.filter { !$0.name.hasSuffix(TransferEngine.partSuffix) && !$0.name.hasPrefix(".twinftp-") }
        let q = searchText.trimmingCharacters(in: .whitespaces)
        if !q.isEmpty { list = list.filter { $0.name.localizedCaseInsensitiveContains(q) } }
        return sort.sort(list)
    }

    var selectedItems: [FileItem] { items.filter { selection.contains($0.path) } }

    var localRoot: LocalRoot? {
        if case .local(let id) = source { return app?.roots.root(id) }
        return nil
    }

    var remoteProfile: ServerProfile? {
        if case .remote(let id) = source { return app?.servers.profile(id) }
        return nil
    }

    var session: RemoteSession? {
        if case .remote(let id) = source { return app?.sessions.existing(id) }
        return nil
    }

    var rootPath: String {
        switch source {
        case .local: return localRoot?.path ?? "/"
        default: return "/"
        }
    }

    var sourceKey: String {
        switch source {
        case .local(let id): return "local:\(id)"
        case .remote(let id): return "ftp:\(id.uuidString)"
        case .serverPicker: return "none"
        }
    }

    var sourceTitle: String {
        switch source {
        case .local: return localRoot?.name ?? "iPhone"
        case .remote: return remoteProfile?.displayName ?? "FTP"
        case .serverPicker: return "FTP"
        }
    }

    var sourceSymbol: String {
        switch source {
        case .local: return localRoot?.symbol ?? "iphone"
        case .remote: return "server.rack"
        case .serverPicker: return "server.rack"
        }
    }

    /// Хлебные крошки относительно корня источника.
    var breadcrumbs: [(name: String, path: String)] {
        let root = rootPath
        guard let rel = PathUtils.relative(path, to: root) else { return [(sourceTitle, path)] }
        var result: [(String, String)] = [(source.isLocal ? sourceTitle : "/", root)]
        var current = root
        for comp in rel.split(separator: "/") where !comp.isEmpty {
            current = PathUtils.child(current, String(comp))
            result.append((String(comp), current))
        }
        return result
    }

    var displayPath: String {
        if source.isLocal, let rel = PathUtils.relative(path, to: rootPath) {
            return "/" + rel
        }
        return path
    }

    var canGoUp: Bool { path != rootPath && PathUtils.isDescendant(path, of: rootPath) }

    // MARK: - Смена источника

    func setSource(_ newSource: PaneSource, path newPath: String? = nil) {
        if source != .serverPicker { lastPathBySource[source] = path }
        source = newSource
        selection.removeAll()
        selectionMode = false
        searchText = ""
        isSearching = false
        backStack.removeAll()
        forwardStack.removeAll()
        updateHistoryFlags()
        items = []
        error = nil
        switch newSource {
        case .local:
            let target = newPath ?? lastPathBySource[newSource] ?? rootPath
            Task { await load(target, recordHistory: false) }
        case .remote(let id):
            let initial = app?.servers.profile(id)?.initialPath ?? ""
            let target = newPath ?? lastPathBySource[newSource] ?? (initial.isEmpty ? nil : initial)
            Task { await connectAndLoad(target) }
        case .serverPicker:
            path = "/"
        }
    }

    func persistSort() {
        if let data = try? JSONEncoder().encode(sort) { UserDefaults.standard.set(data, forKey: "sort.\(id)") }
    }

    // MARK: - Загрузка

    private func connectAndLoad(_ target: String?) async {
        guard let session else {
            error = "Нет данных для подключения. Откройте сервер из списка."
            return
        }
        isLoading = true
        error = nil
        do {
            try await session.run { try await $0.connect() }
            app?.servers.markConnected(session.profile.id)
            let home = session.browse.homeDirectory
            await load(target ?? home, recordHistory: false)
        } catch {
            isLoading = false
            self.error = Self.describe(error)
        }
    }

    func load(_ newPath: String, recordHistory: Bool = true) async {
        let token = UUID()
        loadToken = token
        isLoading = true
        error = nil
        let normalized = PathUtils.normalize(newPath)
        do {
            let list: [FileItem]
            switch source {
            case .local:
                let root = rootPath
                let target = PathUtils.isDescendant(normalized, of: root) ? normalized : root
                list = try await Task.detached { try LocalFileSystem().list(target) }.value
                guard token == loadToken else { return }
                commit(path: target, items: list, recordHistory: recordHistory)
                return
            case .remote:
                guard let session else { throw FTPError.notConnected }
                list = try await session.run { try await $0.list(normalized) }
            case .serverPicker:
                list = []
            }
            guard token == loadToken else { return }
            commit(path: normalized, items: list, recordHistory: recordHistory)
        } catch {
            guard token == loadToken else { return }
            isLoading = false
            self.error = Self.describe(error)
            Haptics.error()
        }
    }

    private func commit(path newPath: String, items list: [FileItem], recordHistory: Bool) {
        if recordHistory && newPath != path {
            backStack.append(path)
            forwardStack.removeAll()
        }
        if newPath != path {
            selection.removeAll()
            searchText = ""
        } else {
            // Сохраняем выделение только для существующих элементов
            let paths = Set(list.map(\.path))
            selection = selection.intersection(paths)
        }
        path = newPath
        items = list
        isLoading = false
        error = nil
        lastLoaded = Date()
        updateHistoryFlags()
    }

    func refresh() {
        Task { await load(path, recordHistory: false) }
    }

    func navigate(to newPath: String) {
        Task { await load(newPath) }
    }

    func goUp() {
        guard canGoUp else { return }
        Haptics.selection()
        navigate(to: PathUtils.parent(path))
    }

    func goBack() {
        guard let prev = backStack.popLast() else { return }
        forwardStack.append(path)
        updateHistoryFlags()
        Haptics.selection()
        Task { await load(prev, recordHistory: false) }
    }

    func goForward() {
        guard let next = forwardStack.popLast() else { return }
        backStack.append(path)
        updateHistoryFlags()
        Haptics.selection()
        Task { await load(next, recordHistory: false) }
    }

    private func updateHistoryFlags() {
        canGoBack = !backStack.isEmpty
        canGoForward = !forwardStack.isEmpty
    }

    /// Переход по введённому пути (относительно корня для локальной панели).
    func jump(to text: String) {
        var t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return }
        if source.isLocal {
            if !t.hasPrefix(rootPath) {
                if !t.hasPrefix("/") { t = "/" + t }
                t = PathUtils.normalize(rootPath + t)
            }
        } else if !t.hasPrefix("/") {
            t = PathUtils.join(path, t)
        }
        navigate(to: t)
    }

    // MARK: - Выделение

    func toggleSelection(_ item: FileItem) {
        if selection.contains(item.path) { selection.remove(item.path) } else { selection.insert(item.path) }
        Haptics.selection()
    }

    func selectAll() { selection = Set(visibleItems.map(\.path)) }
    func clearSelection() { selection.removeAll() }

    func exitSelectionMode() {
        selectionMode = false
        selection.removeAll()
    }

    /// Элементы для действия: выделенные, иначе переданный элемент.
    func targets(for item: FileItem?) -> [FileItem] {
        if let item {
            if selection.contains(item.path) && selection.count > 1 { return selectedItems }
            return [item]
        }
        return selectedItems
    }

    // MARK: - Файловые операции

    func makeDirectory(named name: String) async throws {
        if let problem = PathUtils.validateName(name) { throw LocalFileError.invalidName(problem) }
        let target = PathUtils.child(path, name)
        switch source {
        case .local:
            try LocalFileSystem().makeDirectory(target)
        case .remote:
            guard let session else { throw FTPError.notConnected }
            if items.contains(where: { $0.name == name }) { throw LocalFileError.alreadyExists(name) }
            try await session.run { try await $0.makeDirectory(target) }
        case .serverPicker: return
        }
        await load(path, recordHistory: false)
        selection = [target]
    }

    func createFile(named name: String) async throws -> FileItem {
        if let problem = PathUtils.validateName(name) { throw LocalFileError.invalidName(problem) }
        let target = PathUtils.child(path, name)
        if items.contains(where: { $0.name == name }) { throw LocalFileError.alreadyExists(name) }
        switch source {
        case .local:
            try LocalFileSystem().createFile(target)
        case .remote:
            guard let session else { throw FTPError.notConnected }
            try await session.run { conn in
                var sent = false
                try await conn.upload(target) {
                    defer { sent = true }
                    return sent ? nil : Data()
                }
            }
        case .serverPicker: break
        }
        await load(path, recordHistory: false)
        return items.first { $0.path == target } ?? FileItem(name: name, path: target, kind: .file, size: 0)
    }

    func rename(_ item: FileItem, to newName: String) async throws {
        if let problem = PathUtils.validateName(newName) { throw LocalFileError.invalidName(problem) }
        if newName == item.name { return }
        switch source {
        case .local:
            _ = try LocalFileSystem().rename(item.path, to: newName)
        case .remote:
            guard let session else { throw FTPError.notConnected }
            if items.contains(where: { $0.name == newName }) { throw LocalFileError.alreadyExists(newName) }
            let target = PathUtils.child(PathUtils.parent(item.path), newName)
            try await session.run { try await $0.rename(from: item.path, to: target) }
        case .serverPicker: break
        }
        await load(path, recordHistory: false)
    }

    /// Удаление (рекурсивно для папок). Возвращает список ошибок по элементам.
    func delete(_ targets: [FileItem], progress: @escaping (String) -> Void) async -> [String] {
        var errors: [String] = []
        for item in targets {
            do {
                switch source {
                case .local:
                    try LocalFileSystem().delete(item.path)
                case .remote:
                    guard let session else { throw FTPError.notConnected }
                    try await session.run { conn in
                        try await RemoteTree.delete(conn, item: item) { name in
                            Task { @MainActor in progress(name) }
                        }
                    }
                case .serverPicker: break
                }
            } catch {
                errors.append("\(item.name): \(Self.describe(error))")
            }
        }
        selection.subtract(targets.map(\.path))
        await load(path, recordHistory: false)
        return errors
    }

    /// Права доступа (только FTP).
    func chmod(_ item: FileItem, mode: Int) async throws {
        guard let session else { throw FTPError.notConnected }
        try await session.run { try await $0.chmod(item.path, mode: mode) }
        await load(path, recordHistory: false)
    }

    func didFinishTransfer(touching directory: String) {
        if directory == path || PathUtils.isDescendant(directory, of: path) || PathUtils.isDescendant(path, of: directory) {
            refresh()
        }
    }

    static func describe(_ error: Error) -> String {
        if let e = error as? LocalizedError, let d = e.errorDescription { return d }
        return error.localizedDescription
    }
}
