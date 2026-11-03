import Foundation
import SwiftUI
import TwinCore

/// Всплывающее уведомление внизу экрана.
struct Toast: Identifiable, Equatable {
    enum Style { case info, success, warning, error }
    let id = UUID()
    var message: String
    var style: Style = .info
}

/// Корневая модель приложения.
@MainActor
final class AppModel: ObservableObject {
    let settings: AppSettings
    let servers: ServerStore
    let roots: LocalRootStore
    let log: InMemoryFTPLog
    let sessions: SessionManager
    let transfers: TransferManager
    let editor: EditorManager
    let network: NetworkMonitor

    let leftPane: PaneModel
    let rightPane: PaneModel

    @Published var activePaneID: String = "left"
    @Published var maximizedPaneID: String?
    @Published var toast: Toast?
    @Published var passwordPrompt: ServerProfile?
    @Published var plainTextWarning: ServerProfile?
    @Published var showServers = false
    @Published var showQueue = false
    @Published var showSettings = false
    @Published var showEditor = false
    @Published var preview: PreviewRequest?
    @Published var archiveToOpen: ArchiveRequest?
    @Published var busyMessage: String?
    /// Панель, в которую нужно открыть выбранный сервер (из списка серверов).
    var serverTargetPaneID: String?

    private var toastTask: Task<Void, Never>?

    init() {
        let settings = AppSettings()
        let servers = ServerStore()
        let log = InMemoryFTPLog(limit: 5000)
        let sessions = SessionManager(servers: servers, log: log)
        self.settings = settings
        self.servers = servers
        self.roots = LocalRootStore()
        self.log = log
        self.sessions = sessions
        self.transfers = TransferManager(sessions: sessions, settings: settings)
        self.editor = EditorManager(sessions: sessions, servers: servers)
        self.network = NetworkMonitor()
        self.leftPane = PaneModel(id: "left", source: .local(rootID: "documents"))
        self.rightPane = PaneModel(id: "right", source: .serverPicker)
        leftPane.app = self
        rightPane.app = self
        leftPane.setSource(.local(rootID: "documents"))
        transfers.onJobFinished = { [weak self] job in self?.jobFinished(job) }
        configureForUITests()
    }

    var activePane: PaneModel { activePaneID == leftPane.id ? leftPane : rightPane }
    var otherPane: PaneModel { activePaneID == leftPane.id ? rightPane : leftPane }

    func pane(_ id: String) -> PaneModel { id == leftPane.id ? leftPane : rightPane }
    func otherPane(of pane: PaneModel) -> PaneModel { pane.id == leftPane.id ? rightPane : leftPane }

    func activate(_ pane: PaneModel) {
        if activePaneID != pane.id {
            activePaneID = pane.id
            Haptics.selection()
        }
    }

    // MARK: - Уведомления

    func show(_ message: String, style: Toast.Style = .info) {
        toast = Toast(message: message, style: style)
        toastTask?.cancel()
        toastTask = Task {
            try? await Task.sleep(nanoseconds: style == .error ? 4_500_000_000 : 2_600_000_000)
            if !Task.isCancelled { withAnimation { self.toast = nil } }
        }
    }

    func showError(_ error: Error) {
        show(PaneModel.describe(error), style: .error)
        Haptics.error()
    }

    // MARK: - Подключение к серверу

    /// Открывает сервер в панели (по умолчанию — в FTP-панели, иначе в активной).
    func open(server profile: ServerProfile, in paneID: String? = nil) {
        let targetID = paneID ?? serverTargetPaneID ?? preferredRemotePaneID
        serverTargetPaneID = nil
        if !profile.plainTextWarningAcknowledged {
            pendingOpen = (profile, targetID)
            plainTextWarning = profile
            return
        }
        if servers.needsPasswordPrompt(profile) {
            pendingOpen = (profile, targetID)
            passwordPrompt = profile
            return
        }
        guard sessions.session(for: profile) != nil else {
            pendingOpen = (profile, targetID)
            passwordPrompt = profile
            return
        }
        let pane = pane(targetID)
        pane.setSource(.remote(profileID: profile.id))
        activate(pane)
        showServers = false
    }

