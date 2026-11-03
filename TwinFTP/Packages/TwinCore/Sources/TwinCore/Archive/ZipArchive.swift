import Foundation
import CZlib

public enum ZipError: Error, LocalizedError, Equatable {
    case notAZip
    case corrupted(String)
    case unsupportedMethod(Int)
    case passwordRequired
    case wrongPassword
    case encryptionUnsupported(String)
    case unsafePath(String)
    case tooLarge(String)
    case suspiciousCompression(String)
    case notEnoughSpace(needed: Int64, available: Int64)
    case cancelled
    case io(String)

    public var errorDescription: String? {
        switch self {
        case .notAZip: return "Файл не является ZIP-архивом или повреждён."
        case .corrupted(let s): return "Архив повреждён: \(s)"
        case .unsupportedMethod(let m): return "Метод сжатия \(m) не поддерживается (поддерживаются Store и Deflate)."
        case .passwordRequired: return "Архив защищён паролем."
        case .wrongPassword: return "Неверный пароль архива."
        case .encryptionUnsupported(let s): return "Шифрование не поддерживается: \(s)"
        case .unsafePath(let p): return "Небезопасный путь в архиве: \(p)"
        case .tooLarge(let s): return "Архив слишком большой: \(s)"
        case .suspiciousCompression(let n): return "Подозрительно высокая степень сжатия у «\(n)» (возможна zip-бомба). Распаковка остановлена."
        case .notEnoughSpace(let need, let avail):
            return "Недостаточно места: требуется \(ByteFormatter.string(need)), доступно \(ByteFormatter.string(avail))."
        case .cancelled: return "Операция отменена."
        case .io(let s): return s
        }
    }
}

public enum ZipEncryption: Equatable, Sendable {
    case none
    case traditional
    case aes(strength: Int, actualMethod: Int)
}

public struct ZipEntry: Identifiable, Sendable, Hashable {
    public var id: String { path + "#\(localHeaderOffset)" }
    public let path: String
    public let isDirectory: Bool
    public let isSymlink: Bool
    public let method: Int
    public let flags: UInt16
    public let crc32: UInt32
    public let compressedSize: Int64
    public let uncompressedSize: Int64
    public let localHeaderOffset: Int64
    public let modified: Date?
    public let unixMode: Int?
    public let encryption: ZipEncryption

    public var isEncrypted: Bool { encryption != .none }
    public var name: String { PathUtils.lastComponent("/" + path) }

    public static func == (a: ZipEntry, b: ZipEntry) -> Bool { a.id == b.id }
    public func hash(into h: inout Hasher) { h.combine(id) }
}

/// Ограничения распаковки (защита от zip-бомб и переполнения диска).
public struct ZipLimits: Sendable {
    public var maxEntries = 200_000
    public var maxTotalUncompressed: Int64 = 32 * 1024 * 1024 * 1024
    /// Максимальный коэффициент сжатия для крупных файлов (> 10 МБ).
    /// Deflate физически не сжимает лучше ~1032:1, поэтому больший коэффициент означает подделанный заголовок.
    public var maxRatio: Double = 1100
    public var maxPathDepth = 64
    public init() {}
}

public struct ZipExtractionReport: Sendable {
    public var extractedFiles = 0
    public var createdDirectories = 0
    public var skipped: [String] = []
    public var renamed: [(from: String, to: String)] = []
    public var totalBytes: Int64 = 0
    /// Пути верхнего уровня, созданные распаковкой.
    public var topLevelItems: [String] = []
}

public enum ZipExistingFilePolicy: String, Sendable, CaseIterable {
    case replace, skip, rename
    public var title: String {
        switch self {
        case .replace: return "Заменять"
        case .skip: return "Пропускать"
        case .rename: return "Переименовывать"
        }
    }
}

/// Чтение ZIP-архивов: Store/Deflate, ZIP64, имена в UTF-8/CP866, ZipCrypto и WinZip AES (на iOS).
public final class ZipArchiveReader {
    public let url: URL
    public private(set) var entries: [ZipEntry] = []
    private let handle: FileHandle
    private let fileSize: Int64

