import Foundation
import XCTest
@testable import TwinCore

final class ReplyParserTests: XCTestCase {
    func feed(_ lines: [String]) throws -> [FTPReply] {
        var a = FTPReplyAssembler()
        var out: [FTPReply] = []
        for l in lines { if let r = try a.feed(line: l) { out.append(r) } }
        return out
    }

    func testSingleAndMultiline() throws {
        let replies = try feed([
            "220-Добро пожаловать",
            "220-Second line",
            " 220 indented line is still inside",
            "220 Ready",
            "331 Password required"
        ])
        XCTAssertEqual(replies.count, 2)
        XCTAssertEqual(replies[0].code, 220)
        XCTAssertEqual(replies[0].lines.count, 4)
        XCTAssertTrue(replies[0].message.contains("Добро пожаловать"))
        XCTAssertEqual(replies[1].code, 331)
        XCTAssertEqual(replies[1].message, "Password required")
    }

    func testMultilineWithDifferentCodeInside() throws {
        let replies = try feed(["211-Features:", " MLST type*;size*;", "200 not the end", " UTF8", "211 End"])
        XCTAssertEqual(replies.count, 1)
        XCTAssertEqual(replies[0].lines.count, 5)
        let feat = FTPReplyParsing.parseFeatures(replies[0])
        XCTAssertNotNil(feat["MLST"])
        XCTAssertNotNil(feat["UTF8"])
    }

    func testGarbageIsRejected() {
        var a = FTPReplyAssembler()
        XCTAssertThrowsError(try a.feed(line: "hello"))
        XCTAssertNil(try a.feed(line: ""))
        XCTAssertNil(FTPReplyAssembler.leadingCode("12"))
        XCTAssertNil(FTPReplyAssembler.leadingCode("999 x"))
        XCTAssertEqual(FTPReplyAssembler.leadingCode("250"), 250)
    }

    func testPASV() {
        XCTAssertEqual(FTPReplyParsing.parsePASV("Entering Passive Mode (192,168,1,10,195,80).").map { "\($0.host):\($0.port)" }, "192.168.1.10:50000")
        XCTAssertEqual(FTPReplyParsing.parsePASV("Entering Passive Mode 10,0,0,1,4,1").map { "\($0.host):\($0.port)" }, "10.0.0.1:1025")
        XCTAssertEqual(FTPReplyParsing.parsePASV("=127,0,0,1,117,48").map { $0.port }, 30000)
        XCTAssertNil(FTPReplyParsing.parsePASV("Entering Passive Mode (300,1,1,1,1,1)"))
    }

    func testEPSV() {
        XCTAssertEqual(FTPReplyParsing.parseEPSV("Entering Extended Passive Mode (|||6446|)"), 6446)
        XCTAssertEqual(FTPReplyParsing.parseEPSV("Entering Extended Passive Mode (!!!6446!)"), 6446)
        XCTAssertNil(FTPReplyParsing.parseEPSV("Entering Extended Passive Mode"))
    }

    func testPWD() {
        XCTAssertEqual(FTPReplyParsing.parsePWD("\"/home/user\" is current directory"), "/home/user")
        XCTAssertEqual(FTPReplyParsing.parsePWD("\"/dir with \"\"quotes\"\"\" ok"), "/dir with \"quotes\"")
        XCTAssertEqual(FTPReplyParsing.parsePWD("\"/папка с пробелом\""), "/папка с пробелом")
        XCTAssertEqual(FTPReplyParsing.parsePWD("/plain is cwd"), "/plain")
    }

