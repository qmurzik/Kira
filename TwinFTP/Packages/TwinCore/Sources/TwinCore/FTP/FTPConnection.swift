import Foundation

/// Одно FTP-соединение (управляющий канал + каналы данных по запросу).
///
/// Все публичные операции сериализуются через `AsyncLock`, поэтому объект можно безопасно
/// использовать из нескольких задач. При потере соединения выполняется автоматическое
/// переподключение; идемпотентные операции (листинг, SIZE, MDTM, CWD) повторяются один раз.
public final class FTPConnection: @unchecked Sendable {
    public let label: String
    public let configuration: FTPConfiguration
    private let credentials: FTPCredentials
    private let connector: ByteStreamConnector
    private let logSink: FTPLogSink?
    public let codec: FTPTextCodec

    private let opLock = AsyncLock()
    private let state = Locked(State())

    private struct State {
        var control: ByteStream?
        var lineBuffer = LineBuffer()
        var assembler = FTPReplyAssembler()
        var features = FTPServerFeatures()
        var homeDirectory = "/"
        var welcome = ""
        var systemType = ""
        var lastActivity = Date.distantPast
        var mlsdBroken = false
        var listAllBroken = false
        var epsvBroken = false
        var pasvBroken = false
        var sizeBroken = false
        var mdtmBroken = false
        var hasConnectedOnce = false
    }

    public init(label: String, configuration: FTPConfiguration, credentials: FTPCredentials,
                connector: ByteStreamConnector? = nil, log: FTPLogSink? = nil) {
        self.label = label
        self.configuration = configuration
        self.credentials = credentials
        self.connector = connector ?? configuration.networkStack.makeConnector()
        self.logSink = log
        self.codec = FTPTextCodec(configuration.encoding, escapeIAC: configuration.escapeTelnetIAC)
    }

    deinit {
        state.withValue { $0.control?.close() }
    }

    // MARK: - Публичное состояние

    public var isConnected: Bool { state.withValue { $0.control != nil } }
    public var features: FTPServerFeatures { state.withValue { $0.features } }
    public var homeDirectory: String { state.withValue { $0.homeDirectory } }
    public var welcomeMessage: String { state.withValue { $0.welcome } }
    public var systemType: String { state.withValue { $0.systemType } }

    // MARK: - Журнал

    func log(_ kind: FTPLogKind, _ text: String) {
        logSink?.record(FTPLogEntry(kind: kind, connection: label, text: text))
    }

    // MARK: - Подключение

    /// Устанавливает соединение и выполняет вход. Повторный вызов для активного соединения ничего не делает.
    public func connect() async throws {
        try await opLock.withLock {
            if state.withValue({ $0.control }) == nil {
                try await establish()
            }
        }
    }

    /// Вежливо закрывает соединение (QUIT).
    public func disconnect() async {
        try? await opLock.withLock {
            guard state.withValue({ $0.control }) != nil else { return }
            if let _ = try? await sendCommand("QUIT") {
                _ = try? await readReply(timeout: 3)
            }
            teardown(reason: nil)
        }
    }

    /// Немедленно разрывает соединение (без ожидания блокировки) — для отмены.
    public func forceClose() {
        let control = state.withValue { s -> ByteStream? in
            let c = s.control
            return c
        }
        control?.close()
    }

    private func teardown(reason: String?) {
        let control = state.withValue { s -> ByteStream? in
            let c = s.control
            s.control = nil
            s.lineBuffer = LineBuffer()
            s.assembler = FTPReplyAssembler()
            return c
        }
        control?.close()
        if let reason { log(.info, reason) }
    }

