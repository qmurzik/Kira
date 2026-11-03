import Foundation

/// Создание ZIP-архивов (Deflate/Store, имена в UTF-8, ZIP64 для больших файлов).
public enum ZipArchiveWriter {
    struct CentralRecord {
        var name: Data
        var method: UInt16
        var dosTime: UInt16
        var dosDate: UInt16
        var crc: UInt32
        var compressed: UInt64
        var uncompressed: UInt64
        var offset: UInt64
        var isDirectory: Bool
        var mode: UInt32
    }

    /// Расширения уже сжатых форматов — для них используется Store.
    static let storedExtensions: Set<String> = ["zip", "jpg", "jpeg", "png", "gif", "webp", "heic", "mp4", "mov", "m4a",
                                                "mp3", "aac", "gz", "tgz", "bz2", "xz", "7z", "rar", "woff", "woff2", "pdf", "ipa", "apk"]

    /// Собирает список (локальный путь, путь в архиве) для файлов и папок.
    public static func collect(_ paths: [String]) throws -> [(source: String, archivePath: String, isDirectory: Bool)] {
        let lfs = LocalFileSystem()
        var result: [(String, String, Bool)] = []
        var usedTop = Set<String>()
        for path in paths {
            var top = PathUtils.lastComponent(path)
            top = PathUtils.uniqueName(for: top, existing: usedTop)
            usedTop.insert(top)
            if lfs.isDirectory(path) {
                result.append((path, top + "/", true))
                for item in try lfs.walk(path) {
                    guard let rel = PathUtils.relative(item.path, to: path) else { continue }
                    if item.kind == .directory {
                        result.append((item.path, top + "/" + rel + "/", true))
                    } else if item.kind == .file {
                        result.append((item.path, top + "/" + rel, false))
                    }
                }
            } else {
                result.append((path, top, false))
            }
        }
        return result
    }

