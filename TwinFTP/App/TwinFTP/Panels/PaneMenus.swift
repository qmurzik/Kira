import SwiftUI
import TwinCore

/// Диалоги, вызываемые из панелей.
enum PaneDialog: Identifiable {
    case newFolder(String)
    case newFile(String)
    case rename(String, FileItem)
    case delete(String, [FileItem])
    case jump(String, String)
    case info(String, FileItem)
    case chmod(String, FileItem)
    case zip(String, [FileItem])

    var id: String {
        switch self {
        case .newFolder(let p): return "nf-\(p)"
        case .newFile(let p): return "nfile-\(p)"
        case .rename(let p, let i): return "rn-\(p)-\(i.path)"
        case .delete(let p, let items): return "del-\(p)-\(items.count)-\(items.first?.path ?? "")"
        case .jump(let p, _): return "jump-\(p)"
        case .info(let p, let i): return "info-\(p)-\(i.path)"
        case .chmod(let p, let i): return "chmod-\(p)-\(i.path)"
        case .zip(let p, let items): return "zip-\(p)-\(items.count)"
        }
    }

    var paneID: String {
        switch self {
        case .newFolder(let p), .newFile(let p), .rename(let p, _), .delete(let p, _), .jump(let p, _),
             .info(let p, _), .chmod(let p, _), .zip(let p, _):
            return p
        }
    }
}

/// Переключатель источника панели (iPhone / папки / серверы).
struct SourceMenu: View {
    @ObservedObject var pane: PaneModel
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var servers: ServerStore
    @EnvironmentObject var roots: LocalRootStore
    @State private var showFolderPicker = false

    var body: some View {
        Menu {
            Section("iPhone") {
                ForEach(roots.roots) { root in
                    Button {
                        pane.setSource(.local(rootID: root.id))
                        app.activate(pane)
                    } label: {
                        Label(root.name, systemImage: root.symbol)
                    }
                    .disabled(root.isStale)
                }
                Button { showFolderPicker = true } label: {
                    Label("Добавить папку из «Файлов»…", systemImage: "folder.badge.plus")
                }
            }
            Section("FTP-серверы") {
                ForEach(servers.servers) { s in
                    Button {
                        app.open(server: s, in: pane.id)
                    } label: {
                        Label(s.displayName, systemImage: "server.rack")
                    }
                }
                Button {
                    app.serverTargetPaneID = pane.id
                    app.showServers = true
                } label: {
                    Label("Управление серверами…", systemImage: "list.bullet.rectangle")
                }
            }
            if pane.source.isRemote {
                Button(role: .destructive) { app.disconnect(pane) } label: {
                    Label("Отключиться", systemImage: "bolt.horizontal.circle")
                }
            }
        } label: {
            HStack(spacing: 6) {
                ZStack(alignment: .bottomTrailing) {
                    Image(systemName: pane.sourceSymbol)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.accentColor)
                        .frame(width: 26, height: 26)
                        .background(RoundedRectangle(cornerRadius: 7, style: .continuous).fill(Color.accentColor.opacity(0.14)))
                    if pane.source.isRemote {
                        ConnectionDot(session: pane.session)
                            .offset(x: 3, y: 3)
                    }
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(pane.sourceTitle)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    Text(subtitle)
                        .font(.system(size: 10.5))
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Theme.textTertiary)
            }
            .contentShape(Rectangle())
        }
        .accessibilityLabel("Источник: \(pane.sourceTitle). Сменить")
        .sheet(isPresented: $showFolderPicker) {
            FolderPicker { url in
                do {
                    let root = try roots.addFolder(url)
                    pane.setSource(.local(rootID: root.id))
                    app.show("Папка «\(root.name)» добавлена", style: .success)
                } catch {
                    app.showError(error)
                }
            }
            .ignoresSafeArea()
        }
    }

    private var subtitle: String {
        switch pane.source {
        case .local:
            return "\(pane.visibleItems.count) \(RussianPlural.objects(pane.visibleItems.count))"
        case .remote:
            return pane.session?.status.title ?? "FTP"
        case .serverPicker:
            return "Выберите сервер"
        }
    }
}

/// Индикатор состояния соединения.
struct ConnectionDot: View {
    var session: RemoteSession?

