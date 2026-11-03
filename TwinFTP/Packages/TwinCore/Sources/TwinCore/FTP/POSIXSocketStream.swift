import Foundation
#if canImport(Glibc)
import Glibc
#elseif canImport(Darwin)
import Darwin
#endif

/// TCP-соединение поверх BSD-сокетов. Работает на Linux (для тестов) и на iOS (как запасной стек).
/// Все блокирующие вызовы выполняются вне кооперативного пула Swift Concurrency
/// и нарезаются короткими интервалами `poll`, чтобы корректно реагировать на отмену и закрытие.
public final class POSIXSocketStream: ByteStream, @unchecked Sendable {
    private let lock = NSLock()
    private var fd: Int32
    private var closed = false
    public let remoteAddress: String?
    public let localAddress: String?
    private static let ioQueue = DispatchQueue(label: "twinftp.posix.io", attributes: .concurrent)

    init(fd: Int32, remote: String?, local: String?) {
        self.fd = fd
        self.remoteAddress = remote
        self.localAddress = local
    }

    deinit { close() }

    private func currentFD() -> Int32? {
        lock.lock(); defer { lock.unlock() }
        return closed ? nil : fd
    }

    private var isClosed: Bool {
        lock.lock(); defer { lock.unlock() }
        return closed
    }

    public func close() {
        lock.lock()
        if closed { lock.unlock(); return }
        closed = true
        let f = fd
        fd = -1
        lock.unlock()
        #if canImport(Glibc)
        Glibc.shutdown(f, Int32(SHUT_RDWR))
        _ = Glibc.close(f)
        #else
        Darwin.shutdown(f, SHUT_RDWR)
        _ = Darwin.close(f)
        #endif
    }

    public func closeWrite() async {
        guard let f = currentFD() else { return }
        #if canImport(Glibc)
        Glibc.shutdown(f, Int32(SHUT_WR))
        #else
        Darwin.shutdown(f, SHUT_WR)
        #endif
    }

    /// Ожидание готовности сокета с нарезкой по 200 мс. Возвращает true, если готов.
    private func waitReady(events: Int16, deadline: Date, cancelCheck: () -> Bool) throws -> Bool {
        while true {
            if cancelCheck() { throw FTPError.cancelled }
            guard let f = currentFD() else { throw FTPError.connectionClosed }
            let remaining = deadline.timeIntervalSinceNow
            if remaining <= 0 { return false }
            var pfd = pollfd(fd: f, events: events, revents: 0)
            let slice = Int32(min(200, max(1, remaining * 1000)))
            let r = poll(&pfd, 1, slice)
            if r > 0 {
                return true
            }
            if r < 0 && errno != EINTR {
                throw FTPError.dataConnectionFailed(String(cString: strerror(errno)))
            }
        }
    }

    private func runBlocking<T>(_ body: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<T, Error>) in
            Self.ioQueue.async {
                do { cont.resume(returning: try body()) } catch { cont.resume(throwing: error) }
            }
        }
    }

    public func send(_ data: Data, timeout: TimeInterval) async throws {
        if data.isEmpty { return }
        let cancelled = CancellationFlag()
        try await withTaskCancellationHandler {
            try await runBlocking { [self] in
                let deadline = Date().addingTimeInterval(timeout)
                var offset = 0
                try data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
                    guard let base = raw.baseAddress else { return }
                    while offset < raw.count {
                        guard try waitReady(events: Int16(POLLOUT), deadline: deadline, cancelCheck: { cancelled.isSet }) else {
                            throw FTPError.timeout(operation: "отправка данных")
                        }
                        guard let f = currentFD() else { throw FTPError.connectionClosed }
                        #if canImport(Glibc)
                        let n = Glibc.send(f, base + offset, raw.count - offset, Int32(MSG_NOSIGNAL))
                        #else
                        let n = Darwin.send(f, base + offset, raw.count - offset, 0)
                        #endif
                        if n > 0 {
                            offset += n
                        } else if n < 0 {
                            if errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR { continue }
                            if errno == EPIPE || errno == ECONNRESET { throw FTPError.connectionClosed }
                            throw FTPError.dataConnectionFailed(String(cString: strerror(errno)))
                        }
                    }
                }
            }
        } onCancel: {
            cancelled.set()
        }
    }

    public func receive(maxLength: Int, timeout: TimeInterval) async throws -> Data {
        let cancelled = CancellationFlag()
        return try await withTaskCancellationHandler {
            try await runBlocking { [self] in
                let deadline = Date().addingTimeInterval(timeout)
                var buffer = [UInt8](repeating: 0, count: max(1, maxLength))
                while true {
                    guard try waitReady(events: Int16(POLLIN), deadline: deadline, cancelCheck: { cancelled.isSet }) else {
                        throw FTPError.timeout(operation: "ожидание данных от сервера")
                    }
                    guard let f = currentFD() else { throw FTPError.connectionClosed }
                    let n = buffer.withUnsafeMutableBytes { recv(f, $0.baseAddress, maxLength, 0) }
                    if n > 0 { return Data(buffer[0..<n]) }
                    if n == 0 { return Data() }
                    if errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR { continue }
                    if errno == ECONNRESET { throw FTPError.connectionClosed }
                    throw FTPError.dataConnectionFailed(String(cString: strerror(errno)))
                }
            }
        } onCancel: {
            cancelled.set()
        }
    }
}