    public init(url: URL) throws {
        self.url = url
        guard let h = FileHandle(forReadingAtPath: url.path) else { throw ZipError.io("Не удалось открыть архив.") }
        handle = h
        fileSize = Int64((try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)??.int64Value ?? 0)
        try readCentralDirectory()
    }

    deinit { try? handle.close() }

    public var totalUncompressedSize: Int64 { entries.reduce(0) { $0 + $1.uncompressedSize } }
    public var hasEncryptedEntries: Bool { entries.contains { $0.isEncrypted } }

    // MARK: Чтение структуры

    private func read(at offset: Int64, count: Int) throws -> Data {
        guard offset >= 0, offset + Int64(count) <= fileSize else { throw ZipError.corrupted("выход за пределы файла") }
        try handle.seek(toOffset: UInt64(offset))
        let d = try handle.read(upToCount: count) ?? Data()
        guard d.count == count else { throw ZipError.corrupted("неожиданный конец файла") }
        return d
    }

    private func readCentralDirectory() throws {
        guard fileSize >= 22 else { throw ZipError.notAZip }
        let tailSize = Int(min(fileSize, 65_557))
        let tail = try read(at: fileSize - Int64(tailSize), count: tailSize)
        var eocdPos: Int?
        var i = tail.count - 22
        while i >= 0 {
            if tail.u32(i) == 0x06054b50 { eocdPos = i; break }
            i -= 1
        }
        guard let eocd = eocdPos else { throw ZipError.notAZip }
        var entryCount = Int64(tail.u16(eocd + 10))
        var cdSize = Int64(tail.u32(eocd + 12))
        var cdOffset = Int64(tail.u32(eocd + 16))
        let eocdAbsolute = fileSize - Int64(tailSize) + Int64(eocd)

        // ZIP64
        if eocdAbsolute >= 20 {
            let loc = try read(at: eocdAbsolute - 20, count: 20)
            if loc.u32(0) == 0x07064b50 {
                let z64Offset = Int64(loc.u64(8))
                let z64 = try read(at: z64Offset, count: 56)
                guard z64.u32(0) == 0x06064b50 else { throw ZipError.corrupted("ZIP64 EOCD") }
                entryCount = Int64(z64.u64(32))
                cdSize = Int64(z64.u64(40))
                cdOffset = Int64(z64.u64(48))
            }
        }
        guard entryCount <= 1_000_000, cdSize <= 512 * 1024 * 1024 else { throw ZipError.tooLarge("слишком много записей") }
        let cd = try read(at: cdOffset, count: Int(cdSize))
        var pos = 0
        var result: [ZipEntry] = []
        result.reserveCapacity(Int(entryCount))
        while pos + 46 <= cd.count && cd.u32(pos) == 0x02014b50 {
            let versionMadeBy = cd.u16(pos + 4)
            let flags = cd.u16(pos + 8)
            var method = Int(cd.u16(pos + 10))
            let dosTime = cd.u16(pos + 12)
            let dosDate = cd.u16(pos + 14)
            let crc = cd.u32(pos + 16)
            var compSize = Int64(cd.u32(pos + 20))
            var uncompSize = Int64(cd.u32(pos + 24))
            let nameLen = Int(cd.u16(pos + 28))
            let extraLen = Int(cd.u16(pos + 30))
            let commentLen = Int(cd.u16(pos + 32))
            let externalAttrs = cd.u32(pos + 38)
            var localOffset = Int64(cd.u32(pos + 42))
            guard pos + 46 + nameLen + extraLen + commentLen <= cd.count else { throw ZipError.corrupted("центральный каталог") }
            let nameData = cd.subdata(in: (pos + 46)..<(pos + 46 + nameLen))
            let extra = cd.subdata(in: (pos + 46 + nameLen)..<(pos + 46 + nameLen + extraLen))
            var name = ZipNameDecoding.decode(nameData, utf8Flag: flags & 0x0800 != 0)
            var encryption: ZipEncryption = flags & 1 != 0 ? .traditional : .none

            // Разбор дополнительных полей
            var e = 0
            while e + 4 <= extra.count {
                let tag = extra.u16(e)
                let size = Int(extra.u16(e + 2))
                guard e + 4 + size <= extra.count else { break }
                let body = extra.subdata(in: (e + 4)..<(e + 4 + size))
                switch tag {
                case 0x0001: // ZIP64
                    var p = 0
                    if uncompSize == 0xFFFFFFFF, p + 8 <= body.count { uncompSize = Int64(body.u64(p)); p += 8 }
                    if compSize == 0xFFFFFFFF, p + 8 <= body.count { compSize = Int64(body.u64(p)); p += 8 }
                    if localOffset == 0xFFFFFFFF, p + 8 <= body.count { localOffset = Int64(body.u64(p)); p += 8 }
                case 0x7075: // Info-ZIP Unicode Path
                    if body.count > 5 {
                        name = String(decoding: body.subdata(in: 5..<body.count), as: UTF8.self)
                    }
                case 0x9901: // WinZip AES
                    if body.count >= 7 {
                        let strength = Int(body[body.startIndex + 4])
                        let actual = Int(body.u16(5))
                        encryption = .aes(strength: strength, actualMethod: actual)
                        method = actual
                    }
                default: break
                }
                e += 4 + size
            }
            let hostOS = versionMadeBy >> 8
            var unixMode: Int?
            var isSymlink = false
            if hostOS == 3 || hostOS == 19 {
                let mode = Int(externalAttrs >> 16)
                if mode != 0 {
                    unixMode = mode & 0o7777
                    isSymlink = (mode & 0o170000) == 0o120000
                }
            }
            let normalizedName = name.replacingOccurrences(of: "\\", with: "/")
            let isDir = normalizedName.hasSuffix("/") || (externalAttrs & 0x10) != 0 && uncompSize == 0
            result.append(ZipEntry(path: normalizedName, isDirectory: isDir, isSymlink: isSymlink,
                                   method: method, flags: flags, crc32: crc,
                                   compressedSize: compSize, uncompressedSize: uncompSize,
                                   localHeaderOffset: localOffset,
                                   modified: Self.dosDate(date: dosDate, time: dosTime),
                                   unixMode: unixMode, encryption: encryption))
            pos += 46 + nameLen + extraLen + commentLen
        }
        guard !result.isEmpty || entryCount == 0 else { throw ZipError.corrupted("пустой центральный каталог") }
        entries = result
    }

