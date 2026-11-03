import Foundation
import SwiftUI
import TwinCore

/// Пользовательские настройки (UserDefaults).
@MainActor
final class AppSettings: ObservableObject {
    private let d = UserDefaults.standard

    @Published var appearance: AppearanceMode { didSet { d.set(appearance.rawValue, forKey: "appearance") } }
    @Published var accent: AccentChoice { didSet { d.set(accent.rawValue, forKey: "accent") } }
    @Published var density: RowDensity { didSet { d.set(density.rawValue, forKey: "density") } }
    @Published var textSize: ListTextSize { didSet { d.set(textSize.rawValue, forKey: "textSize") } }
    @Published var showHiddenFiles: Bool { didSet { d.set(showHiddenFiles, forKey: "showHidden") } }
    @Published var showDetails: Bool { didSet { d.set(showDetails, forKey: "showDetails") } }
    @Published var confirmDelete: Bool { didSet { d.set(confirmDelete, forKey: "confirmDelete") } }
    @Published var haptics: Bool { didSet { d.set(haptics, forKey: "haptics"); Haptics.enabled = haptics } }
    @Published var defaultConflictPolicy: ConflictPolicy { didSet { d.set(defaultConflictPolicy.rawValue, forKey: "conflictPolicy") } }
    @Published var safeUploads: Bool { didSet { d.set(safeUploads, forKey: "safeUploads") } }
    @Published var parallelTransfers: Int { didSet { d.set(parallelTransfers, forKey: "parallel") } }
    @Published var keepAwakeDuringTransfers: Bool { didSet { d.set(keepAwakeDuringTransfers, forKey: "keepAwake") } }
    @Published var paneRatio: Double { didSet { d.set(paneRatio, forKey: "paneRatio") } }
    @Published var swapSides: Bool { didSet { d.set(swapSides, forKey: "swapSides") } }

    // Редактор
    @Published var editorFontSize: Double { didSet { d.set(editorFontSize, forKey: "editorFont") } }
    @Published var editorWrapLines: Bool { didSet { d.set(editorWrapLines, forKey: "editorWrap") } }
    @Published var editorLineNumbers: Bool { didSet { d.set(editorLineNumbers, forKey: "editorLines") } }
    @Published var editorHighlighting: Bool { didSet { d.set(editorHighlighting, forKey: "editorHighlight") } }
    @Published var editorTabWidth: Int { didSet { d.set(editorTabWidth, forKey: "editorTab") } }
    @Published var editorUseSpaces: Bool { didSet { d.set(editorUseSpaces, forKey: "editorSpaces") } }

    /// Избранные папки: ключ — идентификатор источника ("local:<root>" или "ftp:<uuid>").
    @Published var favorites: [String: [String]] { didSet { d.set(favorites, forKey: "favorites") } }

    init() {
        appearance = AppearanceMode(rawValue: d.string(forKey: "appearance") ?? "") ?? .system
        accent = AccentChoice(rawValue: d.string(forKey: "accent") ?? "") ?? .violet
        density = RowDensity(rawValue: d.string(forKey: "density") ?? "") ?? .standard
        textSize = ListTextSize(rawValue: d.string(forKey: "textSize") ?? "") ?? .medium
        showHiddenFiles = d.object(forKey: "showHidden") as? Bool ?? true
        showDetails = d.object(forKey: "showDetails") as? Bool ?? true
        confirmDelete = d.object(forKey: "confirmDelete") as? Bool ?? true
        haptics = d.object(forKey: "haptics") as? Bool ?? true
        defaultConflictPolicy = ConflictPolicy(rawValue: d.string(forKey: "conflictPolicy") ?? "") ?? .ask
        safeUploads = d.object(forKey: "safeUploads") as? Bool ?? true
        parallelTransfers = d.object(forKey: "parallel") as? Int ?? 2
        keepAwakeDuringTransfers = d.object(forKey: "keepAwake") as? Bool ?? true
        paneRatio = d.object(forKey: "paneRatio") as? Double ?? 0.5
        swapSides = d.object(forKey: "swapSides") as? Bool ?? false
        editorFontSize = d.object(forKey: "editorFont") as? Double ?? 13
        editorWrapLines = d.object(forKey: "editorWrap") as? Bool ?? true
        editorLineNumbers = d.object(forKey: "editorLines") as? Bool ?? true
        editorHighlighting = d.object(forKey: "editorHighlight") as? Bool ?? true
        editorTabWidth = d.object(forKey: "editorTab") as? Int ?? 4
        editorUseSpaces = d.object(forKey: "editorSpaces") as? Bool ?? true
        favorites = d.dictionary(forKey: "favorites") as? [String: [String]] ?? [:]
        Haptics.enabled = haptics
    }

    func isFavorite(_ path: String, source: String) -> Bool { favorites[source]?.contains(path) == true }

    func toggleFavorite(_ path: String, source: String) {
        var list = favorites[source] ?? []
        if let i = list.firstIndex(of: path) { list.remove(at: i) } else { list.append(path) }
        favorites[source] = list
    }
}
