import Foundation

/// Пул соединений для передачи файлов (отдельно от соединения обзора панели).
public actor FTPConnectionPool {
    public let configuration: FTPConfiguration
    private let credentials: FTPCredentials
    private let log: FTPLogSink?
    private let connector: ByteStreamConnector?
    public private(set) var maxConnections: Int

    private var idle: [FTPConnection] = []
    private var leasedCount = 0
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var counter = 0

    public init(configuration: FTPConfiguration, credentials: FTPCredentials, maxConnections: Int,
                log: FTPLogSink? = nil, connector: ByteStreamConnector? = nil) {
        self.configuration = configuration
        self.credentials = credentials
        self.maxConnections = max(1, maxConnections)
        self.log = log
        self.connector = connector
    }

    /// Уменьшает лимит (например, сервер ответил 421 «слишком много подключений»).
    public func reduceLimit() {
        if maxConnections > 1 { maxConnections -= 1 }
    }

    public func lease() async -> FTPConnection {
        while idle.isEmpty && leasedCount >= maxConnections {
            await withCheckedContinuation { waiters.append($0) }
        }
        leasedCount += 1
        if let c = idle.popLast() { return c }
        counter += 1
        return FTPConnection(label: "передача \(counter)", configuration: configuration,
                             credentials: credentials, connector: connector, log: log)
    }

    public func giveBack(_ connection: FTPConnection) {
        leasedCount -= 1
        if idle.count < maxConnections {
            idle.append(connection)
        } else {
            Task { await connection.disconnect() }
        }
        if !waiters.isEmpty { waiters.removeFirst().resume() }
    }

    public func closeAll() async {
        let all = idle
        idle.removeAll()
        for c in all { await c.disconnect() }
    }
}

/// Рекурсивные операции над удалённой файловой системой.
public enum RemoteTree {
    /// Рекурсивно обходит каталог. Каталоги идут раньше своего содержимого.
    /// Символические ссылки на каталоги раскрываются только если ведут в ещё не посещённый каталог.
    public static func walk(_ connection: FTPConnection, root: String, followLinks: Bool = true,
                            limit: Int = 200_000,
                            progress: ((Int) -> Void)? = nil) async throws -> [FileItem] {
        var result: [FileItem] = []
        var visited = Set<String>()
        if let canonical = try await connection.canonicalDirectory(root) { visited.insert(canonical) }
        var stack = [(path: root, depth: 0)]
        while let (dir, depth) = stack.popLast() {
            try Task.checkCancellation()
            let items = try await connection.list(dir).sorted { $0.name < $1.name }
            for var item in items {
                if item.kind == .symlink && followLinks {
                    if let canonical = try await connection.canonicalDirectory(item.path) {
                        item.linkPointsToDirectory = true
                        if visited.contains(canonical) || depth > 64 { continue }
                        visited.insert(canonical)
                    } else {
                        item.linkPointsToDirectory = false
                    }
                }
                result.append(item)
                if result.count > limit { throw FTPError.unsupported("слишком много файлов (более \(limit))") }
                if item.isDirectory { stack.append((item.path, depth + 1)) }
            }
            progress?(result.count)
        }
        return result
    }

    /// Рекурсивное удаление файла или каталога на сервере.
    public static func delete(_ connection: FTPConnection, item: FileItem,
                              progress: ((String) -> Void)? = nil) async throws {
        if item.kind == .directory {
            let all = try await walk(connection, root: item.path, followLinks: false)
            // Сначала файлы, затем каталоги от самых глубоких.
            for f in all where !f.isDirectory || f.kind == .symlink {
                try Task.checkCancellation()
                progress?(f.name)
                try await connection.deleteFile(f.path)
            }
            let dirs = all.filter { $0.kind == .directory }.sorted { $0.path.count > $1.path.count }
            for d in dirs {
                try Task.checkCancellation()
                progress?(d.name)
                try await connection.removeDirectory(d.path)
            }
            progress?(item.name)
            try await connection.removeDirectory(item.path)
        } else {
            progress?(item.name)
            try await connection.deleteFile(item.path)
        }
    }
}
