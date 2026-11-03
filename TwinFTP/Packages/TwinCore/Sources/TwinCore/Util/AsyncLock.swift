import Foundation

/// Асинхронный мьютекс (FIFO) с поддержкой отмены ожидания.
/// Нужен, потому что акторы реентерабельны: без него команды FTP разных задач могли бы перемешаться.
public final class AsyncLock: @unchecked Sendable {
    private let lock = NSLock()
    private var locked = false
    private var waiters: [(id: UInt64, cont: CheckedContinuation<Void, Error>)] = []
    private var nextID: UInt64 = 0

    public init() {}

    public func acquire() async throws {
        try Task.checkCancellation()
        guard let id = tryAcquireOrReserveID() else { return }
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
                lock.lock()
                if !locked {
                    locked = true
                    lock.unlock()
                    cont.resume()
                    return
                }
                if Task.isCancelled {
                    lock.unlock()
                    cont.resume(throwing: CancellationError())
                    return
                }
                waiters.append((id, cont))
                lock.unlock()
            }
        } onCancel: {
            lock.lock()
            if let idx = waiters.firstIndex(where: { $0.id == id }) {
                let w = waiters.remove(at: idx)
                lock.unlock()
                w.cont.resume(throwing: CancellationError())
            } else {
                lock.unlock()
            }
        }
    }

    private func tryAcquireOrReserveID() -> UInt64? {
        lock.lock(); defer { lock.unlock() }
        if !locked {
            locked = true
            return nil
        }
        nextID += 1
        return nextID
    }

    public func release() {
        lock.lock()
        if waiters.isEmpty {
            locked = false
            lock.unlock()
        } else {
            let next = waiters.removeFirst()
            lock.unlock()
            next.cont.resume()
        }
    }

    public func withLock<T>(_ body: () async throws -> T) async throws -> T {
        try await acquire()
        defer { release() }
        return try await body()
    }
}

/// Потокобезопасный контейнер значения.
public final class Locked<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    public init(_ value: Value) { self.value = value }
    public func withValue<T>(_ body: (inout Value) throws -> T) rethrows -> T {
        lock.lock(); defer { lock.unlock() }
        return try body(&value)
    }
    public var current: Value { withValue { $0 } }
}
