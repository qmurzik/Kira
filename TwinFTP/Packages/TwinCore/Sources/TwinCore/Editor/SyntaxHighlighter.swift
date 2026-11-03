import Foundation

public enum SyntaxLanguage: String, CaseIterable, Sendable {
    case php, html, css, javascript, json, xml, config, sql, markdown, plain

    public var title: String {
        switch self {
        case .php: return "PHP"
        case .html: return "HTML"
        case .css: return "CSS"
        case .javascript: return "JavaScript"
        case .json: return "JSON"
        case .xml: return "XML"
        case .config: return "Конфигурация"
        case .sql: return "SQL"
        case .markdown: return "Markdown"
        case .plain: return "Текст"
        }
    }

    public static func detect(fileName: String) -> SyntaxLanguage {
        let lower = fileName.lowercased()
        if lower == ".htaccess" || lower == ".htpasswd" || lower == ".env" || lower.hasPrefix(".env.")
            || lower == "nginx.conf" || lower == ".user.ini" || lower == "robots.txt" || lower == ".gitignore" {
            return .config
        }
        let ext = PathUtils.splitExtension(lower).ext.dropFirst()
        switch ext {
        case "php", "phtml", "php3", "php4", "php5", "php7", "php8", "inc": return .php
        case "html", "htm", "xhtml", "tpl", "twig", "vue", "svelte", "shtml": return .html
        case "css", "scss", "sass", "less": return .css
        case "js", "mjs", "cjs", "ts", "tsx", "jsx": return .javascript
        case "json", "webmanifest", "map", "lock": return .json
        case "xml", "svg", "plist", "rss", "atom", "xsl", "xslt", "wsdl": return .xml
        case "conf", "ini", "cfg", "yml", "yaml", "toml", "env", "properties", "htaccess", "cnf", "sh", "bash", "py": return .config
        case "sql": return .sql
        case "md", "markdown": return .markdown
        default: return .plain
        }
    }

    /// Расширения, которые можно открывать в редакторе.
    public static let editableExtensions: Set<String> = [
        "php", "phtml", "inc", "html", "htm", "xhtml", "tpl", "twig", "vue", "css", "scss", "sass", "less",
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "json", "xml", "svg", "txt", "md", "markdown", "ini", "conf", "cfg",
        "yml", "yaml", "toml", "env", "sql", "csv", "log", "htaccess", "htpasswd", "py", "sh", "bash", "rb", "pl",
        "c", "h", "cpp", "swift", "java", "kt", "go", "rs", "properties", "lock", "map", "webmanifest", "rss",
        "atom", "plist", "srt", "vtt", "gitignore", "editorconfig", "shtml", "svelte"
    ]

    public static func isEditable(fileName: String) -> Bool {
        let lower = fileName.lowercased()
        if lower.hasPrefix(".") && !lower.dropFirst().contains(".") { return true } // .htaccess, .env
        let ext = String(PathUtils.splitExtension(lower).ext.dropFirst())
        return editableExtensions.contains(ext)
    }
}

public enum SyntaxTokenKind: String, Sendable, CaseIterable {
    case comment, string, keyword, number, tag, attribute, variable, property, meta, function, constant
}

public struct SyntaxToken: Equatable, Sendable {
    public let range: NSRange
    public let kind: SyntaxTokenKind
}

/// Подсветка синтаксиса на регулярных выражениях. Возвращает диапазоны в UTF-16 (NSString),
/// которые напрямую применяются к NSAttributedString.
public final class SyntaxHighlighter: @unchecked Sendable {
    public static let shared = SyntaxHighlighter()

    private struct Rule {
        let regex: NSRegularExpression
        let kind: SyntaxTokenKind
        let group: Int
    }

    private struct LanguageRules {
        /// Комбинированное выражение: группа 1 — комментарий, группа 2 — строка (кто раньше начался, тот и победил).
        let commentsAndStrings: NSRegularExpression?
        let rules: [Rule]
    }

    private var cache: [SyntaxLanguage: LanguageRules] = [:]
    private let lock = NSLock()

    private static func re(_ p: String, _ opts: NSRegularExpression.Options = []) -> NSRegularExpression {
        // swiftlint:disable:next force_try
        try! NSRegularExpression(pattern: p, options: opts)
    }

    private static func keywords(_ words: [String], caseInsensitive: Bool = false) -> NSRegularExpression {
        re("\\b(?:" + words.joined(separator: "|") + ")\\b", caseInsensitive ? [.caseInsensitive] : [])
    }

    private func rules(for lang: SyntaxLanguage) -> LanguageRules {
        lock.lock(); defer { lock.unlock() }
        if let r = cache[lang] { return r }
        let r = Self.build(lang)
        cache[lang] = r
        return r
    }