    var body: some View {
        if let session {
            ConnectionDotInner(session: session)
        } else {
            Circle().fill(Color.gray).frame(width: 8, height: 8)
        }
    }
}

private struct ConnectionDotInner: View {
    @ObservedObject var session: RemoteSession
    @State private var pulse = false

    var color: Color {
        switch session.status {
        case .connected: return Theme.success
        case .busy, .connecting: return Theme.warning
        case .failed: return Theme.danger
        case .disconnected: return .gray
        }
    }

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 8, height: 8)
            .overlay(Circle().stroke(Theme.surface, lineWidth: 1.5))
            .scaleEffect(pulse && (session.status == .busy || session.status == .connecting) ? 1.25 : 1)
            .animation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true), value: pulse)
            .onAppear { pulse = true }
            .accessibilityLabel(session.status.title)
    }
}

/// Меню действий панели.
struct PaneMenu: View {
    @ObservedObject var pane: PaneModel
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @Binding var dialog: PaneDialog?
    @State private var showImporter = false

    var body: some View {
        Menu {
            Section {
                Button {
                    pane.selectionMode.toggle()
                    if !pane.selectionMode { pane.clearSelection() }
                    app.activate(pane)
                } label: {
                    Label(pane.selectionMode ? "Завершить выбор" : "Выбрать", systemImage: "checkmark.circle")
                }
                if pane.selectionMode {
                    Button { pane.selectAll() } label: { Label("Выбрать все", systemImage: "checklist") }
                }
                Button { dialog = .newFolder(pane.id) } label: { Label("Новая папка", systemImage: "folder.badge.plus") }
                Button { dialog = .newFile(pane.id) } label: { Label("Новый файл", systemImage: "doc.badge.plus") }
                if pane.source.isLocal {
                    Button { showImporter = true } label: { Label("Импорт из «Файлов»…", systemImage: "square.and.arrow.down") }
                }
                Button { pane.refresh() } label: { Label("Обновить", systemImage: "arrow.clockwise") }
            }
            Section {
                Menu {
                    Picker("Сортировка", selection: Binding(get: { pane.sort.field }, set: { pane.sort.field = $0; pane.persistSort() })) {
                        ForEach(FileSortField.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Toggle("По возрастанию", isOn: Binding(get: { pane.sort.ascending }, set: { pane.sort.ascending = $0; pane.persistSort() }))
                    Toggle("Папки сверху", isOn: Binding(get: { pane.sort.foldersFirst }, set: { pane.sort.foldersFirst = $0; pane.persistSort() }))
                } label: { Label("Сортировка: \(pane.sort.field.title)", systemImage: "arrow.up.arrow.down") }
                Toggle(isOn: $settings.showHiddenFiles) { Label("Скрытые файлы", systemImage: "eye") }
            }
            Section("Избранное") {
                let favs = settings.favorites[pane.sourceKey] ?? []
                Button {
                    settings.toggleFavorite(pane.path, source: pane.sourceKey)
                    Haptics.success()
                } label: {
                    if settings.isFavorite(pane.path, source: pane.sourceKey) {
                        Label("Убрать из избранного", systemImage: "star.slash")
                    } else {
                        Label("Добавить папку в избранное", systemImage: "star")
                    }
                }
                ForEach(favs, id: \.self) { path in
                    Button { pane.navigate(to: path) } label: {
                        Label(PathUtils.lastComponent(path), systemImage: "star.fill")
                    }
                }
            }
            Section {
                Button { dialog = .jump(pane.id, pane.displayPath) } label: { Label("Перейти по пути…", systemImage: "arrow.right.to.line") }
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                        app.maximizedPaneID = app.maximizedPaneID == pane.id ? nil : pane.id
                    }
                } label: {
                    Label(app.maximizedPaneID == pane.id ? "Вернуть две панели" : "Развернуть панель",
                          systemImage: app.maximizedPaneID == pane.id ? "rectangle.split.2x1" : "arrow.up.left.and.arrow.down.right")
                }
                if pane.source.isRemote {
                    Button(role: .destructive) { app.disconnect(pane) } label: { Label("Отключиться", systemImage: "bolt.horizontal.circle") }
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.system(size: 16, weight: .semibold))
                .frame(width: 30, height: 30)
        }
        .accessibilityLabel("Меню панели")
        .simultaneousGesture(TapGesture().onEnded { app.activate(pane) })
        .sheet(isPresented: $showImporter) {
            DocumentImporter { urls in app.importFiles(urls, into: pane) }
                .ignoresSafeArea()
        }
    }
}

/// Контекстное меню файла.
struct FileContextMenu: View {
    let item: FileItem
    @ObservedObject var pane: PaneModel
    @Binding var dialog: PaneDialog?
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings

