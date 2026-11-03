import Foundation

public enum TransferDirection: String, Codable, Sendable {
    case upload    // iPhone → FTP
    case download  // FTP → iPhone

    public var title: String { self == .upload ? "Загрузка на сервер" : "Скачивание" }
    public var symbol: String { self == .upload ? "arrow.up.circle.fill" : "arrow.down.circle.fill" }
}

public enum ConflictAction: String, Codable, Sendable, CaseIterable {
    case replace, skip, rename

    public var title: String {
        switch self {
        case .replace: return "Заменить"
        case .skip: return "Пропустить"
        case .rename: return "Сохранить оба"
        }
    }
}

public enum ConflictPolicy: String, Codable, Sendable, CaseIterable {
    case ask, replace, skip, rename

    public var title: String {
        switch self {
        case .ask: return "Спрашивать"
        case .replace: return "Заменять"
        case .skip: return "Пропускать"
        case .rename: return "Переименовывать"
        }
    }

    var action: ConflictAction? {
        switch self {
        case .ask: return nil
        case .replace: return .replace
        case .skip: return .skip
        case .rename: return .rename
        }
    }
}

public struct ConflictInfo: Sendable, Identifiable {
    public let id = UUID()
    public let jobID: UUID
    public let direction: TransferDirection
    public let name: String
    public let destinationPath: String
    public let sourceSize: Int64?
    public let sourceDate: Date?
    public let destinationSize: Int64?
    public let destinationDate: Date?
    public let remainingInJob: Int
}

public struct ConflictDecision: Sendable {
    public var action: ConflictAction
    public var applyToAll: Bool
    public init(action: ConflictAction, applyToAll: Bool) {
        self.action = action
        self.applyToAll = applyToAll
    }
}

public enum TransferTaskState: String, Codable, Sendable {
    case pending, running, completed, skipped, failed, cancelled
}

public struct TransferTask: Codable, Identifiable, Sendable, Hashable {
    public let id: UUID
    public var sourcePath: String
    public var destinationDirectory: String
    public var destinationName: String
    public var size: Int64?
    public var sourceModified: Date?
    public var isSymlink: Bool
    public var state: TransferTaskState
    public var transferred: Int64
    public var errorMessage: String?
    public var attempts: Int
    /// Есть частично переданные данные во временном файле (для докачки).
    public var hasPartial: Bool
    /// Решение по конфликту имён, принятое при первой попытке (повторы его не переспрашивают).
    public var resolvedName: String?
    public var resolvedReplacing: Bool?

    public init(sourcePath: String, destinationDirectory: String, destinationName: String,
                size: Int64?, sourceModified: Date?, isSymlink: Bool = false) {
        self.id = UUID()
        self.sourcePath = sourcePath
        self.destinationDirectory = destinationDirectory
        self.destinationName = destinationName
        self.size = size
        self.sourceModified = sourceModified
        self.isSymlink = isSymlink
        self.state = .pending
        self.transferred = 0
        self.attempts = 0
        self.hasPartial = false
    }

    public var destinationPath: String { PathUtils.child(destinationDirectory, destinationName) }
    public var name: String { PathUtils.lastComponent(sourcePath) }
    public var isFinished: Bool { [.completed, .skipped, .failed, .cancelled].contains(state) }
}

public enum TransferJobState: String, Codable, Sendable {
    case preparing, queued, running, paused, completed, completedWithErrors, failed, cancelled

    public var title: String {
        switch self {
        case .preparing: return "Подготовка"
        case .queued: return "В очереди"
        case .running: return "Выполняется"
        case .paused: return "Пауза"
        case .completed: return "Завершено"
        case .completedWithErrors: return "Завершено с ошибками"
        case .failed: return "Ошибка"
        case .cancelled: return "Отменено"
        }
    }

    public var isActive: Bool { [.preparing, .queued, .running].contains(self) }
    public var isFinished: Bool { [.completed, .completedWithErrors, .failed, .cancelled].contains(self) }
}

public enum TransferPostAction: Codable, Sendable, Hashable {
    case none
    /// После скачивания распаковать архив в указанную локальную папку.
    case extractArchive(destination: String)
    /// После завершения удалить временную локальную папку (например, распакованный архив).
    case cleanupLocal(path: String)
}

