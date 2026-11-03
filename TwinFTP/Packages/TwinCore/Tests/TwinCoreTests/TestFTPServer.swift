import Foundation
import XCTest
@testable import TwinCore

/// Запускает реальный FTP-сервер (pyftpdlib) для интеграционных тестов.
/// Если python3/pyftpdlib недоступны — тесты пропускаются (XCTSkip), а не «проходят».
final class TestFTPServer {
    let port: Int
    let root: URL
    private var process: Process
    private let extraArgs: [String]

    static let user = "twin"
    static let password = "пароль 123"

    static var scriptURL: URL {
        // Tests/TwinCoreTests/TestFTPServer.swift → ../../../../Scripts/test_ftp_server.py
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Scripts/test_ftp_server.py")
    }

    init(extraArgs: [String] = []) throws {
        guard ProcessInfo.processInfo.environment["TWINFTP_SKIP_INTEGRATION"] == nil else {
            throw XCTSkip("Интеграционные тесты отключены переменной TWINFTP_SKIP_INTEGRATION")
        }
        let check = Process()
        check.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        check.arguments = ["python3", "-c", "import pyftpdlib"]
        check.standardOutput = FileHandle.nullDevice
        check.standardError = FileHandle.nullDevice
        try check.run()
        let checkDeadline = Date().addingTimeInterval(20)
        while check.isRunning && Date() < checkDeadline { Thread.sleep(forTimeInterval: 0.05) }
        guard check.terminationStatus == 0 else {
            throw XCTSkip("python3 + pyftpdlib не установлены — интеграционные тесты пропущены")
        }

        self.extraArgs = extraArgs
        root = FileManager.default.temporaryDirectory.appendingPathComponent("twinftp-root-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        port = Int.random(in: 21000...29000)
        process = try Self.launch(port: port, root: root, extraArgs: extraArgs)
    }

    private static func launch(port: Int, root: URL, extraArgs: [String]) throws -> Process {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        process.arguments = ["python3", Self.scriptURL.path, "--port", String(port), "--root", root.path] + extraArgs
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()

        // Ждём, пока порт начнёт принимать соединения.
        let deadline = Date().addingTimeInterval(10)
        while Date() < deadline {
            if Self.canConnect(port: port) { return process }
            Thread.sleep(forTimeInterval: 0.1)
        }
        process.terminate()
        throw XCTSkip("Тестовый FTP-сервер не запустился")
    }

    /// Имитация сбоя: убивает сервер (обрывая все соединения) и запускает заново на том же порту.
    func crashAndRestart(downtime: TimeInterval = 0.5) throws {
        kill(process.processIdentifier, SIGKILL)
        let deadline = Date().addingTimeInterval(5)
        while Self.canConnect(port: port) && Date() < deadline { Thread.sleep(forTimeInterval: 0.05) }
        Thread.sleep(forTimeInterval: downtime)
        process = try Self.launch(port: port, root: root, extraArgs: extraArgs)
    }

    static func canConnect(port: Int) -> Bool {
        let sem = DispatchSemaphore(value: 0)
        let ok = Locked(false)
        Task {
            if let s = try? await POSIXSocketConnector().connect(host: "127.0.0.1", port: port, timeout: 1) {
                s.close()
                ok.withValue { $0 = true }
            }
            sem.signal()
        }
        sem.wait()
        return ok.current
    }

    func stop() {
        // waitUntilExit() в async-контексте на Linux может заблокироваться — убиваем и ждём закрытия порта.
        kill(process.processIdentifier, SIGKILL)
        let deadline = Date().addingTimeInterval(5)
        while Self.canConnect(port: port) && Date() < deadline { Thread.sleep(forTimeInterval: 0.05) }
        try? FileManager.default.removeItem(at: root)
    }

    func configuration(stack: NetworkStack = .bsd, encoding: FTPEncoding = .auto,
                       listing: FTPListingMode = .auto, passive: FTPPassiveMode = .auto) -> FTPConfiguration {
        FTPConfiguration(host: "127.0.0.1", port: port, username: Self.user, encoding: encoding,
                         passiveMode: passive, listingMode: listing, timeout: 10, networkStack: stack)
    }

    var credentials: FTPCredentials { FTPCredentials(username: Self.user, password: Self.password) }

    func connection(stack: NetworkStack = .bsd, encoding: FTPEncoding = .auto,
                    listing: FTPListingMode = .auto, passive: FTPPassiveMode = .auto,
                    log: FTPLogSink? = nil) -> FTPConnection {
        FTPConnection(label: "test", configuration: configuration(stack: stack, encoding: encoding, listing: listing, passive: passive),
                      credentials: credentials, log: log ?? (ProcessInfo.processInfo.environment["TWINFTP_TEST_VERBOSE"] != nil ? PrintLog() : nil))
    }
}

struct PrintLog: FTPLogSink {
    func record(_ entry: FTPLogEntry) { FileHandle.standardError.write(Data("[\(entry.connection)] \(entry.kind.rawValue): \(entry.text)\n".utf8)) }
}

/// Стеки, доступные на текущей платформе (на macOS проверяем и Network.framework).
var availableStacks: [NetworkStack] {
    #if canImport(Network)
    return [.bsd, .network]
    #else
    return [.bsd]
    #endif
}
