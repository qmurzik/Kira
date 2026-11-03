import Foundation
import Security
import TwinCore

/// Профиль FTP-сервера. Пароль здесь не хранится — только в Keychain.
struct ServerProfile: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String = ""
    var host: String = ""
    var port: Int = 21
    var username: String = ""
    var anonymous = false
    var savePassword = true
    var initialPath: String = ""
    var encoding: FTPEncoding = .auto
    var passiveMode: FTPPassiveMode = .auto
    var fixPassiveAddress = true
    var listingMode: FTPListingMode = .auto
    var timeout: Double = 20
    var networkStack: NetworkStack = .platformDefault
    var maxTransferConnections = 2
    var escapeTelnetIAC = true
    var plainTextWarningAcknowledged = false
    var colorTag: Int = 0
    var createdAt = Date()
    var lastConnectedAt: Date?

    var displayName: String {
        let n = name.trimmingCharacters(in: .whitespaces)
        return n.isEmpty ? host : n
    }

    var subtitle: String {
        let user = anonymous ? "anonymous" : username
        return "\(user)@\(host)\(port == 21 ? "" : ":\(port)")"
    }

    var configuration: FTPConfiguration {
        var c = FTPConfiguration(host: host, port: port, username: anonymous ? "anonymous" : username,
                                 encoding: encoding, passiveMode: passiveMode, fixPassiveAddress: fixPassiveAddress,
                                 listingMode: listingMode, timeout: timeout, networkStack: networkStack)
        c.escapeTelnetIAC = escapeTelnetIAC
        return c
    }

    func credentials(password: String) -> FTPCredentials {
        anonymous ? FTPCredentials(username: "anonymous", password: "twinftp@example.com")
                  : FTPCredentials(username: username, password: password)
    }

    static let tagColors: [String] = ["violet", "blue", "green", "orange", "pink", "gray"]
}

/// Хранение паролей в связке ключей iOS.
enum Keychain {
    private static let service = "app.twinftp.ftp-password"

    @discardableResult
    static func save(password: String, for id: UUID) -> Bool {
        let account = id.uuidString
        let data = Data(password.utf8)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(add as CFDictionary, nil) == errSecSuccess
    }

    static func password(for id: UUID) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: id.uuidString,
                                    kSecReturnData as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(for id: UUID) {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: id.uuidString]
        SecItemDelete(query as CFDictionary)
    }
}

/// Список серверов: JSON в Application Support, пароли — в Keychain.
@MainActor
final class ServerStore: ObservableObject {
    @Published private(set) var servers: [ServerProfile] = []
    /// Пароли, введённые на время сеанса (если «Сохранять пароль» выключено).
    private var sessionPasswords: [UUID: String] = [:]

    private let url: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("servers.json")
    }()

    init() { load() }

    private func load() {
        guard let data = try? Data(contentsOf: url) else { return }
        servers = (try? JSONDecoder().decode([ServerProfile].self, from: data)) ?? []
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(servers) {
            try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }

    func profile(_ id: UUID) -> ServerProfile? { servers.first { $0.id == id } }

    func upsert(_ profile: ServerProfile, password: String?) {
        if let idx = servers.firstIndex(where: { $0.id == profile.id }) {
            servers[idx] = profile
        } else {
            servers.append(profile)
        }
        if let password {
            if profile.savePassword && !profile.anonymous {
                if !Keychain.save(password: password, for: profile.id) {
                    // Связка ключей недоступна (например, неподписанная сборка) — пароль живёт до перезапуска.
                    sessionPasswords[profile.id] = password
                }
            } else {
                Keychain.delete(for: profile.id)
                sessionPasswords[profile.id] = password.isEmpty ? nil : password
            }
        }
        persist()
    }

    func delete(_ id: UUID) {
        servers.removeAll { $0.id == id }
        Keychain.delete(for: id)
        sessionPasswords[id] = nil
        persist()
    }

    func duplicate(_ id: UUID) {
        guard var copy = profile(id) else { return }
        let pw = password(for: id)
        copy.id = UUID()
        copy.name = copy.displayName + " (копия)"
        copy.createdAt = Date()
        copy.lastConnectedAt = nil
        upsert(copy, password: pw)
    }

    func move(from: IndexSet, to: Int) {
        servers.move(fromOffsets: from, toOffset: to)
        persist()
    }

    func markConnected(_ id: UUID) {
        guard let idx = servers.firstIndex(where: { $0.id == id }) else { return }
        servers[idx].lastConnectedAt = Date()
        persist()
    }

    func acknowledgePlainText(_ id: UUID) {
        guard let idx = servers.firstIndex(where: { $0.id == id }) else { return }
        servers[idx].plainTextWarningAcknowledged = true
        persist()
    }

    func password(for id: UUID) -> String? {
        sessionPasswords[id] ?? Keychain.password(for: id)
    }

    func setSessionPassword(_ pw: String, for id: UUID) { sessionPasswords[id] = pw }

    func needsPasswordPrompt(_ profile: ServerProfile) -> Bool {
        !profile.anonymous && password(for: profile.id) == nil
    }
}