    static func dosDate(date: UInt16, time: UInt16) -> Date? {
        var c = DateComponents()
        c.year = Int((date >> 9) & 0x7F) + 1980
        c.month = Int((date >> 5) & 0x0F)
        c.day = Int(date & 0x1F)
        c.hour = Int((time >> 11) & 0x1F)
        c.minute = Int((time >> 5) & 0x3F)
        c.second = Int(time & 0x1F) * 2
        guard (1...12).contains(c.month!), (1...31).contains(c.day!) else { return nil }
        return Calendar(identifier: .gregorian).date(from: c)
    }

    // MARK: Безопасные пути

    /// Возвращает безопасный относительный путь или nil (Zip Slip, абсолютные пути, "..").
    public static func sanitize(_ path: String, maxDepth: Int = 64) -> String? {
        var p = path.replacingOccurrences(of: "\\", with: "/")
        // Буква диска Windows "C:"
        if p.count >= 2, p[p.index(after: p.startIndex)] == ":" { p = String(p.dropFirst(2)) }
        var comps: [String] = []
        for c in p.split(separator: "/", omittingEmptySubsequences: true) {
            if c == "." { continue }
            if c == ".." { return nil }
            if c.contains("\0") { return nil }
            comps.append(String(c))
        }
        guard !comps.isEmpty, comps.count <= maxDepth else { return nil }
        return comps.joined(separator: "/")
    }

    // MARK: Извлечение

    private func dataOffset(for entry: ZipEntry) throws -> Int64 {
        let header = try read(at: entry.localHeaderOffset, count: 30)
        guard header.u32(0) == 0x04034b50 else { throw ZipError.corrupted("локальный заголовок «\(entry.name)»") }
        let nameLen = Int64(header.u16(26))
        let extraLen = Int64(header.u16(28))
        return entry.localHeaderOffset + 30 + nameLen + extraLen
    }

