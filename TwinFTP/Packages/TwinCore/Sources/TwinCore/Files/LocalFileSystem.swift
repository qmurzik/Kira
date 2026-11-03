import Foundation

public enum LocalFileError: Error, LocalizedError, Equatable {
    case alreadyExists(String)
    case notFound(String)
    case invalidName(String)
    case cannotMoveIntoItself(String)
    case io(String)

    public var errorDescription: String? {
        switch self {
        case .alreadyExists(let n): return "Объект «\(n)» уже существует."
        case .notFound(let p): return "Не найдено: \(p)"
        case .invalidName(let r): return r
        case .cannotMoveIntoItself(let n): return "Нельзя скопировать или переместить «\(n)» внутрь самой себя."
        case .io(let s): return s
        }
    }
}

/// Операции с локальными файлами (внутри песочницы и разрешённых пользователем папок).
public struct LocalFileSystem: Sendable {
    public init() {}

    private var fm: FileManager { FileManager.default }

    public func item(at url: URL) -> FileItem? {
        let path = url.path
        guard let attrs = try? fm.attributesOfItem(atPath: path) else { return nil }
        return Self.makeItem(path: path, attrs: attrs, fm: fm)
    }

    static func makeItem(path: String, attrs: [FileAttributeKey: Any], fm: FileManager) -> FileItem {
        let type = attrs[.type] as? FileAttributeType
        var kind: FileKind = .file
        var linkTarget: String?
        var linkIsDir: Bool?
        switch type {
        case .typeDirectory?: kind = .directory
        case .typeSymbolicLink?:
            kind = .symlink
            linkTarget = try? fm.destinationOfSymbolicLink(atPath: path)
            var isDir: ObjCBool = false
            if fm.fileExists(atPath: path, isDirectory: &isDir) { linkIsDir = isDir.boolValue }
        case .typeRegular?: kind = .file
        default: kind = .other
        }
        let mode = (attrs[.posixPermissions] as? NSNumber)?.intValue
        let size = (attrs[.size] as? NSNumber)?.int64Value
        return FileItem(name: (path as NSString).lastPathComponent,
                        path: path,
                        kind: kind,
                        size: kind == .directory ? nil : size,
                        modified: attrs[.modificationDate] as? Date,
                        permissions: mode.map { UnixPermissions.string(fromMode: $0 & 0o777) },
                        unixMode: mode.map { $0 & 0o777 },
                        linkTarget: linkTarget,
                        linkPointsToDirectory: linkIsDir)
    }

    public func list(_ path: String) throws -> [FileItem] {
        let names: [String]
        do {
            names = try fm.contentsOfDirectory(atPath: path)
        } catch {
            throw LocalFileError.io("Не удалось открыть папку: \(error.localizedDescription)")
        }
        var result: [FileItem] = []
        result.reserveCapacity(names.count)
        for name in names {
            let full = PathUtils.child(path, name)
            if let attrs = try? fm.attributesOfItem(atPath: full) {
                result.append(Self.makeItem(path: full, attrs: attrs, fm: fm))
            }
        }
        return result
    }

    public func exists(_ path: String) -> Bool { fm.fileExists(atPath: path) }

    public func isDirectory(_ path: String) -> Bool {
        var isDir: ObjCBool = false
        return fm.fileExists(atPath: path, isDirectory: &isDir) && isDir.boolValue
    }

    public func makeDirectory(_ path: String) throws {
        if exists(path) { throw LocalFileError.alreadyExists(PathUtils.lastComponent(path)) }
        try fm.createDirectory(atPath: path, withIntermediateDirectories: true)
    }

    public func ensureDirectory(_ path: String) throws {
        if isDirectory(path) { return }
        try fm.createDirectory(atPath: path, withIntermediateDirectories: true)
    }

    public func createFile(_ path: String, contents: Data = Data()) throws {
        if exists(path) { throw LocalFileError.alreadyExists(PathUtils.lastComponent(path)) }
        guard fm.createFile(atPath: path, contents: contents) else {
            throw LocalFileError.io("Не удалось создать файл «\(PathUtils.lastComponent(path))».")
        }
    }

