import Foundation

/// Двунаправленный поток байтов (TCP-соединение).
/// Реализации: `POSIXSocketStream` (BSD-сокеты, Linux + Darwin) и `NWSocketStream` (Network.framework).
public protocol ByteStream: AnyObject, Sendable {
    /// Отправляет все байты целиком.
    func send(_ data: Data, timeout: TimeInterval) async throws
    /// Читает до `maxLength` байт. Возвращает пустой `Data` при EOF.
    func receive(maxLength: Int, timeout: TimeInterval) async throws -> Data
    /// Закрывает передающую сторону (FIN), продолжая читать.
    func closeWrite() async
    /// Полностью закрывает соединение. Можно вызывать из любого потока многократно.
    func close()
    /// IP-адрес удалённой стороны (если известен).
    var remoteAddress: String? { get }
    /// IP-адрес локальной стороны (если известен).
    var localAddress: String? { get }
}

/// Фабрика TCP-соединений.
public protocol ByteStreamConnector: Sendable {
    func connect(host: String, port: Int, timeout: TimeInterval) async throws -> ByteStream
}

/// Выбор сетевого стека.
public enum NetworkStack: String, Codable, CaseIterable, Sendable {
    /// Network.framework (рекомендуется на iOS: корректно поднимает сотовую сеть и VPN).
    case network
    /// BSD-сокеты (запасной вариант).
    case bsd

    public var title: String {
        switch self {
        case .network: return "Network.framework"
        case .bsd: return "BSD-сокеты"
        }
    }

    public static var platformDefault: NetworkStack {
        #if canImport(Network)
        return .network
        #else
        return .bsd
        #endif
    }

    public func makeConnector() -> ByteStreamConnector {
        switch self {
        case .network:
            #if canImport(Network)
            return NWSocketConnector()
            #else
            return POSIXSocketConnector()
            #endif
        case .bsd:
            return POSIXSocketConnector()
        }
    }
}

/// Проверка на частные/служебные IPv4-адреса (для обхода некорректного PASV за NAT).
public enum IPAddressClassifier {
    public static func isIPv4(_ s: String) -> Bool {
        let parts = s.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return false }
        return parts.allSatisfy { p in
            guard let v = Int(p), (0...255).contains(v), !p.isEmpty, p.count <= 3 else { return false }
            return true
        }
    }

    public static func isPrivateOrUnroutable(_ s: String) -> Bool {
        guard isIPv4(s) else { return false }
        let n = s.split(separator: ".").compactMap { Int($0) }
        switch (n[0], n[1]) {
        case (10, _), (127, _), (0, _): return true
        case (172, 16...31): return true
        case (192, 168): return true
        case (169, 254): return true
        case (100, 64...127): return true // CGNAT
        default: return n[0] >= 224
        }
    }
}