    private func establish() async throws {
        let host = configuration.normalizedHost
        guard !host.isEmpty else { throw FTPError.invalidHost }
        let port = configuration.port
        log(.info, "Подключение к \(host):\(port)…")
        let stream: ByteStream
        do {
            stream = try await connector.connect(host: host, port: port, timeout: configuration.timeout)
        } catch let e as FTPError {
            log(.error, e.localizedDescription)
            throw e
        }
        state.withValue {
            $0.control = stream
            $0.lineBuffer = LineBuffer()
            $0.assembler = FTPReplyAssembler()
            $0.lastActivity = Date()
        }
        log(.info, "Соединение установлено (\(stream.remoteAddress ?? host)).")
        do {
            // Приветствие (120 — «подождите», затем 220).
            var greeting = try await readReply(timeout: configuration.timeout)
            if greeting.code == 120 { greeting = try await readReply(timeout: configuration.timeout * 3) }
            guard greeting.code == 220 else {
                throw mapError(greeting, command: "приветствие")
            }
            state.withValue { $0.welcome = greeting.message }
            try await login()
            try await negotiate()
            state.withValue { $0.hasConnectedOnce = true }
        } catch {
            teardown(reason: nil)
            throw error
        }
    }

    private func login() async throws {
        let user = credentials.username.isEmpty ? "anonymous" : credentials.username
        let r1 = try await command("USER \(user)")
        switch r1.code {
        case 230:
            return
        case 331, 332:
            let r2 = try await command("PASS \(credentials.password)")
            if r2.code == 230 || r2.code == 202 { return }
            if r2.code == 332 { throw FTPError.unsupported("ACCT (учётная запись)") }
            throw FTPError.loginFailed(code: r2.code, message: r2.message)
        default:
            throw FTPError.loginFailed(code: r1.code, message: r1.message)
        }
    }

    private func negotiate() async throws {
        let feat = try await command("FEAT")
        let features = FTPServerFeatures(raw: FTPReplyParsing.parseFeatures(feat))
        state.withValue { $0.features = features }
        if features.utf8 && (configuration.encoding == .auto || configuration.encoding == .utf8) {
            let r = try await command("OPTS UTF8 ON")
            if r.isSuccess || configuration.encoding == .auto { codec.serverAnnouncedUTF8() }
        }
        if let syst = try? await command("SYST"), syst.isSuccess {
            state.withValue { $0.systemType = syst.message }
        }
        let type = try await command("TYPE I")
        guard type.isSuccess else { throw mapError(type, command: "TYPE I") }
        let pwd = try await command("PWD")
        if pwd.code == 257, let p = FTPReplyParsing.parsePWD(pwd.message) {
            state.withValue { $0.homeDirectory = p.hasPrefix("/") ? p : "/" + p }
        }
    }

    // MARK: - Сессия с автопереподключением

    /// Выполняет операцию под блокировкой, обеспечивая живое соединение.
    private func session<T>(idempotent: Bool, _ body: () async throws -> T) async throws -> T {
        try await opLock.withLock {
            try await ensureAlive()
            do {
                return try await body()
            } catch let error as FTPError where error.isConnectionLoss && idempotent && !Task.isCancelled {
                teardown(reason: "Соединение потеряно (\(error.localizedDescription)). Переподключение…")
                try await establish()
                return try await body()
            } catch is CancellationError {
                teardown(reason: "Операция отменена, соединение сброшено.")
                throw FTPError.cancelled
            } catch FTPError.cancelled {
                teardown(reason: "Операция отменена, соединение сброшено.")
                throw FTPError.cancelled
            } catch let error as FTPError where error.isConnectionLoss {
                teardown(reason: "Соединение потеряно: \(error.localizedDescription)")
                throw error
            }
        }
    }

    private func ensureAlive() async throws {
        let (control, last) = state.withValue { ($0.control, $0.lastActivity) }
        if control == nil {
            try await establish()
            return
        }
        // Соединение простаивало — проверяем его NOOP, чтобы не потерять мутирующую команду.
        if Date().timeIntervalSince(last) > 15 {
            do {
                let r = try await command("NOOP", timeout: min(configuration.timeout, 10))
                if r.code == 421 { throw FTPError.connectionClosed }
            } catch {
                teardown(reason: "Сервер закрыл простаивающее соединение. Переподключение…")
                try await establish()
            }
        }
    }

    // MARK: - Низкоуровневый ввод-вывод

    private func sendCommand(_ text: String) async throws {
        guard let control = state.withValue({ $0.control }) else { throw FTPError.notConnected }
        var data = try codec.encode(text)
        data.append(contentsOf: [0x0D, 0x0A])
        log(.command, FTPLogSanitizer.sanitize(command: text))
        try await control.send(data, timeout: configuration.timeout)
        state.withValue { $0.lastActivity = Date() }
    }

