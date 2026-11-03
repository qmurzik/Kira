import Foundation
import XCTest
@testable import TwinCore

/// Интеграционные тесты против настоящего FTP-сервера (pyftpdlib).
final class FTPIntegrationTests: XCTestCase {

    private func download(_ c: FTPConnection, _ path: String, offset: Int64 = 0) async throws -> Data {
        let buffer = Locked(Data())
        try await c.download(path, offset: offset) { chunk in buffer.withValue { $0.append(chunk) } }
        return buffer.current
    }

    private func upload(_ c: FTPConnection, _ path: String, _ data: Data, append: Bool = false, chunk: Int = 7000) async throws {
        let data = Data(data)
        let offset = Locked(0)
        try await c.upload(path, append: append) {
            offset.withValue { off -> Data? in
                if off >= data.count { return nil }
                let end = min(data.count, off + chunk)
                defer { off = end }
                return data.subdata(in: off..<end)
            }
        }
    }

    func testLoginListUploadDownloadAllStacks() async throws {
        let server = try TestFTPServer()
        defer { server.stop() }
        for stack in availableStacks {
            let log = InMemoryFTPLog()
            do {
                try await runFullScenario(server: server, stack: stack, log: log)
            } catch {
                // Диагностика: печатаем журнал FTP при сбое.
                for e in log.all { FileHandle.standardError.write(Data("[\(stack)] \(e.kind.rawValue): \(e.text)\n".utf8)) }
                XCTFail("стек \(stack): \(error)")
            }
        }
    }

    private func runFullScenario(server: TestFTPServer, stack: NetworkStack, log: InMemoryFTPLog) async throws {
        do {
            let c = server.connection(stack: stack, log: log)
            try await c.connect()
            XCTAssertEqual(c.homeDirectory, "/")
            XCTAssertTrue(c.features.mlsd)
            // Пароль не должен попасть в журнал.
            XCTAssertFalse(log.all.contains { $0.text.contains(TestFTPServer.password) })
            XCTAssertTrue(log.all.contains { $0.text == "PASS ********" })

            let dir = "/папка \(stack.rawValue) с пробелами"
            try await c.makeDirectory(dir)
            let payload = Data((0..<300_000).map { UInt8(truncatingIfNeeded: $0 &* 31) })
            let file = dir + "/Файл №1 (тест).bin"
            try await upload(c, file, payload)
            let back = try await download(c, file)
            XCTAssertEqual(back, payload, "стек \(stack)")

            // Пустой файл
            try await upload(c, dir + "/empty.txt", Data())
            let empty = try await download(c, dir + "/empty.txt")
            XCTAssertEqual(empty.count, 0)

            let items = try await c.list(dir)
            XCTAssertEqual(Set(items.map(\.name)), ["Файл №1 (тест).bin", "empty.txt"])
            let f = items.first { $0.name.hasPrefix("Файл") }!
            XCTAssertEqual(f.size, Int64(payload.count))
            XCTAssertEqual(f.kind, .file)
            XCTAssertNotNil(f.modified)

            let size = try await c.size(file)
            XCTAssertEqual(size, Int64(payload.count))
            let mdtm = try await c.modificationDate(file)
            XCTAssertNotNil(mdtm)

            // Переименование и удаление
            try await c.rename(from: file, to: dir + "/переименован.bin")
            let after = try await c.list(dir).map(\.name)
            XCTAssertTrue(after.contains("переименован.bin"))
            try await c.deleteFile(dir + "/переименован.bin")
            try await c.deleteFile(dir + "/empty.txt")
            try await c.removeDirectory(dir)
            let root = try await c.list("/").map(\.name)
            XCTAssertFalse(root.contains(dir.dropFirst().description))
            await c.disconnect()
        }
    }

    func testListFallbackWithoutMLSDAndEPSV() async throws {
        let server = try TestFTPServer(extraArgs: ["--no-mlsd", "--no-epsv"])
        defer { server.stop() }
        try "<?php echo 1;".write(to: server.root.appendingPathComponent("index.php"), atomically: true, encoding: .utf8)
        try "deny".write(to: server.root.appendingPathComponent(".htaccess"), atomically: true, encoding: .utf8)
        try FileManager.default.createDirectory(at: server.root.appendingPathComponent("Документы и фото"), withIntermediateDirectories: true)
        let c = server.connection(passive: .epsvFirst)
        try await c.connect()
        XCTAssertFalse(c.features.mlsd)
        let items = try await c.list("/")
        let names = Set(items.map(\.name))
        XCTAssertTrue(names.contains("index.php"))
        XCTAssertTrue(names.contains(".htaccess"), "скрытые файлы должны быть видны (LIST -a)")
        XCTAssertTrue(names.contains("Документы и фото"))
        XCTAssertEqual(items.first { $0.name == "Документы и фото" }?.kind, .directory)
        XCTAssertEqual(items.first { $0.name == "index.php" }?.size, 13)
        // EPSV не поддерживается — должен сработать переход на PASV.
        let data = try await download(c, "/index.php")
        XCTAssertEqual(String(decoding: data, as: UTF8.self), "<?php echo 1;")
    }

    func testResumeDownloadAndAppendUpload() async throws {
        let server = try TestFTPServer()
        defer { server.stop() }
        let c = server.connection()
        let payload = Data((0..<100_000).map { UInt8(truncatingIfNeeded: $0 &* 7) })
        try await upload(c, "/big.bin", payload.prefix(40_000))
        try await upload(c, "/big.bin", payload.suffix(from: 40_000), append: true)
        let full = try await download(c, "/big.bin")
        XCTAssertEqual(full, payload)
        let tail = try await download(c, "/big.bin", offset: 70_000)
        XCTAssertEqual(tail, payload.suffix(from: 70_000))
    }