    var body: some View {
        let targets = pane.targets(for: item)
        let other = app.otherPane(of: pane)
        let multi = targets.count > 1
        let arrow = pane.id == "left" ? "arrow.right" : "arrow.left"

        Section(multi ? "Выбрано: \(targets.count)" : item.name) {
            if !multi {
                Button { app.open(item, in: pane) } label: {
                    Label(item.isDirectory ? "Открыть" : "Открыть / просмотр", systemImage: item.isDirectory ? "folder" : "eye")
                }
                if !item.isDirectory && SyntaxLanguage.isEditable(fileName: item.name) == false && item.fileExtension != "zip" {
                    Button { app.openInEditor(item, from: pane) } label: { Label("Открыть как текст", systemImage: "chevron.left.forwardslash.chevron.right") }
                }
            }
            if other.source != .serverPicker {
                Button { app.transfer(from: pane, items: targets) } label: {
                    Label(copyTitle(other: other), systemImage: arrow + ".circle")
                }
                Button { app.transfer(from: pane, items: targets, move: true) } label: {
                    Label("Переместить в соседнюю панель", systemImage: "arrow.right.doc.on.clipboard")
                }
            }
        }
        Section {
            if !multi {
                Button { dialog = .rename(pane.id, item) } label: { Label("Переименовать", systemImage: "pencil") }
            }
            if pane.source.isLocal {
                Button { dialog = .zip(pane.id, targets) } label: { Label("Сжать в ZIP", systemImage: "doc.zipper") }
                if !multi && !item.isDirectory {
                    ShareLink(item: URL(fileURLWithPath: item.path)) { Label("Поделиться", systemImage: "square.and.arrow.up") }
                }
            }
            if !multi && item.fileExtension == "zip" {
                Button { app.archiveToOpen = ArchiveRequest(item: item, paneID: pane.id) } label: {
                    Label("Открыть архив", systemImage: "archivebox")
                }
                if pane.source.isRemote && other.source.isLocal {
                    Button { app.downloadAndExtract(item, from: pane) } label: {
                        Label("Скачать и распаковать на iPhone", systemImage: "arrow.down.doc")
                    }
                }
                if pane.source.isLocal && other.source.isRemote {
                    Button { app.archiveToOpen = ArchiveRequest(item: item, paneID: pane.id, uploadAfterExtract: true) } label: {
                        Label("Распаковать и загрузить на FTP", systemImage: "arrow.up.doc")
                    }
                }
            }
            if pane.source.isRemote && !multi {
                Button { dialog = .chmod(pane.id, item) } label: { Label("Права доступа…", systemImage: "lock.shield") }
            }
            if !multi {
                Button { dialog = .info(pane.id, item) } label: { Label("Свойства", systemImage: "info.circle") }
            }
            if item.isDirectory && !multi {
                Button { settings.toggleFavorite(item.path, source: pane.sourceKey) } label: {
                    Label(settings.isFavorite(item.path, source: pane.sourceKey) ? "Убрать из избранного" : "В избранное", systemImage: "star")
                }
            }
            Button {
                pane.selectionMode = true
                pane.selection.insert(item.path)
            } label: { Label("Выбрать", systemImage: "checkmark.circle") }
        }
        Section {
            Button(role: .destructive) { dialog = .delete(pane.id, targets) } label: {
                Label(multi ? "Удалить (\(targets.count))" : "Удалить", systemImage: "trash")
            }
        }
    }

    private func copyTitle(other: PaneModel) -> String {
        switch (pane.source, other.source) {
        case (.local, .remote): return "Загрузить на сервер"
        case (.remote, .local): return "Скачать на iPhone"
        default: return "Копировать в соседнюю панель"
        }
    }
}