    private func readReply(timeout: TimeInterval) async throws -> FTPReply {
        guard let control = state.withValue({ $0.control }) else { throw FTPError.notConnected }
        while true {
            // Разбираем уже полученные строки.
            while let lineData = state.withValue({ $0.lineBuffer.nextLine() }) {
                let line = codec.decode(lineData)
                let reply = try state.withValue { try $0.assembler.feed(line: line) }
                if let reply {
                    log(.reply, reply.lines.joined(separator: "\n"))
                    state.withValue { $0.lastActivity = Date() }
                    return reply
                }
            }
            let chunk = try await control.receive(maxLength: 16 * 1024, timeout: timeout)
            if chunk.isEmpty { throw FTPError.connectionClosed }
            state.withValue { $0.lineBuffer.append(chunk) }
        }
    }

    /// Отправляет команду и возвращает первый окончательный ответ (1xx пропускаются).
    @discardableResult
    private func command(_ text: String, timeout: TimeInterval? = nil) async throws -> FTPReply {
        try await sendCommand(text)
        var reply = try await readReply(timeout: timeout ?? configuration.timeout)
        while reply.isPreliminary {
            reply = try await readReply(timeout: timeout ?? configuration.timeout)
        }
        if reply.code == 421 { throw FTPError.transientFailure(command: commandName(text), code: 421, message: reply.message) }
        return reply
    }

    private func commandName(_ text: String) -> String {
        let name = text.split(separator: " ").first.map(String.init) ?? text
        return name.uppercased()
    }

    func mapError(_ reply: FTPReply, command: String) -> FTPError {
        let name = commandName(command)
        if reply.isTransientError { return .transientFailure(command: name, code: reply.code, message: reply.message) }
        if reply.isPermanentError { return .permanentFailure(command: name, code: reply.code, message: reply.message) }
        return .unexpectedReply(command: name, code: reply.code, message: reply.message)
    }

    private func expectSuccess(_ text: String, codes: Set<Int>? = nil) async throws -> FTPReply {
        let r = try await command(text)
        if let codes {
            guard codes.contains(r.code) else { throw mapError(r, command: text) }
        } else {
            guard r.isSuccess else { throw mapError(r, command: text) }
        }
        return r
    }

    // MARK: - Канал данных

    private enum PassiveKind { case pasv, epsv }

    private func passiveOrder() -> [PassiveKind] {
        let (epsvBroken, pasvBroken, controlAddr) = state.withValue { ($0.epsvBroken, $0.pasvBroken, $0.control?.remoteAddress) }
        let isIPv6 = controlAddr?.contains(":") == true
        var order: [PassiveKind]
        switch configuration.passiveMode {
        case .auto: order = isIPv6 ? [.epsv, .pasv] : [.pasv, .epsv]
        case .epsvFirst: order = [.epsv, .pasv]
        case .pasvOnly: order = [.pasv]
        }
        order.removeAll { ($0 == .epsv && epsvBroken) || ($0 == .pasv && pasvBroken) }
        if order.isEmpty { order = configuration.passiveMode == .pasvOnly ? [.pasv] : [.pasv, .epsv] }
        return order
    }

    private func openDataConnection() async throws -> ByteStream {
        let controlHost = state.withValue { $0.control?.remoteAddress } ?? configuration.normalizedHost
        var lastError: Error = FTPError.passiveModeFailed("сервер не поддерживает пассивный режим")
        for kind in passiveOrder() {
            var host = controlHost
            var port = 0
            switch kind {
            case .epsv:
                let r = try await command("EPSV")
                guard r.code == 229, let p = FTPReplyParsing.parseEPSV(r.message) else {
                    state.withValue { $0.epsvBroken = true }
                    lastError = mapError(r, command: "EPSV")
                    continue
                }
                port = p
            case .pasv:
                let r = try await command("PASV")
                guard r.code == 227, let (h, p) = FTPReplyParsing.parsePASV(r.message) else {
                    state.withValue { $0.pasvBroken = true }
                    lastError = r.code == 227 ? FTPError.passiveModeFailed(r.message) : mapError(r, command: "PASV")
                    continue
                }
                port = p
                host = h
                if h == "0.0.0.0" {
                    host = controlHost
                } else if configuration.fixPassiveAddress,
                          IPAddressClassifier.isPrivateOrUnroutable(h),
                          !IPAddressClassifier.isPrivateOrUnroutable(controlHost) {
                    log(.info, "Сервер вернул внутренний адрес \(h) — используем \(controlHost).")
                    host = controlHost
                }
            }
            do {
                let stream = try await connector.connect(host: host, port: port, timeout: configuration.timeout)
                return stream
            } catch let e as FTPError {
                if case .cancelled = e { throw e }
                log(.error, "Канал данных \(host):\(port): \(e.localizedDescription)")
                lastError = FTPError.dataConnectionFailed("\(host):\(port) — \(e.localizedDescription)")
                if kind == .epsv { state.withValue { $0.epsvBroken = true } }
                if kind == .pasv && passiveOrder().count > 1 { state.withValue { $0.pasvBroken = true } }
            }
        }
        throw lastError
    }

