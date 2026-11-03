import SwiftUI
import TwinCore

/// Экран очереди и истории передач.
struct TransferQueueView: View {
    @EnvironmentObject var transfers: TransferManager
    @Environment(\.dismiss) private var dismiss
    @State private var filter = 0

    private var activeJobs: [TransferJob] { transfers.jobs.filter { !$0.state.isFinished } }
    private var finishedJobs: [TransferJob] { transfers.jobs.filter { $0.state.isFinished } }

    var body: some View {
        NavigationStack {
            List {
                Picker("", selection: $filter) {
                    Text("Активные (\(activeJobs.count))").tag(0)
                    Text("История (\(finishedJobs.count))").tag(1)
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))

                let list = filter == 0 ? activeJobs : finishedJobs
                if list.isEmpty {
                    StateView(symbol: filter == 0 ? "tray" : "clock.arrow.circlepath",
                              title: filter == 0 ? "Нет активных передач" : "История пуста",
                              message: filter == 0 ? "Выберите файлы в панели и нажмите «Загрузить» или «Скачать»." : "Здесь появятся завершённые задания.",
                              actionTitle: nil, action: nil)
                        .frame(minHeight: 260)
                        .listRowBackground(Color.clear)
                }
                ForEach(list) { job in
                    NavigationLink {
                        TransferJobDetailView(jobID: job.id)
                    } label: {
                        TransferJobRow(job: job)
                    }
                    .swipeActions {
                        if job.state.isFinished {
                            Button(role: .destructive) { transfers.remove(job.id) } label: { Label("Убрать", systemImage: "trash") }
                        } else {
                            Button(role: .destructive) { transfers.cancel(job.id) } label: { Label("Отменить", systemImage: "xmark") }
                        }
                    }
                }
                if filter == 0 && !activeJobs.isEmpty {
                    Section {
                        Label("iOS ограничивает работу приложений в фоне: при сворачивании TwinFTP передача продолжается около 30 секунд, затем очередь ставится на паузу и автоматически продолжится, когда вы вернётесь.",
                              systemImage: "info.circle")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Передачи")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Закрыть") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { transfers.pauseAll() } label: { Label("Приостановить все", systemImage: "pause") }
                            .disabled(activeJobs.isEmpty)
                        Button(role: .destructive) { transfers.clearFinished() } label: { Label("Очистить историю", systemImage: "trash") }
                            .disabled(finishedJobs.isEmpty)
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
        }
    }
}

struct TransferJobRow: View {
    let job: TransferJob
    @EnvironmentObject var transfers: TransferManager

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: job.direction.symbol)
                    .font(.system(size: 22))
                    .foregroundStyle(stateColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text(job.title).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                    Text("\(job.direction == .upload ? "→" : "←") \(job.serverName): \(job.direction == .upload ? job.destinationDirectory : job.sourceDirectory)")
                        .font(.system(size: 11.5)).foregroundStyle(Theme.textSecondary).lineLimit(1).truncationMode(.middle)
                }
                Spacer(minLength: 4)
                controls
            }
            if !job.state.isFinished || job.state == .completedWithErrors {
                ProgressView(value: job.progress)
                    .tint(stateColor)
            }
            HStack(spacing: 6) {
                Text(statusLine)
                Spacer()
                if job.state == .running, job.bytesPerSecond > 0 {
                    Text(ByteFormatter.speed(job.bytesPerSecond))
                    if let eta = job.estimatedSecondsRemaining { Text("· ≈\(ByteFormatter.duration(eta))") }
                }
            }
            .font(.system(size: 11.5).monospacedDigit())
            .foregroundStyle(Theme.textSecondary)
            if let note = job.statusNote ?? job.errorMessage {
                Text(note).font(.system(size: 11.5)).foregroundStyle(job.errorMessage != nil ? Theme.danger : Theme.textSecondary)
                    .lineLimit(3)
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder private var controls: some View {
        switch job.state {
        case .running, .queued:
            Button { transfers.pause(job.id) } label: { Image(systemName: "pause.circle.fill").font(.system(size: 26)) }
                .buttonStyle(.plain).foregroundStyle(Color.accentColor)
                .accessibilityLabel("Пауза")
        case .paused:
            Button { transfers.resume(job.id) } label: { Image(systemName: "play.circle.fill").font(.system(size: 26)) }
                .buttonStyle(.plain).foregroundStyle(Color.accentColor)
                .accessibilityLabel("Продолжить")
        case .completedWithErrors, .failed, .cancelled:
            Button { transfers.retry(job.id) } label: { Image(systemName: "arrow.clockwise.circle.fill").font(.system(size: 26)) }
                .buttonStyle(.plain).foregroundStyle(Theme.warning)
                .accessibilityLabel("Повторить сбойные")
        case .completed:
            Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).foregroundStyle(Theme.success)
        case .preparing:
            ProgressView().controlSize(.small)
        }
    }

    private var stateColor: Color {
        switch job.state {
        case .completed: return Theme.success
        case .completedWithErrors, .paused: return Theme.warning
        case .failed, .cancelled: return Theme.danger
        default: return Color.accentColor
        }
    }

    private var statusLine: String {
        let files = job.tasks.count
        var parts = [job.state.title]
        if files > 0 {
            parts.append("\(job.completedCount)/\(files) \(RussianPlural.files(files))")
            parts.append("\(ByteFormatter.string(job.transferredBytes)) из \(ByteFormatter.string(job.totalBytes))")
        }
        if job.failedCount > 0 { parts.append("ошибок: \(job.failedCount)") }
        if job.skippedCount > 0 { parts.append("пропущено: \(job.skippedCount)") }
        return parts.joined(separator: " · ")
    }
}

