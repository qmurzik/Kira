import SwiftUI
import TwinCore

@main
struct TwinFTPApp: App {
    @StateObject private var app = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environmentObject(app.settings)
                .environmentObject(app.servers)
                .environmentObject(app.roots)
                .environmentObject(app.transfers)
                .onOpenURL { url in
                    if url.isFileURL { app.handleIncoming(url: url) }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .background: app.transfers.didEnterBackground()
            case .active: app.transfers.willEnterForeground()
            default: break
            }
        }
    }
}

/// Корневой вид: главный экран + листы, оверлеи, уведомления.
struct RootView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var transfers: TransferManager
    @State private var passwordInput = ""
    @State private var rememberPassword = true

    var body: some View {
        DualPaneView(left: app.leftPane, right: app.rightPane)
            .tint(settings.accent.color)
            .preferredColorScheme(settings.appearance.colorScheme)
            .overlay(alignment: .bottom) { toastView }
            .overlay { busyOverlay }
            .sheet(isPresented: $app.showServers) { ServerListView().tint(settings.accent.color) }
            .sheet(isPresented: $app.showQueue) { TransferQueueView().tint(settings.accent.color) }
            .sheet(isPresented: $app.showSettings) { SettingsView().tint(settings.accent.color) }
            .sheet(item: $app.preview) { req in PreviewScreen(request: req).tint(settings.accent.color) }
            .sheet(item: $app.archiveToOpen) { req in ArchiveBrowserView(request: req).tint(settings.accent.color) }
            .fullScreenCover(isPresented: $app.showEditor) {
                EditorView(manager: app.editor)
                    .tint(settings.accent.color)
                    .preferredColorScheme(settings.appearance.colorScheme)
            }
            .sheet(item: Binding(get: { transfers.currentConflict }, set: { _ in })) { conflict in
                ConflictDialog(conflict: conflict).tint(settings.accent.color)
            }
            .alert("Пароль для «\(app.passwordPrompt?.displayName ?? "")»", isPresented: Binding(
                get: { app.passwordPrompt != nil }, set: { if !$0 { app.passwordPrompt = nil } })) {
                SecureField("Пароль", text: $passwordInput)
                Button("Подключиться") {
                    app.providePassword(passwordInput, remember: rememberPassword && (app.passwordPrompt?.savePassword ?? true))
                    passwordInput = ""
                }
                Button("Отмена", role: .cancel) { app.cancelPendingOpen(); passwordInput = "" }
            } message: {
                Text("\(app.passwordPrompt?.subtitle ?? "")\nПароль сохраняется в связке ключей, если это разрешено в настройках сервера.")
            }
            .alert("Незашифрованное соединение", isPresented: Binding(
                get: { app.plainTextWarning != nil }, set: { if !$0 { app.plainTextWarning = nil } })) {
                Button("Подключиться") { app.acknowledgePlainText(dontShowAgain: false) }
                Button("Больше не спрашивать") { app.acknowledgePlainText(dontShowAgain: true) }
                Button("Отмена", role: .cancel) { app.cancelPendingOpen() }
            } message: {
                Text("FTP передаёт логин, пароль и файлы открытым текстом. В общественных Wi-Fi их могут перехватить. Продолжить?")
            }
    }

    @ViewBuilder private var toastView: some View {
        if let toast = app.toast {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: icon(toast.style)).foregroundStyle(color(toast.style)).font(.system(size: 16, weight: .semibold))
                Text(toast.message).font(.system(size: 13.5, weight: .medium)).foregroundStyle(Theme.textPrimary)
                    .lineLimit(5)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Theme.surfaceRaised)
                    .shadow(color: .black.opacity(0.18), radius: 14, y: 6)
            )
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(color(toast.style).opacity(0.35), lineWidth: 1))
            .padding(.horizontal, 14)
            .padding(.bottom, 64)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .onTapGesture { withAnimation { app.toast = nil } }
            .id(toast.id)
            .accessibilityAddTraits(.isStaticText)
        }
    }

    @ViewBuilder private var busyOverlay: some View {
        if let message = app.busyMessage {
            ZStack {
                Color.black.opacity(0.22).ignoresSafeArea()
                BusyCard(message: message)
            }
            .transition(.opacity)
        }
    }

    private func icon(_ s: Toast.Style) -> String {
        switch s {
        case .info: return "info.circle.fill"
        case .success: return "checkmark.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .error: return "xmark.octagon.fill"
        }
    }

    private func color(_ s: Toast.Style) -> Color {
        switch s {
        case .info: return .accentColor
        case .success: return Theme.success
        case .warning: return Theme.warning
        case .error: return Theme.danger
        }
    }
}

private struct BusyCard: View {
    let message: String
    @ObservedObject var progress = BusyProgress.shared

    var body: some View {
        VStack(spacing: 12) {
            if progress.fraction > 0 && progress.fraction < 1 {
                ProgressView(value: progress.fraction).frame(width: 200)
            } else {
                ProgressView().controlSize(.large)
            }
            Text(message).font(.system(size: 14, weight: .medium)).multilineTextAlignment(.center).lineLimit(3)
            if message.contains("ZIP") {
                Button("Отмена") { progress.cancel() }.font(.system(size: 14))
            }
        }
        .padding(22)
        .frame(minWidth: 200)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Theme.surfaceRaised))
        .shadow(radius: 20)
    }
}
