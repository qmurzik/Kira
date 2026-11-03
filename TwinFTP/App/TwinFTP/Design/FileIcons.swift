import SwiftUI
import TwinCore

/// Иконки типов файлов (SF Symbols) с цветовой кодировкой.
enum FileIcon {
    struct Style {
        let symbol: String
        let color: Color
    }

    static func style(for item: FileItem) -> Style {
        if item.isDirectory {
            if item.kind == .symlink { return Style(symbol: "folder.badge.gearshape", color: Theme.folder) }
            return Style(symbol: "folder.fill", color: Theme.folder)
        }
        if item.kind == .symlink { return Style(symbol: "link", color: Theme.textSecondary) }
        if item.name.hasSuffix(".icloud") { return Style(symbol: "icloud.and.arrow.down", color: .blue) }
        return style(forExtension: item.fileExtension, name: item.name)
    }

    static func style(forExtension ext: String, name: String = "") -> Style {
        let lower = name.lowercased()
        if lower == ".htaccess" || lower == ".htpasswd" || lower.hasPrefix(".env") {
            return Style(symbol: "gearshape.fill", color: Color(.systemGray))
        }
        switch ext {
        case "php", "phtml", "inc":
            return Style(symbol: "chevron.left.forwardslash.chevron.right", color: Color(red: 0.47, green: 0.48, blue: 0.85))
        case "html", "htm", "xhtml", "tpl", "twig", "vue":
            return Style(symbol: "globe", color: .orange)
        case "css", "scss", "sass", "less":
            return Style(symbol: "paintbrush.fill", color: .blue)
        case "js", "mjs", "cjs", "ts", "tsx", "jsx":
            return Style(symbol: "curlybraces", color: Color(red: 0.9, green: 0.7, blue: 0.1))
        case "json", "webmanifest", "map":
            return Style(symbol: "curlybraces.square.fill", color: .teal)
        case "xml", "svg", "plist", "rss":
            return Style(symbol: "chevron.left.slash.chevron.right", color: .mint)
        case "txt", "md", "markdown", "log", "csv", "srt":
            return Style(symbol: "doc.text.fill", color: Color(.systemGray))
        case "ini", "conf", "cfg", "yml", "yaml", "toml", "env", "properties":
            return Style(symbol: "slider.horizontal.3", color: Color(.systemGray))
        case "sql", "db", "sqlite":
            return Style(symbol: "cylinder.split.1x2.fill", color: .indigo)
        case "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "tiff", "ico":
            return Style(symbol: "photo.fill", color: .pink)
        case "mp4", "mov", "m4v", "avi", "mkv", "webm":
            return Style(symbol: "film.fill", color: .purple)
        case "mp3", "m4a", "wav", "aac", "flac", "ogg":
            return Style(symbol: "music.note", color: .red)
        case "pdf":
            return Style(symbol: "doc.richtext.fill", color: .red)
        case "zip", "rar", "7z", "gz", "tgz", "tar", "bz2", "xz":
            return Style(symbol: "doc.zipper", color: .brown)
        case "doc", "docx", "odt", "rtf", "pages":
            return Style(symbol: "doc.fill", color: .blue)
        case "xls", "xlsx", "ods", "numbers":
            return Style(symbol: "tablecells.fill", color: .green)
        case "ppt", "pptx", "key":
            return Style(symbol: "rectangle.on.rectangle.angled", color: .orange)
        case "ttf", "otf", "woff", "woff2":
            return Style(symbol: "textformat", color: Color(.systemGray))
        case "sh", "py", "rb", "pl", "swift", "c", "cpp", "h", "java", "go", "rs":
            return Style(symbol: "terminal.fill", color: .green)
        case "ipa", "apk", "dmg", "exe":
            return Style(symbol: "shippingbox.fill", color: .gray)
        default:
            return Style(symbol: "doc.fill", color: Color(.systemGray2))
        }
    }

    static func isImage(_ ext: String) -> Bool { ["jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "tiff"].contains(ext) }
    static func isArchive(_ ext: String) -> Bool { ext == "zip" }
}

/// Иконка файла в цветной плашке.
struct FileIconView: View {
    let item: FileItem
    var size: CGFloat = 24

    var body: some View {
        let style = FileIcon.style(for: item)
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(style.color.opacity(item.isDirectory ? 0.16 : 0.13))
            Image(systemName: style.symbol)
                .font(.system(size: size * 0.5, weight: .semibold))
                .foregroundStyle(style.color)
        }
        .frame(width: size + 6, height: size + 6)
        .accessibilityHidden(true)
    }
}
