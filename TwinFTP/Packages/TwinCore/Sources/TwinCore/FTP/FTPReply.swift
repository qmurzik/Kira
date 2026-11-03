import Foundation

/// Ответ FTP-сервера (возможно, многострочный).
public struct FTPReply: Equatable, Sendable, CustomStringConvertible {
    public let code: Int
    /// Все строки ответа без завершающих CRLF.
    public let lines: [String]

    public init(code: Int, lines: [String]) {
        self.code = code
        self.lines = lines
    }

    /// Текст ответа без кода (первая строка без "NNN " / "NNN-").
    public var message: String {
        guard let first = lines.first else { return "" }
        let text: String
        if first.count >= 4 {
            text = String(first.dropFirst(4))
        } else {
            text = ""
        }
        if lines.count == 1 { return text.trimmingCharacters(in: .whitespaces) }
        var rest = lines.dropFirst().map { line -> String in
            // Последняя строка многострочного ответа начинается с "NNN "
            if line.count >= 4, line.prefix(3) == String(code), line.dropFirst(3).first == " " {
                return String(line.dropFirst(4))
            }
            return line
        }
        rest.insert(text, at: 0)
        return rest.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public var isPreliminary: Bool { (100..<200).contains(code) }
    public var isSuccess: Bool { (200..<300).contains(code) }
    public var isIntermediate: Bool { (300..<400).contains(code) }
    public var isTransientError: Bool { (400..<500).contains(code) }
    public var isPermanentError: Bool { (500..<600).contains(code) }

    public var description: String { lines.joined(separator: "\n") }
}

/// Инкрементальный разборщик ответов управляющего соединения.
/// Принимает строки (уже декодированные) и собирает многострочные ответы по RFC 959:
/// первая строка "NNN-текст", последняя "NNN текст" с тем же кодом.
public struct FTPReplyAssembler: Sendable {
    private var pendingCode: Int?
    private var pendingLines: [String] = []

    public init() {}

    /// Добавляет строку. Возвращает готовый ответ, если он завершён.
    public mutating func feed(line rawLine: String) throws -> FTPReply? {
        let line = rawLine
        if let code = pendingCode {
            pendingLines.append(line)
            if Self.isTerminator(line, code: code) {
                let reply = FTPReply(code: code, lines: pendingLines)
                pendingCode = nil
                pendingLines = []
                return reply
            }
            return nil
        }
        // Начало нового ответа
        guard let code = Self.leadingCode(line) else {
            // Некоторые серверы шлют пустые строки или мусор до приветствия — игнорируем пустые.
            if line.trimmingCharacters(in: .whitespaces).isEmpty { return nil }
            throw FTPError.malformedReply(line)
        }
        let sep = line.count > 3 ? line[line.index(line.startIndex, offsetBy: 3)] : " "
        if sep == "-" {
            pendingCode = code
            pendingLines = [line]
            return nil
        }
        return FTPReply(code: code, lines: [line])
    }

    public var isInsideMultiline: Bool { pendingCode != nil }

    static func leadingCode(_ line: String) -> Int? {
        let utf8 = Array(line.utf8.prefix(4))
        guard utf8.count >= 3 else { return nil }
        for b in utf8[0..<3] where !(b >= 0x30 && b <= 0x39) { return nil }
        if utf8.count == 4, utf8[3] != 0x20, utf8[3] != 0x2D { return nil }
        let code = Int(utf8[0] - 0x30) * 100 + Int(utf8[1] - 0x30) * 10 + Int(utf8[2] - 0x30)
        guard (100...599).contains(code) else { return nil }
        return code
    }

