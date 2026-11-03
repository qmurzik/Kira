import Foundation

public enum FileKind: String, Codable, Sendable, Hashable {
    case file
    case directory
    case symlink
    case other
}

/// Элемент файлового списка — общая модель для локальной и удалённой панели.
public struct FileItem: Hashable, Identifiable, Sendable, Codable {
    public var name: String
    /// Абсолютный путь (POSIX). Для локальных файлов — путь в файловой системе, для FTP — путь на сервере.
    public var path: String
    public var kind: FileKind
    public var size: Int64?
    public var modified: Date?
    /// Права в виде строки "rwxr-xr-x" (если известны).
    public var permissions: String?
    /// Числовой режим UNIX (если известен), например 0o644.
    public var unixMode: Int?
    public var linkTarget: String?
    /// Для символьных ссылок: указывает ли ссылка на каталог (если удалось определить).
    public var linkPointsToDirectory: Bool?

    public init(name: String, path: String, kind: FileKind, size: Int64? = nil, modified: Date? = nil,
                permissions: String? = nil, unixMode: Int? = nil, linkTarget: String? = nil,
                linkPointsToDirectory: Bool? = nil) {
        self.name = name
        self.path = path
        self.kind = kind
        self.size = size
        self.modified = modified
        self.permissions = permissions
        self.unixMode = unixMode
        self.linkTarget = linkTarget
        self.linkPointsToDirectory = linkPointsToDirectory
    }

    public var id: String { path }
    public var isDirectory: Bool { kind == .directory || (kind == .symlink && linkPointsToDirectory == true) }
    public var isHidden: Bool { name.hasPrefix(".") }

    public var fileExtension: String {
        guard let dot = name.lastIndex(of: "."), dot != name.startIndex else { return "" }
        return String(name[name.index(after: dot)...]).lowercased()
    }
}

public enum FileSortField: String, Codable, CaseIterable, Sendable {
    case name, size, modified, type

    public var title: String {
        switch self {
        case .name: return "Имя"
        case .size: return "Размер"
        case .modified: return "Дата"
        case .type: return "Тип"
        }
    }
}

public struct FileSortOrder: Codable, Hashable, Sendable {
    public var field: FileSortField
    public var ascending: Bool
    public var foldersFirst: Bool

    public init(field: FileSortField = .name, ascending: Bool = true, foldersFirst: Bool = true) {
        self.field = field
        self.ascending = ascending
        self.foldersFirst = foldersFirst
    }

    public func sort(_ items: [FileItem]) -> [FileItem] {
        items.sorted { a, b in
            if foldersFirst, a.isDirectory != b.isDirectory { return a.isDirectory }
            let result: ComparisonResult
            switch field {
            case .name:
                result = a.name.localizedStandardCompare(b.name)
            case .size:
                let sa = a.size ?? -1, sb = b.size ?? -1
                result = sa == sb ? a.name.localizedStandardCompare(b.name) : (sa < sb ? .orderedAscending : .orderedDescending)
            case .modified:
                let da = a.modified ?? .distantPast, db = b.modified ?? .distantPast
                result = da == db ? a.name.localizedStandardCompare(b.name) : (da < db ? .orderedAscending : .orderedDescending)
            case .type:
                let c = a.fileExtension.compare(b.fileExtension)
                result = c == .orderedSame ? a.name.localizedStandardCompare(b.name) : c
            }
            return ascending ? result == .orderedAscending : result == .orderedDescending
        }
    }
}

/// Форматирование прав доступа.
public enum UnixPermissions {
    public static func string(fromMode mode: Int) -> String {
        let chars: [Character] = ["r", "w", "x"]
        var s = ""
        for shift in stride(from: 6, through: 0, by: -3) {
            let bits = (mode >> shift) & 0o7
            for (i, c) in chars.enumerated() {
                s.append((bits & (4 >> i)) != 0 ? c : "-")
            }
        }
        return s
    }

    /// "rwxr-xr-x" или "-rwxr-xr-x" → 0o755. Биты setuid/sticky игнорируются.
    public static func mode(fromString string: String) -> Int? {
        var chars = Array(string)
        if chars.count == 10 { chars.removeFirst() }
        guard chars.count >= 9 else { return nil }
        var mode = 0
        for i in 0..<9 {
            let c = chars[i]
            let bit = 1 << (8 - i)
            switch c {
            case "r", "w", "x", "s", "t": mode |= bit
            case "-", "S", "T": break
            default: return nil
            }
        }
        return mode
    }
}