    /// Потоковое извлечение записи: `sink` получает распакованные блоки.
    public func extract(_ entry: ZipEntry, password: String?, limits: ZipLimits = ZipLimits(),
                        isCancelled: () -> Bool = { false }, sink: (Data) throws -> Void) throws {
        guard !entry.isDirectory else { return }
        guard entry.method == 0 || entry.method == 8 else { throw ZipError.unsupportedMethod(entry.method) }
        let dataStart = try dataOffset(for: entry)
        var offset = dataStart
        var remaining = entry.compressedSize
        var zipCrypto: TraditionalZipDecryptor?
        var aes: WinZipAESDecryptor?

        switch entry.encryption {
        case .none: break
        case .traditional:
            guard let password, !password.isEmpty else { throw ZipError.passwordRequired }
            var dec = TraditionalZipDecryptor(password: Data(password.utf8))
            var header = try read(at: offset, count: 12)
            dec.decrypt(&header)
            let check = header[header.startIndex + 11]
            let expected = entry.flags & 0x08 != 0 ? UInt8(truncatingIfNeeded: (Self.dosTime(of: entry) >> 8)) : UInt8(entry.crc32 >> 24)
            if check != expected {
                // Повтор с паролем в кодировке CP866/Latin1 не делаем — сообщаем о неверном пароле.
                throw ZipError.wrongPassword
            }
            zipCrypto = dec
            offset += 12
            remaining -= 12
        case .aes(let strength, _):
            guard WinZipAESDecryptor.isSupported else { throw ZipError.encryptionUnsupported("AES на этой платформе") }
            guard let password, !password.isEmpty else { throw ZipError.passwordRequired }
            let saltLen = WinZipAESDecryptor.saltLength(strength: strength)
            let salt = try read(at: offset, count: saltLen)
            let verifier = try read(at: offset + Int64(saltLen), count: 2)
            guard let dec = WinZipAESDecryptor(password: Data(password.utf8), salt: salt, verifier: verifier, strength: strength) else {
                throw ZipError.wrongPassword
            }
            aes = dec
            offset += Int64(saltLen + 2)
            remaining -= Int64(saltLen + 2 + 10)
        }
        guard remaining >= 0 else { throw ZipError.corrupted("размер записи «\(entry.name)»") }

        var crc = CRC32()
        var produced: Int64 = 0
        let declared = entry.uncompressedSize
        func emit(_ chunk: Data) throws {
            produced += Int64(chunk.count)
            if produced > declared {
                throw ZipError.suspiciousCompression(entry.name)
            }
            crc.update(chunk)
            try sink(chunk)
        }

        let inflater: Inflater? = entry.method == 8 ? try Inflater() : nil
        let chunkSize = 256 * 1024
        try handle.seek(toOffset: UInt64(offset))
        while remaining > 0 {
            if isCancelled() { throw ZipError.cancelled }
            let n = Int(min(Int64(chunkSize), remaining))
            guard var chunk = try handle.read(upToCount: n), chunk.count == n else { throw ZipError.corrupted("обрезанные данные «\(entry.name)»") }
            remaining -= Int64(n)
            if zipCrypto != nil { zipCrypto!.decrypt(&chunk) }
            if let aes { aes.decrypt(&chunk) }
            if let inflater {
                try inflater.process(chunk, output: emit)
            } else {
                try emit(chunk)
            }
        }
        if let inflater { try inflater.finish(output: emit) }
        if let aes {
            let code = try read(at: dataStart + entry.compressedSize - 10, count: 10)
            guard aes.verify(authCode: code) else { throw ZipError.corrupted("проверка подлинности AES «\(entry.name)»") }
        }
        guard produced == declared else { throw ZipError.corrupted("размер «\(entry.name)»: \(produced) вместо \(declared)") }
        // В AE-2 CRC не хранится (0).
        if case .aes = entry.encryption, entry.crc32 == 0 { return }
        guard crc.value == entry.crc32 else { throw ZipError.corrupted("контрольная сумма «\(entry.name)»") }
    }

