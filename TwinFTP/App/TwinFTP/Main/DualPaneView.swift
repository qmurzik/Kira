import SwiftUI
import TwinCore

/// Главный экран: две независимые панели, разделитель, нижняя панель действий.
struct DualPaneView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var transfers: TransferManager
    @ObservedObject var left: PaneModel
    @ObservedObject var right: PaneModel
    @State private var dialog: PaneDialog?
    @State private var dragStartRatio: Double?

    var body: some View {
        VStack(spacing: 0) {
            TopBar()
            if !app.network.isOnline {
                Label("Нет подключения к сети — операции с FTP недоступны", systemImage: "wifi.slash")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Theme.danger)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
            GeometryReader { geo in
                panes(in: geo.size)
            }
            .padding(.horizontal, 6)
            .padding(.top, 4)
            BottomActionBar(dialog: $dialog)
        }
        .background(Theme.background.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.2), value: app.network.isOnline)
        .modifier(PaneDialogsHost(dialog: $dialog))
    }

    @ViewBuilder
    private func panes(in size: CGSize) -> some View {
        let (first, second) = settings.swapSides ? (right, left) : (left, right)
        let dividerWidth: CGFloat = 12
        let available = max(0, size.width - dividerWidth)
        // Ограничиваем соотношение так, чтобы ни одна панель не стала уже ~120pt.
        let minRatio = min(0.45, 120 / max(available, 1))
        let ratio = min(1 - minRatio, max(minRatio, settings.paneRatio))
        let firstWidth = available * ratio
        let secondWidth = available - firstWidth

        if let maxID = app.maximizedPaneID {
            let pane = app.pane(maxID)
            PaneView(pane: pane, isActive: true, width: size.width, dialog: $dialog)
                .transition(.scale(scale: 0.96).combined(with: .opacity))
                .onAppear { app.activate(pane) }
        } else {
            HStack(spacing: 0) {
                PaneView(pane: first, isActive: app.activePaneID == first.id, width: firstWidth, dialog: $dialog)
                    .frame(width: firstWidth)
                divider(available: available, minRatio: minRatio)
                    .frame(width: dividerWidth)
                PaneView(pane: second, isActive: app.activePaneID == second.id, width: secondWidth, dialog: $dialog)
                    .frame(width: secondWidth)
            }
        }
    }

    private func divider(available: CGFloat, minRatio: Double) -> some View {
        ZStack {
            Color.clear
            Capsule()
                .fill(dragStartRatio != nil ? Color.accentColor : Theme.textTertiary.opacity(0.5))
                .frame(width: 4, height: 44)
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 2)
                .onChanged { value in
                    if dragStartRatio == nil { dragStartRatio = settings.paneRatio; Haptics.selection() }
                    let start = dragStartRatio ?? 0.5
                    let newRatio = start + Double(value.translation.width / max(available, 1))
                    settings.paneRatio = min(1 - minRatio, max(minRatio, newRatio))
                }
                .onEnded { _ in
                    dragStartRatio = nil
                    // Прилипание к середине
                    if abs(settings.paneRatio - 0.5) < 0.04 { settings.paneRatio = 0.5 }
                }
        )
        .onTapGesture(count: 2) {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { settings.paneRatio = 0.5 }
            Haptics.tap()
        }
        .accessibilityElement()
        .accessibilityLabel("Разделитель панелей")
        .accessibilityValue("\(Int(settings.paneRatio * 100)) процентов")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: settings.paneRatio = min(0.75, settings.paneRatio + 0.05)
            case .decrement: settings.paneRatio = max(0.25, settings.paneRatio - 0.05)
            @unknown default: break
            }
        }
    }
}

/// Верхняя панель: серверы, название, смена сторон, очередь, настройки.
struct TopBar: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var transfers: TransferManager

    var body: some View {
        HStack(spacing: 4) {
            Button {
                app.serverTargetPaneID = nil
                app.showServers = true
            } label: {
                Image(systemName: "server.rack")
            }
            .buttonStyle(ToolbarIconButtonStyle())
            .accessibilityLabel("Серверы")
            .accessibilityIdentifier("servers-button")

            Spacer(minLength: 4)
            HStack(spacing: 6) {
                Image(systemName: "rectangle.split.2x1.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.accentColor)
                Text("TwinFTP")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }
            .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 4)

            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { settings.swapSides.toggle() }
                Haptics.tap()
            } label: {
                Image(systemName: "arrow.left.arrow.right")
            }
            .buttonStyle(ToolbarIconButtonStyle())
            .accessibilityLabel("Поменять панели местами")

            Button { app.showQueue = true } label: {
                ZStack(alignment: .topTrailing) {
                    if transfers.hasActive {
                        ZStack {
                            Circle().stroke(Color.accentColor.opacity(0.25), lineWidth: 2.5)
                            Circle().trim(from: 0, to: max(0.03, transfers.overallProgress))
                                .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                            Image(systemName: "arrow.up.arrow.down").font(.system(size: 10, weight: .bold))
                        }
                        .frame(width: 24, height: 24)
                    } else {
                        Image(systemName: "arrow.up.arrow.down.circle")
                    }
                    let failed = transfers.jobs.filter { $0.state == .completedWithErrors || $0.state == .failed }.count
                    if failed > 0 {
                        Circle().fill(Theme.danger).frame(width: 8, height: 8).offset(x: 3, y: -1)
                    }
                }
            }
            .buttonStyle(ToolbarIconButtonStyle())
            .accessibilityLabel(transfers.hasActive ? "Очередь передач, выполняется \(transfers.activeCount)" : "Очередь передач")
            .accessibilityIdentifier("queue-button")

            Button { app.showSettings = true } label: {
                Image(systemName: "gearshape")
            }
            .buttonStyle(ToolbarIconButtonStyle())
            .accessibilityLabel("Настройки")
            .accessibilityIdentifier("settings-button")
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 2)
    }
}

