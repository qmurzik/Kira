#if canImport(Network)
import Foundation
import Network

/// TCP-соединение на Network.framework — основной стек на iOS.
public final class NWSocketStream: ByteStream, @unchecked Sendable {
    private let connection: NWConnection
    private let queue: DispatchQueue
    private let lock = NSLock()
    private var closed = false

    init(connection: NWConnection, queue: DispatchQueue) {
        self.connection = connection
        self.queue = queue
    }

    deinit { close() }

    public var remoteAddress: String? {
        guard let ep = connection.currentPath?.remoteEndpoint else { return nil }
        return Self.describe(ep)
    }

    public var localAddress: String? {
        guard let ep = connection.currentPath?.localEndpoint else { return nil }
        return Self.describe(ep)
    }

    static func describe(_ ep: NWEndpoint) -> String? {
        if case let .hostPort(host, _) = ep {
            switch host {
            case .ipv4(let a):
                return "\(a)".components(separatedBy: "%").first
            case .ipv6(let a):
                var s = "\(a)".components(separatedBy: "%").first ?? "\(a)"
                if s.hasPrefix("::ffff:") { s = String(s.dropFirst(7)) }
                return s
            case .name(let n, _):
                return n
            @unknown default:
                return nil
            }
        }
        return nil
    }

    public func close() {
        lock.lock()
        if closed { lock.unlock(); return }
        closed = true
        lock.unlock()
        connection.cancel()
    }

    private var isClosed: Bool { lock.lock(); defer { lock.unlock() }; return closed }

    public func closeWrite() async {
        if isClosed { return }
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            connection.send(content: nil, contentContext: .finalMessage, isComplete: true, completion: .contentProcessed { _ in
                cont.resume()
            })
        }
    }

    public func send(_ data: Data, timeout: TimeInterval) async throws {
        if isClosed { throw FTPError.connectionClosed }
        if data.isEmpty { return }
        try await withTimeout(timeout, operation: "отправка данных") { [connection] in
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
                connection.send(content: data, completion: .contentProcessed { error in
                    if let error {
                        cont.resume(throwing: FTPError.dataConnectionFailed(error.localizedDescription))
                    } else {
                        cont.resume()
                    }
                })
            }
        }
    }

    /// Получен конец потока (FIN) — последующие чтения сразу возвращают пустые данные.
    private var _eof = false
    private var eof: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _eof }
        set { lock.lock(); _eof = newValue; lock.unlock() }
    }

    public func receive(maxLength: Int, timeout: TimeInterval) async throws -> Data {
        if eof { return Data() }
        if isClosed { throw FTPError.connectionClosed }
        let result: (Data, Bool) = try await withTimeout(timeout, operation: "ожидание данных от сервера") { [connection] in
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<(Data, Bool), Error>) in
                connection.receive(minimumIncompleteLength: 1, maximumLength: maxLength) { content, _, isComplete, error in
                    if let content, !content.isEmpty {
                        // Данные могут прийти вместе с признаком конца потока.
                        cont.resume(returning: (content, isComplete))
                    } else if isComplete {
                        cont.resume(returning: (Data(), true))
                    } else if let error {
                        if case .posix(let code) = error {
                            switch code {
                            case .ENODATA:
                                // Сокет уже закрыт удалённой стороной: это конец потока, а не ошибка.
                                cont.resume(returning: (Data(), true))
                                return
                            case .ECONNRESET:
                                cont.resume(throwing: FTPError.connectionClosed)
                                return
                            default:
                                break
                            }
                        }
                        cont.resume(throwing: FTPError.dataConnectionFailed(error.localizedDescription))
                    } else {
                        cont.resume(returning: (Data(), true))
                    }
                }
            }
        }
        if result.1 { eof = true }
        return result.0
    }

    /// Выполняет операцию с тайм-аутом; по истечении закрывает соединение (что завершает висящий вызов).
    private func withTimeout<T: Sendable>(_ seconds: TimeInterval, operation: String, _ body: @escaping @Sendable () async throws -> T) async throws -> T {
        let timer = DispatchWorkItem { [weak self] in self?.markTimedOut() }
        queue.asyncAfter(deadline: .now() + seconds, execute: timer)
        defer { timer.cancel() }
        do {
            return try await withTaskCancellationHandler {
                try await body()
            } onCancel: { [weak self] in
                self?.close()
            }
        } catch {
            if timedOut { throw FTPError.timeout(operation: operation) }
            if Task.isCancelled { throw FTPError.cancelled }
            throw error
        }
    }

    private var _timedOut = false
    private var timedOut: Bool { lock.lock(); defer { lock.unlock() }; return _timedOut }
    private func markTimedOut() {
        lock.lock(); _timedOut = true; lock.unlock()
        close()
    }
}

