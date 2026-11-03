import SwiftUI
import UIKit

/// Цветовая система TwinFTP: графитовая тёмная тема с фиолетовыми акцентами и чистая светлая.
enum Theme {
    static func dynamic(light: UIColor, dark: UIColor) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? dark : light })
    }

    static func hex(_ v: UInt32, _ a: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
                blue: CGFloat(v & 0xFF) / 255, alpha: a)
    }

    // Поверхности
    static let background = dynamic(light: hex(0xF4F3F8), dark: hex(0x131317))
    static let surface = dynamic(light: hex(0xFFFFFF), dark: hex(0x1C1B22))
    static let surfaceRaised = dynamic(light: hex(0xFFFFFF), dark: hex(0x24232C))
    static let surfaceSunken = dynamic(light: hex(0xECEBF2), dark: hex(0x0E0E12))
    static let separator = dynamic(light: hex(0x000000, 0.08), dark: hex(0xFFFFFF, 0.08))
    static let stroke = dynamic(light: hex(0x000000, 0.06), dark: hex(0xFFFFFF, 0.07))

    // Текст
    static let textPrimary = dynamic(light: hex(0x17161C), dark: hex(0xF2F0F7))
    static let textSecondary = dynamic(light: hex(0x6B6878), dark: hex(0x9E9AAD))
    static let textTertiary = dynamic(light: hex(0x9C99A8), dark: hex(0x6C6979))

    // Семантика
    static let success = dynamic(light: hex(0x1F9D57), dark: hex(0x3DD68C))
    static let warning = dynamic(light: hex(0xD98A00), dark: hex(0xFFB547))
    static let danger = dynamic(light: hex(0xD93A3A), dark: hex(0xFF6B6B))
    static let folder = dynamic(light: hex(0x7C57E6), dark: hex(0xA788FF))

    static let corner: CGFloat = 14
    static let smallCorner: CGFloat = 9
}

/// Акцентный цвет, выбираемый в настройках.
enum AccentChoice: String, CaseIterable, Identifiable, Codable {
    case violet, indigo, blue, teal, pink, orange

    var id: String { rawValue }

    var title: String {
        switch self {
        case .violet: return "Фиолетовый"
        case .indigo: return "Индиго"
        case .blue: return "Синий"
        case .teal: return "Бирюзовый"
        case .pink: return "Розовый"
        case .orange: return "Оранжевый"
        }
    }

    var color: Color {
        switch self {
        case .violet: return Theme.dynamic(light: Theme.hex(0x7C57E6), dark: Theme.hex(0xA788FF))
        case .indigo: return Theme.dynamic(light: Theme.hex(0x4F55E3), dark: Theme.hex(0x8A8FFF))
        case .blue: return Theme.dynamic(light: Theme.hex(0x1F7AEC), dark: Theme.hex(0x5AA7FF))
        case .teal: return Theme.dynamic(light: Theme.hex(0x0E9AA0), dark: Theme.hex(0x3FD1C7))
        case .pink: return Theme.dynamic(light: Theme.hex(0xD6407E), dark: Theme.hex(0xFF7AB0))
        case .orange: return Theme.dynamic(light: Theme.hex(0xE07012), dark: Theme.hex(0xFFA552))
        }
    }

    var uiColor: UIColor {
        switch self {
        case .violet: return UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0xA788FF) : Theme.hex(0x7C57E6) }
        case .indigo: return UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0x8A8FFF) : Theme.hex(0x4F55E3) }
        case .blue: return UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0x5AA7FF) : Theme.hex(0x1F7AEC) }
        case .teal: return UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0x3FD1C7) : Theme.hex(0x0E9AA0) }
        case .pink: return UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0xFF7AB0) : Theme.hex(0xD6407E) }
        case .orange: return UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0xFFA552) : Theme.hex(0xE07012) }
        }
    }
}

enum AppearanceMode: String, CaseIterable, Identifiable, Codable {
    case system, light, dark
    var id: String { rawValue }
    var title: String {
        switch self {
        case .system: return "Как в системе"
        case .light: return "Светлая"
        case .dark: return "Тёмная"
        }
    }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

enum RowDensity: String, CaseIterable, Identifiable, Codable {
    case compact, standard, comfortable
    var id: String { rawValue }
    var title: String {
        switch self {
        case .compact: return "Компактная"
        case .standard: return "Стандартная"
        case .comfortable: return "Просторная"
        }
    }
    var verticalPadding: CGFloat {
        switch self {
        case .compact: return 4
        case .standard: return 7
        case .comfortable: return 11
        }
    }
    var iconSize: CGFloat {
        switch self {
        case .compact: return 20
        case .standard: return 24
        case .comfortable: return 28
        }
    }
}

enum ListTextSize: String, CaseIterable, Identifiable, Codable {
    case small, medium, large
    var id: String { rawValue }
    var title: String {
        switch self {
        case .small: return "Мелкий"
        case .medium: return "Средний"
        case .large: return "Крупный"
        }
    }
    var nameFont: Font {
        switch self {
        case .small: return .system(size: 12.5, weight: .medium)
        case .medium: return .system(size: 14, weight: .medium)
        case .large: return .system(size: 16, weight: .medium)
        }
    }
    var detailFont: Font {
        switch self {
        case .small: return .system(size: 10)
        case .medium: return .system(size: 11)
        case .large: return .system(size: 12.5)
        }
    }
}

/// Тактильная отдача.
enum Haptics {
    static var enabled = true

    static func tap() {
        guard enabled else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    static func selection() {
        guard enabled else { return }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    static func success() {
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    static func warning() {
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }

    static func error() {
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }
}

/// Карточка с мягкой подложкой.
struct CardBackground: ViewModifier {
    var padding: CGFloat = 14
    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(RoundedRectangle(cornerRadius: Theme.corner, style: .continuous).fill(Theme.surface))
            .overlay(RoundedRectangle(cornerRadius: Theme.corner, style: .continuous).stroke(Theme.stroke, lineWidth: 1))
    }
}

extension View {
    func card(padding: CGFloat = 14) -> some View { modifier(CardBackground(padding: padding)) }
}

/// Кнопка с акцентной заливкой.
struct AccentButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    var tint: Color = .accentColor
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 13, style: .continuous).fill(tint.opacity(isEnabled ? 1 : 0.4)))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Небольшая «таблетка» для кнопок панели инструментов.
struct ToolbarIconButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    var prominent = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .medium))
            .foregroundStyle(prominent ? Color.white : (isEnabled ? Color.accentColor : Theme.textTertiary))
            .frame(minWidth: 40, minHeight: 38)
            .padding(.horizontal, prominent ? 8 : 0)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(prominent ? Color.accentColor.opacity(isEnabled ? 1 : 0.35)
                          : (configuration.isPressed ? Color.accentColor.opacity(0.14) : Color.clear))
            )
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