    static func dosTime(of entry: ZipEntry) -> UInt16 {
        guard let d = entry.modified else { return 0 }
        let c = Calendar(identifier: .gregorian).dateComponents([.hour, .minute, .second], from: d)
        return UInt16((c.hour! << 11) | (c.minute! << 5) | (c.second! / 2))
    }

    /// Чтение записи в память (для предпросмотра) с ограничением размера.
    public func data(for entry: ZipEntry, password: String?, maxSize: Int64 = 50 * 1024 * 1024) throws -> Data {
        guard entry.uncompressedSize <= maxSize else { throw ZipError.tooLarge(entry.name) }
        var out = Data()
        try extract(entry, password: password) { out.append($0) }
        return out
    }

    /// Проверка ограничений до начала распаковки.
    public func validate(entries selected: [ZipEntry], destination: String, limits: ZipLimits) throws {
        guard selected.count <= limits.maxEntries else { throw ZipError.tooLarge("более \(limits.maxEntries) записей") }
        let total = selected.reduce(Int64(0)) { $0 + $1.uncompressedSize }
        guard total <= limits.maxTotalUncompressed else { throw ZipError.tooLarge(ByteFormatter.string(total)) }
        for e in selected where e.uncompressedSize > 10 * 1024 * 1024 {
            let ratio = Double(e.uncompressedSize) / Double(max(1, e.compressedSize))
            if ratio > limits.maxRatio { throw ZipError.suspiciousCompression(e.name) }
        }
        if let free = LocalFileSystem().freeSpace(at: destination), total + 50 * 1024 * 1024 > free {
            throw ZipError.notEnoughSpace(needed: total, available: free)
        }
    }

