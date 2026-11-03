import Foundation

/// Разбор листингов каталогов: MLSD (RFC 3659), UNIX `ls -l`, DOS/IIS.
public enum FTPListParser {

    // MARK: MLSD

    /// Разбирает одну строку MLSD/MLST: "type=file;size=12;modify=20240101120000; name".
    public static func parseMLSDLine(_ line: String, directory: String) -> FileItem? {
        var line = line
        if line.hasPrefix(" ") && !line.contains("=") { line.removeFirst() }
        guard let space = line.firstIndex(of: " ") else { return nil }
        let factsPart = line[line.startIndex..<space]
        let name = String(line[line.index(after: space)...])
        guard !name.isEmpty else { return nil }
        var facts: [String: String] = [:]
        for fact in factsPart.split(separator: ";") {
            guard let eq = fact.firstIndex(of: "=") else { continue }
            let key = fact[fact.startIndex..<eq].lowercased()
            facts[key] = String(fact[fact.index(after: eq)...])
        }
        let type = facts["type"]?.lowercased() ?? "file"
        if type == "cdir" || type == "pdir" || name == "." || name == ".." { return nil }
        // MLST возвращает полный путь — берём последний компонент
        let baseName = name.contains("/") && name.hasPrefix("/") ? PathUtils.lastComponent(name) : name
        var kind: FileKind
        var linkTarget: String?
        switch type {
        case "dir": kind = .directory
        case "file": kind = .file
        default:
            if type.hasPrefix("os.unix=slink") || type.hasPrefix("os.unix=symlink") {
                kind = .symlink
                if let r = type.range(of: ":") { linkTarget = String(type[r.upperBound...]) }
            } else if type.hasPrefix("os.unix=") {
                kind = .other
            } else {
                kind = .file
            }
        }
        var mode: Int?
        if let m = facts["unix.mode"], let v = Int(m, radix: 8) { mode = v & 0o777 }
        var perms: String?
        if let mode { perms = UnixPermissions.string(fromMode: mode) }
        let size = facts["size"].flatMap { Int64($0) } ?? facts["sizd"].flatMap { Int64($0) }
        let modified = facts["modify"].flatMap { FTPReplyParsing.parseTimeVal($0) }
        return FileItem(name: baseName,
                        path: PathUtils.child(directory, baseName),
                        kind: kind,
                        size: kind == .directory ? nil : size,
                        modified: modified,
                        permissions: perms,
                        unixMode: mode,
                        linkTarget: linkTarget)
    }

    public static func parseMLSD(_ lines: [String], directory: String) -> [FileItem] {
        lines.compactMap { parseMLSDLine($0, directory: directory) }
    }

    // MARK: LIST

    public static func parseLIST(_ lines: [String], directory: String, now: Date = Date()) -> [FileItem] {
        var result: [FileItem] = []
        for raw in lines {
            let line = raw.trimmingCharacters(in: CharacterSet(charactersIn: "\r"))
            if line.isEmpty { continue }
            let lower = line.lowercased()
            if lower.hasPrefix("total ") || lower.hasPrefix("итого ") || lower.hasPrefix("всего ") { continue }
            if let item = parseUnixLine(line, directory: directory, now: now)
                ?? parseDOSLine(line, directory: directory) {
                if item.name == "." || item.name == ".." { continue }
                result.append(item)
            }
        }
        return result
    }

    private static let englishMonths = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
    private static let russianMonths: [[String]] = [
        ["янв"], ["фев"], ["мар"], ["апр"], ["май", "мая"], ["июн"],
        ["июл"], ["авг"], ["сен"], ["окт"], ["ноя"], ["дек"]
    ]

    static func monthIndex(_ token: Substring) -> Int? {
        var t = token.lowercased()
        if t.hasSuffix(".") { t.removeLast() }
        if t.count < 3 { return nil }
        let prefix = String(t.prefix(3))
        if let i = englishMonths.firstIndex(of: prefix), t.count <= 9 { return i + 1 }
        for (i, variants) in russianMonths.enumerated() {
            for v in variants where t.hasPrefix(v) { return i + 1 }
        }
        return nil
    }

    private struct Token {
        let text: Substring
        let start: String.Index
        let end: String.Index
    }