public struct TransferJob: Codable, Identifiable, Sendable {
    public let id: UUID
    public var direction: TransferDirection
    public var profileID: UUID
    public var serverName: String
    public var title: String
    public var sources: [FileItem]
    public var sourceDirectory: String
    public var destinationDirectory: String
    public var conflictPolicy: ConflictPolicy
    public var deleteSourcesOnSuccess: Bool
    public var postAction: TransferPostAction
    public var createdAt: Date
    public var finishedAt: Date?
    public var state: TransferJobState
    public var tasks: [TransferTask]
    /// Каталоги назначения, которые нужно создать (в порядке вложенности).
    public var directoriesToCreate: [String]
    /// Исходные каталоги (для удаления после перемещения).
    public var sourceDirectories: [String]
    public var errorMessage: String?
    public var pausedBySystem: Bool
    public var bytesPerSecond: Double
    public var statusNote: String?

    public init(direction: TransferDirection, profileID: UUID, serverName: String, sources: [FileItem],
                sourceDirectory: String, destinationDirectory: String,
                conflictPolicy: ConflictPolicy = .ask, deleteSourcesOnSuccess: Bool = false,
                postAction: TransferPostAction = .none, title: String? = nil) {
        self.id = UUID()
        self.direction = direction
        self.profileID = profileID
        self.serverName = serverName
        self.sources = sources
        self.sourceDirectory = sourceDirectory
        self.destinationDirectory = destinationDirectory
        self.conflictPolicy = conflictPolicy
        self.deleteSourcesOnSuccess = deleteSourcesOnSuccess
        self.postAction = postAction
        self.createdAt = Date()
        self.state = .preparing
        self.tasks = []
        self.directoriesToCreate = []
        self.sourceDirectories = []
        self.pausedBySystem = false
        self.bytesPerSecond = 0
        if let title {
            self.title = title
        } else if sources.count == 1 {
            self.title = sources[0].name
        } else {
            self.title = "\(sources.count) \(RussianPlural.objects(sources.count))"
        }
    }

    public var totalBytes: Int64 { tasks.reduce(0) { $0 + max(0, $1.size ?? 0) } }
    public var transferredBytes: Int64 {
        tasks.reduce(0) { acc, t in
            switch t.state {
            case .completed, .skipped: return acc + max(0, t.size ?? t.transferred)
            default: return acc + t.transferred
            }
        }
    }
    public var progress: Double {
        let total = totalBytes
        if total > 0 { return min(1, Double(transferredBytes) / Double(total)) }
        if tasks.isEmpty { return state.isFinished ? 1 : 0 }
        return Double(tasks.filter(\.isFinished).count) / Double(tasks.count)
    }
    public var completedCount: Int { tasks.filter { $0.state == .completed }.count }
    public var failedCount: Int { tasks.filter { $0.state == .failed }.count }
    public var skippedCount: Int { tasks.filter { $0.state == .skipped }.count }
    public var remainingCount: Int { tasks.filter { $0.state == .pending || $0.state == .running }.count }

    /// Оставшееся время (секунды) по текущей скорости.
    public var estimatedSecondsRemaining: Double? {
        guard bytesPerSecond > 1, state == .running else { return nil }
        let left = Double(totalBytes - transferredBytes)
        return left > 0 ? left / bytesPerSecond : nil
    }
}

public enum RussianPlural {
    /// Склонение: 1 объект, 2 объекта, 5 объектов.
    public static func form(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
        let n10 = n % 10, n100 = n % 100
        if n10 == 1 && n100 != 11 { return one }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return few }
        return many
    }

    public static func objects(_ n: Int) -> String { form(n, "объект", "объекта", "объектов") }
    public static func files(_ n: Int) -> String { form(n, "файл", "файла", "файлов") }
    public static func folders(_ n: Int) -> String { form(n, "папка", "папки", "папок") }
}

public enum ByteFormatter {
    public static func string(_ bytes: Int64) -> String {
        let units = ["Б", "КБ", "МБ", "ГБ", "ТБ"]
        var value = Double(bytes)
        var i = 0
        while value >= 1024 && i < units.count - 1 { value /= 1024; i += 1 }
        if i == 0 { return "\(bytes) Б" }
        let fmt = value >= 100 ? "%.0f" : (value >= 10 ? "%.1f" : "%.2f")
        return String(format: fmt, value).replacingOccurrences(of: ".", with: ",") + " " + units[i]
    }

    public static func speed(_ bps: Double) -> String { string(Int64(bps)) + "/с" }

    public static func duration(_ seconds: Double) -> String {
        let s = Int(seconds.rounded())
        if s < 60 { return "\(s) с" }
        if s < 3600 { return "\(s / 60) мин \(s % 60) с" }
        return "\(s / 3600) ч \((s % 3600) / 60) мин"
    }
}
