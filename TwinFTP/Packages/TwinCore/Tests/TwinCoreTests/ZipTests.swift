import Foundation
import XCTest
@testable import TwinCore

final class ZipTests: XCTestCase {
    var tmp: URL!

    override func setUpWithError() throws {
        tmp = FileManager.default.temporaryDirectory.appendingPathComponent("twinzip-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
    }

    override func tearDown() { try? FileManager.default.removeItem(at: tmp) }

    func fixture(_ name: String) -> URL {
        Bundle.module.url(forResource: name, withExtension: nil, subdirectory: "Fixtures")!
    }

    func testReadAndExtractCyrillicArchive() throws {
        let reader = try ZipArchiveReader(url: fixture("site.zip"))
        let names = reader.entries.map(\.path)
        XCTAssertEqual(names, ["сайт/", "сайт/index.php", "сайт/css/style.css", "сайт/пустой.txt", "readme.txt"])
        XCTAssertTrue(reader.entries[0].isDirectory)
        let report = try reader.extractAll(to: tmp.path, password: nil)
        XCTAssertEqual(report.extractedFiles, 4)
        XCTAssertEqual(try String(contentsOf: tmp.appendingPathComponent("сайт/index.php"), encoding: .utf8), "<?php echo 'Привет'; ?>\n")
        XCTAssertEqual(try Data(contentsOf: tmp.appendingPathComponent("сайт/пустой.txt")).count, 0)
        // Повторная распаковка с политикой «переименовывать» создаёт «сайт (2)».
        let report2 = try reader.extractAll(to: tmp.path, password: nil, policy: .rename)
        XCTAssertTrue(FileManager.default.fileExists(atPath: tmp.appendingPathComponent("сайт (2)/css/style.css").path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: tmp.appendingPathComponent("readme (2).txt").path))
        XCTAssertEqual(report2.extractedFiles, 4)
        // Временных файлов не осталось
        let all = try LocalFileSystem().walk(tmp.path).map(\.name)
        XCTAssertFalse(all.contains { $0.hasSuffix(TransferEngine.partSuffix) })
    }

    func testZipCryptoPassword() throws {
        let reader = try ZipArchiveReader(url: fixture("zipcrypto.zip"))
        XCTAssertEqual(reader.entries.first?.encryption, .traditional)
        XCTAssertThrowsError(try reader.extractAll(to: tmp.path, password: nil)) { XCTAssertEqual($0 as? ZipError, .passwordRequired) }
        XCTAssertThrowsError(try reader.extractAll(to: tmp.path, password: "неверный")) { XCTAssertEqual($0 as? ZipError, .wrongPassword) }
        _ = try reader.extractAll(to: tmp.path, password: "пароль")
        let text = try String(contentsOf: tmp.appendingPathComponent("secret.txt"), encoding: .utf8)
        XCTAssertEqual(text, String(repeating: "секретные данные\n", count: 100))
    }

    func testAESPassword() throws {
        let reader = try ZipArchiveReader(url: fixture("aes.zip"))
        guard case .aes(let strength, let method) = reader.entries.first?.encryption else { return XCTFail("ожидался AES") }
        XCTAssertEqual(strength, 3)
        XCTAssertEqual(method, 8)
        #if canImport(CommonCrypto)
        XCTAssertThrowsError(try reader.extractAll(to: tmp.path, password: "нет")) { XCTAssertEqual($0 as? ZipError, .wrongPassword) }
        _ = try reader.extractAll(to: tmp.path, password: "пароль")
        XCTAssertEqual(try String(contentsOf: tmp.appendingPathComponent("aes.txt"), encoding: .utf8),
                       String(repeating: "AES содержимое\n", count: 100))
        #else
        XCTAssertThrowsError(try reader.extractAll(to: tmp.path, password: "пароль"))
        #endif
    }

    func testCP866Names() throws {
        let reader = try ZipArchiveReader(url: fixture("cp866.zip"))
        XCTAssertEqual(reader.entries.map(\.path), ["Отчёт.txt"])
    }

    func testZipSlipAndSymlinksAreBlocked() throws {
        let reader = try ZipArchiveReader(url: fixture("evil.zip"))
        let dest = tmp.appendingPathComponent("out")
        let report = try reader.extractAll(to: dest.path, password: nil)
        XCTAssertFalse(FileManager.default.fileExists(atPath: tmp.appendingPathComponent("evil.txt").path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: tmp.appendingPathComponent("evil2.txt").path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: dest.appendingPathComponent("good/link").path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: dest.appendingPathComponent("good/fine.txt").path))
        // "/abs.txt" безопасно распаковывается внутрь папки назначения
        XCTAssertTrue(FileManager.default.fileExists(atPath: dest.appendingPathComponent("abs.txt").path))
        XCTAssertEqual(report.skipped.count, 3)
        XCTAssertNil(ZipArchiveReader.sanitize("../../etc/passwd"))
        XCTAssertNil(ZipArchiveReader.sanitize("a/../../b"))
        XCTAssertEqual(ZipArchiveReader.sanitize("C:\\Windows\\x.txt"), "Windows/x.txt")
        XCTAssertEqual(ZipArchiveReader.sanitize("./a//b/./c"), "a/b/c")
    }

    func testLyingSizeIsDetected() throws {
        let reader = try ZipArchiveReader(url: fixture("liar.zip"))
        XCTAssertThrowsError(try reader.extractAll(to: tmp.path, password: nil)) { error in
            if case .suspiciousCompression = error as? ZipError {} else { XCTFail("\(error)") }
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: tmp.appendingPathComponent("bomb.bin").path))
    }