    private static func tokenize(_ line: String) -> [Token] {
        var tokens: [Token] = []
        var i = line.startIndex
        while i < line.endIndex {
            while i < line.endIndex, line[i] == " " || line[i] == "\t" { i = line.index(after: i) }
            if i >= line.endIndex { break }
            let start = i
            while i < line.endIndex, line[i] != " " && line[i] != "\t" { i = line.index(after: i) }
            tokens.append(Token(text: line[start..<i], start: start, end: i))
        }
        return tokens
    }

    private static func isTime(_ t: Substring) -> (Int, Int)? {
        let parts = t.split(separator: ":")
        guard parts.count == 2 || parts.count == 3, let h = Int(parts[0]), let m = Int(parts[1]),
              (0...23).contains(h), (0...59).contains(m) else { return nil }
        return (h, m)
    }

    private static func isYear(_ t: Substring) -> Int? {
        guard t.count == 4, let y = Int(t), (1900...2200).contains(y) else { return nil }
        return y
    }

    private static func isDay(_ t: Substring) -> Int? {
        var s = t
        if s.hasSuffix(".") || s.hasSuffix(",") { s = s.dropLast() }
        guard let d = Int(s), (1...31).contains(d) else { return nil }
        return d
    }

    /// Разбор строки в стиле `ls -l`:
    /// "-rw-r--r--   1 user  group   1234 Jan 15 10:30 имя файла.txt"
    static func parseUnixLine(_ line: String, directory: String, now: Date) -> FileItem? {
        guard let first = line.first, "-dlbcps".contains(first) else { return nil }
        let tokens = tokenize(line)
        guard tokens.count >= 4 else { return nil }
        let permToken = tokens[0].text
        guard permToken.count >= 10 else { return nil }
        let permChars = Array(permToken)
        let validPerm = permChars[1..<10].allSatisfy { "rwxsStTl-".contains($0) }
        guard validPerm else { return nil }

        // Ищем дату: [Mon DD (HH:MM|YYYY)] | [DD Mon (HH:MM|YYYY)] | [YYYY-MM-DD HH:MM]
        var dateStart = -1, dateEnd = -1
        var month = 0, day = 0, year: Int?, hour = 0, minute = 0
        var i = 2
        while i < tokens.count - 1 {
            let t0 = tokens[i].text
            if i + 2 < tokens.count, let m = monthIndex(t0), let d = isDay(tokens[i + 1].text) {
                let t2 = tokens[i + 2].text
                if let (h, mi) = isTime(t2) { month = m; day = d; hour = h; minute = mi; year = nil }
                else if let y = isYear(t2) { month = m; day = d; year = y }
                else { i += 1; continue }
                dateStart = i; dateEnd = i + 2
                break
            }
            if i + 2 < tokens.count, let d = isDay(t0), let m = monthIndex(tokens[i + 1].text) {
                let t2 = tokens[i + 2].text
                if let (h, mi) = isTime(t2) { month = m; day = d; hour = h; minute = mi; year = nil }
                else if let y = isYear(t2) { month = m; day = d; year = y }
                else { i += 1; continue }
                dateStart = i; dateEnd = i + 2
                break
            }
            // ISO: 2024-01-15 10:30
            let iso = t0.split(separator: "-")
            if iso.count == 3, let y = Int(iso[0]), iso[0].count == 4, let mo = Int(iso[1]), let d = Int(iso[2]),
               let (h, mi) = isTime(tokens[i + 1].text) {
                year = y; month = mo; day = d; hour = h; minute = mi
                dateStart = i; dateEnd = i + 1
                break
            }
            i += 1
        }
        guard dateStart > 1, dateEnd + 1 < tokens.count else { return nil }

        // Размер — числовой токен непосредственно перед датой.
        var size: Int64?
        let sizeToken = tokens[dateStart - 1].text
        if let s = Int64(sizeToken) { size = s }
        else if let s = parseHumanSize(sizeToken) { size = s }

        // Имя — всё после даты, с сохранением пробелов внутри.
        var name = String(line[tokens[dateEnd + 1].start...])
        var kind: FileKind
        switch first {
        case "d": kind = .directory
        case "-": kind = .file
        case "l": kind = .symlink
        default: kind = .other
        }
        var linkTarget: String?
        if kind == .symlink, let arrow = name.range(of: " -> ") {
            linkTarget = String(name[arrow.upperBound...])
            name = String(name[..<arrow.lowerBound])
        }
        if name.isEmpty { return nil }

        let date = makeDate(year: year, month: month, day: day, hour: hour, minute: minute, now: now)
        let mode = UnixPermissions.mode(fromString: String(permToken.prefix(10)))
        return FileItem(name: name,
                        path: PathUtils.child(directory, name),
                        kind: kind,
                        size: kind == .directory ? nil : size,
                        modified: date,
                        permissions: String(permToken.dropFirst().prefix(9)),
                        unixMode: mode,
                        linkTarget: linkTarget)
    }

