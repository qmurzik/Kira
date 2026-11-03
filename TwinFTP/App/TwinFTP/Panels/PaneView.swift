import SwiftUI
import UniformTypeIdentifiers
import TwinCore

/// Одна панель файлового менеджера.
struct PaneView: View {
    @ObservedObject var pane: PaneModel
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var servers: ServerStore
    @EnvironmentObject var roots: LocalRootStore
    let isActive: Bool
    let width: CGFloat
    @Binding var dialog: PaneDialog?

    @State private var dropTargeted = false
    @FocusState private var searchFocused: Bool

    private var wide: Bool { width > 330 }

    var body: some View {
        VStack(spacing: 0) {
            header
            navigationRow
            if pane.isSearching { searchField }
            Divider().overlay(Theme.separator)
            content
        }
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.corner, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.corner, style: .continuous)
                .stroke(isActive ? Color.accentColor.opacity(0.75) : Theme.stroke, lineWidth: isActive ? 1.5 : 1)
        )
        .overlay(alignment: .top) {
            if isActive {
                Capsule().fill(Color.accentColor).frame(width: 34, height: 3).padding(.top, 2)
                    .transition(.opacity)
            }
        }
        .overlay {
            if dropTargeted {
                RoundedRectangle(cornerRadius: Theme.corner, style: .continuous)
                    .fill(Color.accentColor.opacity(0.12))
                    .overlay(
                        Label("Отпустите для копирования сюда", systemImage: "arrow.down.doc.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.accentColor)
                            .padding(10)
                            .background(Capsule().fill(Theme.surfaceRaised))
                    )
                    .allowsHitTesting(false)
            }
        }
        .shadow(color: isActive ? Color.accentColor.opacity(0.18) : .clear, radius: 10, y: 2)
        .contentShape(Rectangle())
        .simultaneousGesture(TapGesture().onEnded { app.activate(pane) })
        .onDrop(of: [UTType.plainText], isTargeted: $dropTargeted, perform: handleDrop)
        .animation(.easeInOut(duration: 0.18), value: isActive)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(pane.source.isRemote ? "Панель сервера \(pane.sourceTitle)" : "Панель iPhone \(pane.sourceTitle)")
    }

    // MARK: - Заголовок

    private var header: some View {
        HStack(spacing: 6) {
            SourceMenu(pane: pane)
            Spacer(minLength: 2)
            if pane.source != .serverPicker {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        pane.isSearching.toggle()
                        if !pane.isSearching { pane.searchText = "" } else { searchFocused = true }
                    }
                    app.activate(pane)
                } label: {
                    Image(systemName: pane.isSearching ? "xmark.circle.fill" : "magnifyingglass")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(width: 30, height: 30)
                }
                .accessibilityLabel(pane.isSearching ? "Закрыть поиск" : "Поиск в папке")
                PaneMenu(pane: pane, dialog: $dialog)
            }
        }
        .padding(.leading, 8)
        .padding(.trailing, 4)
        .padding(.top, 6)
        .padding(.bottom, 2)
    }

    private var navigationRow: some View {
        HStack(spacing: 2) {
            if pane.source != .serverPicker {
                Button { app.activate(pane); pane.goBack() } label: {
                    Image(systemName: "chevron.backward").font(.system(size: 13, weight: .semibold)).frame(width: 26, height: 26)
                }
                .disabled(!pane.canGoBack)
                .accessibilityLabel("Назад")
                Button { app.activate(pane); pane.goUp() } label: {
                    Image(systemName: "arrow.turn.left.up").font(.system(size: 13, weight: .semibold)).frame(width: 26, height: 26)
                }
                .disabled(!pane.canGoUp)
                .accessibilityLabel("Вверх на уровень")
                BreadcrumbBar(pane: pane) {
                    dialog = .jump(pane.id, pane.displayPath)
                }
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 4)
        .frame(minHeight: pane.source == .serverPicker ? 0 : 30)
    }

    private var searchField: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass").foregroundStyle(Theme.textTertiary).font(.system(size: 12))
            TextField("Поиск", text: $pane.searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: 14))
                .focused($searchFocused)
                .submitLabel(.search)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Theme.surfaceSunken))
        .padding(.horizontal, 8)
        .padding(.bottom, 6)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    // MARK: - Содержимое

    @ViewBuilder
    private var content: some View {
        if pane.source == .serverPicker {
            ServerPickerInline(paneID: pane.id)
        } else if let error = pane.error, pane.items.isEmpty {
            StateView(symbol: app.network.isOnline ? "exclamationmark.triangle" : "wifi.slash",
                      title: app.network.isOnline ? "Ошибка" : "Нет сети",
                      message: error,
                      actionTitle: "Повторить") { pane.refresh() }
        } else if pane.isLoading && pane.items.isEmpty {
            VStack(spacing: 10) {
                ProgressView()
                Text(pane.source.isRemote ? "Загрузка списка…" : "Чтение папки…")
                    .font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            fileList
        }
    }

    private var fileList: some View {
        let items = pane.visibleItems
        return List {
            if let error = pane.error {
                Text(error).font(.system(size: 12)).foregroundStyle(Theme.danger)
                    .listRowBackground(Theme.danger.opacity(0.08))
            }
            if items.isEmpty {
                StateView(symbol: pane.searchText.isEmpty ? "folder" : "magnifyingglass",
                          title: pane.searchText.isEmpty ? "Папка пуста" : "Ничего не найдено",
                          message: pane.searchText.isEmpty ? (pane.source.isLocal ? "Импортируйте файлы или скопируйте их с сервера." : "Загрузите сюда файлы с iPhone.") : "Измените запрос.",
                          actionTitle: nil, action: nil)
                    .frame(minHeight: 220)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
            }
            ForEach(items) { item in
                row(item)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 10)
        .refreshable { await pane.load(pane.path, recordHistory: false) }
        .overlay(alignment: .top) {
            if pane.isLoading && !pane.items.isEmpty {
                ProgressView().controlSize(.small).padding(6)
                    .background(Capsule().fill(Theme.surfaceRaised).shadow(radius: 2))
                    .padding(.top, 6)
            }
        }
    }

    private func row(_ item: FileItem) -> some View {
        FileRowView(item: item,
                    isSelected: pane.selection.contains(item.path),
                    selectionMode: pane.selectionMode,
                    density: settings.density,
                    textSize: settings.textSize,
                    showDetails: settings.showDetails,
                    wide: wide)
            .accessibilityIdentifier("row-\(pane.source.isRemote ? "remote" : pane.id)-\(item.name)")
            .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8))
            .listRowBackground(pane.selection.contains(item.path) ? Color.accentColor.opacity(0.13) : Color.clear)
            .listRowSeparatorTint(Theme.separator)
            .onTapGesture {
                app.activate(pane)
                if pane.selectionMode {
                    pane.toggleSelection(item)
                } else {
                    Haptics.tap()
                    app.open(item, in: pane)
                }
            }
            .contextMenu {
                FileContextMenu(item: item, pane: pane, dialog: $dialog)
            }
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button(role: .destructive) {
                    dialog = .delete(pane.id, [item])
                } label: { Label("Удалить", systemImage: "trash") }
                Button {
                    dialog = .rename(pane.id, item)
                } label: { Label("Имя", systemImage: "pencil") }
                .tint(.orange)
            }
            .swipeActions(edge: .leading, allowsFullSwipe: true) {
                Button {
                    app.transfer(from: pane, items: pane.targets(for: item))
                } label: {
                    Label("Копировать", systemImage: pane.id == "left" ? "arrow.right.doc.on.clipboard" : "arrow.left.doc.on.clipboard")
                }
                .tint(.accentColor)
            }
            .onDrag {
                app.activate(pane)
                return NSItemProvider(object: "twinftp-drag:\(pane.id):\(item.path)" as NSString)
            }
    }

    // MARK: - Drag & Drop

    private func handleDrop(_ providers: [NSItemProvider]) -> Bool {
        guard let provider = providers.first(where: { $0.canLoadObject(ofClass: NSString.self) }) else { return false }
        _ = provider.loadObject(ofClass: NSString.self) { object, _ in
            guard let s = object as? String, s.hasPrefix("twinftp-drag:") else { return }
            let parts = s.dropFirst("twinftp-drag:".count).split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { return }
            Task { @MainActor in
                let sourcePane = app.pane(parts[0])
                guard sourcePane.id != pane.id,
                      let item = sourcePane.items.first(where: { $0.path == parts[1] }) else { return }
                app.transfer(from: sourcePane, items: sourcePane.targets(for: item))
            }
        }
        return true
    }
}

