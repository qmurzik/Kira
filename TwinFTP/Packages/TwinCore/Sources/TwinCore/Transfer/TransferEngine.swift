import Foundation

/// Окружение движка передач: доступ к пулам соединений, разрешение конфликтов, уведомления.
public struct TransferEnvironment: Sendable {
    public var pool: @Sendable (UUID) async throws -> FTPConnectionPool
    public var resolveConflict: @Sendable (ConflictInfo) async -> ConflictDecision
    public var jobDidChange: @Sendable (TransferJob) -> Void
    /// Выполнение пост-действия (распаковка и т. п.). Возвращает текст ошибки или nil.
    public var performPostAction: @Sendable (TransferJob) async -> String?
    /// Загружать во временный файл и переименовывать (защита от порчи файла на сервере).
    public var safeUploads: @Sendable () -> Bool
    public var maxRetries: Int

    public init(pool: @escaping @Sendable (UUID) async throws -> FTPConnectionPool,
                resolveConflict: @escaping @Sendable (ConflictInfo) async -> ConflictDecision,
                jobDidChange: @escaping @Sendable (TransferJob) -> Void,
                performPostAction: @escaping @Sendable (TransferJob) async -> String? = { _ in nil },
                safeUploads: @escaping @Sendable () -> Bool = { true },
                maxRetries: Int = 3) {
        self.pool = pool
        self.resolveConflict = resolveConflict
        self.jobDidChange = jobDidChange
        self.performPostAction = performPostAction
        self.safeUploads = safeUploads
        self.maxRetries = maxRetries
    }
}