    private static func parseHumanSize(_ t: Substring) -> Int64? {
        guard let last = t.last, "KMGkmg".contains(last), let v = Double(t.dropLast()) else { return nil }
        let mult: Double = last.lowercased() == "k" ? 1024 : last.lowercased() == "m" ? 1024 * 1024 : 1024 * 1024 * 1024
        return Int64(v * mult)
    }

    private static func makeDate(year: Int?, month: Int, day: Int, hour: Int, minute: Int, now: Date) -> Date? {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        var comps = DateComponents()
        comps.month = month; comps.day = day; comps.hour = hour; comps.minute = minute
        if let year {
            comps.year = year
            return cal.date(from: comps)
        }
        // Без года: текущий год, но если дата в будущем (> суток) — прошлый.
        let currentYear = cal.component(.year, from: now)
        comps.year = currentYear
        guard let d = cal.date(from: comps) else { return nil }
        if d.timeIntervalSince(now) > 86400 * 2 {
            comps.year = currentYear - 1
            return cal.date(from: comps)
        }
        return d
    }

    /// DOS/IIS: "01-15-24  10:30AM       <DIR>          folder"
    ///          "2024-01-15  10:30         1234 file.txt"
    static func parseDOSLine(_ line: String, directory: String) -> FileItem? {
        let tokens = tokenize(line)
        guard tokens.count >= 4 else { return nil }
        let dateParts = tokens[0].text.split(whereSeparator: { $0 == "-" || $0 == "/" || $0 == "." })
        guard dateParts.count == 3, dateParts.allSatisfy({ Int($0) != nil }) else { return nil }
        var timeText = String(tokens[1].text)
        var nextIndex = 2
        var pm = false, am = false
        if timeText.uppercased().hasSuffix("PM") { pm = true; timeText.removeLast(2) }
        else if timeText.uppercased().hasSuffix("AM") { am = true; timeText.removeLast(2) }
        else if tokens.count > 4, ["AM", "PM"].contains(tokens[2].text.uppercased()) {
            pm = tokens[2].text.uppercased() == "PM"; am = !pm; nextIndex = 3
        }
        guard let (h0, mi) = isTime(Substring(timeText)) else { return nil }
        var h = h0
        if pm && h < 12 { h += 12 }
        if am && h == 12 { h = 0 }
        let sizeTok = tokens[nextIndex].text
        guard nextIndex + 1 < tokens.count else { return nil }
        let name = String(line[tokens[nextIndex + 1].start...])
        let isDir = sizeTok.uppercased() == "<DIR>"
        let size = isDir ? nil : Int64(sizeTok.replacingOccurrences(of: ",", with: ""))
        if !isDir && size == nil { return nil }

        var y: Int, mo: Int, d: Int
        let a = Int(dateParts[0])!, b = Int(dateParts[1])!, c = Int(dateParts[2])!
        if dateParts[0].count == 4 { y = a; mo = b; d = c }
        else { mo = a; d = b; y = c < 100 ? (c < 70 ? 2000 + c : 1900 + c) : c }
        if mo > 12 && d <= 12 { swap(&mo, &d) }
        let date = makeDate(year: y, month: mo, day: d, hour: h, minute: mi, now: Date())
        return FileItem(name: name, path: PathUtils.child(directory, name),
                        kind: isDir ? .directory : .file, size: size, modified: date)
    }
}
