import SwiftUI
import TwinCore

/// Экран редактора с вкладками.
struct EditorView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @ObservedObject var manager: EditorManager
    @Environment(\.dismiss) private var dismiss
    @State private var command: EditorCommand?
    @State private var showGoToLine = false
    @State private var goToLineText = ""
    @State private var closeCandidate: EditorDocument?
    @State private var showCloseAllConfirm = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                tabStrip
                Divider().overlay(Theme.separator)
                if let doc = manager.active {
                    EditorDocumentView(document: doc, command: $command)
                        .id(doc.id)
                } else {
                    ContentUnavailableView("Нет открытых файлов", systemImage: "doc.text",
                                           description: Text("Откройте PHP, HTML, CSS, JS или другой текстовый файл из панели."))
                }
            }
            .background(Theme.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
            .alert("Перейти к строке", isPresented: $showGoToLine) {
                TextField("Номер строки", text: $goToLineText).keyboardType(.numberPad)
                Button("Перейти") {
                    if let n = Int(goToLineText) { command = .goToLine(n) }
                    goToLineText = ""
                }
                Button("Отмена", role: .cancel) { goToLineText = "" }
            } message: {
                if let doc = manager.active { Text("Всего строк: \(doc.lineCount)") }
            }
            .confirmationDialog("Сохранить изменения в «\(closeCandidate?.name ?? "")»?", isPresented: Binding(
                get: { closeCandidate != nil }, set: { if !$0 { closeCandidate = nil } }), titleVisibility: .visible) {
                if let doc = closeCandidate {
                    Button("Сохранить и закрыть") {
                        Task {
                            await manager.save(doc)
                            if !doc.isDirty { manager.close(doc) }
                        }
                    }
                    Button("Закрыть без сохранения", role: .destructive) { manager.close(doc) }
                    Button("Отмена", role: .cancel) {}
                }
            } message: {
                Text("Несохранённые изменения будут потеряны.")
            }
            .alert("Файл изменён на сервере", isPresented: Binding(
                get: { manager.remoteConflict != nil }, set: { if !$0 { manager.remoteConflict = nil } })) {
                if let doc = manager.remoteConflict {
                    Button("Перезаписать", role: .destructive) {
                        manager.remoteConflict = nil
                        Task { await manager.save(doc, force: true) }
                    }
                    Button("Сохранить копию рядом") {
                        manager.remoteConflict = nil
                        Task { await manager.saveAsCopy(doc) }
                    }
                    Button("Отмена", role: .cancel) { manager.remoteConflict = nil }
                }
            } message: {
                Text("С момента открытия кто-то изменил этот файл на сервере. Перезапись уничтожит те изменения.")
            }
        }
        .interactiveDismissDisabled(manager.hasUnsaved)
    }

    private var tabStrip: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(manager.documents) { doc in
                        EditorTab(document: doc, isActive: doc.id == manager.active?.id) {
                            manager.activeID = doc.id
                            Haptics.selection()
                        } onClose: {
                            if doc.isDirty { closeCandidate = doc } else { manager.close(doc) }
                        }
                        .id(doc.id)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
            }
            .onChange(of: manager.activeID) { _, id in
                if let id { withAnimation { proxy.scrollTo(id, anchor: .center) } }
            }
        }
        .background(Theme.surface)
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button {
                command = .dismissKeyboard
                dismiss()
            } label: {
                Image(systemName: "chevron.down").font(.system(size: 15, weight: .semibold))
            }
            .accessibilityLabel("Свернуть редактор")
            .accessibilityIdentifier("editor-close")
        }
        ToolbarItem(placement: .principal) {
            if let doc = manager.active {
                VStack(spacing: 0) {
                    Text(doc.name).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                    Text(doc.isRemote ? "FTP · \(doc.language.title)" : "iPhone · \(doc.language.title)")
                        .font(.system(size: 11)).foregroundStyle(Theme.textSecondary)
                }
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            if let doc = manager.active {
                SaveButton(document: doc) { Task { await manager.save(doc) } }
                Menu {
                    Button { command = .find(replace: false) } label: { Label("Найти", systemImage: "magnifyingglass") }
                    Button { command = .find(replace: true) } label: { Label("Найти и заменить", systemImage: "arrow.left.arrow.right") }
                    Button { showGoToLine = true } label: { Label("Перейти к строке…", systemImage: "number") }
                    Divider()
                    Button { command = .undo } label: { Label("Отменить", systemImage: "arrow.uturn.backward") }
                    Button { command = .redo } label: { Label("Повторить", systemImage: "arrow.uturn.forward") }
                    Divider()
                    Menu {
                        Picker("Подсветка", selection: Binding(get: { doc.language }, set: { doc.language = $0 })) {
                            ForEach(SyntaxLanguage.allCases, id: \.self) { Text($0.title).tag($0) }
                        }
                    } label: { Label("Синтаксис: \(doc.language.title)", systemImage: "paintpalette") }
                    Menu {
                        Picker("Кодировка", selection: Binding(get: { doc.encoding }, set: {
                            doc.encoding = $0
                            doc.textDidChange(doc.text + "")
                            doc.isDirty = true
                        })) {
                            ForEach(TextEncodingKind.allCases, id: \.self) { Text($0.title).tag($0) }
                        }
                    } label: { Label("Кодировка: \(doc.encoding.title)", systemImage: "textformat") }
                    Menu {
                        Picker("Переводы строк", selection: Binding(get: { doc.lineEnding }, set: {
                            doc.lineEnding = $0
                            doc.isDirty = true
                        })) {
                            Text(LineEnding.lf.title).tag(LineEnding.lf)
                            Text(LineEnding.crlf.title).tag(LineEnding.crlf)
                            Text(LineEnding.cr.title).tag(LineEnding.cr)
                        }
                    } label: { Label("Строки: \(doc.lineEnding.title)", systemImage: "return") }
                    Divider()
                    Toggle(isOn: $settings.editorWrapLines) { Label("Перенос строк", systemImage: "text.word.spacing") }
                    Toggle(isOn: $settings.editorLineNumbers) { Label("Номера строк", systemImage: "list.number") }
                    Toggle(isOn: $settings.editorHighlighting) { Label("Подсветка синтаксиса", systemImage: "highlighter") }
                    Button { settings.editorFontSize = min(24, settings.editorFontSize + 1) } label: { Label("Крупнее", systemImage: "plus.magnifyingglass") }
                    Button { settings.editorFontSize = max(9, settings.editorFontSize - 1) } label: { Label("Мельче", systemImage: "minus.magnifyingglass") }
                    Divider()
                    Button { manager.reload(doc) } label: { Label(doc.isRemote ? "Перезагрузить с сервера" : "Перечитать с диска", systemImage: "arrow.clockwise") }
                    Button(role: .destructive) {
                        if doc.isDirty { closeCandidate = doc } else { manager.close(doc) }
                    } label: { Label("Закрыть вкладку", systemImage: "xmark") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel("Действия редактора")
            }
        }
    }
}

private struct SaveButton: View {
    @ObservedObject var document: EditorDocument
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            switch document.saveState {
            case .saving:
                ProgressView().controlSize(.small)
            case .saved where !document.isDirty:
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.success)
            default:
                Text("Сохранить").font(.system(size: 15, weight: .semibold))
            }
        }
        .disabled(!document.isDirty || document.readOnly || document.isLoading || { if case .saving = document.saveState { return true }; return false }())
        .accessibilityLabel("Сохранить файл")
    }
}