/// Нижняя панель действий над активной панелью.
struct BottomActionBar: View {
    @EnvironmentObject var app: AppModel
    @Binding var dialog: PaneDialog?

    var body: some View {
        BottomActionBarContent(pane: app.activePane, other: app.otherPane, dialog: $dialog)
    }
}

private struct BottomActionBarContent: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var settings: AppSettings
    @ObservedObject var pane: PaneModel
    @ObservedObject var other: PaneModel
    @Binding var dialog: PaneDialog?

    private var arrow: String {
        // Направление стрелки — от активной панели к соседней, с учётом смены сторон.
        let activeIsLeftVisually = (pane.id == "left") != settings.swapSides
        return activeIsLeftVisually ? "arrow.right" : "arrow.left"
    }

    private var transferTitle: String {
        switch (pane.source, other.source) {
        case (.local, .remote): return "Загрузить"
        case (.remote, .local): return "Скачать"
        default: return "Копировать"
        }
    }

    var body: some View {
        let count = pane.selection.count
        HStack(spacing: 2) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    if pane.selectionMode { pane.exitSelectionMode() } else { pane.selectionMode = true }
                }
                Haptics.tap()
            } label: {
                Image(systemName: pane.selectionMode ? "checkmark.circle.fill" : "checkmark.circle")
            }
            .buttonStyle(ToolbarIconButtonStyle())
            .accessibilityLabel(pane.selectionMode ? "Завершить выбор" : "Выбрать файлы")
            .accessibilityIdentifier("select-button")
            .disabled(pane.source == .serverPicker)

            Button { dialog = .newFolder(pane.id) } label: { Image(systemName: "folder.badge.plus") }
                .buttonStyle(ToolbarIconButtonStyle())
                .accessibilityLabel("Новая папка")
                .disabled(pane.source == .serverPicker)

            Spacer(minLength: 2)

            Button {
                app.transfer(from: pane)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: arrow).font(.system(size: 15, weight: .bold))
                    Text(count > 0 ? "\(transferTitle) · \(count)" : transferTitle)
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                }
                .padding(.horizontal, 6)
            }
            .buttonStyle(ToolbarIconButtonStyle(prominent: true))
            .disabled(count == 0 || other.source == .serverPicker && !pane.source.isLocal)
            .accessibilityLabel("\(transferTitle) выбранное (\(count)) в соседнюю панель")
            .accessibilityIdentifier("transfer-button")

            Spacer(minLength: 2)

            Menu {
                Button { app.transfer(from: pane, move: true) } label: { Label("Переместить в соседнюю панель", systemImage: "arrow.right.doc.on.clipboard") }
                    .disabled(count == 0)
                if count == 1, let item = pane.selectedItems.first {
                    Button { dialog = .rename(pane.id, item) } label: { Label("Переименовать", systemImage: "pencil") }
                }
                if pane.source.isLocal {
                    Button { dialog = .zip(pane.id, pane.selectedItems) } label: { Label("Сжать в ZIP", systemImage: "doc.zipper") }
                        .disabled(count == 0)
                }
                Button { pane.selectionMode = true; pane.selectAll() } label: { Label("Выбрать все", systemImage: "checklist") }
                Button { dialog = .newFile(pane.id) } label: { Label("Новый файл", systemImage: "doc.badge.plus") }
                Divider()
                Button(role: .destructive) { dialog = .delete(pane.id, pane.selectedItems) } label: {
                    Label("Удалить выбранное", systemImage: "trash")
                }
                .disabled(count == 0)
            } label: {
                Image(systemName: "ellipsis")
                    .frame(minWidth: 40, minHeight: 38)
            }
            .accessibilityLabel("Другие действия")
            .disabled(pane.source == .serverPicker)

            Button(role: .destructive) { dialog = .delete(pane.id, pane.selectedItems) } label: {
                Image(systemName: "trash")
            }
            .buttonStyle(ToolbarIconButtonStyle())
            .disabled(count == 0)
            .accessibilityLabel("Удалить выбранное")
        }
        .padding(.horizontal, 10)
        .padding(.top, 6)
        .padding(.bottom, 4)
        .background(
            Theme.surface
                .shadow(color: .black.opacity(0.06), radius: 6, y: -2)
                .ignoresSafeArea(edges: .bottom)
        )
    }
}