    func testMDTMAndSize() {
        let d = FTPReplyParsing.parseMDTM("20240115103045")!
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        let c = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: d)
        XCTAssertEqual([c.year, c.month, c.day, c.hour, c.minute, c.second], [2024, 1, 15, 10, 30, 45])
        XCTAssertNotNil(FTPReplyParsing.parseMDTM("20240115103045.123"))
        XCTAssertNil(FTPReplyParsing.parseMDTM("garbage"))
        XCTAssertEqual(FTPReplyParsing.parseSize("123456"), 123456)
    }

    func testLogSanitizer() {
        XCTAssertEqual(FTPLogSanitizer.sanitize(command: "PASS секрет 123"), "PASS ********")
        XCTAssertEqual(FTPLogSanitizer.sanitize(command: "pass x"), "PASS ********")
        XCTAssertEqual(FTPLogSanitizer.sanitize(command: "USER bob"), "USER bob")
    }

    func testPrivateIP() {
        XCTAssertTrue(IPAddressClassifier.isPrivateOrUnroutable("192.168.0.5"))
        XCTAssertTrue(IPAddressClassifier.isPrivateOrUnroutable("10.1.2.3"))
        XCTAssertTrue(IPAddressClassifier.isPrivateOrUnroutable("172.20.0.1"))
        XCTAssertFalse(IPAddressClassifier.isPrivateOrUnroutable("172.32.0.1"))
        XCTAssertFalse(IPAddressClassifier.isPrivateOrUnroutable("93.184.216.34"))
        XCTAssertFalse(IPAddressClassifier.isPrivateOrUnroutable("example.com"))
    }
}

final class ListParserTests: XCTestCase {
    let now: Date = {
        var c = DateComponents(); c.year = 2024; c.month = 6; c.day = 1
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        return cal.date(from: c)!
    }()

    func testUnixListing() {
        let lines = [
            "total 24",
            "drwxr-xr-x   5 user  group     4096 Jan 15 10:30 public_html",
            "-rw-r--r--   1 user  group    12345 Dec 31  2023 файл с пробелами.txt",
            "lrwxrwxrwx   1 user  group       11 Mar  2 09:00 www -> public_html",
            "-rw-------   1 1001  1001        0 May  5 23:59 .htaccess",
            "drwxr-xr-x   2 user  group     4096 Jan 15 10:30 .",
            "drwxr-xr-x   2 user  group     4096 Jan 15 10:30 ..",
            "crw-rw-rw-   1 root  root    1,   3 Jan  1  2020 null",
            "-rw-r--r--   1 owner          999 Feb 29 12:00 no-group.css",
            "-rw-r--r--   1 user  group      100 Jul 10 12:00 future.txt"
        ]
        let items = FTPListParser.parseLIST(lines, directory: "/home", now: now)
        XCTAssertEqual(items.map(\.name), ["public_html", "файл с пробелами.txt", "www", ".htaccess", "null", "no-group.css", "future.txt"])
        XCTAssertEqual(items[0].kind, .directory)
        XCTAssertEqual(items[0].path, "/home/public_html")
        XCTAssertEqual(items[1].size, 12345)
        XCTAssertEqual(items[2].kind, .symlink)
        XCTAssertEqual(items[2].linkTarget, "public_html")
        XCTAssertEqual(items[3].unixMode, 0o600)
        XCTAssertEqual(items[3].size, 0)
        XCTAssertEqual(items[5].size, 999)
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        XCTAssertEqual(cal.component(.year, from: items[1].modified!), 2023)
        XCTAssertEqual(cal.component(.year, from: items[0].modified!), 2024)
        // Дата без года в будущем (июль при текущем июне) относится к прошлому году
        XCTAssertEqual(cal.component(.year, from: items[6].modified!), 2023)
    }

    func testRussianMonthsAndISODates() {
        let lines = [
            "-rw-r--r--  1 user group  42 янв 15 10:30 отчёт.docx",
            "-rw-r--r--  1 user group  43 15 мая 2023 май.txt",
            "-rw-r--r--  1 user group  44 2024-02-03 04:05 iso name.txt"
        ]
        let items = FTPListParser.parseLIST(lines, directory: "/", now: now)
        XCTAssertEqual(items.map(\.name), ["отчёт.docx", "май.txt", "iso name.txt"])
        XCTAssertEqual(items.map(\.size), [42, 43, 44])
        XCTAssertEqual(items[2].path, "/iso name.txt")
    }

    func testDOSListing() {
        let lines = [
            "01-15-24  10:30AM       <DIR>          Мои документы",
            "12-31-2023  11:59PM              1,234 report final.pdf",
            "2024-02-03  14:05              77 iis.txt"
        ]
        let items = FTPListParser.parseLIST(lines, directory: "/", now: now)
        XCTAssertEqual(items.map(\.name), ["Мои документы", "report final.pdf", "iis.txt"])
        XCTAssertEqual(items[0].kind, .directory)
        XCTAssertEqual(items[1].size, 1234)
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        XCTAssertEqual(cal.component(.hour, from: items[1].modified!), 23)
    }