    /// Общая процедура передачи: открыть канал данных, (REST), команда, обмен данными, финальный ответ.
    private func dataTransfer(_ commandText: String, restOffset: Int64 = 0,
                              allowEmptyOn: Set<Int> = [],
                              body: (ByteStream) async throws -> Void) async throws -> Bool {
        let data = try await openDataConnection()
        if restOffset > 0 {
            let r = try await command("REST \(restOffset)")
            guard r.code == 350 else { data.close(); throw FTPError.unsupported("докачка (REST): \(r.code) \(r.message)") }
        }
        do {
            try await sendCommand(commandText)
        } catch {
            data.close(); throw error
        }
        let first: FTPReply
        do {
            first = try await readReply(timeout: configuration.timeout)
        } catch {
            data.close(); throw error
        }
        if first.code == 421 {
            data.close()
            throw FTPError.transientFailure(command: commandName(commandText), code: 421, message: first.message)
        }
        if allowEmptyOn.contains(first.code) {
            data.close()
            return false
        }
        guard first.isPreliminary || first.isSuccess else {
            data.close()
            throw mapError(first, command: commandText)
        }
        do {
            try await body(data)
        } catch {
            data.close()
            // Состояние управляющего канала неизвестно (сервер может ждать ABOR) — сбрасываем.
            teardown(reason: "Передача прервана: \(error.localizedDescription)")
            throw error
        }
        data.close()
        if first.isPreliminary {
            let final = try await readReply(timeout: max(configuration.timeout, 60))
            guard final.isSuccess else { throw mapError(final, command: commandText) }
        }
        return true
    }

    private func readAll(_ stream: ByteStream) async throws -> Data {
        var result = Data()
        while true {
            try Task.checkCancellation()
            let chunk = try await stream.receive(maxLength: 64 * 1024, timeout: configuration.timeout)
            if chunk.isEmpty { break }
            result.append(chunk)
            if result.count > 64 * 1024 * 1024 { throw FTPError.dataConnectionFailed("листинг слишком большой") }
        }
        return result
    }

    private func splitLines(_ data: Data) -> [String] {
        var buffer = LineBuffer()
        buffer.append(data)
        var lines: [String] = []
        while let l = buffer.nextLine() { lines.append(codec.decode(l)) }
        if let rest = buffer.drainRemainder() { lines.append(codec.decode(rest)) }
        return lines
    }

    // MARK: - Операции с каталогами

    /// Возвращает текущий рабочий каталог.
    public func printWorkingDirectory() async throws -> String {
        try await session(idempotent: true) {
            let r = try await expectSuccess("PWD", codes: [257])
            return FTPReplyParsing.parsePWD(r.message) ?? "/"
        }
    }

    /// Проверяет, что путь — каталог (через CWD).
    public func isDirectory(_ path: String) async throws -> Bool {
        try await session(idempotent: true) {
            let r = try await command("CWD \(path)")
            return r.isSuccess
        }
    }

    /// Переходит в каталог и возвращает его канонический путь (PWD) или nil, если это не каталог.
    public func canonicalDirectory(_ path: String) async throws -> String? {
        try await session(idempotent: true) {
            let r = try await command("CWD \(path)")
            guard r.isSuccess else { return nil }
            let p = try await command("PWD")
            return p.code == 257 ? FTPReplyParsing.parsePWD(p.message) ?? path : path
        }
    }

