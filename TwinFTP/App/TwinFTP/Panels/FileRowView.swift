import SwiftUI
import TwinCore

enum DateText {
    private static let time: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "HH:mm"
        return f
    }()
    private static let dayMonth: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "d MMM"
        return f
    }()
    private static let full: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "dd.MM.yy"
        return f
    }()
    static let long: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateStyle = .long
        f.timeStyle = .medium
        return f
    }()

    /// Компактная дата для строк списка.
    static func short(_ date: Date?) -> String {
        guard let date else { return "" }
        let cal = Calendar.current
        if cal.isDateInToday(date) { return "сегодня " + time.string(from: date) }
        if cal.isDateInYesterday(date) { return "вчера" }
        if cal.component(.year, from: date) == cal.component(.year, from: Date()) {
            return dayMonth.string(from: date).replacingOccurrences(of: ".", with: "")
        }
        return full.string(from: date)
    }
}

/// Строка файла в панели.
struct FileRowView: View {
    let item: FileItem
    let isSelected: Bool
    let selectionMode: Bool
    let density: RowDensity
    let textSize: ListTextSize
    let showDetails: Bool
    let wide: Bool

    var body: some View {
        HStack(spacing: 8) {
            if selectionMode {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 19))
                    .foregroundStyle(isSelected ? Color.accentColor : Theme.textTertiary)
                    .transition(.scale.combined(with: .opacity))
            }
            FileIconView(item: item, size: density.iconSize)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.name)
                    .font(textSize.nameFont)
                    .foregroundStyle(item.isHidden ? Theme.textSecondary : Theme.textPrimary)
                    .lineLimit(wide ? 1 : 2)
                    .truncationMode(.middle)
                if showDetails {
                    Text(details)
                        .font(textSize.detailFont.monospacedDigit())
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if wide, let perms = item.permissions {
                Text(perms)
                    .font(.system(size: 10.5, design: .monospaced))
                    .foregroundStyle(Theme.textTertiary)
            }
            if item.isDirectory && wide {
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Theme.textTertiary)
            }
        }
        .padding(.vertical, density.verticalPadding)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }

    private var details: String {
        var parts: [String] = []
        if item.isDirectory {
            parts.append(item.kind == .symlink ? "ссылка" : "папка")
        } else if let size = item.size {
            parts.append(ByteFormatter.string(size))
        } else if item.kind == .symlink {
            parts.append("ссылка")
        }
        let d = DateText.short(item.modified)
        if !d.isEmpty { parts.append(d) }
        return parts.joined(separator: " · ")
    }

    private var accessibilityText: String {
        var s = (item.isDirectory ? "Папка " : "Файл ") + item.name
        if let size = item.size, !item.isDirectory { s += ", " + ByteFormatter.string(size) }
        if isSelected { s += ", выбран" }
        return s
    }
}