/// Подробности задания: список файлов с состояниями и ошибками.
struct TransferJobDetailView: View {
    let jobID: UUID
    @EnvironmentObject var transfers: TransferManager
    @State private var showOnlyProblems = false

    var body: some View {
        if let job = transfers.jobs.first(where: { $0.id == jobID }) {
            List {
                Section { TransferJobRow(job: job) }
                Section {
                    Toggle("Только ошибки и пропуски", isOn: $showOnlyProblems)
                }
                Section("Файлы (\(job.tasks.count))") {
                    let tasks = showOnlyProblems ? job.tasks.filter { $0.state == .failed || $0.state == .skipped || $0.errorMessage != nil } : job.tasks
                    ForEach(tasks.prefix(2000)) { task in
                        TaskRow(task: task)
                    }
                    if tasks.count > 2000 {
                        Text("… и ещё \(tasks.count - 2000)").foregroundStyle(Theme.textSecondary)
                    }
                }
            }
            .navigationTitle(job.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        if job.state == .running || job.state == .queued {
                            Button { transfers.pause(job.id) } label: { Label("Пауза", systemImage: "pause") }
                        }
                        if job.state == .paused {
                            Button { transfers.resume(job.id) } label: { Label("Продолжить", systemImage: "play") }
                        }
                        if job.failedCount > 0 || job.state == .failed || job.state == .cancelled {
                            Button { transfers.retry(job.id) } label: { Label("Повторить сбойные", systemImage: "arrow.clockwise") }
                        }
                        if !job.state.isFinished {
                            Button(role: .destructive) { transfers.cancel(job.id) } label: { Label("Отменить", systemImage: "xmark") }
                        }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
        } else {
            Text("Задание удалено").foregroundStyle(Theme.textSecondary)
        }
    }
}

private struct TaskRow: View {
    let task: TransferTask

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                icon
                Text(task.destinationName).font(.system(size: 13.5, weight: .medium)).lineLimit(1).truncationMode(.middle)
                Spacer()
                if let size = task.size { Text(ByteFormatter.string(size)).font(.system(size: 11).monospacedDigit()).foregroundStyle(Theme.textSecondary) }
            }
            if task.state == .running, let size = task.size, size > 0 {
                ProgressView(value: Double(task.transferred), total: Double(size))
            }
            Text(task.destinationDirectory).font(.system(size: 10.5)).foregroundStyle(Theme.textTertiary).lineLimit(1).truncationMode(.middle)
            if let err = task.errorMessage {
                Text(err).font(.system(size: 11)).foregroundStyle(task.state == .failed ? Theme.danger : Theme.warning)
            }
        }
    }

    @ViewBuilder private var icon: some View {
        switch task.state {
        case .pending: Image(systemName: "clock").foregroundStyle(Theme.textTertiary)
        case .running: ProgressView().controlSize(.mini)
        case .completed: Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.success)
        case .skipped: Image(systemName: "arrow.uturn.right.circle").foregroundStyle(Theme.textSecondary)
        case .failed: Image(systemName: "exclamationmark.circle.fill").foregroundStyle(Theme.danger)
        case .cancelled: Image(systemName: "xmark.circle").foregroundStyle(Theme.textSecondary)
        }
    }
}

/// Диалог конфликта имён.
struct ConflictDialog: View {
    let conflict: PendingConflict
    @EnvironmentObject var transfers: TransferManager
    @State private var applyToAll = false

    var body: some View {
        let info = conflict.info
        VStack(spacing: 16) {
            Capsule().fill(Theme.textTertiary.opacity(0.4)).frame(width: 36, height: 5).padding(.top, 8)
            Image(systemName: "doc.on.doc.fill")
                .font(.system(size: 30))
                .foregroundStyle(Theme.warning)
            VStack(spacing: 4) {
                Text("Файл уже существует").font(.system(size: 18, weight: .bold))
                Text(info.name).font(.system(size: 14, weight: .medium)).multilineTextAlignment(.center).lineLimit(2)
                Text(info.direction == .upload ? "на сервере" : "на iPhone").font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
            }
            HStack(spacing: 10) {
                compareCard(title: "Новый", size: info.sourceSize, date: info.sourceDate, highlight: true)
                compareCard(title: "Существующий", size: info.destinationSize, date: info.destinationDate, highlight: false)
            }
            if info.remainingInJob > 1 {
                Toggle(isOn: $applyToAll) {
                    Text("Применить ко всем конфликтам в этом задании").font(.system(size: 13.5))
                }
                .tint(.accentColor)
            }
            VStack(spacing: 8) {
                Button { decide(.replace) } label: { Text("Заменить") }
                    .buttonStyle(AccentButtonStyle())
                HStack(spacing: 8) {
                    Button { decide(.rename) } label: { Text("Сохранить оба").frame(maxWidth: .infinity, minHeight: 44) }
                        .buttonStyle(.bordered)
                    Button { decide(.skip) } label: { Text("Пропустить").frame(maxWidth: .infinity, minHeight: 44) }
                        .buttonStyle(.bordered)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .presentationDetents([.height(460), .large])
        .interactiveDismissDisabled()
    }

    private func decide(_ action: ConflictAction) {
        Haptics.tap()
        transfers.resolveConflict(ConflictDecision(action: action, applyToAll: applyToAll))
    }

    private func compareCard(title: String, size: Int64?, date: Date?, highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 12, weight: .semibold)).foregroundStyle(highlight ? Color.accentColor : Theme.textSecondary)
            Text(size.map { ByteFormatter.string($0) } ?? "—").font(.system(size: 14, weight: .semibold).monospacedDigit())
            Text(date.map { DateText.short($0) } ?? "дата неизвестна").font(.system(size: 11.5)).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(highlight ? Color.accentColor.opacity(0.1) : Theme.surfaceSunken))
    }
}
