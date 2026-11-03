import SwiftUI
import TwinCore

/// Настройки и внешний вид.
struct SettingsView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var roots: LocalRootStore
    @Environment(\.dismiss) private var dismiss
    @State private var showFolderPicker = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Оформление") {
                    Picker("Тема", selection: $settings.appearance) {
                        ForEach(AppearanceMode.allCases) { Text($0.title).tag($0) }
                    }
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Акцентный цвет")
                        HStack(spacing: 12) {
                            ForEach(AccentChoice.allCases) { choice in
                                Button {
                                    settings.accent = choice
                                    Haptics.selection()
                                } label: {
                                    Circle()
                                        .fill(choice.color)
                                        .frame(width: 30, height: 30)
                                        .overlay(Circle().stroke(Theme.textPrimary, lineWidth: settings.accent == choice ? 2.5 : 0).padding(-4))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(choice.title)
                                .accessibilityAddTraits(settings.accent == choice ? .isSelected : [])
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    Picker("Плотность строк", selection: $settings.density) {
                        ForEach(RowDensity.allCases) { Text($0.title).tag($0) }
                    }
                    Picker("Размер текста", selection: $settings.textSize) {
                        ForEach(ListTextSize.allCases) { Text($0.title).tag($0) }
                    }
                    Toggle("Размер и дата в списке", isOn: $settings.showDetails)
                    Toggle("Показывать скрытые файлы", isOn: $settings.showHiddenFiles)
                    Toggle("Тактильная отдача", isOn: $settings.haptics)
                }

                Section {
                    Toggle("Подтверждать удаление", isOn: $settings.confirmDelete)
                    Picker("При совпадении имён", selection: $settings.defaultConflictPolicy) {
                        ForEach(ConflictPolicy.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Toggle("Загрузка через временный файл", isOn: $settings.safeUploads)
                    Stepper("Параллельных передач: \(settings.parallelTransfers)", value: $settings.parallelTransfers, in: 1...4)
                    Toggle("Не гасить экран во время передачи", isOn: $settings.keepAwakeDuringTransfers)
                } header: { Text("Передача файлов") } footer: {
                    Text("Временный файл защищает от порчи: файл загружается под именем «.имя.twinftp-part» и переименовывается только после успешной проверки размера. Права заменяемого файла сохраняются.")
                }

                Section("Редактор") {
                    Stepper("Размер шрифта: \(Int(settings.editorFontSize))", value: $settings.editorFontSize, in: 9...24)
                    Toggle("Перенос строк", isOn: $settings.editorWrapLines)
                    Toggle("Номера строк", isOn: $settings.editorLineNumbers)
                    Toggle("Подсветка синтаксиса", isOn: $settings.editorHighlighting)
                    Picker("Ширина табуляции", selection: $settings.editorTabWidth) {
                        Text("2").tag(2); Text("4").tag(4); Text("8").tag(8)
                    }
                    Toggle("Табуляция пробелами", isOn: $settings.editorUseSpaces)
                }

                Section {
                    ForEach(roots.roots) { root in
                        HStack {
                            Label(root.name, systemImage: root.symbol)
                            Spacer()
                            if root.kind == .bookmark {
                                Button(role: .destructive) { roots.remove(root.id) } label: {
                                    Image(systemName: "minus.circle.fill").foregroundStyle(Theme.danger)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Убрать папку \(root.name)")
                            }
                        }
                    }
                    Button { showFolderPicker = true } label: { Label("Добавить папку из «Файлов»…", systemImage: "folder.badge.plus") }
                } header: { Text("Локальные папки") } footer: {
                    Text("«На iPhone» — папка TwinFTP, видна в приложении «Файлы». Можно добавить папки из iCloud Drive или «На iPhone» — доступ сохраняется через системное разрешение.")
                }

                Section("Диагностика") {
                    NavigationLink { LogView(log: app.log) } label: { Label("Журнал FTP", systemImage: "list.bullet.rectangle") }
                    Button(role: .destructive) {
                        clearCaches()
                    } label: { Label("Очистить временные файлы", systemImage: "trash") }
                }

                Section("О приложении") {
                    HStack {
                        Text("Версия")
                        Spacer()
                        Text((Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0") + " (" + (Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1") + ")")
                            .foregroundStyle(Theme.textSecondary)
                    }
                    Text("TwinFTP — двухпанельный файловый менеджер для FTP. Без рекламы, аналитики и аккаунтов. Пароли хранятся только в связке ключей этого устройства.")
                        .font(.system(size: 12.5)).foregroundStyle(Theme.textSecondary)
                    Text("Обычный FTP не шифрует трафик. FTPS/SFTP в этой версии не поддерживаются.")
                        .font(.system(size: 12.5)).foregroundStyle(Theme.warning)
                }
            }
            .navigationTitle("Настройки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Готово") { dismiss() } } }
            .onChange(of: settings.parallelTransfers) { _, _ in app.transfers.applySettings() }
            .onChange(of: settings.keepAwakeDuringTransfers) { _, _ in app.transfers.applySettings() }
            .sheet(isPresented: $showFolderPicker) {
                FolderPicker { url in
                    do { try roots.addFolder(url) } catch { app.showError(error) }
                }
                .ignoresSafeArea()
            }
        }
    }

    private func clearCaches() {
        let fm = FileManager.default
        let caches = fm.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        var freed: Int64 = 0
        for name in ["Preview", "Archives"] {
            let dir = caches.appendingPathComponent(name)
            if let all = try? LocalFileSystem().walk(dir.path) {
                freed += all.reduce(0) { $0 + ($1.size ?? 0) }
            }
            try? fm.removeItem(at: dir)
        }
        if let tmp = try? fm.contentsOfDirectory(at: fm.temporaryDirectory, includingPropertiesForKeys: nil) {
            for u in tmp where u.lastPathComponent.hasPrefix("twinftp-") || u.lastPathComponent.hasPrefix("zip-preview-") {
                try? fm.removeItem(at: u)
            }
        }
        app.show("Временные файлы удалены (\(ByteFormatter.string(freed)))", style: .success)
    }
}