    /// Распаковка (всех или выбранных записей) в папку.
    public func extractAll(to destination: String, entries selected: [ZipEntry]? = nil, password: String?,
                           policy: ZipExistingFilePolicy = .rename, limits: ZipLimits = ZipLimits(),
                           isCancelled: () -> Bool = { false },
                           progress: (Int64, Int64, String) -> Void = { _, _, _ in }) throws -> ZipExtractionReport {
        let list = selected ?? entries
        try validate(entries: list, destination: destination, limits: limits)
        let fm = FileManager.default
        try fm.createDirectory(atPath: destination, withIntermediateDirectories: true)
        let destRoot = PathUtils.normalize(destination)
        var report = ZipExtractionReport()
        let total = list.reduce(Int64(0)) { $0 + $1.uncompressedSize }
        var done: Int64 = 0
        var topLevel = Set<String>()
        // Отображение переименованных каталогов верхнего уровня при policy == .rename
        var renamedRoots: [String: String] = [:]

        for entry in list {
            if isCancelled() { throw ZipError.cancelled }
            guard let rel = Self.sanitize(entry.path, maxDepth: limits.maxPathDepth) else {
                report.skipped.append(entry.path)
                continue
            }
            if entry.isSymlink {
                report.skipped.append(entry.path + " (символьная ссылка)")
                continue
            }
            var comps = rel.split(separator: "/").map(String.init)
            if policy == .rename, comps.count > 1 || entry.isDirectory {
                let root = comps[0]
                if let mapped = renamedRoots[root] { comps[0] = mapped }
                else if fm.fileExists(atPath: PathUtils.child(destRoot, root)) && !topLevel.contains(root) {
                    let existing = Set((try? fm.contentsOfDirectory(atPath: destRoot)) ?? [])
                    let newRoot = PathUtils.uniqueName(for: root, existing: existing)
                    renamedRoots[root] = newRoot
                    report.renamed.append((root, newRoot))
                    comps[0] = newRoot
                } else {
                    renamedRoots[root] = root
                }
            }
            var target = PathUtils.normalize(destRoot + "/" + comps.joined(separator: "/"))
            guard PathUtils.isDescendant(target, of: destRoot), target != destRoot else {
                report.skipped.append(entry.path)
                continue
            }
            topLevel.insert(comps[0])
            if entry.isDirectory {
                if !fm.fileExists(atPath: target) {
                    try fm.createDirectory(atPath: target, withIntermediateDirectories: true)
                    report.createdDirectories += 1
                }
                continue
            }
            let parent = PathUtils.parent(target)
            // Защита от символических ссылок внутри пути назначения (созданных ранее не нами).
            if let real = try? fm.destinationOfSymbolicLink(atPath: parent), !real.isEmpty {
                report.skipped.append(entry.path)
                continue
            }
            try fm.createDirectory(atPath: parent, withIntermediateDirectories: true)
            if fm.fileExists(atPath: target) {
                switch policy {
                case .skip:
                    report.skipped.append(entry.path)
                    done += entry.uncompressedSize
                    continue
                case .rename:
                    let existing = Set((try? fm.contentsOfDirectory(atPath: parent)) ?? [])
                    let newName = PathUtils.uniqueName(for: PathUtils.lastComponent(target), existing: existing)
                    report.renamed.append((PathUtils.lastComponent(target), newName))
                    target = PathUtils.child(parent, newName)
                case .replace:
                    break
                }
            }
            let temp = PathUtils.child(parent, TransferEngine.partName(for: PathUtils.lastComponent(target)))
            try? fm.removeItem(atPath: temp)
            guard fm.createFile(atPath: temp, contents: nil), let out = FileHandle(forWritingAtPath: temp) else {
                throw ZipError.io("Не удалось создать «\(PathUtils.lastComponent(target))».")
            }
            do {
                let base = done
                var written: Int64 = 0
                try extract(entry, password: password, limits: limits, isCancelled: isCancelled) { chunk in
                    try out.write(contentsOf: chunk)
                    written += Int64(chunk.count)
                    progress(base + written, total, entry.name)
                }
                try out.close()
            } catch {
                try? out.close()
                try? fm.removeItem(atPath: temp)
                throw error
            }
            if fm.fileExists(atPath: target) {
                try LocalFileSystem.replaceItem(at: target, with: temp)
            } else {
                try fm.moveItem(atPath: temp, toPath: target)
            }
            if let date = entry.modified { try? fm.setAttributes([.modificationDate: date], ofItemAtPath: target) }
            if let mode = entry.unixMode, mode & 0o400 != 0 {
                try? fm.setAttributes([.posixPermissions: mode & 0o755 | 0o600], ofItemAtPath: target)
            }
            done += entry.uncompressedSize
            report.extractedFiles += 1
            report.totalBytes += entry.uncompressedSize
            progress(done, total, entry.name)
        }
        report.topLevelItems = topLevel.sorted().map { PathUtils.child(destRoot, renamedRoots[$0] ?? $0) }
        return report
    }
}

/// Потоковая распаковка raw deflate через zlib.
/// z_stream размещается в куче: zlib хранит обратный указатель и не допускает копирования структуры.
final class Inflater {
    private let stream: UnsafeMutablePointer<z_stream>
    private var finished = false
    private var ended = false

    init() throws {
        stream = .allocate(capacity: 1)
        stream.initialize(to: z_stream())
        let rc = inflateInit2_(stream, -MAX_WBITS, zlibVersion(), Int32(MemoryLayout<z_stream>.size))
        guard rc == Z_OK else {
            stream.deallocate()
            throw ZipError.corrupted("zlib init \(rc)")
        }
    }

    deinit {
        if !ended { inflateEnd(stream) }
        stream.deallocate()
    }

