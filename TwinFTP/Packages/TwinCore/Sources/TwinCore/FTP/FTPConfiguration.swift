import Foundation

public enum FTPPassiveMode: String, Codable, CaseIterable, Sendable {
    /// PASV для IPv4, EPSV для IPv6; при ошибке — переход на другой режим.
    case auto
    /// Сначала EPSV, затем PASV.
    case epsvFirst
    /// Только PASV.
    case pasvOnly

    public var title: String {
        switch self {
        case .auto: return "Авто (PASV → EPSV)"
        case .epsvFirst: return "Сначала EPSV"
        case .pasvOnly: return "Только PASV"
        }
    }
}

public enum FTPListingMode: String, Codable, CaseIterable, Sendable {
    /// MLSD, если поддерживается; иначе LIST -a, затем LIST.
    case auto
    case mlsd
    case listAll
    case list

    public var title: String {
        switch self {
        case .auto: return "Авто (MLSD → LIST)"
        case .mlsd: return "Только MLSD"
        case .listAll: return "LIST -a"
        case .list: return "LIST"
        }
    }
}

/// Параметры подключения к FTP-серверу (без пароля — он хранится отдельно, в Keychain).
public struct FTPConfiguration: Codable, Hashable, Sendable {
    public var host: String
    public var port: Int
    public var username: String
    public var encoding: FTPEncoding
    public var passiveMode: FTPPassiveMode
    /// Заменять «серый» IP из ответа PASV адресом сервера (для серверов за NAT).
    public var fixPassiveAddress: Bool
    public var listingMode: FTPListingMode
    public var timeout: TimeInterval
    public var networkStack: NetworkStack
    /// Удваивать 0xFF в именах для однобайтовых кодировок (буква «я» в Windows-1251).
    public var escapeTelnetIAC: Bool = true

    public init(host: String, port: Int = 21, username: String = "anonymous",
                encoding: FTPEncoding = .auto, passiveMode: FTPPassiveMode = .auto,
                fixPassiveAddress: Bool = true, listingMode: FTPListingMode = .auto,
                timeout: TimeInterval = 20, networkStack: NetworkStack = .platformDefault) {
        self.host = host
        self.port = port
        self.username = username
        self.encoding = encoding
        self.passiveMode = passiveMode
        self.fixPassiveAddress = fixPassiveAddress
        self.listingMode = listingMode
        self.timeout = timeout
        self.networkStack = networkStack
    }

    /// Хост без схемы и пробелов ("ftp://example.com/" → "example.com").
    public var normalizedHost: String {
        var h = host.trimmingCharacters(in: .whitespacesAndNewlines)
        for scheme in ["ftp://", "ftps://", "FTP://"] where h.hasPrefix(scheme) { h.removeFirst(scheme.count) }
        if let slash = h.firstIndex(of: "/") { h = String(h[..<slash]) }
        if h.hasPrefix("["), h.hasSuffix("]") { h = String(h.dropFirst().dropLast()) }
        return h
    }
}

public struct FTPCredentials: Sendable {
    public var username: String
    public var password: String
    public init(username: String, password: String) {
        self.username = username
        self.password = password
    }
}

/// Возможности сервера, полученные из FEAT.
public struct FTPServerFeatures: Sendable, Equatable {
    public var raw: [String: String] = [:]
    public var mlsd: Bool { raw["MLSD"] != nil || raw["MLST"] != nil }
    public var size: Bool { raw["SIZE"] != nil }
    public var mdtm: Bool { raw["MDTM"] != nil }
    public var utf8: Bool { raw["UTF8"] != nil }
    public var epsv: Bool { raw["EPSV"] != nil }
    public var restStream: Bool { raw["REST"]?.uppercased().contains("STREAM") == true }
    public var mfmt: Bool { raw["MFMT"] != nil }
    public var authTLS: Bool { raw["AUTH"]?.uppercased().contains("TLS") == true }
    public init(raw: [String: String] = [:]) { self.raw = raw }
}