    /// Листинг каталога.
    public func list(_ path: String) async throws -> [FileItem] {
        try await session(idempotent: true) {
            try await listLocked(path)
        }
    }

    private func listLocked(_ path: String) async throws -> [FileItem] {
        let cwd = try await command("CWD \(path)")
        guard cwd.isSuccess else {
            if cwd.code == 550 || cwd.code == 553 || cwd.code == 501 {
                throw FTPError.notADirectory(path)
            }
            throw mapError(cwd, command: "CWD")
        }
        let (mlsdBroken, listAllBroken, features) = state.withValue { ($0.mlsdBroken, $0.listAllBroken, $0.features) }
        let mode = configuration.listingMode

        if (mode == .mlsd || (mode == .auto && features.mlsd)) && !mlsdBroken {
            var raw = Data()
            do {
                _ = try await dataTransfer("MLSD") { raw = try await readAll($0) }
                return FTPListParser.parseMLSD(splitLines(raw), directory: path)
            } catch let e as FTPError {
                guard let code = e.replyCode, [500, 501, 502, 504].contains(code), mode == .auto else { throw e }
                state.withValue { $0.mlsdBroken = true }
                log(.info, "MLSD не поддерживается, используем LIST.")
            }
        }
        if (mode == .auto || mode == .listAll) && !listAllBroken {
            var raw = Data()
            do {
                _ = try await dataTransfer("LIST -a") { raw = try await readAll($0) }
                let items = FTPListParser.parseLIST(splitLines(raw), directory: path)
                if !items.isEmpty || mode == .listAll { return items }
                // Пустой результат: возможно, сервер воспринял "-a" как имя файла — перепроверим простым LIST.
            } catch let e as FTPError {
                guard let code = e.replyCode, (500...559).contains(code) else { throw e }
                state.withValue { $0.listAllBroken = true }
            }
        }
        var raw = Data()
        _ = try await dataTransfer("LIST", allowEmptyOn: [450]) { raw = try await readAll($0) }
        return FTPListParser.parseLIST(splitLines(raw), directory: path)
    }

    public func makeDirectory(_ path: String) async throws {
        try await session(idempotent: false) {
            let r = try await command("MKD \(path)")
            guard r.code == 257 || r.isSuccess else { throw mapError(r, command: "MKD") }
        }
    }

    /// Создаёт каталог, если его ещё нет (ошибка «уже существует» игнорируется).
    public func ensureDirectory(_ path: String) async throws {
        try await session(idempotent: true) {
            let cwd = try await command("CWD \(path)")
            if cwd.isSuccess { return }
            let r = try await command("MKD \(path)")
            if r.code == 257 || r.isSuccess { return }
            // Возможно, каталог создан параллельно — проверяем ещё раз.
            let again = try await command("CWD \(path)")
            if again.isSuccess { return }
            throw mapError(r, command: "MKD")
        }
    }

    /// Создаёт каталог со всеми недостающими родителями (аналог mkdir -p).
    public func ensureDirectoryTree(_ path: String) async throws {
        let normalized = PathUtils.normalize(path)
        if normalized == "/" { return }
        if try await isDirectory(normalized) { return }
        try await ensureDirectoryTree(PathUtils.parent(normalized))
        try await ensureDirectory(normalized)
    }

    public func removeDirectory(_ path: String) async throws {
        try await session(idempotent: false) {
            // Выходим из удаляемого каталога, иначе некоторые серверы откажут.
            _ = try await command("CWD \(PathUtils.parent(path))")
            let r = try await command("RMD \(path)")
            guard r.isSuccess else { throw mapError(r, command: "RMD") }
        }
    }

    public func deleteFile(_ path: String) async throws {
        try await session(idempotent: false) {
            let r = try await command("DELE \(path)")
            guard r.isSuccess else { throw mapError(r, command: "DELE") }
        }
    }

    public func rename(from: String, to: String) async throws {
        try await session(idempotent: false) {
            try await renameLocked(from: from, to: to)
        }
    }