    func testErrorsAreMeaningful() async throws {
        let server = try TestFTPServer()
        defer { server.stop() }
        let c = server.connection()
        do {
            _ = try await c.list("/нет такой папки")
            XCTFail("ожидалась ошибка")
        } catch let e as FTPError {
            if case .notADirectory = e {} else { XCTFail("неожиданная ошибка \(e)") }
        }
        do {
            _ = try await download(c, "/missing.txt")
            XCTFail("ожидалась ошибка")
        } catch let e as FTPError {
            XCTAssertEqual(e.replyCode, 550)
        }
        // После ошибок соединение остаётся рабочим
        let items = try await c.list("/")
        XCTAssertEqual(items.count, 0)

        let bad = FTPConnection(label: "bad", configuration: server.configuration(),
                                credentials: FTPCredentials(username: "twin", password: "wrong"), log: PrintLog())
        do {
            try await bad.connect()
            XCTFail("ожидалась ошибка входа")
        } catch let e as FTPError {
            if case .loginFailed(let code, _) = e { XCTAssertEqual(code, 530) } else { XCTFail("\(e)") }
        }
    }

    func testConnectionRefusedAndDNS() async throws {
        let cfg = FTPConfiguration(host: "127.0.0.1", port: 1, timeout: 3, networkStack: .bsd)
        let c = FTPConnection(label: "x", configuration: cfg, credentials: FTPCredentials(username: "a", password: "b"))
        do { try await c.connect(); XCTFail() } catch let e as FTPError {
            XCTAssertTrue(e.isConnectionLoss || { if case .connectionFailed = e { return true }; return false }())
        }
        let cfg2 = FTPConfiguration(host: "nonexistent-host.invalid", port: 21, timeout: 3, networkStack: .bsd)
        let c2 = FTPConnection(label: "x", configuration: cfg2, credentials: FTPCredentials(username: "a", password: "b"))
        do { try await c2.connect(); XCTFail() } catch let e as FTPError {
            if case .dnsFailure = e {} else { XCTFail("\(e)") }
        }
    }

    func testReconnectAfterServerDropsConnection() async throws {
        var server: TestFTPServer? = try TestFTPServer()
        let port = server!.port
        let c = server!.connection()
        try await c.connect()
        _ = try await c.list("/")
        // Сервер «упал» — следующая операция должна вернуть ошибку соединения, а не зависнуть.
        let root = server!.root
        server!.stop()
        server = nil
        do {
            _ = try await c.list("/")
            XCTFail("ожидалась ошибка")
        } catch let e as FTPError {
            XCTAssertTrue(e.isConnectionLoss || { if case .connectionFailed = e { return true }; return false }(), "\(e)")
        }
        _ = port; _ = root
    }

    func testCancelDuringDownloadKeepsClientUsable() async throws {
        let server = try TestFTPServer()
        defer { server.stop() }
        let big = Data(count: 30 * 1024 * 1024)
        try big.write(to: server.root.appendingPathComponent("huge.bin"))
        let c = server.connection()
        try await c.connect()
        let received = Locked(0)
        let task = Task {
            try await c.download("/huge.bin") { chunk in
                received.withValue { $0 += chunk.count }
                try await Task.sleep(nanoseconds: 2_000_000)
            }
        }
        while received.current < 200_000 { try await Task.sleep(nanoseconds: 10_000_000) }
        task.cancel()
        do { try await task.value; XCTFail("ожидалась отмена") } catch {}
        XCTAssertLessThan(received.current, big.count)
        // Клиент переподключается и продолжает работать.
        let items = try await c.list("/")
        XCTAssertEqual(items.first?.name, "huge.bin")
    }

    func testWindows1251Server() async throws {
        let server = try TestFTPServer(extraArgs: ["--cp1251", "--no-mlsd"])
        defer { server.stop() }
        var cfg = server.configuration(encoding: .windows1251)
        cfg.escapeTelnetIAC = false // pyftpdlib не обрабатывает Telnet IAC
        let c = FTPConnection(label: "cp1251", configuration: cfg, credentials: server.credentials)
        try await upload(c, "/Привет мир.txt", Data("ok".utf8))
        let items = try await c.list("/")
        XCTAssertEqual(items.map(\.name), ["Привет мир.txt"])
        // Имя на диске сервера в cp1251 → проверяем через python-кодировку: файл реально существует
        let onDisk = try FileManager.default.contentsOfDirectory(atPath: server.root.path)
        XCTAssertEqual(onDisk.count, 1)
        let data = try await download(c, "/Привет мир.txt")
        XCTAssertEqual(data, Data("ok".utf8))
        // Буква «я» в cp1251 = 0xFF — проверка удвоения IAC.
        try await upload(c, "/яблоко.txt", Data("я".utf8))
        let names = try await c.list("/").map(\.name).sorted()
        XCTAssertEqual(names, ["Привет мир.txt", "яблоко.txt"])
    }

    func testRenameReplacing() async throws {
        let server = try TestFTPServer()
        defer { server.stop() }
        let c = server.connection()
        try await upload(c, "/site.php", Data("old".utf8))
        try await upload(c, "/.site.php.twinftp-tmp", Data("new content".utf8))
        try await c.renameReplacing(temporary: "/.site.php.twinftp-tmp", target: "/site.php")
        let d = try await download(c, "/site.php")
        XCTAssertEqual(String(decoding: d, as: UTF8.self), "new content")
        let names = try await c.list("/").map(\.name)
        XCTAssertEqual(names, ["site.php"])
    }
}