    private static let number = re("(?<![\\w.])(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b")

    private static func build(_ lang: SyntaxLanguage) -> LanguageRules {
        switch lang {
        case .php:
            return LanguageRules(
                commentsAndStrings: re("(/\\*[\\s\\S]*?(?:\\*/|$)|//[^\\n]*?(?=\\?>|$)|#(?!\\[)[^\\n]*?(?=\\?>|$))|(\"(?:\\\\[\\s\\S]|[^\"\\\\])*\"|'(?:\\\\[\\s\\S]|[^'\\\\])*'|<<<['\"]?(\\w+)['\"]?[\\s\\S]*?^\\s*\\3\\b)", [.anchorsMatchLines]),
                rules: [
                    Rule(regex: re("<\\?(?:php|=)?|\\?>"), kind: .meta, group: 0),
                    Rule(regex: re("\\$[A-Za-z_\\x{80}-\\x{10FFFF}][\\w\\x{80}-\\x{10FFFF}]*"), kind: .variable, group: 0),
                    Rule(regex: keywords(["abstract", "and", "array", "as", "break", "callable", "case", "catch", "class", "clone", "const", "continue",
                                          "declare", "default", "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
                                          "endswitch", "endwhile", "enum", "extends", "final", "finally", "fn", "for", "foreach", "function", "global",
                                          "goto", "if", "implements", "include", "include_once", "instanceof", "insteadof", "interface", "isset", "list",
                                          "match", "namespace", "new", "or", "print", "private", "protected", "public", "readonly", "require",
                                          "require_once", "return", "static", "switch", "throw", "trait", "try", "unset", "use", "var", "while",
                                          "xor", "yield"], caseInsensitive: true), kind: .keyword, group: 0),
                    Rule(regex: keywords(["true", "false", "null", "TRUE", "FALSE", "NULL", "self", "parent", "__DIR__", "__FILE__", "__LINE__",
                                          "__CLASS__", "__FUNCTION__", "__METHOD__", "__NAMESPACE__", "PHP_EOL", "PHP_VERSION"]), kind: .constant, group: 0),
                    Rule(regex: re("\\b([A-Za-z_]\\w*)\\s*(?=\\()"), kind: .function, group: 1),
                    Rule(regex: number, kind: .number, group: 0)
                ])
        case .javascript:
            return LanguageRules(
                commentsAndStrings: re("(/\\*[\\s\\S]*?(?:\\*/|$)|//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\[\\s\\S]|[^`\\\\])*`)"),
                rules: [
                    Rule(regex: keywords(["async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
                                          "do", "else", "export", "extends", "finally", "for", "from", "function", "get", "if", "import", "in",
                                          "instanceof", "let", "new", "of", "return", "set", "static", "super", "switch", "this", "throw", "try",
                                          "typeof", "var", "void", "while", "with", "yield", "interface", "type", "enum", "implements"]), kind: .keyword, group: 0),
                    Rule(regex: keywords(["true", "false", "null", "undefined", "NaN", "Infinity", "window", "document", "console"]), kind: .constant, group: 0),
                    Rule(regex: re("\\b([A-Za-z_$][\\w$]*)\\s*(?=\\()"), kind: .function, group: 1),
                    Rule(regex: number, kind: .number, group: 0)
                ])
        case .css:
            return LanguageRules(
                commentsAndStrings: re("(/\\*[\\s\\S]*?(?:\\*/|$))|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')"),
                rules: [
                    Rule(regex: re("@[\\w-]+"), kind: .keyword, group: 0),
                    Rule(regex: re("([\\w-]+)\\s*:(?![^{]*\\{)"), kind: .property, group: 1),
                    Rule(regex: re("#[0-9a-fA-F]{3,8}\\b"), kind: .constant, group: 0),
                    Rule(regex: re("(?<![\\w-])-?\\d*\\.?\\d+(?:px|em|rem|%|vh|vw|vmin|vmax|s|ms|deg|fr|pt|ch|ex)?\\b"), kind: .number, group: 0),
                    Rule(regex: re("!important\\b"), kind: .keyword, group: 0),
                    Rule(regex: re("(^|[{};,\\s])([.#]?[A-Za-z_][\\w-]*(?:[.#:][\\w-]+)*)(?=[^{};]*\\{)", [.anchorsMatchLines]), kind: .tag, group: 2)
                ])
        case .json:
            return LanguageRules(
                commentsAndStrings: re("(//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\")"),
                rules: [
                    Rule(regex: keywords(["true", "false", "null"]), kind: .constant, group: 0),
                    Rule(regex: re("-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b"), kind: .number, group: 0)
                ])
        case .html, .xml:
            return LanguageRules(
                commentsAndStrings: re("(<!--[\\s\\S]*?(?:-->|$))|(<!\\[CDATA\\[[\\s\\S]*?\\]\\]>)"),
                rules: [
                    Rule(regex: re("<!DOCTYPE[^>]*>|<\\?xml[^>]*\\?>", [.caseInsensitive]), kind: .meta, group: 0),
                    Rule(regex: re("&[#\\w]+;"), kind: .constant, group: 0)
                ])
        case .config:
            return LanguageRules(
                commentsAndStrings: re("(^[ \\t]*[#;][^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'[^'\\n]*')", [.anchorsMatchLines]),
                rules: [
                    Rule(regex: re("^[ \\t]*(\\[[^\\]\\n]+\\])", [.anchorsMatchLines]), kind: .tag, group: 1),
                    Rule(regex: re("^[ \\t]*(</?[A-Za-z][\\w]*)[^>\\n]*>?", [.anchorsMatchLines]), kind: .tag, group: 1),
                    Rule(regex: re("^[ \\t]*([A-Za-z_][\\w.-]*)(?=[ \\t]*[=:])", [.anchorsMatchLines]), kind: .property, group: 1),
                    Rule(regex: re("^[ \\t]*([A-Z][A-Za-z]+)\\b", [.anchorsMatchLines]), kind: .keyword, group: 1),
                    Rule(regex: re("%\\{[^}\\n]*\\}|\\$\\{[^}\\n]*\\}|\\$[\\w]+"), kind: .variable, group: 0),
                    Rule(regex: re("\\[(?:[A-Z]+,?)+\\]"), kind: .meta, group: 0),
                    Rule(regex: keywords(["on", "off", "true", "false", "yes", "no", "null", "On", "Off"]), kind: .constant, group: 0),
                    Rule(regex: number, kind: .number, group: 0)
                ])
        case .sql:
            return LanguageRules(
                commentsAndStrings: re("(--[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)|#[^\\n]*)|('(?:''|\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|`[^`]*`)"),
                rules: [
                    Rule(regex: keywords(["select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete",
                                          "create", "table", "drop", "alter", "add", "index", "primary", "key", "foreign", "references", "join",
                                          "left", "right", "inner", "outer", "on", "group", "by", "order", "having", "limit", "offset", "as",
                                          "distinct", "union", "all", "null", "is", "in", "like", "between", "exists", "case", "when", "then",
                                          "else", "end", "default", "unique", "auto_increment", "engine", "charset", "collate", "if", "database",
                                          "use", "show", "grant", "int", "varchar", "text", "datetime", "timestamp", "decimal", "bigint", "tinyint"],
                                         caseInsensitive: true), kind: .keyword, group: 0),
                    Rule(regex: number, kind: .number, group: 0)
                ])
        case .markdown:
            return LanguageRules(
                commentsAndStrings: re("(<!--[\\s\\S]*?-->)|(`[^`\\n]+`)"),
                rules: [
                    Rule(regex: re("^#{1,6} [^\\n]*", [.anchorsMatchLines]), kind: .keyword, group: 0),
                    Rule(regex: re("\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__"), kind: .constant, group: 0),
                    Rule(regex: re("\\[[^\\]\\n]*\\]\\([^)\\n]*\\)"), kind: .attribute, group: 0),
                    Rule(regex: re("^\\s*(?:[-*+]|\\d+\\.)\\s", [.anchorsMatchLines]), kind: .meta, group: 0)
                ])
        case .plain:
            return LanguageRules(commentsAndStrings: nil, rules: [])
        }
    }