public struct NWSocketConnector: ByteStreamConnector {
    public init() {}

    public func connect(host: String, port: Int, timeout: TimeInterval) async throws -> ByteStream {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(clamping: port)), port > 0, port <= 65535 else {
            throw FTPError.connectionFailed(host: host, port: port, detail: "Некорректный порт")
        }
        let tcp = NWProtocolTCP.Options()
        tcp.connectionTimeout = max(1, Int(timeout))
        tcp.noDelay = true
        tcp.enableKeepalive = true
        tcp.keepaliveIdle = 30
        let params = NWParameters(tls: nil, tcp: tcp)
        params.preferNoProxies = true
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: params)
        let queue = DispatchQueue(label: "twinftp.nw.\(host).\(port)")
        let state = ConnectState()

        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<ByteStream, Error>) in
                state.setContinuation(cont)
                connection.stateUpdateHandler = { newState in
                    switch newState {
                    case .ready:
                        connection.stateUpdateHandler = nil
                        state.finish(.success(NWSocketStream(connection: connection, queue: queue)))
                    case .waiting(let error):
                        // .waiting означает, что сеть недоступна или соединение отклонено.
                        connection.cancel()
                        state.finish(.failure(Self.map(error, host: host, port: port)))
                    case .failed(let error):
                        connection.cancel()
                        state.finish(.failure(Self.map(error, host: host, port: port)))
                    case .cancelled:
                        state.finish(.failure(FTPError.cancelled))
                    default:
                        break
                    }
                }
                connection.start(queue: queue)
                queue.asyncAfter(deadline: .now() + timeout + 1) {
                    if state.finish(.failure(FTPError.timeout(operation: "подключение к \(host):\(port)"))) {
                        connection.cancel()
                    }
                }
            }
        } onCancel: {
            connection.cancel()
        }
    }

    static func map(_ error: NWError, host: String, port: Int) -> FTPError {
        switch error {
        case .dns(let code):
            return .dnsFailure(host: host, detail: "DNS \(code)")
        case .posix(let code):
            switch code {
            case .ECONNREFUSED:
                return .connectionFailed(host: host, port: port, detail: "Соединение отклонено (порт закрыт).")
            case .ETIMEDOUT:
                return .timeout(operation: "подключение к \(host):\(port)")
            case .ENETUNREACH, .EHOSTUNREACH, .ENETDOWN:
                return .connectionFailed(host: host, port: port, detail: "Сеть недоступна.")
            default:
                return .connectionFailed(host: host, port: port, detail: error.localizedDescription)
            }
        default:
            return .connectionFailed(host: host, port: port, detail: error.localizedDescription)
        }
    }
}

private final class ConnectState: @unchecked Sendable {
    private let lock = NSLock()
    private var cont: CheckedContinuation<ByteStream, Error>?
    private var done = false

    func setContinuation(_ c: CheckedContinuation<ByteStream, Error>) {
        lock.lock(); cont = c; lock.unlock()
    }

    @discardableResult
    func finish(_ result: Result<ByteStream, Error>) -> Bool {
        lock.lock()
        guard !done, let c = cont else { lock.unlock(); return false }
        done = true
        cont = nil
        lock.unlock()
        c.resume(with: result)
        return true
    }
}
#endif