final class CancellationFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false
    var isSet: Bool { lock.lock(); defer { lock.unlock() }; return value }
    func set() { lock.lock(); value = true; lock.unlock() }
}

public struct POSIXSocketConnector: ByteStreamConnector {
    public init() {}

    public func connect(host: String, port: Int, timeout: TimeInterval) async throws -> ByteStream {
        let cancelled = CancellationFlag()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<ByteStream, Error>) in
                DispatchQueue.global(qos: .userInitiated).async {
                    do {
                        cont.resume(returning: try Self.blockingConnect(host: host, port: port, timeout: timeout, cancelled: cancelled))
                    } catch {
                        cont.resume(throwing: error)
                    }
                }
            }
        } onCancel: {
            cancelled.set()
        }
    }

    private static func blockingConnect(host: String, port: Int, timeout: TimeInterval, cancelled: CancellationFlag) throws -> ByteStream {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        #if canImport(Glibc)
        hints.ai_socktype = Int32(SOCK_STREAM.rawValue)
        #else
        hints.ai_socktype = SOCK_STREAM
        #endif
        hints.ai_protocol = Int32(IPPROTO_TCP)
        var result: UnsafeMutablePointer<addrinfo>?
        let rc = getaddrinfo(host, String(port), &hints, &result)
        guard rc == 0, let first = result else {
            let detail = rc != 0 ? String(cString: gai_strerror(rc)) : "нет адресов"
            throw FTPError.dnsFailure(host: host, detail: detail)
        }
        defer { freeaddrinfo(first) }

        // IPv4 в приоритете (многие FTP-хостинги не настраивают IPv6 для пассивного режима).
        var candidates: [UnsafeMutablePointer<addrinfo>] = []
        var ptr: UnsafeMutablePointer<addrinfo>? = first
        while let p = ptr { candidates.append(p); ptr = p.pointee.ai_next }
        candidates.sort { a, _ in a.pointee.ai_family == AF_INET }

        var lastError = "нет доступных адресов"
        let deadline = Date().addingTimeInterval(timeout)
        for info in candidates {
            if cancelled.isSet { throw FTPError.cancelled }
            let fd = socket(info.pointee.ai_family, info.pointee.ai_socktype, info.pointee.ai_protocol)
            if fd < 0 { lastError = String(cString: strerror(errno)); continue }
            #if canImport(Darwin)
            var one: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
            #endif
            var nodelay: Int32 = 1
            setsockopt(fd, Int32(IPPROTO_TCP), TCP_NODELAY, &nodelay, socklen_t(MemoryLayout<Int32>.size))
            let flags = fcntl(fd, F_GETFL, 0)
            _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)

            #if canImport(Glibc)
            let r = Glibc.connect(fd, info.pointee.ai_addr, info.pointee.ai_addrlen)
            #else
            let r = Darwin.connect(fd, info.pointee.ai_addr, info.pointee.ai_addrlen)
            #endif
            if r != 0 && errno != EINPROGRESS {
                lastError = String(cString: strerror(errno))
                _ = closeFD(fd)
                continue
            }
            var connected = r == 0
            while !connected {
                if cancelled.isSet { _ = closeFD(fd); throw FTPError.cancelled }
                let remaining = deadline.timeIntervalSinceNow
                if remaining <= 0 { break }
                var pfd = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
                let pr = poll(&pfd, 1, Int32(min(200, max(1, remaining * 1000))))
                if pr > 0 {
                    var soError: Int32 = 0
                    var len = socklen_t(MemoryLayout<Int32>.size)
                    getsockopt(fd, SOL_SOCKET, SO_ERROR, &soError, &len)
                    if soError == 0 { connected = true } else {
                        lastError = String(cString: strerror(soError))
                        break
                    }
                } else if pr < 0 && errno != EINTR {
                    lastError = String(cString: strerror(errno))
                    break
                }
            }
            if connected {
                return POSIXSocketStream(fd: fd, remote: Self.peerName(fd, peer: true), local: Self.peerName(fd, peer: false))
            }
            _ = closeFD(fd)
            if deadline.timeIntervalSinceNow <= 0 {
                throw FTPError.timeout(operation: "подключение к \(host):\(port)")
            }
        }
        throw FTPError.connectionFailed(host: host, port: port, detail: lastError)
    }

    private static func closeFD(_ fd: Int32) -> Int32 {
        #if canImport(Glibc)
        return Glibc.close(fd)
        #else
        return Darwin.close(fd)
        #endif
    }

    static func peerName(_ fd: Int32, peer: Bool) -> String? {
        var storage = sockaddr_storage()
        var len = socklen_t(MemoryLayout<sockaddr_storage>.size)
        let rc = withUnsafeMutablePointer(to: &storage) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                peer ? getpeername(fd, $0, &len) : getsockname(fd, $0, &len)
            }
        }
        guard rc == 0 else { return nil }
        var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        let r = withUnsafePointer(to: &storage) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                getnameinfo($0, len, &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
            }
        }
        guard r == 0 else { return nil }
        var s = String(cString: host)
        if s.hasPrefix("::ffff:") { s = String(s.dropFirst(7)) }
        return s
    }
}