    private var pendingOpen: (ServerProfile, String)?

    func acknowledgePlainText(dontShowAgain: Bool) {
        guard let (profile, paneID) = pendingOpen else { return }
        plainTextWarning = nil
        var p = profile
        if dontShowAgain {
            servers.acknowledgePlainText(profile.id)
            p.plainTextWarningAcknowledged = true
        } else {
            p.plainTextWarningAcknowledged = true // только для этого подключения
        }
        pendingOpen = nil
        open(server: p, in: paneID)
    }

    func cancelPendingOpen() {
        pendingOpen = nil
        plainTextWarning = nil
        passwordPrompt = nil
    }

    func providePassword(_ password: String, remember: Bool) {
        guard let (profile, paneID) = pendingOpen else { return }
        passwordPrompt = nil
        if remember {
            var p = profile
            p.savePassword = true
            servers.upsert(p, password: password)
        } else {
            servers.setSessionPassword(password, for: profile.id)
        }
        pendingOpen = nil
        var p = servers.profile(profile.id) ?? profile
        p.plainTextWarningAcknowledged = true
        open(server: p, in: paneID)
    }

    var preferredRemotePaneID: String {
        if rightPane.source == .serverPicker || rightPane.source.isRemote { return settings.swapSides ? leftPane.id : rightPane.id }
        if leftPane.source == .serverPicker || leftPane.source.isRemote { return leftPane.id }
        return otherPane.id
    }

    func disconnect(_ pane: PaneModel) {
        guard case .remote(let id) = pane.source else { return }
        let stillUsed = otherPane(of: pane).source == pane.source || transfers.jobs.contains { $0.profileID == id && $0.state.isActive }
        pane.setSource(.serverPicker)
        if !stillUsed { Task { await sessions.disconnect(id) } }
    }

    // MARK: - Передача между панелями

    /// Копирование/перемещение выделенного из панели `from` в соседнюю панель.
    func transfer(from source: PaneModel, items explicit: [FileItem]? = nil, move: Bool = false) {
        let dest = otherPane(of: source)
        let items = explicit ?? source.selectedItems
        guard !items.isEmpty else {
            show("Сначала выберите файлы или папки.", style: .warning)
            return
        }
        switch (source.source, dest.source) {
        case (.local, .remote(let pid)):
            guard let profile = servers.profile(pid) else { return }
            let job = TransferJob(direction: .upload, profileID: pid, serverName: profile.displayName, sources: items,
                                  sourceDirectory: source.path, destinationDirectory: dest.path,
                                  deleteSourcesOnSuccess: move)
            transfers.enqueue(job)
            show("\(move ? "Перемещение" : "Загрузка") на сервер: \(job.title)", style: .info)
        case (.remote(let pid), .local):
            guard let profile = servers.profile(pid) else { return }
            let job = TransferJob(direction: .download, profileID: pid, serverName: profile.displayName, sources: items,
                                  sourceDirectory: source.path, destinationDirectory: dest.path,
                                  deleteSourcesOnSuccess: move)
            transfers.enqueue(job)
            show("\(move ? "Перемещение" : "Скачивание"): \(job.title)", style: .info)
        case (.local, .local):
            copyLocally(items, to: dest, move: move, source: source)
        case (.remote(let a), .remote(let b)):
            if a == b && move {
                moveOnServer(items, in: source, to: dest.path)
            } else {
                show("Копирование между FTP-серверами напрямую не поддерживается протоколом. Скачайте файлы на iPhone, затем загрузите.", style: .warning)
            }
        default:
            show("Подключитесь к серверу в соседней панели.", style: .warning)
            serverTargetPaneID = dest.id
            showServers = true
        }
        source.exitSelectionMode()
    }