    // Регулярные выражения для разметки HTML/XML (внутри тегов).
    private static let tagRegex = re("<(/?)([A-Za-z][\\w:.-]*)((?:\"[^\"]*\"|'[^']*'|[^'\">])*)(/?)>|<(/?)([A-Za-z][\\w:.-]*)")
    private static let attrRegex = re("([A-Za-z_:@][\\w:.@-]*)(?=\\s*=)|(\"[^\"]*\"|'[^']*')")
    private static let phpBlockRegex = re("<\\?(?:php\\b|=)?[\\s\\S]*?(?:\\?>|$(?![\\s\\S]))")
    private static let scriptRegex = re("<script\\b[^>]*>([\\s\\S]*?)</script\\s*>", [.caseInsensitive])
    private static let styleRegex = re("<style\\b[^>]*>([\\s\\S]*?)</style\\s*>", [.caseInsensitive])

    // MARK: - Публичный API

    /// Подсветка всего текста (или указанного диапазона, выровненного по строкам).
    public func tokens(for text: String, language: SyntaxLanguage) -> [SyntaxToken] {
        let ns = text as NSString
        let full = NSRange(location: 0, length: ns.length)
        var out: [SyntaxToken] = []
        switch language {
        case .php:
            var phpRanges: [NSRange] = []
            for m in Self.phpBlockRegex.matches(in: text, range: full) {
                phpRanges.append(m.range)
            }
            // Вне блоков PHP — HTML.
            var cursor = 0
            for r in phpRanges {
                if r.location > cursor {
                    highlightMarkup(text, range: NSRange(location: cursor, length: r.location - cursor), into: &out, html: true)
                }
                highlight(text, range: r, language: .php, into: &out)
                cursor = NSMaxRange(r)
            }
            if cursor < ns.length {
                highlightMarkup(text, range: NSRange(location: cursor, length: ns.length - cursor), into: &out, html: true)
            }
        case .html:
            highlightMarkup(text, range: full, into: &out, html: true)
        case .xml:
            highlightMarkup(text, range: full, into: &out, html: false)
        default:
            highlight(text, range: full, language: language, into: &out)
        }
        return out
    }

