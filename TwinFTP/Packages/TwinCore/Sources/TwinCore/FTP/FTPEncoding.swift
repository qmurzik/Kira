import Foundation

/// Кодировка имён файлов на сервере.
public enum FTPEncoding: String, Codable, CaseIterable, Sendable {
    /// UTF-8 с автоматическим переходом на Windows-1251, если сервер прислал невалидный UTF-8.
    case auto
    case utf8
    case windows1251
    case koi8r
    case latin1

    public var title: String {
        switch self {
        case .auto: return "Авто (UTF-8 → Windows-1251)"
        case .utf8: return "UTF-8"
        case .windows1251: return "Windows-1251"
        case .koi8r: return "KOI8-R"
        case .latin1: return "ISO-8859-1"
        }
    }

}


/// Кодек для управляющего соединения и листингов, хранящий текущее состояние автоопределения.
public final class FTPTextCodec: @unchecked Sendable {
    private let lock = NSLock()
    private var _effective: FTPEncoding

    public let configured: FTPEncoding
    /// Удваивать байт 0xFF (Telnet IAC) в однобайтовых кодировках (RFC 959/2640; нужно, например, для ProFTPD).
    public let escapeIAC: Bool

    public init(_ encoding: FTPEncoding, escapeIAC: Bool = true) {
        self.configured = encoding
        self._effective = encoding
        self.escapeIAC = escapeIAC
    }

    /// Фактически используемая кодировка (для режима auto может переключиться на windows1251).
    public var effective: FTPEncoding {
        lock.lock(); defer { lock.unlock() }
        return _effective
    }

    /// Сервер явно сообщил о поддержке UTF8 (FEAT) — в авто-режиме фиксируем UTF-8.
    public func serverAnnouncedUTF8() {
        lock.lock(); defer { lock.unlock() }
        if configured == .auto { _effective = .utf8 }
    }

    public func decode(_ bytes: Data) -> String {
        let enc = effective
        switch enc {
        case .auto:
            if let s = String(data: bytes, encoding: .utf8) { return s }
            // Невалидный UTF-8 — сервер, вероятно, отдаёт имена в Windows-1251.
            lock.lock()
            if _effective == .auto { _effective = .windows1251 }
            lock.unlock()
            return SingleByteCodec.decode(bytes, encoding: .windows1251)
        case .utf8:
            return String(decoding: bytes, as: UTF8.self)
        default:
            return SingleByteCodec.decode(bytes, encoding: enc)
        }
    }

    /// Кодирует строку команды. Для однобайтовых кодировок удваивает байт 0xFF (Telnet IAC), RFC 2640.
    public func encode(_ string: String) throws -> Data {
        let enc = effective
        if enc == .auto || enc == .utf8 {
            return Data(string.utf8)
        }
        guard let data = SingleByteCodec.encode(string, encoding: enc) else {
            throw FTPError.encodingFailed(string)
        }
        if !escapeIAC || !data.contains(0xFF) { return data }
        var out = Data(capacity: data.count + 4)
        for b in data {
            out.append(b)
            if b == 0xFF { out.append(0xFF) }
        }
        return out
    }
}

/// Буферизованное чтение строк (CRLF или LF) из байтового потока.
struct LineBuffer {
    private(set) var buffer = Data()

    mutating func append(_ data: Data) { buffer.append(data) }

    /// Извлекает следующую строку без CR/LF или nil, если строка ещё не пришла целиком.
    mutating func nextLine() -> Data? {
        guard let lf = buffer.firstIndex(of: 0x0A) else { return nil }
        var end = lf
        if end > buffer.startIndex, buffer[buffer.index(before: end)] == 0x0D {
            end = buffer.index(before: end)
        }
        let line = Data(buffer[buffer.startIndex..<end])
        buffer = Data(buffer[buffer.index(after: lf)...])
        return line
    }

    /// Остаток без завершающего перевода строки (для конца потока).
    mutating func drainRemainder() -> Data? {
        guard !buffer.isEmpty else { return nil }
        var rest = buffer
        if rest.last == 0x0D { rest.removeLast() }
        buffer = Data()
        return rest.isEmpty ? nil : rest
    }
}
