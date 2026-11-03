import Foundation
import Network
import TwinCore

enum ConnectionStatus: Equatable {
    case disconnected
    case connecting
    case connected
    case busy
    case failed(String)

    var title: String {
        switch self {
        case .disconnected: return "Не подключено"
        case .connecting: return "Подключение…"
        case .connected: return "Подключено"
        case .busy: return "Обмен данными…"
        case .failed: return "Ошибка соединения"
        }
    }
}

/// Сеанс работы с сервером: соединение обзора для панели + пул соединений для очереди.
@MainActor
final class RemoteSession: ObservableObject, Identifiable {
    let profile: ServerProfile
    let browse: FTPConnection
    let pool: FTPConnectionPool
    @Published var status: ConnectionStatus = .disconnected
    @Published var activeOperations = 0

    nonisolated var id: UUID { profile.id }

    init(profile: ServerProfile, password: String, log: FTPLogSink) {
        self.profile = profile
        let creds = profile.credentials(password: password)
        browse = FTPConnection(label: "\(profile.displayName) · обзор", configuration: profile.configuration,
                               credentials: creds, log: log)
        pool = FTPConnectionPool(configuration: profile.configuration, credentials: creds,
                                 maxConnections: max(1, profile.maxTransferConnections), log: log)
    }

    /// Выполняет операцию на соединении обзора с индикацией состояния.
    func run<T>(_ body: (FTPConnection) async throws -> T) async throws -> T {
        activeOperations += 1
        if status != .connected && status != .busy { status = .connecting } else { status = .busy }
        defer {
            activeOperations -= 1
        }
        do {
            let result = try await body(browse)
            status = activeOperations > 1 ? .busy : .connected
            return result
        } catch let e as FTPError {
            if e.isConnectionLoss || { if case .loginFailed = e { return true }; if case .dnsFailure = e { return true }; return false }() {
                status = .failed(e.localizedDescription)
            } else {
                status = browse.isConnected ? .connected : .disconnected
            }
            throw e
        } catch {
            status = browse.isConnected ? .connected : .disconnected
            throw error
        }
    }

    func disconnect() async {
        await browse.disconnect()
        await pool.closeAll()
        status = .disconnected
    }
}

/// Реестр активных сеансов (по одному на профиль).
@MainActor
final class SessionManager: ObservableObject {
    @Published private(set) var sessions: [UUID: RemoteSession] = [:]
    let log: InMemoryFTPLog
    private let servers: ServerStore

    init(servers: ServerStore, log: InMemoryFTPLog) {
        self.servers = servers
        self.log = log
    }

    func session(for profile: ServerProfile) -> RemoteSession? {
        if let s = sessions[profile.id], Self.sameConnection(s.profile, profile) { return s }
        guard let password = profile.anonymous ? "" : servers.password(for: profile.id) else { return nil }
        if let old = sessions[profile.id] { Task { await old.disconnect() } }
        let s = RemoteSession(profile: profile, password: password, log: log)
        sessions[profile.id] = s
        return s
    }

    func existing(_ id: UUID) -> RemoteSession? { sessions[id] }

    /// Профили эквивалентны для соединения (служебные поля вроде даты подключения не учитываются).
    static func sameConnection(_ a: ServerProfile, _ b: ServerProfile) -> Bool {
        a.configuration == b.configuration && a.anonymous == b.anonymous
            && a.maxTransferConnections == b.maxTransferConnections && a.username == b.username
    }

    func pool(for profileID: UUID) throws -> FTPConnectionPool {
        if let s = sessions[profileID] { return s.pool }
        guard let profile = servers.profile(profileID), let s = session(for: profile) else {
            throw FTPError.notConnected
        }
        return s.pool
    }

    func disconnect(_ id: UUID) async {
        guard let s = sessions[id] else { return }
        await s.disconnect()
        sessions[id] = nil
    }

    func disconnectAll() async {
        for s in sessions.values { await s.disconnect() }
        sessions.removeAll()
    }
}

/// Наблюдение за доступностью сети.
@MainActor
final class NetworkMonitor: ObservableObject {
    @Published private(set) var isOnline = true
    @Published private(set) var isExpensive = false
    private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.isOnline = path.status == .satisfied
                self?.isExpensive = path.isExpensive
            }
        }
        monitor.start(queue: DispatchQueue(label: "twinftp.netmonitor"))
    }
}
