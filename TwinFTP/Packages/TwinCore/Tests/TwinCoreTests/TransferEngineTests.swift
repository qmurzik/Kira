import Foundation
import XCTest
@testable import TwinCore

/// Тесты очереди передач на реальном FTP-сервере.
final class TransferEngineTests: XCTestCase {
    var server: TestFTPServer!
    var localRoot: URL!
    let profileID = UUID()
    var decisions: Locked<[ConflictDecision]>!
    var askedConflicts: Locked<[String]>!

    override func setUpWithError() throws {
        server = try TestFTPServer()
        localRoot = FileManager.default.temporaryDirectory.appendingPathComponent("twinftp-local-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: localRoot, withIntermediateDirectories: true)
        decisions = Locked([])
        askedConflicts = Locked([])
    }

    override func tearDown() {
        server?.stop()
        if let localRoot { try? FileManager.default.removeItem(at: localRoot) }
    }

    func makeEngine(maxParallel: Int = 3, safe: Bool = true) -> TransferEngine {
        let pool = FTPConnectionPool(configuration: server.configuration(), credentials: server.credentials, maxConnections: maxParallel)
        let decisions = self.decisions!
        let asked = self.askedConflicts!
        let env = TransferEnvironment(
            pool: { _ in pool },
            resolveConflict: { info in
                asked.withValue { $0.append(info.name) }
                return decisions.withValue { d in d.isEmpty ? ConflictDecision(action: .skip, applyToAll: false) : d.removeFirst() }
            },
            jobDidChange: { _ in },
            safeUploads: { safe })
        return TransferEngine(environment: env, maxParallel: maxParallel)
    }

    @discardableResult
    func write(_ rel: String, _ content: String, under base: URL? = nil) throws -> URL {
        let url = (base ?? localRoot).appendingPathComponent(rel)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data(content.utf8).write(to: url)
        return url
    }

    func item(_ url: URL) -> FileItem { LocalFileSystem().item(at: url)! }

    func runJob(_ engine: TransferEngine, _ job: TransferJob, timeout: TimeInterval = 60) async throws -> TransferJob {
        let id = await engine.enqueue(job)
        let idle = await engine.waitUntilIdle(timeout: timeout)
        XCTAssertTrue(idle, "очередь не завершилась вовремя")
        return await engine.job(id)!
    }

    /// Снимок дерева: относительный путь → содержимое (nil для каталогов).
    func tree(_ root: URL) -> [String: Data?] {
        var result: [String: Data?] = [:]
        let lfs = LocalFileSystem()
        for item in (try? lfs.walk(root.path)) ?? [] {
            let rel = PathUtils.relative(item.path, to: root.path)!
            result[rel] = item.kind == .directory ? Optional<Data>.none : (try? Data(contentsOf: URL(fileURLWithPath: item.path)))
        }
        return result
    }

    func testMassRecursiveUploadAndDownload() async throws {
        let site = localRoot.appendingPathComponent("сайт")
        for i in 0..<100 { try write("сайт/страницы/стр \(i).php", "<?php echo \(i); ?>") }
        try write("сайт/css/style.css", "body{}")
        try write("сайт/пустая/.keep", "")
        try FileManager.default.createDirectory(at: site.appendingPathComponent("совсем пустая"), withIntermediateDirectories: true)
        let longName = String(repeating: "д", count: 100) + ".txt" // 200+ байт в UTF-8
        try write("сайт/\(longName)", "long")
        let big = Data((0..<3_000_000).map { UInt8(truncatingIfNeeded: $0 * 13) })
        try big.write(to: site.appendingPathComponent("big.bin"))

        let engine = makeEngine()
        let upload = TransferJob(direction: .upload, profileID: profileID, serverName: "test",
                                 sources: [item(site)], sourceDirectory: localRoot.path, destinationDirectory: "/")
        let done = try await runJob(engine, upload)
        XCTAssertEqual(done.state, .completed, done.tasks.compactMap(\.errorMessage).joined(separator: "; "))
        XCTAssertEqual(done.completedCount, 104)
        XCTAssertEqual(tree(server.root.appendingPathComponent("сайт")), tree(site))
        // Временных файлов не осталось
        XCTAssertFalse(tree(server.root).keys.contains { $0.contains(TransferEngine.partSuffix) })

        // Скачиваем обратно в другую папку
        let dl = localRoot.appendingPathComponent("скачано")
        try FileManager.default.createDirectory(at: dl, withIntermediateDirectories: true)
        let conn = server.connection()
        let remoteItem = try await conn.list("/").first { $0.name == "сайт" }!
        let download = TransferJob(direction: .download, profileID: profileID, serverName: "test",
                                   sources: [remoteItem], sourceDirectory: "/", destinationDirectory: dl.path)
        let done2 = try await runJob(engine, download)
        XCTAssertEqual(done2.state, .completed, done2.tasks.compactMap(\.errorMessage).joined(separator: "; "))
        XCTAssertEqual(tree(dl.appendingPathComponent("сайт")), tree(site))
        XCTAssertEqual(done2.totalBytes, done2.transferredBytes)
    }

    func testConflictsSkipRenameReplace() async throws {
        let a = try write("a.txt", "local A")
        let b = try write("b.txt", "local B")
        try Data("server A".utf8).write(to: server.root.appendingPathComponent("a.txt"))
        try Data("server B".utf8).write(to: server.root.appendingPathComponent("b.txt"))
        let engine = makeEngine(maxParallel: 1)

        // 1. Пропустить для всех
        decisions.withValue { $0 = [ConflictDecision(action: .skip, applyToAll: true)] }
        var job = try await runJob(engine, TransferJob(direction: .upload, profileID: profileID, serverName: "t",
                                                      sources: [item(a), item(b)], sourceDirectory: localRoot.path, destinationDirectory: "/"))
        XCTAssertEqual(job.skippedCount, 2)
        XCTAssertEqual(askedConflicts.current.count, 1, "«применить ко всем» должно спросить только один раз")
        XCTAssertEqual(try String(contentsOf: server.root.appendingPathComponent("a.txt"), encoding: .utf8), "server A")

        // 2. Сохранить оба
        decisions.withValue { $0 = [ConflictDecision(action: .rename, applyToAll: true)] }
        job = try await runJob(engine, TransferJob(direction: .upload, profileID: profileID, serverName: "t",
                                                  sources: [item(a)], sourceDirectory: localRoot.path, destinationDirectory: "/"))
        XCTAssertEqual(job.completedCount, 1)
        XCTAssertEqual(try String(contentsOf: server.root.appendingPathComponent("a (2).txt"), encoding: .utf8), "local A")

        // 3. Заменить
        decisions.withValue { $0 = [ConflictDecision(action: .replace, applyToAll: false), ConflictDecision(action: .skip, applyToAll: false)] }
        job = try await runJob(engine, TransferJob(direction: .upload, profileID: profileID, serverName: "t",
                                                  sources: [item(a), item(b)], sourceDirectory: localRoot.path, destinationDirectory: "/"))
        XCTAssertEqual(job.completedCount, 1)
        XCTAssertEqual(job.skippedCount, 1)
        XCTAssertEqual(try String(contentsOf: server.root.appendingPathComponent("a.txt"), encoding: .utf8), "local A")
        XCTAssertEqual(try String(contentsOf: server.root.appendingPathComponent("b.txt"), encoding: .utf8), "server B")

        // 4. Скачивание с конфликтом: переименование локально
        decisions.withValue { $0 = [ConflictDecision(action: .rename, applyToAll: false)] }
        let conn = server.connection()
        let remoteA = try await conn.list("/").first { $0.name == "a.txt" }!
        job = try await runJob(engine, TransferJob(direction: .download, profileID: profileID, serverName: "t",
                                                  sources: [remoteA], sourceDirectory: "/", destinationDirectory: localRoot.path))
        XCTAssertEqual(job.completedCount, 1)
        XCTAssertEqual(try String(contentsOf: localRoot.appendingPathComponent("a (2).txt"), encoding: .utf8), "local A")
        XCTAssertEqual(try String(contentsOf: a, encoding: .utf8), "local A")
    }

    func testErrorsDoNotStopQueueAndMoveDeletesOnlySuccessful() async throws {
        var files: [URL] = []
        for i in 0..<5 { files.append(try write("f\(i).txt", "file \(i)")) }
        let engine = makeEngine(maxParallel: 1)
        let sources = files.map(item)
        // Удаляем один файл после выбора — его передача должна завершиться ошибкой, остальные — пройти.
        try FileManager.default.removeItem(at: files[2])
        let job = try await runJob(engine, TransferJob(direction: .upload, profileID: profileID, serverName: "t",
                                                      sources: sources, sourceDirectory: localRoot.path,
                                                      destinationDirectory: "/нет/такой", deleteSourcesOnSuccess: true))
        XCTAssertEqual(job.state, .completedWithErrors)
        XCTAssertEqual(job.completedCount, 4)
        XCTAssertEqual(job.failedCount, 1)
        XCTAssertNotNil(job.tasks.first { $0.state == .failed }?.errorMessage)
        for f in files { XCTAssertFalse(FileManager.default.fileExists(atPath: f.path)) }
        XCTAssertEqual(try String(contentsOf: server.root.appendingPathComponent("нет/такой/f4.txt"), encoding: .utf8), "file 4")

        // Перемещение со скачиванием: исходник на сервере удаляется только при успехе.
        let dl = localRoot.appendingPathComponent("dl")
        let remote = try await server.connection().list("/нет/такой")
        let job2 = try await runJob(engine, TransferJob(direction: .download, profileID: profileID, serverName: "t",
                                                       sources: remote, sourceDirectory: "/нет/такой",
                                                       destinationDirectory: dl.path, deleteSourcesOnSuccess: true))
        XCTAssertEqual(job2.state, .completed)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: server.root.appendingPathComponent("нет/такой").path), [])
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: dl.path).count, 4)
    }

    func testFailureMidQueueContinuesWithOthers() async throws {
        // Сервер запрещает запись в каталог only-read (через права файловой системы).
        let ro = server.root.appendingPathComponent("ro")
        try FileManager.default.createDirectory(at: ro, withIntermediateDirectories: true)
        try FileManager.default.setAttributes([.posixPermissions: 0o555], ofItemAtPath: ro.path)
        defer { try? FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: ro.path) }
        if getuid() == 0 { throw XCTSkip("под root права каталога не ограничивают запись") }
        let a = try write("ok.txt", "ok")
        let b = try write("ro/bad.txt", "bad")
        _ = b
        let engine = makeEngine(maxParallel: 1)
        let job = try await runJob(engine, TransferJob(direction: .upload, profileID: profileID, serverName: "t",
                                                      sources: [item(a), item(localRoot.appendingPathComponent("ro"))],
                                                      sourceDirectory: localRoot.path, destinationDirectory: "/"))
        XCTAssertEqual(job.state, .completedWithErrors)
        XCTAssertEqual(job.completedCount, 1)
        XCTAssertEqual(job.failedCount, 1)
    }

    func testPauseResumeLargeDownload() async throws {
        let big = Data((0..<40_000_000).map { UInt8(truncatingIfNeeded: $0 &* 31 &+ 7) })
        try big.write(to: server.root.appendingPathComponent("large.bin"))
        let engine = makeEngine(maxParallel: 1)
        let conn = server.connection()
        let remote = try await conn.list("/").first!
        let id = await engine.enqueue(TransferJob(direction: .download, profileID: profileID, serverName: "t",
                                                  sources: [remote], sourceDirectory: "/", destinationDirectory: localRoot.path))
        // Ждём начала передачи
        var progressed: Int64 = 0
        for _ in 0..<500 {
            progressed = await engine.job(id)?.transferredBytes ?? 0
            if progressed > 1_000_000 { break }
            try await Task.sleep(nanoseconds: 2_000_000)
        }
        await engine.pause(id)
        try await Task.sleep(nanoseconds: 300_000_000)
        var job = await engine.job(id)!
        XCTAssertEqual(job.state, .paused)
        let pausedAt = job.transferredBytes
        if pausedAt >= Int64(big.count) { throw XCTSkip("передача завершилась слишком быстро для проверки паузы") }
        XCTAssertTrue(job.tasks[0].hasPartial)
        await engine.resume(id)
        _ = await engine.waitUntilIdle(timeout: 60)
        job = await engine.job(id)!
        XCTAssertEqual(job.state, .completed, job.tasks.first?.errorMessage ?? "")
        let result = try Data(contentsOf: localRoot.appendingPathComponent("large.bin"))
        XCTAssertEqual(result.count, big.count)
        XCTAssertEqual(result, big)
    }

    func testServerCrashMidQueueIsRetried() async throws {
        for i in 0..<30 { try write("batch/file\(i).dat", String(repeating: "x", count: 200_000 + i)) }
        let engine = makeEngine(maxParallel: 2)
        let id = await engine.enqueue(TransferJob(direction: .upload, profileID: profileID, serverName: "t",
                                                  sources: [item(localRoot.appendingPathComponent("batch"))],
                                                  sourceDirectory: localRoot.path, destinationDirectory: "/"))
        for _ in 0..<1000 {
            if (await engine.job(id)?.completedCount ?? 0) >= 3 { break }
            try await Task.sleep(nanoseconds: 5_000_000)
        }
        try server.crashAndRestart()
        _ = await engine.waitUntilIdle(timeout: 90)
        let job = await engine.job(id)!
        XCTAssertEqual(job.state, .completed, job.tasks.compactMap(\.errorMessage).joined(separator: "; "))
        XCTAssertEqual(tree(server.root.appendingPathComponent("batch")), tree(localRoot.appendingPathComponent("batch")))
    }

    func testCancelRemovesPartials() async throws {
        let big = Data(count: 50_000_000)
        try big.write(to: server.root.appendingPathComponent("cancel.bin"))
        let engine = makeEngine(maxParallel: 1)
        let remote = try await server.connection().list("/").first!
        let id = await engine.enqueue(TransferJob(direction: .download, profileID: profileID, serverName: "t",
                                                  sources: [remote], sourceDirectory: "/", destinationDirectory: localRoot.path))
        for _ in 0..<500 {
            if (await engine.job(id)?.transferredBytes ?? 0) > 100_000 { break }
            try await Task.sleep(nanoseconds: 2_000_000)
        }
        await engine.cancel(id)
        try await Task.sleep(nanoseconds: 500_000_000)
        let job = await engine.job(id)!
        XCTAssertEqual(job.state, .cancelled)
        let leftovers = try FileManager.default.contentsOfDirectory(atPath: localRoot.path)
        XCTAssertEqual(leftovers, [], "после отмены не должно остаться временных файлов")
    }

    func testRestoreMarksInterruptedJobsPaused() async throws {
        var job = TransferJob(direction: .upload, profileID: profileID, serverName: "t", sources: [],
                              sourceDirectory: "/", destinationDirectory: "/")
        job.state = .running
        var t = TransferTask(sourcePath: "/x", destinationDirectory: "/", destinationName: "x", size: 10, sourceModified: nil)
        t.state = .running
        job.tasks = [t]
        let data = try JSONEncoder().encode([job])
        let restored = try JSONDecoder().decode([TransferJob].self, from: data)
        let engine = makeEngine()
        await engine.restore(restored)
        let j = await engine.job(job.id)!
        XCTAssertEqual(j.state, .paused)
        XCTAssertEqual(j.tasks[0].state, .pending)
    }
}