    func testMLSD() {
        let lines = [
            "type=cdir;modify=20240101000000;perm=flcdmpe; .",
            "type=pdir;modify=20240101000000;perm=flcdmpe; ..",
            "type=dir;modify=20240115103000;perm=flcdmpe; Папка",
            "type=file;size=1024;modify=20240115103000.5;UNIX.mode=0644;perm=adfrw; имя; с точкой с запятой.txt",
            "type=OS.unix=symlink;size=10; link",
            "Type=File;Size=5;Modify=20240101000000; Upper.txt"
        ]
        let items = FTPListParser.parseMLSD(lines, directory: "/www")
        XCTAssertEqual(items.map(\.name), ["Папка", "имя; с точкой с запятой.txt", "link", "Upper.txt"])
        XCTAssertEqual(items[0].kind, .directory)
        XCTAssertEqual(items[1].size, 1024)
        XCTAssertEqual(items[1].unixMode, 0o644)
        XCTAssertEqual(items[1].permissions, "rw-r--r--")
        XCTAssertEqual(items[2].kind, .symlink)
        XCTAssertEqual(items[3].size, 5)
        XCTAssertEqual(items[1].path, "/www/имя; с точкой с запятой.txt")
    }
}

final class PathTests: XCTestCase {
    func testNormalize() {
        XCTAssertEqual(PathUtils.normalize("/a//b/./c/../d/"), "/a/b/d")
        XCTAssertEqual(PathUtils.normalize("/../.."), "/")
        XCTAssertEqual(PathUtils.normalize(""), "/")
        XCTAssertEqual(PathUtils.join("/a", "b c"), "/a/b c")
        XCTAssertEqual(PathUtils.join("/", "x"), "/x")
        XCTAssertEqual(PathUtils.parent("/a/b"), "/a")
        XCTAssertEqual(PathUtils.parent("/a"), "/")
        XCTAssertEqual(PathUtils.parent("/"), "/")
        XCTAssertEqual(PathUtils.lastComponent("/a/файл.txt"), "файл.txt")
        XCTAssertEqual(PathUtils.child("/", "a"), "/a")
        XCTAssertEqual(PathUtils.child("/x", "a"), "/x/a")
    }

    func testBreadcrumbsAndRelative() {
        let crumbs = PathUtils.breadcrumbs("/www/site/css")
        XCTAssertEqual(crumbs.map(\.name), ["/", "www", "site", "css"])
        XCTAssertEqual(crumbs.last?.path, "/www/site/css")
        XCTAssertEqual(PathUtils.relative("/a/b/c", to: "/a"), "b/c")
        XCTAssertNil(PathUtils.relative("/ab/c", to: "/a"))
        XCTAssertTrue(PathUtils.isDescendant("/a/b", of: "/a"))
        XCTAssertFalse(PathUtils.isDescendant("/ab", of: "/a"))
    }

    func testValidationAndUniqueNames() {
        XCTAssertNotNil(PathUtils.validateName(""))
        XCTAssertNotNil(PathUtils.validateName("a/b"))
        XCTAssertNotNil(PathUtils.validateName(".."))
        XCTAssertNotNil(PathUtils.validateName(String(repeating: "я", count: 200)))
        XCTAssertNil(PathUtils.validateName("Новый файл (копия).php"))
        XCTAssertEqual(PathUtils.uniqueName(for: "a.txt", existing: ["a.txt", "a (2).txt"]), "a (3).txt")
        XCTAssertEqual(PathUtils.uniqueName(for: ".htaccess", existing: [".htaccess"]), ".htaccess (2)")
        XCTAssertEqual(PathUtils.uniqueName(for: "folder", existing: []), "folder")
        XCTAssertEqual(TransferEngine.partName(for: "x.php"), ".x.php.twinftp-part")
        XCTAssertLessThanOrEqual(TransferEngine.partName(for: String(repeating: "д", count: 127)).utf8.count, 255)
    }

    func testPermissions() {
        XCTAssertEqual(UnixPermissions.string(fromMode: 0o755), "rwxr-xr-x")
        XCTAssertEqual(UnixPermissions.mode(fromString: "-rw-r--r--"), 0o644)
        XCTAssertEqual(UnixPermissions.mode(fromString: "drwxr-x---"), 0o750)
    }

