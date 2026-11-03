import Foundation

/// Состояние файла на сервере в момент открытия (для защиты от перезаписи чужих изменений).
public struct RemoteFileBaseline: Equatable, Sendable {
    public var size: Int64?
    public var modified: Date?
    public init(size: Int64?, modified: Date?) {
        self.size = size
        self.modified = modified
    }
}

/// Загрузка и надёжное сохранение текстового файла на FTP-сервере.
public enum RemoteTextFile {
    /// Скачивает файл целиком (с ограничением размера) и запоминает базовую версию.
    public static func load(_ conn: FTPConnection, path: String,
                            maxSize: Int64 = TextDocumentCodec.maxEditableSize) async throws -> (Data, RemoteFileBaseline) {
        let buffer = Locked(Data())
        try await conn.download(path) { chunk in
            try buffer.withValue { d in
                d.append(chunk)
                if Int64(d.count) > maxSize { throw TextDocumentError.tooLarge(Int64(d.count)) }
            }
        }
        let data = buffer.current
        let date = try? await conn.modificationDate(path)
        return (data, RemoteFileBaseline(size: Int64(data.count), modified: date))
    }

    /// Проверяет, изменился ли файл на сервере относительно базовой версии.
    public static func hasChanged(_ conn: FTPConnection, path: String, baseline: RemoteFileBaseline) async throws -> Bool {
        let size = try await conn.size(path)
        let date = try await conn.modificationDate(path)
        if let s = size, let b = baseline.size, s != b { return true }
        if let d = date, let b = baseline.modified, abs(d.timeIntervalSince(b)) > 1 { return true }
        return false
    }

    /// Сохраняет данные: проверка изменений → загрузка во временный файл → проверка размера →
    /// переименование поверх оригинала → восстановление прав. Возвращает новую базовую версию.
    /// Бросает `FTPError.remoteFileChanged`, если файл изменился на сервере и `force == false`.
    @discardableResult
    public static func save(_ conn: FTPConnection, path: String, data: Data, baseline: RemoteFileBaseline?,
                            mode: Int? = nil, force: Bool = false,
                            progress: ((String) -> Void)? = nil) async throws -> RemoteFileBaseline {
        if !force, let baseline {
            progress?("Проверка сервера…")
            if try await hasChanged(conn, path: path, baseline: baseline) {
                throw FTPError.remoteFileChanged(PathUtils.lastComponent(path))
            }
        }
        progress?("Загрузка на сервер…")
        let dir = PathUtils.parent(path)
        let temp = PathUtils.child(dir, TransferEngine.partName(for: PathUtils.lastComponent(path)))
        let offset = Locked(0)
        try await conn.upload(temp) {
            offset.withValue { off -> Data? in
                if off >= data.count { return nil }
                let end = min(data.count, off + 128 * 1024)
                defer { off = end }
                return data.subdata(in: off..<end)
            }
        }
        progress?("Проверка…")
        if let uploaded = try await conn.size(temp), uploaded != Int64(data.count) {
            try? await conn.deleteFile(temp)
            throw FTPError.verificationFailed("на сервере \(uploaded) байт вместо \(data.count)")
        }
        try await conn.renameReplacing(temporary: temp, target: path)
        if let mode, mode != 0o644 { try? await conn.chmod(path, mode: mode) }
        let newDate = try? await conn.modificationDate(path)
        return RemoteFileBaseline(size: Int64(data.count), modified: newDate)
    }
}
