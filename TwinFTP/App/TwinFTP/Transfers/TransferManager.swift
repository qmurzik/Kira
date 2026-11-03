import Foundation
import SwiftUI
import UIKit
import TwinCore

/// Ожидающий решения пользователя конфликт имён.
struct PendingConflict: Identifiable {
    let info: ConflictInfo
    let continuation: CheckedContinuation<ConflictDecision, Never>
    var id: UUID { info.id }
}

/// Связывает движок передач с интерфейсом: публикует задания, показывает конфликты,
/// сохраняет очередь на диск, учитывает фоновый режим iOS.
@MainActor
final class TransferManager: ObservableObject {
    @Published private(set) var jobs: [TransferJob] = []
    @Published var currentConflict: PendingConflict?
    @Published var lastCompletion: TransferJob?

    private var conflictQueue: [PendingConflict] = []
    private var engine: TransferEngine!
    private let sessions: SessionManager
    private let settings: AppSettings
    private var saveTask: Task<Void, Never>?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    /// Обработчики завершения заданий (обновление панелей).
    var onJobFinished: ((TransferJob) -> Void)?

    private let storeURL: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("transfers.json")
    }()

    init(sessions: SessionManager, settings: AppSettings) {
        self.sessions = sessions
        self.settings = settings
        let env = TransferEnvironment(
            pool: { [weak self] id in
                guard let self else { throw FTPError.notConnected }
                return try await self.pool(for: id)
            },
            resolveConflict: { [weak self] info in
                guard let self else { return ConflictDecision(action: .skip, applyToAll: false) }
                return await self.askConflict(info)
            },
            jobDidChange: { [weak self] job in
                Task { @MainActor in self?.apply(job) }
            },
            performPostAction: { job in
                await Self.performPostAction(job)
            },
            safeUploads: { UserDefaults.standard.object(forKey: "safeUploads") as? Bool ?? true },
            maxRetries: 3)
        engine = TransferEngine(environment: env, maxParallel: settings.parallelTransfers)
        restore()
    }

    private func pool(for id: UUID) throws -> FTPConnectionPool {
        try sessions.pool(for: id)
    }

    // MARK: - Постановка в очередь

    func enqueue(_ job: TransferJob) {
        var job = job
        if job.conflictPolicy == .ask { job.conflictPolicy = settings.defaultConflictPolicy }
        jobs.insert(job, at: 0)
        Haptics.tap()
        Task {
            await engine.setMaxParallel(settings.parallelTransfers)
            await engine.enqueue(job)
        }
        updateIdleTimer()
    }

    func pause(_ id: UUID) { Task { await engine.pause(id) } }
    func resume(_ id: UUID) { Task { await engine.resume(id) } }
    func cancel(_ id: UUID) { Task { await engine.cancel(id) } }
    func retry(_ id: UUID) { Task { await engine.retryFailed(id) } }

    func remove(_ id: UUID) {
        jobs.removeAll { $0.id == id }
        Task { await engine.remove(id); scheduleSave() }
    }

    func clearFinished() {
        jobs.removeAll { $0.state.isFinished }
        Task { await engine.removeFinished(); scheduleSave() }
    }

    func pauseAll() { Task { await engine.pauseAll(bySystem: false) } }

    var activeCount: Int { jobs.filter { $0.state.isActive }.count }
    var hasActive: Bool { jobs.contains { $0.state.isActive } }

    var overallProgress: Double {
        let active = jobs.filter { $0.state.isActive || $0.state == .paused }
        let total = active.reduce(Int64(0)) { $0 + $1.totalBytes }
        guard total > 0 else { return 0 }
        return Double(active.reduce(Int64(0)) { $0 + $1.transferredBytes }) / Double(total)
    }

    // MARK: - Обновления от движка

    private func apply(_ job: TransferJob) {
        if let i = jobs.firstIndex(where: { $0.id == job.id }) {
            let wasFinished = jobs[i].state.isFinished
            jobs[i] = job
            if job.state.isFinished && !wasFinished {
                finished(job)
            }
        } else {
            jobs.insert(job, at: 0)
        }
        scheduleSave()
        updateIdleTimer()
    }

    private func finished(_ job: TransferJob) {
        lastCompletion = job
        switch job.state {
        case .completed: Haptics.success()
        case .completedWithErrors, .failed: Haptics.error()
        default: break
        }
        onJobFinished?(job)
        if !hasActive { endBackgroundTask() }
    }

    // MARK: - Конфликты

    private func askConflict(_ info: ConflictInfo) async -> ConflictDecision {
        await withCheckedContinuation { cont in
            let pending = PendingConflict(info: info, continuation: cont)
            if currentConflict == nil {
                currentConflict = pending
                Haptics.warning()
            } else {
                conflictQueue.append(pending)
            }
        }
    }

    func resolveConflict(_ decision: ConflictDecision) {
        guard let c = currentConflict else { return }
        currentConflict = nil
        c.continuation.resume(returning: decision)
        // Если выбрано «для всех» — остальные ожидающие конфликты того же задания решаются так же.
        if decision.applyToAll {
            let same = conflictQueue.filter { $0.info.jobID == c.info.jobID }
            conflictQueue.removeAll { $0.info.jobID == c.info.jobID }
            for p in same { p.continuation.resume(returning: decision) }
        }
        if !conflictQueue.isEmpty {
            let next = conflictQueue.removeFirst()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { [weak self] in
                self?.currentConflict = next
            }
        }
    }

    // MARK: - Пост-действия

    nonisolated static func performPostAction(_ job: TransferJob) async -> String? {
        guard case .extractArchive(let destination) = job.postAction,
              let task = job.tasks.first(where: { $0.state == .completed }) else { return nil }
        let archive = task.destinationPath
        return await Task.detached(priority: .userInitiated) { () -> String? in
            do {
                let reader = try ZipArchiveReader(url: URL(fileURLWithPath: archive))
                if reader.hasEncryptedEntries {
                    return "Архив защищён паролем — откройте его вручную, чтобы ввести пароль."
                }
                _ = try reader.extractAll(to: destination, password: nil, policy: .rename)
                return nil
            } catch {
                return (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }.value
    }

    // MARK: - Сохранение очереди

    private func restore() {
        guard let data = try? Data(contentsOf: storeURL),
              let saved = try? JSONDecoder().decode([TransferJob].self, from: data) else { return }
        Task {
            await engine.restore(saved)
            let restored = await engine.allJobs
            self.jobs = restored.sorted { $0.createdAt > $1.createdAt }
        }
    }

    private func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 800_000_000)
            guard let self, !Task.isCancelled else { return }
            self.saveNow()
        }
    }

    func saveNow() {
        // Храним не более 100 завершённых заданий.
        var list = jobs
        let finished = list.filter { $0.state.isFinished }
        if finished.count > 100 {
            let drop = Set(finished.suffix(finished.count - 100).map(\.id))
            list.removeAll { drop.contains($0.id) }
        }
        if let data = try? JSONEncoder().encode(list) {
            try? data.write(to: storeURL, options: .atomic)
        }
    }

    // MARK: - Фон и экран

    private func updateIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled = settings.keepAwakeDuringTransfers && hasActive
    }

    /// Приложение уходит в фон: просим у системы немного времени, затем аккуратно ставим очередь на паузу.
    func didEnterBackground() {
        saveNow()
        guard hasActive, backgroundTask == .invalid else { return }
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "TwinFTP transfers") { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                await self.engine.pauseAll(bySystem: true)
                self.saveNow()
                self.endBackgroundTask()
            }
        }
    }

    func willEnterForeground() {
        endBackgroundTask()
        Task { await engine.resumeSystemPaused() }
    }

    private func endBackgroundTask() {
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }

    func applySettings() {
        Task { await engine.setMaxParallel(settings.parallelTransfers) }
        updateIdleTimer()
    }
}
