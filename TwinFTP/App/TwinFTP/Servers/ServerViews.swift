import SwiftUI
import TwinCore

/// Список серверов (лист).
struct ServerListView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var servers: ServerStore
    @Environment(\.dismiss) private var dismiss
    @State private var editing: ServerProfile?
    @State private var deleting: ServerProfile?

    var body: some View {
        NavigationStack {
            List {
                if servers.servers.isEmpty {
                    VStack(spacing: 14) {
                        Image(systemName: "server.rack")
                            .font(.system(size: 40, weight: .light))
                            .foregroundStyle(Color.accentColor)
                        Text("Добавьте первый FTP-сервер").font(.headline)
                        Text("Укажите адрес, логин и пароль от FTP-хостинга. Пароль хранится в связке ключей iOS.")
                            .font(.subheadline).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                        Button { editing = ServerProfile() } label: { Text("Добавить сервер") }
                            .buttonStyle(AccentButtonStyle())
                    }
                    .padding(.vertical, 30)
                    .listRowBackground(Color.clear)
                }
                ForEach(servers.servers) { server in
                    ServerRow(server: server, session: app.sessions.existing(server.id))
                        .contentShape(Rectangle())
                        .onTapGesture {
                            Haptics.tap()
                            app.open(server: server)
                            dismiss()
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { deleting = server } label: { Label("Удалить", systemImage: "trash") }
                            Button { editing = server } label: { Label("Изменить", systemImage: "pencil") }.tint(.orange)
                        }
                        .contextMenu {
                            Button { app.open(server: server); dismiss() } label: { Label("Подключиться", systemImage: "bolt.fill") }
                            Button { editing = server } label: { Label("Изменить", systemImage: "pencil") }
                            Button { servers.duplicate(server.id) } label: { Label("Дублировать", systemImage: "plus.square.on.square") }
                            if app.sessions.existing(server.id) != nil {
                                Button { Task { await app.sessions.disconnect(server.id) } } label: { Label("Отключиться", systemImage: "bolt.slash") }
                            }
                            Button(role: .destructive) { deleting = server } label: { Label("Удалить", systemImage: "trash") }
                        }
                }
                .onMove { servers.move(from: $0, to: $1) }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Серверы")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Закрыть") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editing = ServerProfile() } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Добавить сервер")
                }
            }
            .sheet(item: $editing) { profile in
                ServerEditorView(profile: profile, isNew: servers.profile(profile.id) == nil)
            }
            .confirmationDialog("Удалить сервер «\(deleting?.displayName ?? "")»?", isPresented: Binding(
                get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
                Button("Удалить", role: .destructive) {
                    if let d = deleting {
                        Task { await app.sessions.disconnect(d.id) }
                        for pane in [app.leftPane, app.rightPane] where pane.source == .remote(profileID: d.id) {
                            pane.setSource(.serverPicker)
                        }
                        servers.delete(d.id)
                    }
                    deleting = nil
                }
            } message: { Text("Пароль будет удалён из связки ключей.") }
        }
    }
}

struct ServerRow: View {
    let server: ServerProfile
    let session: RemoteSession?

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottomTrailing) {
                Image(systemName: "server.rack")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 42, height: 42)
                    .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(Color.accentColor.opacity(0.13)))
                if session != nil { ConnectionDot(session: session).offset(x: 2, y: 2) }
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(server.displayName).font(.system(size: 16, weight: .semibold)).lineLimit(1)
                Text(server.subtitle).font(.system(size: 13)).foregroundStyle(Theme.textSecondary).lineLimit(1)
                if let last = server.lastConnectedAt {
                    Text("Подключение: \(DateText.short(last))").font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Theme.textTertiary)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// Список серверов внутри пустой FTP-панели.