/// Пустое состояние / ошибка.
struct StateView: View {
    let symbol: String
    let title: String
    let message: String
    let actionTitle: String?
    let action: (() -> Void)?

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: symbol)
                .font(.system(size: 30, weight: .light))
                .foregroundStyle(Theme.textTertiary)
            Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.textPrimary)
            Text(message).font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.system(size: 14, weight: .semibold))
                    .buttonStyle(.bordered)
                    .padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Хлебные крошки пути с прокруткой к концу.
struct BreadcrumbBar: View {
    @ObservedObject var pane: PaneModel
    let onJump: () -> Void

    var body: some View {
        let crumbs = pane.breadcrumbs
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 1) {
                    ForEach(Array(crumbs.enumerated()), id: \.offset) { idx, crumb in
                        if idx > 0 {
                            Image(systemName: "chevron.compact.right")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Theme.textTertiary)
                        }
                        Button {
                            Haptics.selection()
                            if crumb.path == pane.path { onJump() } else { pane.navigate(to: crumb.path) }
                        } label: {
                            Text(crumb.name)
                                .font(.system(size: 12, weight: idx == crumbs.count - 1 ? .semibold : .regular))
                                .foregroundStyle(idx == crumbs.count - 1 ? Theme.textPrimary : Theme.textSecondary)
                                .lineLimit(1)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 4)
                                .background(
                                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                                        .fill(idx == crumbs.count - 1 ? Theme.surfaceSunken : Color.clear)
                                )
                        }
                        .buttonStyle(.plain)
                        .id(idx)
                    }
                }
            }
            .defaultScrollAnchor(.trailing)
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) { proxy.scrollTo(crumbs.count - 1, anchor: .trailing) }
            }
            .onChange(of: pane.path) { _, _ in
                // Ждём перерисовки крошек, затем прокручиваем к текущей папке.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                    withAnimation { proxy.scrollTo(pane.breadcrumbs.count - 1, anchor: .trailing) }
                }
            }
        }
        .onLongPressGesture(minimumDuration: 0.4, perform: onJump)
        .accessibilityLabel("Путь: \(pane.displayPath). Двойное касание с удержанием — ввести путь")
    }
}