    func testSortOrder() {
        let items = [
            FileItem(name: "b.txt", path: "/b.txt", kind: .file, size: 5),
            FileItem(name: "A", path: "/A", kind: .directory),
            FileItem(name: "a10.txt", path: "/a10.txt", kind: .file, size: 50),
            FileItem(name: "a2.txt", path: "/a2.txt", kind: .file, size: 1)
        ]
        XCTAssertEqual(FileSortOrder().sort(items).map(\.name), ["A", "a2.txt", "a10.txt", "b.txt"])
        XCTAssertEqual(FileSortOrder(field: .size, ascending: false).sort(items).map(\.name), ["A", "a10.txt", "b.txt", "a2.txt"])
    }

    func testLocalFileSystemOperations() throws {
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("lfs-\(UUID().uuidString)").path
        defer { try? FileManager.default.removeItem(atPath: tmp) }
        let lfs = LocalFileSystem()
        try lfs.makeDirectory(tmp + "/папка")
        try lfs.createFile(tmp + "/папка/a.txt", contents: Data("1".utf8))
        XCTAssertThrowsError(try lfs.createFile(tmp + "/папка/a.txt"))
        let renamed = try lfs.rename(tmp + "/папка/a.txt", to: "b.txt")
        XCTAssertEqual(renamed, tmp + "/папка/b.txt")
        XCTAssertThrowsError(try lfs.rename(renamed, to: "x/y"))
        try lfs.makeDirectory(tmp + "/dest")
        _ = try lfs.transfer(tmp + "/папка", into: tmp + "/dest", move: false, replace: false)
        XCTAssertTrue(lfs.exists(tmp + "/dest/папка/b.txt"))
        XCTAssertThrowsError(try lfs.transfer(tmp + "/папка", into: tmp + "/dest", move: false, replace: false))
        XCTAssertThrowsError(try lfs.transfer(tmp + "/dest", into: tmp + "/dest/папка", move: true, replace: false)) {
            XCTAssertEqual($0 as? LocalFileError, .cannotMoveIntoItself("dest"))
        }
        try Data("new".utf8).write(to: URL(fileURLWithPath: tmp + "/папка/b.txt"))
        _ = try lfs.transfer(tmp + "/папка/b.txt", into: tmp + "/dest/папка", move: true, replace: true)
        XCTAssertEqual(try String(contentsOfFile: tmp + "/dest/папка/b.txt", encoding: .utf8), "new")
        XCTAssertFalse(lfs.exists(tmp + "/папка/b.txt"))
        let walked = try lfs.walk(tmp)
        XCTAssertEqual(walked.count, 4)
    }
}

final class EditorCoreTests: XCTestCase {
    func testDecodeEncodeRoundTrip() throws {
        let samples: [Data] = [
            Data("<?php\r\necho 'Привет';\r\n?>\r\n".utf8),
            Data("line1\nline2".utf8),
            Data([0xEF, 0xBB, 0xBF]) + Data("bom\n".utf8),
            SingleByteCodec.encode("Привет\r\nмир", encoding: .windows1251)!,
            Data("old\rmac\r".utf8)
        ]
        for data in samples {
            let decoded = try TextDocumentCodec.decode(data)
            XCTAssertFalse(decoded.text.contains("\r"))
            let back = try TextDocumentCodec.encode(decoded.text, encoding: decoded.encoding, lineEnding: decoded.lineEnding)
            XCTAssertEqual(back, data, "round-trip должен сохранять байты")
        }
        let cp = try TextDocumentCodec.decode(samples[3])
        XCTAssertEqual(cp.encoding, .windows1251)
        XCTAssertEqual(cp.text, "Привет\nмир")
        XCTAssertEqual(try TextDocumentCodec.decode(samples[0]).lineEnding, .crlf)
    }

    func testMixedLineEndingsAndBinary() throws {
        let d = try TextDocumentCodec.decode(Data("a\r\nb\nc\r\n".utf8))
        XCTAssertTrue(d.hadMixedLineEndings)
        XCTAssertEqual(d.lineEnding, .crlf)
        XCTAssertThrowsError(try TextDocumentCodec.decode(Data([0x89, 0x50, 0x4E, 0x47, 0x00, 0x01]))) {
            XCTAssertEqual($0 as? TextDocumentError, .binary)
        }
        XCTAssertThrowsError(try TextDocumentCodec.encode("emoji 😀", encoding: .windows1251, lineEnding: .lf))
        let utf16 = try TextDocumentCodec.encode("Ж\n", encoding: .utf16LE, lineEnding: .crlf)
        XCTAssertEqual(try TextDocumentCodec.decode(utf16).text, "Ж\n")
    }