private struct EditorTab: View {
    @ObservedObject var document: EditorDocument
    let isActive: Bool
    let onSelect: () -> Void
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: FileIcon.style(forExtension: PathUtils.splitExtension(document.name).ext.dropFirst().lowercased(), name: document.name).symbol)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(isActive ? Color.accentColor : Theme.textSecondary)
            Text(document.name)
                .font(.system(size: 13, weight: isActive ? .semibold : .regular))
                .lineLimit(1)
            if document.isRemote {
                Image(systemName: "server.rack").font(.system(size: 9)).foregroundStyle(Theme.textTertiary)
            }
            Button(action: onClose) {
                Image(systemName: document.isDirty ? "circle.fill" : "xmark")
                    .font(.system(size: document.isDirty ? 7 : 9, weight: .bold))
                    .foregroundStyle(document.isDirty ? Color.accentColor : Theme.textTertiary)
                    .frame(width: 18, height: 18)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(document.isDirty ? "Есть несохранённые изменения. Закрыть" : "Закрыть вкладку")
        }
        .padding(.leading, 10)
        .padding(.trailing, 4)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .fill(isActive ? Color.accentColor.opacity(0.14) : Theme.surfaceSunken)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .stroke(isActive ? Color.accentColor.opacity(0.5) : Color.clear, lineWidth: 1)
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
        .accessibilityAddTraits(isActive ? .isSelected : [])
    }
}