struct ServerPickerInline: View {
    let paneID: String
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var servers: ServerStore
    @State private var creating: ServerProfile?

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                VStack(spacing: 6) {
                    Image(systemName: "network")
                        .font(.system(size: 26, weight: .light))
                        .foregroundStyle(Color.accentColor)
                    Text("FTP-сервер").font(.system(size: 14, weight: .semibold))
                    Text("Выберите сервер для подключения").font(.system(size: 11.5)).foregroundStyle(Theme.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, 12)
                ForEach(servers.servers) { s in
                    Button {
                        Haptics.tap()
                        app.open(server: s, in: paneID)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "server.rack").foregroundStyle(Color.accentColor).font(.system(size: 13))
                            VStack(alignment: .leading, spacing: 1) {
                                Text(s.displayName).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.textPrimary).lineLimit(1)
                                Text(s.host).font(.system(size: 10.5)).foregroundStyle(Theme.textSecondary).lineLimit(1)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Theme.surfaceSunken))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("server-\(s.displayName)")
                }
                Button { creating = ServerProfile() } label: {
                    Label("Новый сервер", systemImage: "plus")
                        .font(.system(size: 13, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(Color.accentColor.opacity(0.5), style: StrokeStyle(lineWidth: 1, dash: [4])))
                }
                .buttonStyle(.plain)
                .foregroundStyle(Color.accentColor)
            }
            .padding(10)
        }
        .sheet(item: $creating) { p in
            ServerEditorView(profile: p, isNew: true) { saved in
                app.open(server: saved, in: paneID)
            }
        }
    }
}