/// Движок очереди передач. Выполняет задания последовательно по очереди,
/// а файлы внутри — параллельно (до `maxParallel` соединений).
public actor TransferEngine {
    private let env: TransferEnvironment
    private let local = LocalFileSystem()
    public private(set) var maxParallel: Int

    private var jobs: [TransferJob] = []
    private var running: [UUID: Task<Void, Never>] = [:]   // taskID → Task
    private var runningJob: [UUID: UUID] = [:]              // taskID → jobID
    private var preparing: [UUID: Task<Void, Never>] = [:]  // jobID → Task
    private var remoteListingCache: [UUID: [String: [String: FileItem]]] = [:]
    private var lastNotify: [UUID: Date] = [:]
    private var speedSamples: [UUID: [(Date, Int64)]] = [:]
    private var retryAfter: [UUID: Date] = [:]
    private let conflictLock = AsyncLock()
    private var finishing: Set<UUID> = []

    public init(environment: TransferEnvironment, maxParallel: Int = 2) {
        self.env = environment
        self.maxParallel = max(1, maxParallel)
    }

    public static let partSuffix = ".twinftp-part"

    public static func partName(for name: String) -> String {
        var base = "." + name + partSuffix
        // Длинные имена: временное имя не должно превышать 255 байт.
        while base.utf8.count > 255 { base = String(base.dropFirst(2)); base = "." + base }
        return base
    }

    // MARK: - Публичный API

    public var allJobs: [TransferJob] { jobs }

    public func job(_ id: UUID) -> TransferJob? { jobs.first { $0.id == id } }

    public func setMaxParallel(_ n: Int) {
        maxParallel = max(1, min(6, n))
        schedule()
    }

    /// Восстанавливает очередь после перезапуска: незавершённые задания ставятся на паузу.
    public func restore(_ saved: [TransferJob]) {
        for var job in saved where !jobs.contains(where: { $0.id == job.id }) {
            if job.state.isActive {
                job.state = job.state == .preparing ? .failed : .paused
                if job.state == .failed { job.errorMessage = "Подготовка была прервана перезапуском приложения." }
                job.statusNote = "Прервано: приложение было закрыто или выгружено системой."
            }
            for i in job.tasks.indices where job.tasks[i].state == .running {
                job.tasks[i].state = .pending
            }
            job.bytesPerSecond = 0
            jobs.append(job)
        }
    }

    @discardableResult
    public func enqueue(_ job: TransferJob) -> UUID {
        var job = job
        job.state = .preparing
        jobs.append(job)
        notify(job.id, force: true)
        let id = job.id
        preparing[id] = Task { [weak self] in
            await self?.prepare(jobID: id)
        }
        return id
    }

    public func pause(_ jobID: UUID, bySystem: Bool = false) {
        guard let idx = index(jobID), jobs[idx].state.isActive, jobs[idx].state != .preparing else { return }
        jobs[idx].state = .paused
        jobs[idx].pausedBySystem = bySystem
        jobs[idx].bytesPerSecond = 0
        jobs[idx].statusNote = bySystem ? "Приостановлено: приложение ушло в фон." : nil
        cancelRunningTasks(of: jobID)
        notify(jobID, force: true)
    }

    public func pauseAll(bySystem: Bool) {
        for job in jobs where job.state == .running || job.state == .queued {
            pause(job.id, bySystem: bySystem)
        }
    }

    public func resume(_ jobID: UUID) {
        guard let idx = index(jobID), jobs[idx].state == .paused else { return }
        jobs[idx].state = .queued
        jobs[idx].pausedBySystem = false
        jobs[idx].statusNote = nil
        notify(jobID, force: true)
        schedule()
    }

    public func resumeSystemPaused() {
        for job in jobs where job.state == .paused && job.pausedBySystem { resume(job.id) }
    }

    public func cancel(_ jobID: UUID) {
        guard let idx = index(jobID), !jobs[idx].state.isFinished else { return }
        preparing[jobID]?.cancel()
        preparing[jobID] = nil
        jobs[idx].state = .cancelled
        jobs[idx].finishedAt = Date()
        jobs[idx].bytesPerSecond = 0
        for i in jobs[idx].tasks.indices where !jobs[idx].tasks[i].isFinished {
            jobs[idx].tasks[i].state = .cancelled
        }
        cancelRunningTasks(of: jobID)
        let job = jobs[idx]
        notify(jobID, force: true)
        Task { await self.cleanupPartials(job) }
        if case .cleanupLocal(let path) = job.postAction { try? FileManager.default.removeItem(atPath: path) }
    }

    /// Повторяет сбойные (и отменённые) файлы задания.
    public func retryFailed(_ jobID: UUID) {
        guard let idx = index(jobID), jobs[idx].state.isFinished || jobs[idx].state == .paused else { return }
        var changed = false
        for i in jobs[idx].tasks.indices where jobs[idx].tasks[i].state == .failed || jobs[idx].tasks[i].state == .cancelled {
            jobs[idx].tasks[i].state = .pending
            jobs[idx].tasks[i].errorMessage = nil
            jobs[idx].tasks[i].attempts = 0
            changed = true
        }
        if jobs[idx].tasks.isEmpty && jobs[idx].state == .failed {
            // Подготовка не удалась — начинаем заново.
            jobs[idx].errorMessage = nil
            jobs[idx].state = .preparing
            jobs[idx].finishedAt = nil
            notify(jobID, force: true)
            preparing[jobID] = Task { [weak self] in await self?.prepare(jobID: jobID) }
            return
        }
        guard changed else { return }
        jobs[idx].state = .queued
        jobs[idx].finishedAt = nil
        jobs[idx].errorMessage = nil
        notify(jobID, force: true)
        schedule()
    }

    public func remove(_ jobID: UUID) {
        guard let idx = index(jobID) else { return }
        if !jobs[idx].state.isFinished { cancel(jobID) }
        jobs.removeAll { $0.id == jobID }
        remoteListingCache[jobID] = nil
    }

    public func removeFinished() {
        jobs.removeAll { $0.state.isFinished }
    }

    // MARK: - Подготовка

    private func index(_ id: UUID) -> Int? { jobs.firstIndex { $0.id == id } }

    private func prepare(jobID: UUID) async {
        guard let job = job(jobID) else { return }
        do {
            let plan: (tasks: [TransferTask], dirs: [String], sourceDirs: [String])
            switch job.direction {
            case .upload: plan = try planUpload(job)
            case .download: plan = try await planDownload(job)
            }
            try Task.checkCancellation()
            guard let idx = index(jobID), jobs[idx].state == .preparing else { return }
            jobs[idx].tasks = plan.tasks
            jobs[idx].directoriesToCreate = plan.dirs
            jobs[idx].sourceDirectories = plan.sourceDirs

            if job.direction == .download {
                let total = plan.tasks.reduce(Int64(0)) { $0 + ($1.size ?? 0) }
                try local.ensureDirectory(job.destinationDirectory)
                if let free = local.freeSpace(at: job.destinationDirectory), total > free {
                    throw LocalFileError.io("Недостаточно места на iPhone: нужно \(ByteFormatter.string(total)), свободно \(ByteFormatter.string(free)).")
                }
                for d in plan.dirs {
                    if FileManager.default.fileExists(atPath: d) && !local.isDirectory(d) {
                        throw LocalFileError.alreadyExists(PathUtils.lastComponent(d) + " (файл с таким именем уже есть)")
                    }
                    try local.ensureDirectory(d)
                }
            } else {
                let pool = try await env.pool(job.profileID)
                let conn = await pool.lease()
                defer { Task { await pool.giveBack(conn) } }
                try await conn.ensureDirectoryTree(job.destinationDirectory)
                for d in plan.dirs {
                    try Task.checkCancellation()
                    try await conn.ensureDirectory(d)
                }
            }
            guard let idx2 = index(jobID), jobs[idx2].state == .preparing else { return }
            jobs[idx2].state = .queued
            preparing[jobID] = nil
            notify(jobID, force: true)
            if plan.tasks.isEmpty { await finishIfDone(jobID) }
            schedule()
        } catch {
            preparing[jobID] = nil
            guard let idx = index(jobID), jobs[idx].state == .preparing else { return }
            if error is CancellationError || (error as? FTPError) == .cancelled {
                jobs[idx].state = .cancelled
            } else {
                jobs[idx].state = .failed
                jobs[idx].errorMessage = Self.describe(error)
            }
            jobs[idx].finishedAt = Date()
            notify(jobID, force: true)
            schedule()
        }
    }

    private func planUpload(_ job: TransferJob) throws -> ([TransferTask], [String], [String]) {
        var tasks: [TransferTask] = []
        var dirs: [String] = []
        var sourceDirs: [String] = []
        for source in job.sources {
            let topDest = job.destinationDirectory
            if local.isDirectory(source.path) && source.kind != .symlink || (source.kind == .symlink && source.linkPointsToDirectory == true) {
                let rootRemote = PathUtils.child(topDest, source.name)
                dirs.append(rootRemote)
                sourceDirs.append(source.path)
                for item in try local.walk(source.path) {
                    guard let rel = PathUtils.relative(item.path, to: source.path) else { continue }
                    let remote = PathUtils.child(rootRemote, rel)
                    if item.kind == .directory {
                        dirs.append(remote)
                        sourceDirs.append(item.path)
                    } else if item.kind == .file || (item.kind == .symlink && item.linkPointsToDirectory == false) {
                        tasks.append(TransferTask(sourcePath: item.path, destinationDirectory: PathUtils.parent(remote),
                                                  destinationName: item.name, size: item.size ?? fileSize(item.path),
                                                  sourceModified: item.modified))
                    }
                }
            } else {
                // Отсутствующий файл не срывает всё задание — ошибка будет у конкретной задачи.
                tasks.append(TransferTask(sourcePath: source.path, destinationDirectory: topDest, destinationName: source.name,
                                          size: fileSize(source.path), sourceModified: source.modified))
            }
        }
        return (tasks, dirs, sourceDirs)
    }

    private func fileSize(_ path: String) -> Int64? {
        (try? FileManager.default.attributesOfItem(atPath: path)[.size] as? NSNumber)?.int64Value
    }

    private func planDownload(_ job: TransferJob) async throws -> ([TransferTask], [String], [String]) {
        let pool = try await env.pool(job.profileID)
        let conn = await pool.lease()
        defer { Task { await pool.giveBack(conn) } }
        var tasks: [TransferTask] = []
        var dirs: [String] = []
        var sourceDirs: [String] = []
        for var source in job.sources {
            if source.kind == .symlink && source.linkPointsToDirectory == nil {
                source.linkPointsToDirectory = try await conn.canonicalDirectory(source.path) != nil
            }
            let topDest = job.destinationDirectory
            if source.isDirectory {
                let rootLocal = PathUtils.child(topDest, source.name)
                dirs.append(rootLocal)
                sourceDirs.append(source.path)
                let all = try await RemoteTree.walk(conn, root: source.path) { [weak self] count in
                    guard let self else { return }
                    Task { await self.setNote(job.id, "Сканирование: \(count) \(RussianPlural.objects(count))…") }
                }
                for item in all {
                    guard let rel = PathUtils.relative(item.path, to: source.path) else { continue }
                    let localPath = PathUtils.child(rootLocal, rel)
                    if item.isDirectory {
                        dirs.append(localPath)
                        sourceDirs.append(item.path)
                    } else if item.kind == .file || item.kind == .symlink {
                        tasks.append(TransferTask(sourcePath: item.path, destinationDirectory: PathUtils.parent(localPath),
                                                  destinationName: item.name, size: item.kind == .symlink ? nil : item.size,
                                                  sourceModified: item.modified, isSymlink: item.kind == .symlink))
                    }
                }
            } else {
                tasks.append(TransferTask(sourcePath: source.path, destinationDirectory: topDest, destinationName: source.name,
                                          size: source.kind == .symlink ? nil : source.size, sourceModified: source.modified,
                                          isSymlink: source.kind == .symlink))
            }
        }
        setNote(job.id, nil)
        return (tasks, dirs, sourceDirs)
    }

    private func setNote(_ jobID: UUID, _ note: String?) {
        guard let idx = index(jobID) else { return }
        jobs[idx].statusNote = note
        notify(jobID, force: false)
    }

    // MARK: - Планировщик

    private func schedule() {
        while running.count < maxParallel {
            guard let (jobID, taskID) = nextPendingTask() else { break }
            guard let jIdx = index(jobID), let tIdx = jobs[jIdx].tasks.firstIndex(where: { $0.id == taskID }) else { break }
            jobs[jIdx].tasks[tIdx].state = .running
            if jobs[jIdx].state == .queued { jobs[jIdx].state = .running }
            runningJob[taskID] = jobID
            running[taskID] = Task { [weak self] in
                await self?.run(jobID: jobID, taskID: taskID)
            }
            notify(jobID, force: true)
        }
    }

    private func nextPendingTask() -> (UUID, UUID)? {
        let now = Date()
        for job in jobs where job.state == .queued || job.state == .running {
            for task in job.tasks where task.state == .pending {
                if let after = retryAfter[task.id], after > now { continue }
                return (job.id, task.id)
            }
        }
        // Отложенные повторы: перезапускаем планировщик позже.
        if let soonest = retryAfter.values.filter({ $0 > now }).min() {
            let delay = soonest.timeIntervalSince(now)
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(max(0.05, delay) * 1_000_000_000))
                await self?.kick()
            }
        }
        return nil
    }

    private func kick() { schedule() }

    private func cancelRunningTasks(of jobID: UUID) {
        for (taskID, jID) in runningJob where jID == jobID {
            running[taskID]?.cancel()
        }
    }

    // MARK: - Выполнение задачи

    private func run(jobID: UUID, taskID: UUID) async {
        var outcome: TransferTaskState = .completed
        var message: String?
        var retry = false
        do {
            outcome = try await transfer(jobID: jobID, taskID: taskID)
        } catch {
            let jobState = job(jobID)?.state
            if Task.isCancelled || error is CancellationError || (error as? FTPError) == .cancelled {
                outcome = jobState == .paused ? .pending : .cancelled
            } else {
                outcome = .failed
                message = Self.describe(error)
                let attempts = (job(jobID)?.tasks.first { $0.id == taskID }?.attempts ?? 0) + 1
                if let idx = index(jobID), let t = jobs[idx].tasks.firstIndex(where: { $0.id == taskID }) {
                    jobs[idx].tasks[t].attempts = attempts
                }
                if Self.isRetryable(error) && attempts < env.maxRetries && jobState == .running {
                    retry = true
                    outcome = .pending
                    retryAfter[taskID] = Date().addingTimeInterval(Double(attempts) * 2)
                    if let ftp = error as? FTPError, ftp.replyCode == 421 || ftp.replyCode == 530,
                       let job = job(jobID), let pool = try? await env.pool(job.profileID) {
                        await pool.reduceLimit()
                    }
                }
            }
        }
        running[taskID] = nil
        runningJob[taskID] = nil
        if let idx = index(jobID), let t = jobs[idx].tasks.firstIndex(where: { $0.id == taskID }) {
            if jobs[idx].tasks[t].state == .running || jobs[idx].tasks[t].state == .pending {
                jobs[idx].tasks[t].state = outcome
                if outcome == .completed || outcome == .skipped { retryAfter[taskID] = nil }
            }
            if let message {
                jobs[idx].tasks[t].errorMessage = retry ? "Повтор: \(message)" : message
            } else if outcome == .completed {
                jobs[idx].tasks[t].errorMessage = nil
            }
        }
        notify(jobID, force: true)
        await finishIfDone(jobID)
        schedule()
    }

    static func isRetryable(_ error: Error) -> Bool {
        if let e = error as? FTPError {
            if e.isConnectionLoss { return true }
            if case .verificationFailed = e { return true }
            if case .passiveModeFailed = e { return true }
            if let code = e.replyCode, (400..<500).contains(code) { return true }
        }
        return false
    }

    static func describe(_ error: Error) -> String {
        if let e = error as? LocalizedError, let d = e.errorDescription { return d }
        return error.localizedDescription
    }

    private func updateTask(_ jobID: UUID, _ taskID: UUID, _ body: (inout TransferTask) -> Void) {
        guard let idx = index(jobID), let t = jobs[idx].tasks.firstIndex(where: { $0.id == taskID }) else { return }
        body(&jobs[idx].tasks[t])
    }

    private func addProgress(_ jobID: UUID, _ taskID: UUID, _ bytes: Int) {
        updateTask(jobID, taskID) { $0.transferred += Int64(bytes) }
        var samples = speedSamples[jobID] ?? []
        let now = Date()
        let total = (samples.last?.1 ?? 0) + Int64(bytes)
        samples.append((now, total))
        samples.removeAll { now.timeIntervalSince($0.0) > 4 }
        speedSamples[jobID] = samples
        if let first = samples.first, let last = samples.last, last.0.timeIntervalSince(first.0) > 0.3,
           let idx = index(jobID) {
            jobs[idx].bytesPerSecond = Double(last.1 - first.1) / last.0.timeIntervalSince(first.0)
        }
        notify(jobID, force: false)
    }

    private func transfer(jobID: UUID, taskID: UUID) async throws -> TransferTaskState {
        guard let job = job(jobID), let task = job.tasks.first(where: { $0.id == taskID }) else { return .cancelled }
        let pool = try await env.pool(job.profileID)
        let conn = await pool.lease()
        defer { Task { await pool.giveBack(conn) } }
        try Task.checkCancellation()
        switch job.direction {
        case .download: return try await download(job: job, task: task, conn: conn)
        case .upload: return try await upload(job: job, task: task, conn: conn)
        }
    }

    // MARK: Конфликты

    /// Возвращает итоговое имя файла назначения или nil, если файл нужно пропустить.
    private func resolveName(job: TransferJob, task: TransferTask, existing: FileItem?,
                             existingNames: Set<String>) async throws -> (name: String, replacing: Bool)? {
        if let name = task.resolvedName, let replacing = task.resolvedReplacing { return (name, replacing) }
        let result = try await resolveNameFresh(job: job, task: task, existing: existing, existingNames: existingNames)
        updateTask(job.id, task.id) {
            $0.resolvedName = result?.name
            $0.resolvedReplacing = result?.replacing
        }
        return result
    }

    private func resolveNameFresh(job: TransferJob, task: TransferTask, existing: FileItem?,
                                  existingNames: Set<String>) async throws -> (name: String, replacing: Bool)? {
        guard let existing else { return (task.destinationName, false) }
        if existing.isDirectory {
            // Нельзя заменить папку файлом — сохраняем под новым именем.
            return (PathUtils.uniqueName(for: task.destinationName, existing: existingNames), false)
        }
        try await conflictLock.acquire()
        defer { conflictLock.release() }
        let policy = self.job(job.id)?.conflictPolicy ?? job.conflictPolicy
        var action = policy.action
        if action == nil {
            let remaining = self.job(job.id)?.remainingCount ?? 0
            let info = ConflictInfo(jobID: job.id, direction: job.direction, name: task.destinationName,
                                    destinationPath: task.destinationPath,
                                    sourceSize: task.size, sourceDate: task.sourceModified,
                                    destinationSize: existing.size, destinationDate: existing.modified,
                                    remainingInJob: remaining)
            let decision = await env.resolveConflict(info)
            try Task.checkCancellation()
            action = decision.action
            if decision.applyToAll, let idx = index(job.id) {
                jobs[idx].conflictPolicy = ConflictPolicy(rawValue: decision.action.rawValue) ?? .ask
            }
        }
        switch action! {
        case .skip: return nil
        case .replace: return (task.destinationName, true)
        case .rename: return (PathUtils.uniqueName(for: task.destinationName, existing: existingNames), false)
        }
    }

    // MARK: Скачивание

    private func download(job: TransferJob, task: TransferTask, conn: FTPConnection) async throws -> TransferTaskState {
        let dir = task.destinationDirectory
        try local.ensureDirectory(dir)
        let finalPath = task.destinationPath
        let existingNames = Set((try? FileManager.default.contentsOfDirectory(atPath: dir)) ?? [])
        var existing: FileItem?
        if FileManager.default.fileExists(atPath: finalPath) { existing = local.item(at: URL(fileURLWithPath: finalPath)) }
        // Для повторной попытки той же задачи файл может уже существовать как результат — не спрашиваем повторно.
        guard let (name, replacing) = try await resolveName(job: job, task: task, existing: existing, existingNames: existingNames) else {
            return .skipped
        }
        let targetPath = PathUtils.child(dir, name)
        let partPath = PathUtils.child(dir, Self.partName(for: name))

        var expectedSize = task.size
        if expectedSize == nil || task.isSymlink {
            expectedSize = try await conn.size(task.sourcePath)
            if let s = expectedSize { updateTask(job.id, task.id) { $0.size = s } }
        }

        var offset: Int64 = 0
        let canResume = conn.features.restStream
        if task.hasPartial, canResume, let attrs = try? FileManager.default.attributesOfItem(atPath: partPath),
           let partSize = (attrs[.size] as? NSNumber)?.int64Value, partSize > 0,
           expectedSize == nil || partSize <= expectedSize! {
            offset = partSize
        } else {
            try? FileManager.default.removeItem(atPath: partPath)
            FileManager.default.createFile(atPath: partPath, contents: nil)
        }
        updateTask(job.id, task.id) { $0.transferred = offset; $0.hasPartial = true }

        guard let handle = FileHandle(forWritingAtPath: partPath) else {
            throw LocalFileError.io("Не удалось создать файл «\(name)» на iPhone.")
        }
        do {
            if offset > 0 { try handle.seek(toOffset: UInt64(offset)) } else { try handle.truncate(atOffset: 0) }
            let jobID = job.id, taskID = task.id
            try await conn.download(task.sourcePath, offset: offset) { [weak self] chunk in
                try handle.write(contentsOf: chunk)
                await self?.addProgress(jobID, taskID, chunk.count)
            }
            try handle.synchronize()
            try handle.close()
        } catch {
            try? handle.close()
            throw error
        }

        let written = (try? FileManager.default.attributesOfItem(atPath: partPath)[.size] as? NSNumber)?.int64Value ?? -1
        if let expected = expectedSize, written != expected {
            updateTask(job.id, task.id) { $0.hasPartial = false; $0.transferred = 0 }
            try? FileManager.default.removeItem(atPath: partPath)
            throw FTPError.verificationFailed("размер «\(name)»: получено \(written) из \(expected) байт")
        }
        if replacing && FileManager.default.fileExists(atPath: targetPath) {
            try LocalFileSystem.replaceItem(at: targetPath, with: partPath)
        } else {
            if FileManager.default.fileExists(atPath: targetPath) {
                throw LocalFileError.alreadyExists(name)
            }
            try FileManager.default.moveItem(atPath: partPath, toPath: targetPath)
        }
        if let date = task.sourceModified { local.setModificationDate(date, path: targetPath) }
        updateTask(job.id, task.id) {
            $0.hasPartial = false
            $0.destinationName = name
            if $0.size == nil { $0.size = written }
            $0.transferred = written
        }
        return .completed
    }

    // MARK: Загрузка

    private func remoteListing(job: TransferJob, dir: String, conn: FTPConnection, refresh: Bool = false) async throws -> [String: FileItem] {
        if !refresh, let cached = remoteListingCache[job.id]?[dir] { return cached }
        var map: [String: FileItem] = [:]
        do {
            for item in try await conn.list(dir) { map[item.name] = item }
        } catch let e as FTPError {
            if case .notADirectory = e { try await conn.ensureDirectory(dir) } else { throw e }
        }
        remoteListingCache[job.id, default: [:]][dir] = map
        return map
    }

    private func recordRemote(job: TransferJob, dir: String, item: FileItem) {
        remoteListingCache[job.id, default: [:]][dir, default: [:]][item.name] = item
    }

    private func upload(job: TransferJob, task: TransferTask, conn: FTPConnection) async throws -> TransferTaskState {
        let dir = task.destinationDirectory
        guard FileManager.default.fileExists(atPath: task.sourcePath) else {
            throw LocalFileError.notFound(task.name)
        }
        let listing = try await remoteListing(job: job, dir: dir, conn: conn)
        let existing = listing[task.destinationName]
        guard let (name, replacing) = try await resolveName(job: job, task: task, existing: existing,
                                                            existingNames: Set(listing.keys)) else {
            return .skipped
        }
        let target = PathUtils.child(dir, name)
        let safe = env.safeUploads()
        let partName = Self.partName(for: name)
        let uploadPath = safe ? PathUtils.child(dir, partName) : target
        let localSize = fileSize(task.sourcePath) ?? 0

        var offset: Int64 = 0
        if task.hasPartial, safe, let remoteSize = try await conn.size(uploadPath), remoteSize > 0, remoteSize <= localSize {
            offset = remoteSize
        }
        updateTask(job.id, task.id) { $0.transferred = offset; $0.hasPartial = safe; $0.size = localSize }

        guard let handle = FileHandle(forReadingAtPath: task.sourcePath) else {
            throw LocalFileError.io("Не удалось прочитать «\(task.name)».")
        }
        defer { try? handle.close() }
        if offset > 0 { try handle.seek(toOffset: UInt64(offset)) }
        let jobID = job.id, taskID = task.id
        let chunkSize = 256 * 1024
        try await conn.upload(uploadPath, append: offset > 0) { [weak self] in
            let data = try handle.read(upToCount: chunkSize) ?? Data()
            if data.isEmpty { return nil }
            await self?.addProgress(jobID, taskID, data.count)
            return data
        }

        // Проверяем размер на сервере (если сервер поддерживает SIZE).
        if let remoteSize = try await conn.size(uploadPath), remoteSize != localSize {
            updateTask(job.id, task.id) { $0.hasPartial = false; $0.transferred = 0 }
            try? await conn.deleteFile(uploadPath)
            throw FTPError.verificationFailed("размер «\(name)» на сервере \(remoteSize) вместо \(localSize) байт")
        }
        if safe {
            if replacing {
                try await conn.renameReplacing(temporary: uploadPath, target: target)
            } else {
                try await conn.rename(from: uploadPath, to: target)
            }
            // Сохраняем права заменённого файла (например, 600 для конфигов).
            if replacing, let mode = existing?.unixMode, mode != 0o644 {
                try? await conn.chmod(target, mode: mode)
            }
        }
        recordRemote(job: job, dir: dir, item: FileItem(name: name, path: target, kind: .file, size: localSize, modified: Date()))
        updateTask(job.id, task.id) { $0.hasPartial = false; $0.destinationName = name; $0.transferred = localSize }
        return .completed
    }

    // MARK: - Завершение

    private func finishIfDone(_ jobID: UUID) async {
        guard let idx = index(jobID) else { return }
        let job = jobs[idx]
        guard job.state == .running || job.state == .queued else { return }
        guard !job.tasks.contains(where: { $0.state == .pending || $0.state == .running }) else { return }
        guard !finishing.contains(jobID) else { return }
        finishing.insert(jobID)
        defer { finishing.remove(jobID) }

        let failed = job.tasks.contains { $0.state == .failed }
        if job.deleteSourcesOnSuccess {
            await deleteSources(job)
        }
        var postError: String?
        if !failed {
            if case .extractArchive = job.postAction {
                setNote(jobID, "Распаковка архива…")
                postError = await env.performPostAction(job)
            }
        }
        if case .cleanupLocal(let path) = job.postAction, !failed {
            try? FileManager.default.removeItem(atPath: path)
        }
        guard let idx2 = index(jobID) else { return }
        jobs[idx2].state = (failed || postError != nil) ? .completedWithErrors : .completed
        jobs[idx2].errorMessage = postError
        jobs[idx2].finishedAt = Date()
        jobs[idx2].bytesPerSecond = 0
        jobs[idx2].statusNote = nil
        remoteListingCache[jobID] = nil
        speedSamples[jobID] = nil
        notify(jobID, force: true)
    }

    /// Удаляет источники только для успешно переданных файлов. Каталоги — только если опустели.
    private func deleteSources(_ job: TransferJob) async {
        let done = job.tasks.filter { $0.state == .completed }
        switch job.direction {
        case .upload:
            for t in done { try? FileManager.default.removeItem(atPath: t.sourcePath) }
            for d in job.sourceDirectories.sorted(by: { $0.count > $1.count }) {
                if let contents = try? FileManager.default.contentsOfDirectory(atPath: d), contents.isEmpty {
                    try? FileManager.default.removeItem(atPath: d)
                }
            }
        case .download:
            guard let pool = try? await env.pool(job.profileID) else { return }
            let conn = await pool.lease()
            for t in done { try? await conn.deleteFile(t.sourcePath) }
            for d in job.sourceDirectories.sorted(by: { $0.count > $1.count }) {
                if let items = try? await conn.list(d), items.isEmpty { try? await conn.removeDirectory(d) }
            }
            await pool.giveBack(conn)
        }
    }

    private func cleanupPartials(_ job: TransferJob) async {
        let partials = job.tasks.filter { $0.hasPartial && $0.state != .completed }
        guard !partials.isEmpty else { return }
        switch job.direction {
        case .download:
            for t in partials {
                try? FileManager.default.removeItem(atPath: PathUtils.child(t.destinationDirectory, Self.partName(for: t.destinationName)))
            }
        case .upload:
            guard env.safeUploads(), let pool = try? await env.pool(job.profileID) else { return }
            let conn = await pool.lease()
            for t in partials {
                try? await conn.deleteFile(PathUtils.child(t.destinationDirectory, Self.partName(for: t.destinationName)))
            }
            await pool.giveBack(conn)
        }
    }

    private func notify(_ jobID: UUID, force: Bool) {
        let now = Date()
        if !force, let last = lastNotify[jobID], now.timeIntervalSince(last) < 0.25 { return }
        lastNotify[jobID] = now
        if let job = job(jobID) { env.jobDidChange(job) }
    }

    /// Ожидание завершения всех заданий (для тестов).
    public func waitUntilIdle(timeout: TimeInterval = 60) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if !jobs.contains(where: { $0.state.isActive }) && running.isEmpty { return true }
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        return false
    }
}