    private func copyLocally(_ items: [FileItem], to dest: PaneModel, move: Bool, source: PaneModel) {
        let destDir = dest.path
        busyMessage = move ? "Перемещение…" : "Копирование…"
        Task {
            var errors: [String] = []
            for item in items {
                do {
                    let existing = Set((try? FileManager.default.contentsOfDirectory(atPath: destDir)) ?? [])
                    var name = item.name
                    if existing.contains(name) {
                        if PathUtils.child(destDir, name) == item.path && !move {
                            name = PathUtils.uniqueName(for: name, existing: existing)
                        } else if settings.defaultConflictPolicy == .replace {
                            // заменяем
                        } else if settings.defaultConflictPolicy == .skip {
                            continue
                        } else {
                            name = PathUtils.uniqueName(for: name, existing: existing)
                        }
                    }
                    let replace = existing.contains(name)
                    try await Task.detached {
                        _ = try LocalFileSystem().transfer(item.path, into: destDir, newName: name, move: move, replace: replace)
                    }.value
                } catch {
                    errors.append("\(item.name): \(PaneModel.describe(error))")
                }
            }
            busyMessage = nil
            dest.refresh()
            source.refresh()
            if errors.isEmpty {
                show(move ? "Перемещено: \(items.count) \(RussianPlural.objects(items.count))" : "Скопировано: \(items.count) \(RussianPlural.objects(items.count))", style: .success)
                Haptics.success()
            } else {
                show(errors.joined(separator: "\n"), style: .error)
            }
        }
    }

    private func moveOnServer(_ items: [FileItem], in pane: PaneModel, to destDir: String) {
        guard let session = pane.session else { return }
        Task {
            var errors: [String] = []
            for item in items {
                let target = PathUtils.child(destDir, item.name)
                if target == item.path { continue }
                if PathUtils.isDescendant(target, of: item.path) {
                    errors.append("\(item.name): нельзя переместить папку внутрь самой себя")
                    continue
                }
                do { try await session.run { try await $0.rename(from: item.path, to: target) } } catch {
                    errors.append("\(item.name): \(PaneModel.describe(error))")
                }
            }
            leftPane.refresh(); rightPane.refresh()
            if errors.isEmpty { show("Перемещено на сервере", style: .success) } else { show(errors.joined(separator: "\n"), style: .error) }
        }
    }

    private func jobFinished(_ job: TransferJob) {
        for pane in [leftPane, rightPane] {
            switch (job.direction, pane.source) {
            case (.upload, .remote(let id)) where id == job.profileID:
                pane.didFinishTransfer(touching: job.destinationDirectory)
            case (.download, .local):
                pane.didFinishTransfer(touching: job.destinationDirectory)
                if case .extractArchive(let dest) = job.postAction { pane.didFinishTransfer(touching: dest) }
            case (.upload, .local) where job.deleteSourcesOnSuccess:
                pane.didFinishTransfer(touching: job.sourceDirectory)
            case (.download, .remote(let id)) where id == job.profileID && job.deleteSourcesOnSuccess:
                pane.didFinishTransfer(touching: job.sourceDirectory)
            default: break
            }
        }
        switch job.state {
        case .completed:
            show("Готово: \(job.title)", style: .success)
        case .completedWithErrors:
            show("\(job.title): ошибок — \(job.failedCount). Подробности в очереди.", style: .warning)
        case .failed:
            show("\(job.title): \(job.errorMessage ?? "ошибка")", style: .error)
        default: break
        }
    }

    // MARK: - Открытие файлов

    func open(_ item: FileItem, in pane: PaneModel) {
        if item.isDirectory {
            pane.navigate(to: item.path)
            return
        }
        if item.kind == .symlink && item.linkPointsToDirectory == nil && pane.source.isRemote {
            // Неизвестно, папка ли это: пробуем перейти, иначе открываем как файл.
            Task {
                if let session = pane.session, (try? await session.run({ try await $0.isDirectory(item.path) })) == true {
                    pane.navigate(to: item.path)
                } else {
                    openFile(item, in: pane)
                }
            }
            return
        }
        openFile(item, in: pane)
    }