/// Добавление и редактирование сервера.
struct ServerEditorView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var servers: ServerStore
    @Environment(\.dismiss) private var dismiss
    @State var profile: ServerProfile
    let isNew: Bool
    var onSaved: ((ServerProfile) -> Void)?
    @State private var password = ""
    @State private var passwordLoaded = false
    @State private var showPassword = false
    @State private var showTest = false
    @State private var portText = "21"

    init(profile: ServerProfile, isNew: Bool, onSaved: ((ServerProfile) -> Void)? = nil) {
        _profile = State(initialValue: profile)
        _portText = State(initialValue: String(profile.port))
        self.isNew = isNew
        self.onSaved = onSaved
    }

    private var valid: Bool {
        !profile.normalizedHost.isEmpty && (Int(portText).map { (1...65535).contains($0) } ?? false)
            && (profile.anonymous || !profile.username.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "exclamationmark.shield.fill").foregroundStyle(Theme.warning).font(.system(size: 18))
                        Text("Обычный FTP не шифрует трафик: логин, пароль и файлы передаются открытым текстом. Используйте его в доверенных сетях.")
                            .font(.system(size: 12.5)).foregroundStyle(Theme.textSecondary)
                    }
                    .padding(.vertical, 2)
                }
                Section("Сервер") {
                    TextField("Название (необязательно)", text: $profile.name)
                    TextField("Адрес: ftp.example.com", text: $profile.host)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.URL)
                    HStack {
                        Text("Порт")
                        Spacer()
                        TextField("21", text: $portText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 90)
                    }
                }
                Section("Вход") {
                    Toggle("Анонимный вход", isOn: $profile.anonymous)
                    if !profile.anonymous {
                        TextField("Имя пользователя", text: $profile.username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textContentType(.username)
                        HStack {
                            Group {
                                if showPassword {
                                    TextField("Пароль", text: $password)
                                } else {
                                    SecureField("Пароль", text: $password)
                                }
                            }
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textContentType(.password)
                            Button { showPassword.toggle() } label: {
                                Image(systemName: showPassword ? "eye.slash" : "eye").foregroundStyle(Theme.textSecondary)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(showPassword ? "Скрыть пароль" : "Показать пароль")
                        }
                        Toggle("Сохранить пароль в связке ключей", isOn: $profile.savePassword)
                    }
                }
                Section {
                    TextField("Начальная папка, например /public_html", text: $profile.initialPath)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: { Text("Папка") } footer: { Text("Пусто — домашняя папка пользователя на сервере.") }

                Section {
                    Picker("Пассивный режим", selection: $profile.passiveMode) {
                        ForEach(FTPPassiveMode.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Toggle("Исправлять адрес PASV за NAT", isOn: $profile.fixPassiveAddress)
                    Picker("Кодировка имён", selection: $profile.encoding) {
                        ForEach(FTPEncoding.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Picker("Листинг", selection: $profile.listingMode) {
                        ForEach(FTPListingMode.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Stepper("Тайм-аут: \(Int(profile.timeout)) с", value: $profile.timeout, in: 5...120, step: 5)
                    Stepper("Соединений для передачи: \(profile.maxTransferConnections)", value: $profile.maxTransferConnections, in: 1...4)
                    Picker("Сетевой стек", selection: $profile.networkStack) {
                        ForEach(NetworkStack.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    if profile.encoding == .windows1251 || profile.encoding == .koi8r || profile.encoding == .latin1 {
                        Toggle("Экранировать 0xFF (Telnet IAC)", isOn: $profile.escapeTelnetIAC)
                    }
                } header: { Text("Дополнительно") } footer: {
                    Text("Если список файлов не загружается, а вход проходит — попробуйте «Только PASV» или отключите исправление адреса. Большинству хостингов подходят настройки по умолчанию.")
                }

                Section {
                    Button {
                        profile.port = Int(portText) ?? 21
                        showTest = true
                    } label: {
                        Label("Проверить подключение", systemImage: "stethoscope")
                    }
                    .disabled(!valid)
                }
            }
            .navigationTitle(isNew ? "Новый сервер" : "Сервер")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Сохранить") { save() }.disabled(!valid).fontWeight(.semibold)
                }
            }
            .onAppear {
                if !passwordLoaded {
                    password = servers.password(for: profile.id) ?? ""
                    passwordLoaded = true
                }
            }
            .sheet(isPresented: $showTest) {
                ConnectionTestView(profile: profile, password: password)
            }
        }
    }

    private func save() {
        profile.port = Int(portText) ?? 21
        profile.host = profile.normalizedHost
        profile.username = profile.username.trimmingCharacters(in: .whitespaces)
        var p = profile.initialPath.trimmingCharacters(in: .whitespaces)
        if !p.isEmpty && !p.hasPrefix("/") { p = "/" + p }
        profile.initialPath = p
        let old = servers.profile(profile.id)
        servers.upsert(profile, password: profile.anonymous ? "" : password)
        // Параметры изменились — пересоздаём соединения при следующем использовании.
        if let old, old != profile || servers.password(for: profile.id) != password {
            Task { await app.sessions.disconnect(profile.id) }
        }
        Haptics.success()
        dismiss()
        onSaved?(servers.profile(profile.id) ?? profile)
    }
}

extension ServerProfile {
    var normalizedHost: String { configuration.normalizedHost }
}

/// Диагностика подключения: пошаговая проверка с понятными подсказками.
struct ConnectionTestView: View {
    let profile: ServerProfile
    let password: String
    @Environment(\.dismiss) private var dismiss
    @State private var steps: [TestStep] = []
    @State private var running = true
    @State private var logLines: [FTPLogEntry] = []
    @State private var advice: String?

    struct TestStep: Identifiable {
        let id = UUID()
        var title: String
        var detail: String
        var ok: Bool?
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(steps) { step in
                        HStack(alignment: .top, spacing: 10) {
                            Group {
                                if let ok = step.ok {
                                    Image(systemName: ok ? "checkmark.circle.fill" : "xmark.octagon.fill")
                                        .foregroundStyle(ok ? Theme.success : Theme.danger)
                                } else {
                                    ProgressView().controlSize(.small)
                                }
                            }
                            .frame(width: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(step.title).font(.system(size: 15, weight: .semibold))
                                if !step.detail.isEmpty {
                                    Text(step.detail).font(.system(size: 12.5)).foregroundStyle(Theme.textSecondary)
                                        .textSelection(.enabled)
                                }
                            }
                        }
                    }
                }
                if let advice {
                    Section("Что можно сделать") {
                        Text(advice).font(.system(size: 13))
                    }
                }
                if !logLines.isEmpty {
                    Section("Журнал команд (пароль скрыт)") {
                        ForEach(logLines) { line in
                            Text((line.kind == .command ? "→ " : line.kind == .reply ? "← " : "• ") + line.text)
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(line.kind == .error ? Theme.danger : (line.kind == .command ? Color.accentColor : Theme.textPrimary))
                                .textSelection(.enabled)
                        }
                    }
                }
            }
            .navigationTitle("Проверка подключения")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button("Готово") { dismiss() } }
            }
            .task { await run() }
        }
    }

    private func update(_ index: Int, ok: Bool, detail: String) {
        guard index < steps.count else { return }
        steps[index].ok = ok
        steps[index].detail = detail
    }

    private func run() async {
        let log = InMemoryFTPLog()
        let conn = FTPConnection(label: "проверка", configuration: profile.configuration,
                                 credentials: profile.credentials(password: password), log: log)
        steps = [TestStep(title: "Соединение и вход", detail: "\(profile.normalizedHost):\(profile.port)")]
        do {
            try await conn.connect()
            update(0, ok: true, detail: conn.welcomeMessage.isEmpty ? "Вход выполнен" : conn.welcomeMessage)
            let feats = conn.features
            var featureList: [String] = []
            if feats.mlsd { featureList.append("MLSD") }
            if feats.epsv { featureList.append("EPSV") }
            if feats.utf8 { featureList.append("UTF8") }
            if feats.size { featureList.append("SIZE") }
            if feats.mdtm { featureList.append("MDTM") }
            if feats.restStream { featureList.append("REST (докачка)") }
            if feats.authTLS { featureList.append("AUTH TLS") }
            steps.append(TestStep(title: "Сервер", detail: [conn.systemType, featureList.isEmpty ? "FEAT не поддерживается" : featureList.joined(separator: ", ")]
                                    .filter { !$0.isEmpty }.joined(separator: "\n"), ok: true))
            steps.append(TestStep(title: "Домашняя папка", detail: conn.homeDirectory, ok: true))
            steps.append(TestStep(title: "Пассивный режим и листинг", detail: "", ok: nil))
            let dir = profile.initialPath.isEmpty ? conn.homeDirectory : profile.initialPath
            let items = try await conn.list(dir)
            update(3, ok: true, detail: "\(dir): \(items.count) \(RussianPlural.objects(items.count))")
            await conn.disconnect()
        } catch {
            let e = error as? FTPError
            let failedIndex = steps.firstIndex { $0.ok == nil } ?? 0
            update(failedIndex, ok: false, detail: PaneModel.describe(error))
            advice = Self.advice(for: e)
            await conn.disconnect()
        }
        logLines = log.all
        running = false
    }

    static func advice(for e: FTPError?) -> String {
        guard let e else { return "Проверьте адрес и подключение к сети." }
        switch e {
        case .dnsFailure: return "Адрес сервера не найден. Проверьте написание (без «ftp://» и лишних символов) и наличие интернета."
        case .connectionFailed: return "Сервер не принимает соединения на этом порту. Проверьте порт (обычно 21) и что FTP включён на хостинге. Если сервер в локальной сети — разрешите TwinFTP доступ к локальной сети в Настройках iOS."
        case .timeout: return "Сервер не отвечает. Возможно, порт закрыт брандмауэром или неверный адрес."
        case .loginFailed: return "Неверное имя пользователя или пароль. Проверьте данные в панели хостинга; у некоторых хостингов логин имеет вид user@domain."
        case .dataConnectionFailed, .passiveModeFailed: return "Вход выполнен, но канал данных не открылся. Попробуйте «Только PASV» или «Сначала EPSV», а также переключите «Исправлять адрес PASV за NAT»."
        default: return "Посмотрите журнал команд ниже — в ответах сервера обычно указана причина."
        }
    }
}
