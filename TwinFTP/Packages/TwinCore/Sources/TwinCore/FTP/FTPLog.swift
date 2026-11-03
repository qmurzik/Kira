import Foundation

public enum FTPLogKind: String, Codable, Sendable {
    case command   // отправлено клиентом
    case reply     // ответ сервера
    case info      // служебное сообщение
    case error
}

public struct FTPLogEntry: Identifiable, Codable, Sendable, Hashable {
    public let id: UUID
    public let date: Date
    public let kind: FTPLogKind
    public let connection: String
    public let text: String

    public init(kind: FTPLogKind, connection: String, text: String, date: Date = Date()) {
        self.id = UUID()
        self.date = date
        self.kind = kind
        self.connection = connection
        self.text = text
    }
}

/// Приёмник журнала. Реализация в приложении хранит последние записи в памяти и на диске.
public protocol FTPLogSink: Sendable {
    func record(_ entry: FTPLogEntry)
}

/// Маскирует секреты в командах перед записью в журнал.
public enum FTPLogSanitizer {
    public static func sanitize(command: String) -> String {
        let upper = command.uppercased()
        if upper.hasPrefix("PASS") { return "PASS ********" }
        if upper.hasPrefix("ACCT") { return "ACCT ********" }
        return command
    }
}

/// Простой журнал в памяти (ограниченный кольцевой буфер). Потокобезопасен.
public final class InMemoryFTPLog: FTPLogSink, @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [FTPLogEntry] = []
    private let limit: Int
    private var observers: [UUID: @Sendable (FTPLogEntry) -> Void] = [:]

    public init(limit: Int = 3000) { self.limit = limit }

    public func record(_ entry: FTPLogEntry) {
        lock.lock()
        entries.append(entry)
        if entries.count > limit { entries.removeFirst(entries.count - limit) }
        let obs = Array(observers.values)
        lock.unlock()
        for o in obs { o(entry) }
    }

    public var all: [FTPLogEntry] { lock.lock(); defer { lock.unlock() }; return entries }

    public func clear() { lock.lock(); entries.removeAll(); lock.unlock() }

    @discardableResult
    public func observe(_ handler: @escaping @Sendable (FTPLogEntry) -> Void) -> UUID {
        let id = UUID()
        lock.lock(); observers[id] = handler; lock.unlock()
        return id
    }

    public func removeObserver(_ id: UUID) { lock.lock(); observers[id] = nil; lock.unlock() }
}