    private func renameLocked(from: String, to: String) async throws {
        let r1 = try await command("RNFR \(from)")
        guard r1.code == 350 else { throw mapError(r1, command: "RNFR") }
        let r2 = try await command("RNTO \(to)")
        guard r2.isSuccess else { throw mapError(r2, command: "RNTO") }
    }

    /// Права доступа через SITE CHMOD (не все серверы поддерживают).
    public func chmod(_ path: String, mode: Int) async throws {
        try await session(idempotent: true) {
            let r = try await command("SITE CHMOD \(String(mode, radix: 8)) \(path)")
            guard r.isSuccess else { throw mapError(r, command: "SITE CHMOD") }
        }
    }

    /// Размер файла через SIZE. nil — команда не поддерживается или файла нет.
    public func size(_ path: String) async throws -> Int64? {
        try await session(idempotent: true) {
            if state.withValue({ $0.sizeBroken }) { return nil }
            let r = try await command("SIZE \(path)")
            if r.code == 213 { return FTPReplyParsing.parseSize(r.message) }
            if [500, 502, 504].contains(r.code) { state.withValue { $0.sizeBroken = true } }
            return nil
        }
    }

    /// Время изменения через MDTM. nil — не поддерживается или нет файла.
    public func modificationDate(_ path: String) async throws -> Date? {
        try await session(idempotent: true) {
            if state.withValue({ $0.mdtmBroken }) { return nil }
            let r = try await command("MDTM \(path)")
            if r.code == 213 { return FTPReplyParsing.parseMDTM(r.message) }
            if [500, 502, 504].contains(r.code) { state.withValue { $0.mdtmBroken = true } }
            return nil
        }
    }

    /// Информация о файле: сначала SIZE/MDTM, при необходимости — листинг родительского каталога.
    public func stat(_ path: String) async throws -> FileItem? {
        let parent = PathUtils.parent(path)
        let name = PathUtils.lastComponent(path)
        let items = try await list(parent)
        return items.first { $0.name == name }
    }

    public func noop() async throws {
        try await session(idempotent: true) {
            _ = try await expectSuccess("NOOP")
        }
    }

    /// Произвольная команда (для диагностики). Возвращает ответ сервера.
    public func raw(_ text: String) async throws -> FTPReply {
        try await session(idempotent: false) {
            try await command(text)
        }
    }

    // MARK: - Передача файлов

    /// Скачивание файла. `consumer` получает блоки данных по порядку.
    /// - Parameter offset: смещение для докачки (REST). 0 — с начала.
    public func download(_ path: String, offset: Int64 = 0,
                         consumer: @escaping (Data) async throws -> Void) async throws {
        try await session(idempotent: false) {
            _ = try await dataTransfer("RETR \(path)", restOffset: offset) { stream in
                while true {
                    try Task.checkCancellation()
                    let chunk = try await stream.receive(maxLength: 128 * 1024, timeout: configuration.timeout)
                    if chunk.isEmpty { break }
                    try await consumer(chunk)
                }
            }
        }
    }

    /// Загрузка файла. `producer` возвращает следующий блок или nil в конце.
    /// - Parameter append: дописывать в конец (APPE) — для докачки загрузки.
    public func upload(_ path: String, append: Bool = false,
                       producer: @escaping () async throws -> Data?) async throws {
        try await session(idempotent: false) {
            _ = try await dataTransfer(append ? "APPE \(path)" : "STOR \(path)") { stream in
                while true {
                    try Task.checkCancellation()
                    guard let chunk = try await producer() else { break }
                    if chunk.isEmpty { continue }
                    try await stream.send(chunk, timeout: configuration.timeout)
                }
                await stream.closeWrite()
            }
        }
    }

    /// Безопасная замена: загрузка во временный файл рядом и переименование поверх цели.
    /// Используется редактором и очередью (при включённой настройке).
    public func renameReplacing(temporary: String, target: String) async throws {
        try await session(idempotent: false) {
            do {
                try await renameLocked(from: temporary, to: target)
            } catch let e as FTPError where e.replyCode != nil && e.replyCode! >= 500 {
                // Некоторые серверы (IIS) не переименовывают поверх существующего файла.
                let d = try await command("DELE \(target)")
                guard d.isSuccess else { throw e }
                try await renameLocked(from: temporary, to: target)
            }
        }
    }
}