    func testRoundTripCreateAndRatioLimit() throws {
        let src = tmp.appendingPathComponent("src")
        try FileManager.default.createDirectory(at: src.appendingPathComponent("вложенная/глубже"), withIntermediateDirectories: true)
        try Data("<?php phpinfo();".utf8).write(to: src.appendingPathComponent("index.php"))
        try Data().write(to: src.appendingPathComponent("пусто.txt"))
        let random = Data((0..<500_000).map { _ in UInt8.random(in: 0...255) })
        try random.write(to: src.appendingPathComponent("вложенная/глубже/rand.bin"))
        try Data(count: 20 * 1024 * 1024).write(to: src.appendingPathComponent("zeros.bin"))
        try Data([0xFF, 0xD8, 0xFF]).write(to: src.appendingPathComponent("photo.jpg"))
        let zipURL = tmp.appendingPathComponent("архив.zip")
        var lastProgress: Int64 = 0
        try ZipArchiveWriter.create(at: zipURL, from: [src.path]) { done, _, _ in lastProgress = done }
        XCTAssertEqual(lastProgress, 500_000 + 20 * 1024 * 1024 + 16 + 3)

        // Проверка внешним инструментом (если доступен)
        let unzip = Process()
        unzip.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        unzip.arguments = ["unzip", "-tq", zipURL.path]
        unzip.standardOutput = FileHandle.nullDevice
        unzip.standardError = FileHandle.nullDevice
        if (try? unzip.run()) != nil {
            while unzip.isRunning { Thread.sleep(forTimeInterval: 0.05) }
            XCTAssertEqual(unzip.terminationStatus, 0, "unzip -t должен принять архив")
        }

        let reader = try ZipArchiveReader(url: zipURL)
        XCTAssertTrue(reader.entries.contains { $0.path == "src/вложенная/глубже/rand.bin" })
        XCTAssertEqual(reader.entries.first { $0.path == "src/photo.jpg" }?.method, 0)
        let out = tmp.appendingPathComponent("out")
        _ = try reader.extractAll(to: out.path, password: nil)
        XCTAssertEqual(try Data(contentsOf: out.appendingPathComponent("src/вложенная/глубже/rand.bin")), random)
        XCTAssertEqual(try Data(contentsOf: out.appendingPathComponent("src/zeros.bin")).count, 20 * 1024 * 1024)

        // Лимит коэффициента сжатия (защита от zip-бомб)
        var limits = ZipLimits()
        limits.maxRatio = 100
        XCTAssertThrowsError(try reader.extractAll(to: tmp.appendingPathComponent("out2").path, password: nil, limits: limits)) {
            if case .suspiciousCompression = $0 as? ZipError {} else { XCTFail("\($0)") }
        }
    }

    func testNotAZip() throws {
        let f = tmp.appendingPathComponent("fake.zip")
        try Data("not a zip at all".utf8).write(to: f)
        XCTAssertThrowsError(try ZipArchiveReader(url: f)) { XCTAssertEqual($0 as? ZipError, .notAZip) }
    }

    func testCancelCreatesNoPartialArchive() throws {
        let src = tmp.appendingPathComponent("big")
        try FileManager.default.createDirectory(at: src, withIntermediateDirectories: true)
        try Data(count: 5_000_000).write(to: src.appendingPathComponent("a.bin"))
        let zipURL = tmp.appendingPathComponent("c.zip")
        var calls = 0
        XCTAssertThrowsError(try ZipArchiveWriter.create(at: zipURL, from: [src.path], isCancelled: { calls += 1; return calls > 3 }))
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: tmp.path), ["big"])
    }
}