    public static func create(at destination: URL, from paths: [String],
                              isCancelled: () -> Bool = { false },
                              progress: (Int64, Int64, String) -> Void = { _, _, _ in }) throws {
        let entries = try collect(paths)
        let fm = FileManager.default
        let total = entries.reduce(Int64(0)) { acc, e in
            e.isDirectory ? acc : acc + ((try? fm.attributesOfItem(atPath: e.source)[.size] as? NSNumber)??.int64Value ?? 0)
        }
        let temp = destination.deletingLastPathComponent()
            .appendingPathComponent(TransferEngine.partName(for: destination.lastPathComponent))
        try? fm.removeItem(at: temp)
        guard fm.createFile(atPath: temp.path, contents: nil), let out = FileHandle(forWritingAtPath: temp.path) else {
            throw ZipError.io("Не удалось создать архив.")
        }
        var records: [CentralRecord] = []
        var position: UInt64 = 0
        var done: Int64 = 0

        func write(_ d: Data) throws {
            try out.write(contentsOf: d)
            position += UInt64(d.count)
        }

        do {
            for entry in entries {
                if isCancelled() { throw ZipError.cancelled }
                let attrs = try? fm.attributesOfItem(atPath: entry.source)
                let mdate = attrs?[.modificationDate] as? Date ?? Date()
                let (dosTime, dosDate) = dosDateTime(mdate)
                let nameData = Data(entry.archivePath.utf8)
                let mode = UInt32((attrs?[.posixPermissions] as? NSNumber)?.intValue ?? (entry.isDirectory ? 0o755 : 0o644))
                let headerOffset = position

                if entry.isDirectory {
                    var h = Data()
                    h.append32(0x04034b50); h.append16(20); h.append16(0x0800); h.append16(0)
                    h.append16(dosTime); h.append16(dosDate); h.append32(0); h.append32(0); h.append32(0)
                    h.append16(UInt16(nameData.count)); h.append16(0)
                    h.append(nameData)
                    try write(h)
                    records.append(CentralRecord(name: nameData, method: 0, dosTime: dosTime, dosDate: dosDate, crc: 0,
                                                 compressed: 0, uncompressed: 0, offset: headerOffset, isDirectory: true,
                                                 mode: mode | 0o040000))
                    continue
                }

                let size = UInt64((attrs?[.size] as? NSNumber)?.int64Value ?? 0)
                let zip64 = size >= 0xFFFF_0000
                let ext = PathUtils.splitExtension(entry.archivePath).ext.dropFirst().lowercased()
                let method: UInt16 = (storedExtensions.contains(String(ext)) || size == 0) ? 0 : 8

                var h = Data()
                h.append32(0x04034b50); h.append16(zip64 ? 45 : 20); h.append16(0x0800); h.append16(method)
                h.append16(dosTime); h.append16(dosDate)
                h.append32(0)
                h.append32(zip64 ? 0xFFFFFFFF : 0); h.append32(zip64 ? 0xFFFFFFFF : 0)
                h.append16(UInt16(nameData.count)); h.append16(zip64 ? 20 : 0)
                h.append(nameData)
                if zip64 { h.append16(0x0001); h.append16(16); h.append64(0); h.append64(0) }
                try write(h)

                guard let input = FileHandle(forReadingAtPath: entry.source) else {
                    throw ZipError.io("Не удалось прочитать «\(PathUtils.lastComponent(entry.source))».")
                }
                defer { try? input.close() }
                var crc = CRC32()
                var compressed: UInt64 = 0
                var uncompressed: UInt64 = 0
                let deflater: Deflater? = method == 8 ? try Deflater() : nil
                while true {
                    if isCancelled() { throw ZipError.cancelled }
                    let chunk = try input.read(upToCount: 256 * 1024) ?? Data()
                    let last = chunk.isEmpty
                    if !chunk.isEmpty {
                        crc.update(chunk)
                        uncompressed += UInt64(chunk.count)
                        done += Int64(chunk.count)
                        progress(done, total, PathUtils.lastComponent(entry.archivePath))
                    }
                    if let deflater {
                        try deflater.process(chunk, finish: last) { d in
                            try write(d)
                            compressed += UInt64(d.count)
                        }
                    } else if !chunk.isEmpty {
                        try write(chunk)
                        compressed += UInt64(chunk.count)
                    }
                    if last { break }
                }
                // Дописываем CRC и размеры в локальный заголовок.
                let end = position
                try out.seek(toOffset: headerOffset + 14)
                var patch = Data()
                patch.append32(crc.value)
                patch.append32(zip64 ? 0xFFFFFFFF : UInt32(compressed))
                patch.append32(zip64 ? 0xFFFFFFFF : UInt32(uncompressed))
                try out.write(contentsOf: patch)
                if zip64 {
                    try out.seek(toOffset: headerOffset + 30 + UInt64(nameData.count) + 4)
                    var z = Data(); z.append64(uncompressed); z.append64(compressed)
                    try out.write(contentsOf: z)
                } else if compressed >= 0xFFFF_FFFF || uncompressed >= 0xFFFF_FFFF {
                    throw ZipError.tooLarge("файл вырос во время упаковки")
                }
                try out.seek(toOffset: end)
                records.append(CentralRecord(name: nameData, method: method, dosTime: dosTime, dosDate: dosDate,
                                             crc: crc.value, compressed: compressed, uncompressed: uncompressed,
                                             offset: headerOffset, isDirectory: false, mode: mode | 0o100000))
            }

            // Центральный каталог
            let cdStart = position
            for r in records {
                let needZip64 = r.compressed >= 0xFFFFFFFF || r.uncompressed >= 0xFFFFFFFF || r.offset >= 0xFFFFFFFF
                var extra = Data()
                if needZip64 {
                    var body = Data()
                    if r.uncompressed >= 0xFFFFFFFF { body.append64(r.uncompressed) }
                    if r.compressed >= 0xFFFFFFFF { body.append64(r.compressed) }
                    if r.offset >= 0xFFFFFFFF { body.append64(r.offset) }
                    extra.append16(0x0001); extra.append16(UInt16(body.count)); extra.append(body)
                }
                var c = Data()
                c.append32(0x02014b50)
                c.append16((3 << 8) | (needZip64 ? 45 : 20)) // создано: UNIX
                c.append16(needZip64 ? 45 : 20)
                c.append16(0x0800)
                c.append16(r.method)
                c.append16(r.dosTime); c.append16(r.dosDate)
                c.append32(r.crc)
                c.append32(r.compressed >= 0xFFFFFFFF ? 0xFFFFFFFF : UInt32(r.compressed))
                c.append32(r.uncompressed >= 0xFFFFFFFF ? 0xFFFFFFFF : UInt32(r.uncompressed))
                c.append16(UInt16(r.name.count)); c.append16(UInt16(extra.count)); c.append16(0)
                c.append16(0); c.append16(0)
                c.append32((r.mode << 16) | (r.isDirectory ? 0x10 : 0))
                c.append32(r.offset >= 0xFFFFFFFF ? 0xFFFFFFFF : UInt32(r.offset))
                c.append(r.name)
                c.append(extra)
                try write(c)
            }
            let cdSize = position - cdStart
            let count = UInt64(records.count)
            if count >= 0xFFFF || cdStart >= 0xFFFFFFFF || cdSize >= 0xFFFFFFFF {
                let z64Start = position
                var z = Data()
                z.append32(0x06064b50); z.append64(44); z.append16((3 << 8) | 45); z.append16(45)
                z.append32(0); z.append32(0); z.append64(count); z.append64(count); z.append64(cdSize); z.append64(cdStart)
                z.append32(0x07064b50); z.append32(0); z.append64(z64Start); z.append32(1)
                try write(z)
            }
            var e = Data()
            e.append32(0x06054b50); e.append16(0); e.append16(0)
            e.append16(count >= 0xFFFF ? 0xFFFF : UInt16(count)); e.append16(count >= 0xFFFF ? 0xFFFF : UInt16(count))
            e.append32(cdSize >= 0xFFFFFFFF ? 0xFFFFFFFF : UInt32(cdSize))
            e.append32(cdStart >= 0xFFFFFFFF ? 0xFFFFFFFF : UInt32(cdStart))
            e.append16(0)
            try write(e)
            try out.close()
            if fm.fileExists(atPath: destination.path) { try fm.removeItem(at: destination) }
            try fm.moveItem(at: temp, to: destination)
        } catch {
            try? out.close()
            try? fm.removeItem(at: temp)
            throw error
        }
    }

    static func dosDateTime(_ date: Date) -> (UInt16, UInt16) {
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let year: Int = max(1980, min(2107, c.year ?? 1980))
        let hour: Int = c.hour ?? 0, minute: Int = c.minute ?? 0, second: Int = c.second ?? 0
        let month: Int = c.month ?? 1, day: Int = c.day ?? 1
        let time: Int = (hour << 11) | (minute << 5) | (second / 2)
        let date: Int = ((year - 1980) << 9) | (month << 5) | day
        return (UInt16(time), UInt16(date))
    }
}
