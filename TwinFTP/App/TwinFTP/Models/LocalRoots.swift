import Foundation
import TwinCore

/// Корневая локальная папка: папка приложения или папка, выбранная пользователем через «Файлы».
struct LocalRoot: Identifiable, Hashable {
    enum Kind: Hashable { case appDocuments, bookmark }
    let id: String
    var name: String
    var url: URL
    var kind: Kind
    var isStale = false

    var path: String { url.standardizedFileURL.path }
    var symbol: String { kind == .appDocuments ? "iphone" : (url.path.contains("Mobile Documents") ? "icloud" : "folder.badge.person.crop") }
}

/// Управление доступом к папкам вне песочницы через security-scoped bookmarks.
@MainActor
final class LocalRootStore: ObservableObject {
    @Published private(set) var roots: [LocalRoot] = []
    private var accessing: [String: URL] = [:]
    private let defaultsKey = "localRootBookmarks.v1"

    private struct Saved: Codable {
        var id: String
        var name: String
        var bookmark: Data
    }

    static let documentsURL: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    static var inboxURL: URL { documentsURL.appendingPathComponent("Входящие", isDirectory: true) }

    init() {
        prepareDocuments()
        reload()
    }

    private func prepareDocuments() {
        let fm = FileManager.default
        try? fm.createDirectory(at: Self.documentsURL, withIntermediateDirectories: true)
        // Подсказка для пустой папки при первом запуске.
        let marker = Self.documentsURL.appendingPathComponent(".twinftp-initialized")
        if !fm.fileExists(atPath: marker.path) {
            try? fm.createDirectory(at: Self.documentsURL.appendingPathComponent("Сайты"), withIntermediateDirectories: true)
            try? fm.createDirectory(at: Self.inboxURL, withIntermediateDirectories: true)
            fm.createFile(atPath: marker.path, contents: Data())
        }
    }

    func reload() {
        var result = [LocalRoot(id: "documents", name: "На iPhone", url: Self.documentsURL, kind: .appDocuments)]
        for saved in loadSaved() {
            var stale = false
            guard let url = try? URL(resolvingBookmarkData: saved.bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) else {
                result.append(LocalRoot(id: saved.id, name: saved.name + " (недоступна)", url: URL(fileURLWithPath: "/"), kind: .bookmark, isStale: true))
                continue
            }
            if accessing[saved.id] == nil, url.startAccessingSecurityScopedResource() {
                accessing[saved.id] = url
            }
            if stale, let fresh = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) {
                update(id: saved.id, bookmark: fresh)
            }
            result.append(LocalRoot(id: saved.id, name: saved.name, url: url, kind: .bookmark))
        }
        roots = result
    }

    private func loadSaved() -> [Saved] {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey) else { return [] }
        return (try? JSONDecoder().decode([Saved].self, from: data)) ?? []
    }

    private func store(_ list: [Saved]) {
        UserDefaults.standard.set(try? JSONEncoder().encode(list), forKey: defaultsKey)
    }

    private func update(id: String, bookmark: Data) {
        var list = loadSaved()
        if let i = list.firstIndex(where: { $0.id == id }) { list[i].bookmark = bookmark }
        store(list)
    }

    /// Добавляет папку, выбранную в UIDocumentPicker. Возвращает новый корень.
    @discardableResult
    func addFolder(_ url: URL) throws -> LocalRoot {
        let started = url.startAccessingSecurityScopedResource()
        defer { if started { url.stopAccessingSecurityScopedResource() } }
        let bookmark = try url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        var list = loadSaved()
        if let existing = roots.first(where: { $0.path == url.standardizedFileURL.path }) { return existing }
        let id = UUID().uuidString
        list.append(Saved(id: id, name: url.lastPathComponent, bookmark: bookmark))
        store(list)
        reload()
        return roots.first { $0.id == id } ?? roots[0]
    }

    func remove(_ id: String) {
        if let url = accessing[id] { url.stopAccessingSecurityScopedResource(); accessing[id] = nil }
        store(loadSaved().filter { $0.id != id })
        reload()
    }

    func rename(_ id: String, to name: String) {
        var list = loadSaved()
        if let i = list.firstIndex(where: { $0.id == id }) { list[i].name = name }
        store(list)
        reload()
    }

    func root(_ id: String) -> LocalRoot? { roots.first { $0.id == id } }

    /// Корень, которому принадлежит путь.
    func root(containing path: String) -> LocalRoot? {
        roots.filter { !$0.isStale && PathUtils.isDescendant(path, of: $0.path) }.max { $0.path.count < $1.path.count }
    }
}