/// Содержимое одной вкладки: состояние загрузки, ошибки, редактор и строка состояния.
struct EditorDocumentView: View {
    @ObservedObject var document: EditorDocument
    @EnvironmentObject var settings: AppSettings
    @Binding var command: EditorCommand?

    var body: some View {
        VStack(spacing: 0) {
            if document.isLoading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text(document.isRemote ? "Загрузка с сервера…" : "Открытие…").foregroundStyle(Theme.textSecondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error = document.loadError {
                ContentUnavailableView {
                    Label("Не удалось открыть", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(error)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                if document.hadMixedLineEndings {
                    banner("В файле смешаны переводы строк. При сохранении они будут приведены к \(document.lineEnding.title).", symbol: "exclamationmark.triangle.fill", color: Theme.warning)
                }
                if case .failed(let message) = document.saveState {
                    banner(message, symbol: "xmark.octagon.fill", color: Theme.danger)
                }
                CodeTextView(document: document,
                             fontSize: CGFloat(settings.editorFontSize),
                             wrapLines: settings.editorWrapLines,
                             showLineNumbers: settings.editorLineNumbers,
                             highlighting: settings.editorHighlighting,
                             tabWidth: settings.editorTabWidth,
                             useSpaces: settings.editorUseSpaces,
                             tint: settings.accent.uiColor,
                             command: $command)
                statusBar
            }
        }
    }

    private func banner(_ text: String, symbol: String, color: Color) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: symbol).foregroundStyle(color)
            Text(text).font(.system(size: 12.5)).foregroundStyle(Theme.textPrimary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(color.opacity(0.12))
    }

    private var statusBar: some View {
        HStack(spacing: 10) {
            Text("Стр \(document.cursorLine), Кол \(document.cursorColumn)")
            Text("·")
            Text(document.encoding.title)
            Text(document.lineEnding == .crlf ? "CRLF" : (document.lineEnding == .cr ? "CR" : "LF"))
            Spacer()
            if case .saving(let msg) = document.saveState {
                ProgressView().controlSize(.mini)
                Text(msg)
            } else if document.isDirty {
                Label("Изменён", systemImage: "pencil").labelStyle(.titleAndIcon)
                    .foregroundStyle(Color.accentColor)
            } else if case .saved = document.saveState {
                Label(document.isRemote ? "Сохранено на сервере" : "Сохранено", systemImage: "checkmark")
                    .foregroundStyle(Theme.success)
            }
        }
        .font(.system(size: 11, weight: .medium).monospacedDigit())
        .foregroundStyle(Theme.textSecondary)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Theme.surface)
        .overlay(alignment: .top) { Divider().overlay(Theme.separator) }
    }
}