    static func isTerminator(_ line: String, code: Int) -> Bool {
        guard let c = leadingCode(line), c == code else { return false }
        let utf8 = Array(line.utf8.prefix(4))
        return utf8.count == 3 || utf8[3] == 0x20
    }
}

/// Утилиты разбора ответов PASV / EPSV / SIZE / MDTM / PWD / FEAT.
public enum FTPReplyParsing {
    /// "227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)."
    public static func parsePASV(_ message: String) -> (host: String, port: Int)? {
        // Ищем шесть чисел, разделённых запятыми (скобки не обязательны).
        let scalars = Array(message.unicodeScalars)
        var i = 0
        while i < scalars.count {
            if CharacterSet.decimalDigits.contains(scalars[i]) {
                var numbers: [Int] = []
                var j = i
                var current = ""
                while j < scalars.count {
                    let s = scalars[j]
                    if CharacterSet.decimalDigits.contains(s) {
                        current.unicodeScalars.append(s)
                    } else if s == ",", !current.isEmpty {
                        numbers.append(Int(current) ?? -1)
                        current = ""
                    } else if s == " " && current.isEmpty && !numbers.isEmpty {
                        // допускаем пробел после запятой
                    } else {
                        break
                    }
                    j += 1
                }
                if !current.isEmpty { numbers.append(Int(current) ?? -1) }
                if numbers.count >= 6 {
                    let n = Array(numbers.prefix(6))
                    if n.allSatisfy({ (0...255).contains($0) }) {
                        let host = "\(n[0]).\(n[1]).\(n[2]).\(n[3])"
                        let port = n[4] * 256 + n[5]
                        if port > 0 { return (host, port) }
                    }
                }
                i = max(j, i + 1)
            } else {
                i += 1
            }
        }
        return nil
    }

    /// "229 Entering Extended Passive Mode (|||6446|)"
    public static func parseEPSV(_ message: String) -> Int? {
        guard let open = message.firstIndex(of: "("),
              let close = message[open...].firstIndex(of: ")") else { return nil }
        let inner = message[message.index(after: open)..<close]
        guard let delim = inner.first else { return nil }
        let parts = inner.split(separator: delim, omittingEmptySubsequences: false)
        // формат: <d><d><d><port><d> => ["", "", "", "port", ""]
        guard parts.count >= 4, let port = Int(parts[3]), (1...65535).contains(port) else { return nil }
        return port
    }

    /// "257 "/home/user" is current directory" — двойные кавычки внутри экранируются удвоением.
    public static func parsePWD(_ message: String) -> String? {
        guard let start = message.firstIndex(of: "\"") else {
            // Нестандартный сервер: путь — первый токен
            let token = message.split(separator: " ").first.map(String.init)
            return token?.hasPrefix("/") == true ? token : nil
        }
        var result = ""
        var idx = message.index(after: start)
        while idx < message.endIndex {
            let ch = message[idx]
            if ch == "\"" {
                let next = message.index(after: idx)
                if next < message.endIndex, message[next] == "\"" {
                    result.append("\"")
                    idx = message.index(after: next)
                    continue
                }
                return result
            }
            result.append(ch)
            idx = message.index(after: idx)
        }
        return result.isEmpty ? nil : result
    }

    public static func parseSize(_ message: String) -> Int64? {
        let token = message.trimmingCharacters(in: .whitespaces).split(separator: " ").first.map(String.init) ?? ""
        return Int64(token)
    }

    /// "213 20240115103000" или "213 20240115103000.123"
    public static func parseMDTM(_ message: String) -> Date? {
        let token = message.trimmingCharacters(in: .whitespaces).split(separator: " ").first.map(String.init) ?? ""
        return parseTimeVal(token)
    }

    /// Разбор формата времени YYYYMMDDHHMMSS[.sss] (UTC) из MDTM/MLSD.
    public static func parseTimeVal(_ token: String) -> Date? {
        let digits = token.split(separator: ".", maxSplits: 1)
        guard let main = digits.first, main.count >= 14 else { return nil }
        let chars = Array(main)
        func num(_ from: Int, _ len: Int) -> Int? { Int(String(chars[from..<(from + len)])) }
        guard let y = num(0, 4), let mo = num(4, 2), let d = num(6, 2),
              let h = num(8, 2), let mi = num(10, 2), let s = num(12, 2) else { return nil }
        var comps = DateComponents()
        comps.year = y; comps.month = mo; comps.day = d
        comps.hour = h; comps.minute = mi; comps.second = s
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        guard var date = cal.date(from: comps) else { return nil }
        if digits.count == 2, let frac = Double("0." + digits[1]) {
            date.addTimeInterval(frac)
        }
        return date
    }

    /// Разбор ответа FEAT: возвращает множество возможностей в верхнем регистре
    /// (ключ — первое слово, значение — остаток строки).
    public static func parseFeatures(_ reply: FTPReply) -> [String: String] {
        var result: [String: String] = [:]
        guard reply.code == 211 else { return result }
        for line in reply.lines.dropFirst() {
            if FTPReplyAssembler.isTerminator(line, code: 211) { continue }
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty { continue }
            let parts = trimmed.split(separator: " ", maxSplits: 1)
            let key = String(parts[0]).uppercased()
            result[key] = parts.count > 1 ? String(parts[1]) : ""
        }
        return result
    }
}