    private func highlightMarkup(_ text: String, range: NSRange, into out: inout [SyntaxToken], html: Bool) {
        var claimed = IndexSet()
        // Встроенные <script>/<style>
        if html {
            for (regex, lang) in [(Self.scriptRegex, SyntaxLanguage.javascript), (Self.styleRegex, .css)] {
                for m in regex.matches(in: text, range: range) {
                    guard m.range(at: 1).length > 0 else { continue }
                    let inner = m.range(at: 1)
                    highlight(text, range: inner, language: lang, into: &out)
                    claimed.insert(integersIn: inner.location..<NSMaxRange(inner))
                }
            }
        }
        // Комментарии и CDATA
        let base = rules(for: html ? .html : .xml)
        for m in (base.commentsAndStrings?.matches(in: text, range: range) ?? []) {
            
            if claimed.intersects(integersIn: m.range.location..<NSMaxRange(m.range)) { continue }
            out.append(SyntaxToken(range: m.range, kind: m.range(at: 1).location != NSNotFound ? .comment : .string))
            claimed.insert(integersIn: m.range.location..<NSMaxRange(m.range))
        }
        // Теги и атрибуты
        for m in Self.tagRegex.matches(in: text, range: range) {
            
            if claimed.contains(m.range.location) { continue }
            let nameRange = m.range(at: 2).location != NSNotFound ? m.range(at: 2) : m.range(at: 6)
            if nameRange.location != NSNotFound {
                // Подсвечиваем "<", "</" и имя тега
                let start = m.range.location
                out.append(SyntaxToken(range: NSRange(location: start, length: NSMaxRange(nameRange) - start), kind: .tag))
            }
            let attrs = m.range(at: 3)
            if attrs.location != NSNotFound && attrs.length > 0 {
                for a in Self.attrRegex.matches(in: text, range: attrs) {
                    if a.range(at: 1).location != NSNotFound {
                        out.append(SyntaxToken(range: a.range(at: 1), kind: .attribute))
                    } else {
                        out.append(SyntaxToken(range: a.range(at: 2), kind: .string))
                    }
                }
            }
            if m.range(at: 2).location != NSNotFound {
                // Закрывающая скобка тега
                out.append(SyntaxToken(range: NSRange(location: NSMaxRange(m.range) - 1 - m.range(at: 4).length, length: 1 + m.range(at: 4).length), kind: .tag))
            }
        }
        for rule in base.rules {
            for m in rule.regex.matches(in: text, range: range) {
                
                let r = m.range(at: rule.group)
                if r.location == NSNotFound || claimed.intersects(integersIn: r.location..<NSMaxRange(r)) { continue }
                out.append(SyntaxToken(range: r, kind: rule.kind))
            }
        }
    }

    private func highlight(_ text: String, range: NSRange, language: SyntaxLanguage, into out: inout [SyntaxToken]) {
        let lr = rules(for: language)
        var claimed = IndexSet()
        for m in (lr.commentsAndStrings?.matches(in: text, range: range) ?? []) {
            guard m.range.length > 0 else { continue }
            let isComment = m.range(at: 1).location != NSNotFound
            out.append(SyntaxToken(range: m.range, kind: isComment ? .comment : .string))
            claimed.insert(integersIn: m.range.location..<NSMaxRange(m.range))
        }
        for rule in lr.rules {
            for m in rule.regex.matches(in: text, range: range) {
                
                let r = m.range(at: rule.group)
                guard r.location != NSNotFound, r.length > 0 else { continue }
                if claimed.intersects(integersIn: r.location..<NSMaxRange(r)) { continue }
                out.append(SyntaxToken(range: r, kind: rule.kind))
                if rule.kind == .meta || rule.kind == .variable {
                    claimed.insert(integersIn: r.location..<NSMaxRange(r))
                }
            }
        }
    }
}
