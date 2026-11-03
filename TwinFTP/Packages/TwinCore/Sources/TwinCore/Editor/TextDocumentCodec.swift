import Foundation

public enum TextEncodingKind: String, Codable, Sendable, CaseIterable {
    case utf8, utf8BOM, utf16LE, utf16BE, windows1251, koi8r, latin1

    public var title: String {
        switch self {
        case .utf8: return "UTF-8"
        case .utf8BOM: return "UTF-8 с BOM"
        case .utf16LE: return "UTF-16 LE"
        case .utf16BE: return "UTF-16 BE"
        case .windows1251: return "Windows-1251"
        case .koi8r: return "KOI8-R"
        case .latin1: return "ISO-8859-1"
        }
    }
}

public enum LineEnding: String, Codable, Sendable {
    case lf, crlf, cr

    public var title: String {
        switch self {
        case .lf: return "LF (Unix)"
        case .crlf: return "CRLF (Windows)"
        case .cr: return "CR (Mac OS 9)"
        }
    }

    public var string: String {
        switch self {
        case .lf: return "\n"
        case .crlf: return "\r\n"
        case .cr: return "\r"
        }
    }
}

public enum TextDocumentError: Error, LocalizedError, Equatable {
    case binary
    case tooLarge(Int64)
    case cannotEncode(TextEncodingKind)

    public var errorDescription: String? {
        switch self {
        case .binary: return "Файл похож на двоичный — открыть его в текстовом редакторе нельзя без риска повредить."
        case .tooLarge(let s): return "Файл слишком большой для редактора (\(ByteFormatter.string(s))). Максимум — \(ByteFormatter.string(TextDocumentCodec.maxEditableSize))."
        case .cannotEncode(let e): return "Текст содержит символы, которые нельзя сохранить в кодировке \(e.title). Смените кодировку на UTF-8."
        }
    }
}

/// Разобранный текстовый файл: текст с переводами строк "\n" + сведения для обратного сохранения.
public struct DecodedText: Sendable, Equatable {
    public var text: String
    public var encoding: TextEncodingKind
    public var lineEnding: LineEnding
    public var hadMixedLineEndings: Bool
}

/// Определение кодировки, переводов строк и обратное кодирование без лишних изменений.
public enum TextDocumentCodec {
    public static let maxEditableSize: Int64 = 20 * 1024 * 1024

    /// Эвристика двоичного файла: NUL-байты или много управляющих символов в начале.
    public static func looksBinary(_ data: Data) -> Bool {
        let sample = data.prefix(8192)
        if sample.isEmpty { return false }
        // UTF-16 с BOM содержит нули легитимно
        if sample.starts(with: [0xFF, 0xFE]) || sample.starts(with: [0xFE, 0xFF]) { return false }
        var control = 0
        for b in sample {
            if b == 0 { return true }
            if b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B) { control += 1 }
        }
        return Double(control) / Double(sample.count) > 0.1
    }

    public static func decode(_ data: Data, preferred: TextEncodingKind? = nil) throws -> DecodedText {
        if Int64(data.count) > maxEditableSize { throw TextDocumentError.tooLarge(Int64(data.count)) }
        if looksBinary(data) { throw TextDocumentError.binary }
        var encoding: TextEncodingKind
        var raw: String
        if let preferred {
            encoding = preferred
            raw = decodeRaw(data, as: preferred)
        } else if data.starts(with: [0xEF, 0xBB, 0xBF]) {
            encoding = .utf8BOM
            raw = String(decoding: data.dropFirst(3), as: UTF8.self)
        } else if data.starts(with: [0xFF, 0xFE]) {
            encoding = .utf16LE
            raw = decodeRaw(data, as: .utf16LE)
        } else if data.starts(with: [0xFE, 0xFF]) {
            encoding = .utf16BE
            raw = decodeRaw(data, as: .utf16BE)
        } else if let s = String(data: data, encoding: .utf8) {
            encoding = .utf8
            raw = s
        } else {
            encoding = .windows1251
            raw = SingleByteCodec.decode(data, encoding: .windows1251)
        }
        let (normalized, ending, mixed) = normalizeLineEndings(raw)
        return DecodedText(text: normalized, encoding: encoding, lineEnding: ending, hadMixedLineEndings: mixed)
    }

    static func decodeRaw(_ data: Data, as enc: TextEncodingKind) -> String {
        switch enc {
        case .utf8: return String(decoding: data, as: UTF8.self)
        case .utf8BOM: return String(decoding: data.starts(with: [0xEF, 0xBB, 0xBF]) ? data.dropFirst(3) : data[...], as: UTF8.self)
        case .utf16LE, .utf16BE:
            var body = data
            if body.starts(with: [0xFF, 0xFE]) || body.starts(with: [0xFE, 0xFF]) { body = body.dropFirst(2) }
            var units: [UInt16] = []
            units.reserveCapacity(body.count / 2)
            var i = body.startIndex
            while i + 1 < body.endIndex {
                let a = UInt16(body[i]), b = UInt16(body[i + 1])
                units.append(enc == .utf16LE ? (a | (b << 8)) : ((a << 8) | b))
                i += 2
            }
            return String(decoding: units, as: UTF16.self)
        case .windows1251: return SingleByteCodec.decode(data, encoding: .windows1251)
        case .koi8r: return SingleByteCodec.decode(data, encoding: .koi8r)
        case .latin1: return SingleByteCodec.decode(data, encoding: .latin1)
        }
    }

    /// Приводит переводы строк к "\n" и определяет преобладающий стиль.
    public static func normalizeLineEndings(_ s: String) -> (String, LineEnding, Bool) {
        var crlf = 0, lf = 0, cr = 0
        let scalars = Array(s.unicodeScalars)
        var i = 0
        while i < scalars.count {
            if scalars[i] == "\r" {
                if i + 1 < scalars.count && scalars[i + 1] == "\n" { crlf += 1; i += 2; continue }
                cr += 1
            } else if scalars[i] == "\n" {
                lf += 1
            }
            i += 1
        }
        let kinds = [crlf, lf, cr].filter { $0 > 0 }.count
        let ending: LineEnding = crlf >= lf && crlf >= cr && crlf > 0 ? .crlf : (cr > lf ? .cr : .lf)
        if crlf == 0 && cr == 0 { return (s, .lf, false) }
        let normalized = s.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        return (normalized, ending, kinds > 1)
    }

    public static func encode(_ text: String, encoding: TextEncodingKind, lineEnding: LineEnding) throws -> Data {
        let withEndings = lineEnding == .lf ? text : text.replacingOccurrences(of: "\n", with: lineEnding.string)
        switch encoding {
        case .utf8: return Data(withEndings.utf8)
        case .utf8BOM: return Data([0xEF, 0xBB, 0xBF]) + Data(withEndings.utf8)
        case .utf16LE, .utf16BE:
            var d = Data(encoding == .utf16LE ? [0xFF, 0xFE] : [0xFE, 0xFF])
            for u in withEndings.utf16 {
                if encoding == .utf16LE { d.append(UInt8(u & 0xFF)); d.append(UInt8(u >> 8)) }
                else { d.append(UInt8(u >> 8)); d.append(UInt8(u & 0xFF)) }
            }
            return d
        case .windows1251, .koi8r, .latin1:
            let ftpEnc: FTPEncoding = encoding == .windows1251 ? .windows1251 : (encoding == .koi8r ? .koi8r : .latin1)
            guard let d = SingleByteCodec.encode(withEndings, encoding: ftpEnc) else { throw TextDocumentError.cannotEncode(encoding) }
            return d
        }
    }
}