/// Журнал FTP-команд (без паролей).
struct LogView: View {
    let log: InMemoryFTPLog
    @State private var entries: [FTPLogEntry] = []
    @State private var filter = ""
    @State private var onlyErrors = false
    @State private var showShare = false

    private var filtered: [FTPLogEntry] {
        entries.filter { e in
            (!onlyErrors || e.kind == .error || (e.kind == .reply && (e.text.hasPrefix("4") || e.text.hasPrefix("5"))))
                && (filter.isEmpty || e.text.localizedCaseInsensitiveContains(filter) || e.connection.localizedCaseInsensitiveContains(filter))
        }
    }

    var body: some View {
        List {
            Toggle("Только ошибки", isOn: $onlyErrors)
            ForEach(filtered.suffix(1500).reversed()) { e in
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(e.connection).font(.system(size: 10, weight: .semibold)).foregroundStyle(Theme.textTertiary)
                        Spacer()
                        Text(e.date, style: .time).font(.system(size: 10)).foregroundStyle(Theme.textTertiary)
                    }
                    Text((e.kind == .command ? "→ " : e.kind == .reply ? "← " : "• ") + e.text)
                        .font(.system(size: 11.5, design: .monospaced))
                        .foregroundStyle(color(e))
                        .textSelection(.enabled)
                }
            }
        }
        .searchable(text: $filter, prompt: "Фильтр")
        .navigationTitle("Журнал FTP")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { showShare = true } label: { Label("Экспорт журнала", systemImage: "square.and.arrow.up") }
                    Button(role: .destructive) { log.clear(); entries = [] } label: { Label("Очистить", systemImage: "trash") }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .onAppear { entries = log.all }
        .refreshable { entries = log.all }
        .sheet(isPresented: $showShare) {
            ShareSheet(items: [exportText()])
        }
    }

    private func color(_ e: FTPLogEntry) -> Color {
        switch e.kind {
        case .command: return .accentColor
        case .error: return Theme.danger
        case .info: return Theme.textSecondary
        case .reply: return e.text.hasPrefix("4") || e.text.hasPrefix("5") ? Theme.danger : Theme.textPrimary
        }
    }

    private func exportText() -> String {
        let f = ISO8601DateFormatter()
        return entries.map { "\(f.string(from: $0.date)) [\($0.connection)] \($0.kind.rawValue): \($0.text)" }.joined(separator: "\n")
    }
}