    func process(_ input: Data, output: (Data) throws -> Void) throws {
        if finished { return }
        var input = input
        let outSize = 256 * 1024
        var outBuf = [UInt8](repeating: 0, count: outSize)
        try input.withUnsafeMutableBytes { (inRaw: UnsafeMutableRawBufferPointer) in
            stream.pointee.next_in = inRaw.bindMemory(to: Bytef.self).baseAddress
            stream.pointee.avail_in = uInt(inRaw.count)
            while true {
                let produced: Int = try outBuf.withUnsafeMutableBytes { outRaw in
                    stream.pointee.next_out = outRaw.bindMemory(to: Bytef.self).baseAddress
                    stream.pointee.avail_out = uInt(outSize)
                    let rc = inflate(stream, Z_NO_FLUSH)
                    if rc == Z_STREAM_END { finished = true }
                    else if rc != Z_OK && rc != Z_BUF_ERROR { throw ZipError.corrupted("ошибка распаковки (zlib \(rc))") }
                    return outSize - Int(stream.pointee.avail_out)
                }
                if produced > 0 { try output(Data(outBuf[0..<produced])) }
                if finished { break }
                // Выход полностью заполнен — возможно, есть ещё данные; иначе вход исчерпан.
                if produced < outSize && stream.pointee.avail_in == 0 { break }
                if produced == 0 { break }
            }
        }
    }

    func finish(output: (Data) throws -> Void) throws {
        if !finished { try process(Data(), output: output) }
        inflateEnd(stream)
        ended = true
    }
}

/// Потоковое сжатие raw deflate.
final class Deflater {
    private let stream: UnsafeMutablePointer<z_stream>
    private var ended = false

    init(level: Int32 = 6) throws {
        stream = .allocate(capacity: 1)
        stream.initialize(to: z_stream())
        let rc = deflateInit2_(stream, level, Z_DEFLATED, -MAX_WBITS, 8, Z_DEFAULT_STRATEGY, zlibVersion(), Int32(MemoryLayout<z_stream>.size))
        guard rc == Z_OK else {
            stream.deallocate()
            throw ZipError.io("zlib deflate init \(rc)")
        }
    }

    deinit {
        if !ended { deflateEnd(stream) }
        stream.deallocate()
    }

    func process(_ input: Data, finish: Bool, output: (Data) throws -> Void) throws {
        var input = input
        let outSize = 256 * 1024
        var outBuf = [UInt8](repeating: 0, count: outSize)
        try input.withUnsafeMutableBytes { (inRaw: UnsafeMutableRawBufferPointer) in
            stream.pointee.next_in = inRaw.bindMemory(to: Bytef.self).baseAddress
            stream.pointee.avail_in = uInt(inRaw.count)
            var done = false
            repeat {
                let produced: Int = try outBuf.withUnsafeMutableBytes { outRaw in
                    stream.pointee.next_out = outRaw.bindMemory(to: Bytef.self).baseAddress
                    stream.pointee.avail_out = uInt(outSize)
                    let rc = deflate(stream, finish ? Z_FINISH : Z_NO_FLUSH)
                    if rc == Z_STREAM_END { done = true }
                    else if rc != Z_OK && rc != Z_BUF_ERROR { throw ZipError.io("ошибка сжатия (zlib \(rc))") }
                    return outSize - Int(stream.pointee.avail_out)
                }
                if produced > 0 { try output(Data(outBuf[0..<produced])) }
                if !finish && stream.pointee.avail_out != 0 { break }
            } while !done
        }
        if finish {
            deflateEnd(stream)
            ended = true
        }
    }
}

extension Data {
    func u16(_ offset: Int) -> UInt16 {
        let i = startIndex + offset
        return UInt16(self[i]) | (UInt16(self[i + 1]) << 8)
    }
    func u32(_ offset: Int) -> UInt32 {
        let i = startIndex + offset
        return UInt32(self[i]) | (UInt32(self[i + 1]) << 8) | (UInt32(self[i + 2]) << 16) | (UInt32(self[i + 3]) << 24)
    }
    func u64(_ offset: Int) -> UInt64 {
        UInt64(u32(offset)) | (UInt64(u32(offset + 4)) << 32)
    }
    mutating func append16(_ v: UInt16) { append(UInt8(v & 0xFF)); append(UInt8(v >> 8)) }
    mutating func append32(_ v: UInt32) { for s in stride(from: 0, to: 32, by: 8) { append(UInt8((v >> UInt32(s)) & 0xFF)) } }
    mutating func append64(_ v: UInt64) { for s in stride(from: 0, to: 64, by: 8) { append(UInt8((v >> UInt64(s)) & 0xFF)) } }
}