    func kinds(_ text: String, _ lang: SyntaxLanguage) -> [String: SyntaxTokenKind] {
        let ns = text as NSString
        var m: [String: SyntaxTokenKind] = [:]
        for t in SyntaxHighlighter.shared.tokens(for: text, language: lang) { m[ns.substring(with: t.range)] = t.kind }
        return m
    }

    func testPHPHighlighting() {
        let src = """
        <html><body class="main">
        <?php
        // комментарий с "кавычками"
        $name = "Мир // не комментарий";
        echo strtoupper($name) . 42;
        ?>
        </body></html>
        """
        let k = kinds(src, .php)
        XCTAssertEqual(k["// комментарий с \"кавычками\""], .comment)
        XCTAssertEqual(k["\"Мир // не комментарий\""], .string)
        XCTAssertEqual(k["$name"], .variable)
        XCTAssertEqual(k["echo"], .keyword)
        XCTAssertEqual(k["42"], .number)
        XCTAssertEqual(k["strtoupper"], .function)
        XCTAssertEqual(k["<?php"], .meta)
        XCTAssertEqual(k["<html"], .tag)
        XCTAssertEqual(k["class"], .attribute)
        XCTAssertEqual(k["\"main\""], .string)
    }

    func testOtherLanguages() {
        XCTAssertEqual(kinds("a { color: #fff; margin: 10px }", .css)["color"], .property)
        XCTAssertEqual(kinds("{\"key\": true, \"n\": 1.5}", .json)["true"], .constant)
        XCTAssertEqual(kinds("const x = `t`; // c", .javascript)["const"], .keyword)
        let ht = kinds("# Комментарий\nRewriteEngine On\nRewriteRule ^(.*)$ index.php [L,QSA]\n", .config)
        XCTAssertEqual(ht["# Комментарий"], .comment)
        XCTAssertEqual(ht["RewriteEngine"], .keyword)
        XCTAssertEqual(ht["[L,QSA]"], .meta)
        XCTAssertEqual(SyntaxLanguage.detect(fileName: ".htaccess"), .config)
        XCTAssertEqual(SyntaxLanguage.detect(fileName: "index.PHP"), .php)
        XCTAssertTrue(SyntaxLanguage.isEditable(fileName: ".htaccess"))
        XCTAssertFalse(SyntaxLanguage.isEditable(fileName: "photo.jpg"))
        let html = kinds("<script>var a = 1;</script><style>p { color: red }</style>", .html)
        XCTAssertEqual(html["var"], .keyword)
        XCTAssertEqual(html["color"], .property)
    }

    func testPluralAndBytes() {
        XCTAssertEqual(RussianPlural.files(1), "файл")
        XCTAssertEqual(RussianPlural.files(3), "файла")
        XCTAssertEqual(RussianPlural.files(11), "файлов")
        XCTAssertEqual(RussianPlural.files(21), "файл")
        XCTAssertEqual(ByteFormatter.string(512), "512 Б")
        XCTAssertEqual(ByteFormatter.string(1536), "1,50 КБ")
        XCTAssertEqual(ByteFormatter.string(150 * 1024 * 1024), "150 МБ")
    }

    func testCodecIAC() throws {
        let c = FTPTextCodec(.windows1251)
        XCTAssertEqual(try c.encode("я"), Data([0xFF, 0xFF]))
        let c2 = FTPTextCodec(.windows1251, escapeIAC: false)
        XCTAssertEqual(try c2.encode("я"), Data([0xFF]))
        XCTAssertEqual(c.decode(Data([0xCF, 0xF0, 0xE8])), "При")
        let auto = FTPTextCodec(.auto)
        XCTAssertEqual(auto.decode(Data("ок".utf8)), "ок")
        XCTAssertEqual(auto.decode(Data([0xCF, 0xF0, 0xE8])), "При")
        XCTAssertEqual(auto.effective, .windows1251)
        XCTAssertEqual(SingleByteCodec.decode(SingleByteCodec.encode("Ёлка", encoding: .koi8r)!, encoding: .koi8r), "Ёлка")
    }
}