    public func delete(_ path: String) throws {
        do { try fm.removeItem(atPath: path) } catch {
            throw LocalFileError.io("Не удалось удалить «\(PathUtils.lastComponent(path))»: \(error.localizedDescription)")
        }
    }

    public func rename(_ path: String, to newName: String) throws -> String {
        if let problem = PathUtils.validateName(newName) { throw LocalFileError.invalidName(problem) }
        let target = PathUtils.child(PathUtils.parent(path), newName)
        if target == path { return path }
        // Переименование с изменением только регистра на нечувствительной к регистру ФС.
        if target.lowercased() == path.lowercased() {
            let temp = PathUtils.child(PathUtils.parent(path), ".twinftp-rename-\(UUID().uuidString)")
            try fm.moveItem(atPath: path, toPath: temp)
            try fm.moveItem(atPath: temp, toPath: target)
            return target
        }
        if exists(target) { throw LocalFileError.alreadyExists(newName) }
        try fm.moveItem(atPath: path, toPath: target)
        return target
    }

    /// Копирует или перемещает объект в папку. Возвращает итоговый путь.
    public func transfer(_ source: String, into directory: String, newName: String? = nil,
                         move: Bool, replace: Bool) throws -> String {
        let name = newName ?? PathUtils.lastComponent(source)
        let target = PathUtils.child(directory, name)
        if PathUtils.isDescendant(target, of: source) && target != source {
            throw LocalFileError.cannotMoveIntoItself(name)
        }
        if target == source { return target }
        if exists(target) {
            guard replace else { throw LocalFileError.alreadyExists(name) }
            // Сначала копируем во временное имя — исходник цели удаляется только после успеха.
            let temp = PathUtils.child(directory, ".\(name).twinftp-part")
            try? fm.removeItem(atPath: temp)
            if move { try fm.moveItem(atPath: source, toPath: temp) } else { try fm.copyItem(atPath: source, toPath: temp) }
            try Self.replaceItem(at: target, with: temp)
            return target
        }
        if move { try fm.moveItem(atPath: source, toPath: target) } else { try fm.copyItem(atPath: source, toPath: target) }
        return target
    }

    /// Рекурсивный обход: возвращает все вложенные элементы (каталоги раньше их содержимого).
    /// Символические ссылки на каталоги не раскрываются (защита от циклов).
    public func walk(_ root: String, limit: Int = 500_000) throws -> [FileItem] {
        var result: [FileItem] = []
        var stack = [root]
        while let dir = stack.popLast() {
            let items = try list(dir).sorted { $0.name < $1.name }
            for item in items {
                result.append(item)
                if result.count > limit { throw LocalFileError.io("Слишком много файлов (более \(limit)).") }
                if item.kind == .directory { stack.append(item.path) }
            }
        }
        return result
    }

    /// Атомарная (насколько возможно) замена: цель переименовывается в резервную копию,
    /// новый файл ставится на её место, резервная копия удаляется. При ошибке цель восстанавливается.
    public static func replaceItem(at target: String, with source: String) throws {
        let fm = FileManager.default
        guard fm.fileExists(atPath: target) else {
            try fm.moveItem(atPath: source, toPath: target)
            return
        }
        let backup = PathUtils.child(PathUtils.parent(target), "." + PathUtils.lastComponent(target) + ".twinftp-old-" + UUID().uuidString.prefix(8))
        try fm.moveItem(atPath: target, toPath: backup)
        do {
            try fm.moveItem(atPath: source, toPath: target)
        } catch {
            try? fm.moveItem(atPath: backup, toPath: target)
            throw error
        }
        try? fm.removeItem(atPath: backup)
    }

    public func freeSpace(at path: String) -> Int64? {
        #if os(iOS)
        let url = URL(fileURLWithPath: path)
        if let values = try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]),
           let cap = values.volumeAvailableCapacityForImportantUsage {
            return cap
        }
        #endif
        if let attrs = try? fm.attributesOfFileSystem(forPath: path),
           let free = attrs[.systemFreeSize] as? NSNumber {
            return free.int64Value
        }
        return nil
    }

    public func setModificationDate(_ date: Date, path: String) {
        try? fm.setAttributes([.modificationDate: date], ofItemAtPath: path)
    }
}