    private func openFile(_ item: FileItem, in pane: PaneModel) {
        // Файл iCloud Drive, ещё не загруженный на устройство (".имя.icloud").
        if pane.source.isLocal, item.name.hasPrefix("."), item.name.hasSuffix(".icloud") {
            let realName = String(item.name.dropFirst().dropLast(".icloud".count))
            let realURL = URL(fileURLWithPath: PathUtils.child(PathUtils.parent(item.path), realName))
            do {
                try FileManager.default.startDownloadingUbiquitousItem(at: realURL)
                show("Загрузка «\(realName)» из iCloud…", style: .info)
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) { pane.refresh() }
            } catch {
                showError(error)
            }
            return
        }
        let ext = item.fileExtension
        if FileIcon.isArchive(ext) {
            archiveToOpen = ArchiveRequest(item: item, paneID: pane.id)
            return
        }
        if SyntaxLanguage.isEditable(fileName: item.name) {
            openInEditor(item, from: pane)
            return
        }
        preview = PreviewRequest(item: item, paneID: pane.id)
    }

    func openInEditor(_ item: FileItem, from pane: PaneModel) {
        switch pane.source {
        case .local:
            editor.openLocal(path: item.path)
        case .remote(let id):
            guard let profile = servers.profile(id) else { return }
            editor.openRemote(item: item, profile: profile)
        case .serverPicker: return
        }
        showEditor = true
    }

    /// «Скачать архив с FTP и распаковать на iPhone»: скачивание во временную папку + распаковка в текущую папку соседней панели.
    func downloadAndExtract(_ item: FileItem, from pane: PaneModel) {
        guard case .remote(let pid) = pane.source, let profile = servers.profile(pid) else { return }
        let dest = otherPane(of: pane)
        guard dest.source.isLocal else { return }
        let base = PathUtils.splitExtension(item.name).base
        let existing = Set(dest.items.map(\.name))
        let folder = PathUtils.child(dest.path, PathUtils.uniqueName(for: base, existing: existing))
        let staging = FileManager.default.temporaryDirectory.appendingPathComponent("twinftp-dl-\(UUID().uuidString)").path
        try? FileManager.default.createDirectory(atPath: staging, withIntermediateDirectories: true)
        var job = TransferJob(direction: .download, profileID: pid, serverName: profile.displayName, sources: [item],
                              sourceDirectory: pane.path, destinationDirectory: staging,
                              conflictPolicy: .replace, postAction: .extractArchive(destination: folder),
                              title: "\(item.name) → распаковка")
        job.conflictPolicy = .replace
        transfers.enqueue(job)
        show("Скачивание и распаковка: \(item.name)", style: .info)
    }

    /// «Распаковать и загрузить на FTP»: локальная распаковка во временную папку и загрузка её содержимого.
    func extractAndUpload(archive item: FileItem, password: String?, from pane: PaneModel) {
        let dest = otherPane(of: pane)
        guard case .remote(let pid) = dest.source, let profile = servers.profile(pid) else {
            show("В соседней панели должен быть открыт FTP-сервер.", style: .warning)
            return
        }
        let staging = FileManager.default.temporaryDirectory.appendingPathComponent("twinftp-unzip-\(UUID().uuidString)").path
        let remoteDir = dest.path
        busyMessage = "Распаковка «\(item.name)»…"
        Task {
            do {
                try await Task.detached {
                    let reader = try ZipArchiveReader(url: URL(fileURLWithPath: item.path))
                    _ = try reader.extractAll(to: staging, password: password, policy: .rename)
                }.value
                let lfs = LocalFileSystem()
                let top = try lfs.list(staging)
                busyMessage = nil
                guard !top.isEmpty else { show("Архив пуст.", style: .warning); return }
                let job = TransferJob(direction: .upload, profileID: pid, serverName: profile.displayName, sources: top,
                                      sourceDirectory: staging, destinationDirectory: remoteDir,
                                      postAction: .cleanupLocal(path: staging),
                                      title: "\(item.name) → распаковка на сервер")
                transfers.enqueue(job)
                show("Распаковано, загрузка на сервер…", style: .info)
            } catch {
                busyMessage = nil
                try? FileManager.default.removeItem(atPath: staging)
                showError(error)
            }
        }
    }

    // MARK: - Входящие файлы (Открыть в… / Поделиться)

    func handleIncoming(url: URL) {
        let started = url.startAccessingSecurityScopedResource()
        defer { if started { url.stopAccessingSecurityScopedResource() } }
        let inbox = LocalRootStore.inboxURL
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        let existing = Set((try? FileManager.default.contentsOfDirectory(atPath: inbox.path)) ?? [])
        let name = PathUtils.uniqueName(for: url.lastPathComponent, existing: existing)
        let target = inbox.appendingPathComponent(name)
        do {
            // Системная папка Inbox — перемещаем, внешние файлы — копируем.
            if url.path.contains("/Documents/Inbox/") {
                try FileManager.default.moveItem(at: url, to: target)
            } else {
                try FileManager.default.copyItem(at: url, to: target)
            }
            let pane = leftPane.source.isLocal ? leftPane : rightPane
            pane.setSource(.local(rootID: "documents"), path: inbox.path)
            activate(pane)
            show("Файл «\(name)» сохранён во «Входящие»", style: .success)
        } catch {
            showError(error)
        }
    }

    /// Импорт файлов из «Файлов» в текущую локальную папку.
    func importFiles(_ urls: [URL], into pane: PaneModel) {
        guard pane.source.isLocal else { return }
        let dir = pane.path
        busyMessage = "Импорт…"
        Task {
            var count = 0
            var errors: [String] = []
            for url in urls {
                let started = url.startAccessingSecurityScopedResource()
                defer { if started { url.stopAccessingSecurityScopedResource() } }
                let existing = Set((try? FileManager.default.contentsOfDirectory(atPath: dir)) ?? [])
                let name = PathUtils.uniqueName(for: url.lastPathComponent, existing: existing)
                do {
                    let src = url.path
                    try await Task.detached {
                        let coordinator = NSFileCoordinator()
                        var coordError: NSError?
                        var copyError: Error?
                        coordinator.coordinate(readingItemAt: URL(fileURLWithPath: src), options: [], error: &coordError) { readURL in
                            do { try FileManager.default.copyItem(at: readURL, to: URL(fileURLWithPath: PathUtils.child(dir, name))) } catch { copyError = error }
                        }
                        if let e = coordError ?? copyError { throw e }
                    }.value
                    count += 1
                } catch {
                    errors.append("\(url.lastPathComponent): \(error.localizedDescription)")
                }
            }
            busyMessage = nil
            pane.refresh()
            if errors.isEmpty { show("Импортировано: \(count) \(RussianPlural.objects(count))", style: .success) }
            else { show(errors.joined(separator: "\n"), style: .error) }
        }
    }

    // MARK: - Режим UI-тестов

    private func configureForUITests() {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-uitest-ftp"), i + 1 < args.count else { return }
        // Формат: host:port:user:password
        let parts = args[i + 1].split(separator: ":", maxSplits: 3).map(String.init)
        guard parts.count == 4 else { return }
        var profile = ServerProfile()
        profile.name = "Тестовый сервер"
        profile.host = parts[0]
        profile.port = Int(parts[1]) ?? 21
        profile.username = parts[2]
        profile.plainTextWarningAcknowledged = true
        if !servers.servers.contains(where: { $0.host == profile.host && $0.port == profile.port }) {
            servers.upsert(profile, password: parts[3])
        }
        if args.contains("-uitest-dark") { settings.appearance = .dark }
        if args.contains("-uitest-light") { settings.appearance = .light }
        // Немного демонстрационных файлов для снимков экрана UI-теста.
        let site = LocalRootStore.documentsURL.appendingPathComponent("Сайты/мой-сайт", isDirectory: true)
        try? FileManager.default.createDirectory(at: site.appendingPathComponent("css"), withIntermediateDirectories: true)
        let index = site.appendingPathComponent("index.php")
        if !FileManager.default.fileExists(atPath: index.path) {
            try? Data("<?php\n// Главная страница\n$title = \"Привет, мир\";\necho \"<h1>$title</h1>\";\n".utf8).write(to: index)
            try? Data("body { color: #333; margin: 0 }\n".utf8).write(to: site.appendingPathComponent("css/style.css"))
            try? Data("RewriteEngine On\n".utf8).write(to: site.appendingPathComponent(".htaccess"))
        }
    }
}

struct PreviewRequest: Identifiable {
    let id = UUID()
    let item: FileItem
    let paneID: String
}

struct ArchiveRequest: Identifiable {
    let id = UUID()
    let item: FileItem
    let paneID: String
    var uploadAfterExtract = false
}
