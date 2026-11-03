import Foundation

/// Работа с POSIX-путями (используется для путей FTP и для локальных путей внутри песочницы).
public enum PathUtils {
    /// Нормализует абсолютный путь: убирает повторные "/", "." и разворачивает "..".
    /// Подъём выше корня ограничивается корнем.
    public static func normalize(_ path: String) -> String {
        let isAbsolute = path.hasPrefix("/")
        var stack: [Substring] = []
        for comp in path.split(separator: "/", omittingEmptySubsequences: true) {
            if comp == "." { continue }
            if comp == ".." {
                if !stack.isEmpty { stack.removeLast() }
                continue
            }
            stack.append(comp)
        }
        let joined = stack.joined(separator: "/")
        return isAbsolute || joined.isEmpty ? "/" + joined : joined
    }

    public static func join(_ base: String, _ name: String) -> String {
        if name.hasPrefix("/") { return normalize(name) }
        if base.isEmpty { return normalize(name) }
        if base.hasSuffix("/") { return normalize(base + name) }
        return normalize(base + "/" + name)
    }

    /// Соединение без нормализации ".." в имени (имя проверяется отдельно).
    public static func child(_ base: String, _ name: String) -> String {
        if base == "/" { return "/" + name }
        if base.hasSuffix("/") { return base + name }
        return base + "/" + name
    }

    public static func parent(_ path: String) -> String {
        let n = normalize(path)
        if n == "/" { return "/" }
        guard let slash = n.lastIndex(of: "/") else { return "/" }
        if slash == n.startIndex { return "/" }
        return String(n[n.startIndex..<slash])
    }

    public static func lastComponent(_ path: String) -> String {
        let n = normalize(path)
        if n == "/" { return "/" }
        return String(n.split(separator: "/").last ?? "")
    }

    /// Компоненты для хлебных крошек: [("/", "/"), ("www", "/www"), ...]
    public static func breadcrumbs(_ path: String) -> [(name: String, path: String)] {
        let n = normalize(path)
        var result: [(String, String)] = [("/", "/")]
        var current = ""
        for comp in n.split(separator: "/") {
            current += "/" + comp
            result.append((String(comp), current))
        }
        return result
    }

    /// Является ли `path` вложенным в `ancestor` (или равным ему).
    public static func isDescendant(_ path: String, of ancestor: String) -> Bool {
        let p = normalize(path), a = normalize(ancestor)
        if a == "/" { return true }
        return p == a || p.hasPrefix(a + "/")
    }

    /// Относительный путь `path` от `base` (если вложен).
    public static func relative(_ path: String, to base: String) -> String? {
        let p = normalize(path), b = normalize(base)
        if p == b { return "" }
        if b == "/" { return String(p.dropFirst()) }
        guard p.hasPrefix(b + "/") else { return nil }
        return String(p.dropFirst(b.count + 1))
    }

    /// Проверка имени файла/папки, вводимого пользователем.
    public static func validateName(_ name: String) -> String? {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty { return "Имя не может быть пустым." }
        if name == "." || name == ".." { return "Недопустимое имя." }
        if name.contains("/") { return "Имя не может содержать символ «/»." }
        if name.contains("\0") || name.contains("\r") || name.contains("\n") {
            return "Имя содержит недопустимые символы."
        }
        if name.utf8.count > 255 { return "Имя слишком длинное (более 255 байт)." }
        return nil
    }

    /// Разделяет имя на основу и расширение: "archive.tar.gz" → ("archive.tar", ".gz").
    public static func splitExtension(_ name: String) -> (base: String, ext: String) {
        guard let dot = name.lastIndex(of: "."), dot != name.startIndex else { return (name, "") }
        return (String(name[..<dot]), String(name[dot...]))
    }

    /// Подбирает свободное имя вида "file (2).txt".
    public static func uniqueName(for name: String, existing: Set<String>) -> String {
        if !existing.contains(name) { return name }
        let (base, ext) = splitExtension(name)
        var i = 2
        while true {
            let candidate = "\(base) (\(i))\(ext)"
            if !existing.contains(candidate) { return candidate }
            i += 1
        }
    }
}
