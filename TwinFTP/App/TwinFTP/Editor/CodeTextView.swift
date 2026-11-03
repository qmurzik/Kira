import SwiftUI
import UIKit
import TwinCore

/// Цвета подсветки синтаксиса.
enum SyntaxPalette {
    static func color(_ kind: SyntaxTokenKind) -> UIColor {
        func dyn(_ light: UInt32, _ dark: UInt32) -> UIColor {
            UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(dark) : Theme.hex(light) }
        }
        switch kind {
        case .comment: return dyn(0x8A8F98, 0x6F7684)
        case .string: return dyn(0xC2410C, 0xF2A65A)
        case .keyword: return dyn(0x7C3AED, 0xC792EA)
        case .number: return dyn(0x0F766E, 0x7FDBCA)
        case .tag: return dyn(0x2563EB, 0x82AAFF)
        case .attribute: return dyn(0xB45309, 0xFFCB6B)
        case .variable: return dyn(0xBE185D, 0xF07178)
        case .property: return dyn(0x0369A1, 0x89DDFF)
        case .meta: return dyn(0xDC2626, 0xFF5370)
        case .function: return dyn(0x1D4ED8, 0x82AAFF)
        case .constant: return dyn(0x9333EA, 0xF78C6C)
        }
    }
    static let text = UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0xE4E2EC) : Theme.hex(0x1F1D26) }
    static let background = UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0x16151B) : Theme.hex(0xFFFFFF) }
    static let gutter = UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0x1C1B22) : Theme.hex(0xF5F4F9) }
    static let gutterText = UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0x5E5B6B) : Theme.hex(0xA3A0B0) }
    static let currentLine = UIColor { $0.userInterfaceStyle == .dark ? Theme.hex(0xFFFFFF, 0.05) : Theme.hex(0x7C57E6, 0.06) }
}

/// UITextView на TextKit 1 с номерами строк. Номера рисуются отдельным видом поверх левого поля.
final class CodeEditorTextView: UITextView {
    let gutter = LineNumberGutter()
    var showsLineNumbers = true { didSet { updateInsets(); gutter.isHidden = !showsLineNumbers } }
    /// Начала строк (UTF-16 смещения) — для номеров и перехода к строке.
    private(set) var lineStarts: [Int] = [0]

    init() {
        let storage = NSTextStorage()
        let layout = NSLayoutManager()
        storage.addLayoutManager(layout)
        let container = NSTextContainer(size: CGSize(width: 0, height: CGFloat.greatestFiniteMagnitude))
        container.widthTracksTextView = true
        layout.addTextContainer(container)
        super.init(frame: .zero, textContainer: container)
        layout.allowsNonContiguousLayout = true
        gutter.textView = self
        addSubview(gutter)
        alwaysBounceVertical = true
        keyboardDismissMode = .interactive
        autocapitalizationType = .none
        autocorrectionType = .no
        smartDashesType = .no
        smartQuotesType = .no
        smartInsertDeleteType = .no
        spellCheckingType = .no
        dataDetectorTypes = []
        isFindInteractionEnabled = true
        backgroundColor = SyntaxPalette.background
        updateInsets()
    }

    required init?(coder: NSCoder) { fatalError() }

    var gutterWidth: CGFloat {
        guard showsLineNumbers else { return 0 }
        let digits = max(2, String(lineStarts.count).count)
        let font = UIFont.monospacedDigitSystemFont(ofSize: max(10, (self.font?.pointSize ?? 13) - 2), weight: .regular)
        return CGFloat(digits) * ("8" as NSString).size(withAttributes: [.font: font]).width + 14
    }

    func updateInsets() {
        textContainerInset = UIEdgeInsets(top: 10, left: gutterWidth + 6, bottom: 40, right: 8)
        setNeedsLayout()
    }

    func recomputeLineStarts() {
        let ns = text as NSString
        var starts = [0]
        starts.reserveCapacity(ns.length / 30 + 1)
        let len = ns.length
        var buffer = [unichar](repeating: 0, count: min(len, 1 << 16))
        var pos = 0
        while pos < len {
            let n = min(buffer.count, len - pos)
            ns.getCharacters(&buffer, range: NSRange(location: pos, length: n))
            for i in 0..<n where buffer[i] == 10 { starts.append(pos + i + 1) }
            pos += n
        }
        let oldDigits = String(lineStarts.count).count
        lineStarts = starts
        if String(starts.count).count != oldDigits { updateInsets() }
        gutter.setNeedsDisplay()
    }

    /// Номер строки (с 1) для UTF-16 смещения.
    func line(forOffset offset: Int) -> Int {
        var lo = 0, hi = lineStarts.count - 1
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if lineStarts[mid] <= offset { lo = mid } else { hi = mid - 1 }
        }
        return lo + 1
    }

    func offset(forLine line: Int) -> Int {
        let idx = max(0, min(lineStarts.count - 1, line - 1))
        return lineStarts[idx]
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        gutter.frame = CGRect(x: contentOffset.x, y: contentOffset.y, width: gutterWidth, height: bounds.height)
        gutter.setNeedsDisplay()
    }
}

/// Колонка номеров строк.
final class LineNumberGutter: UIView {
    weak var textView: CodeEditorTextView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        isUserInteractionEnabled = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    override func draw(_ rect: CGRect) {
        guard let tv = textView, let ctx = UIGraphicsGetCurrentContext() else { return }
        ctx.setFillColor(SyntaxPalette.gutter.cgColor)
        ctx.fill(bounds)
        ctx.setFillColor(Theme.hex(0x888888, 0.18).cgColor)
        ctx.fill(CGRect(x: bounds.width - 0.5, y: 0, width: 0.5, height: bounds.height))

        let lm = tv.layoutManager
        let container = tv.textContainer
        let inset = tv.textContainerInset
        let visible = CGRect(x: 0, y: tv.contentOffset.y - inset.top, width: tv.bounds.width, height: tv.bounds.height)
        let glyphRange = lm.glyphRange(forBoundingRect: visible, in: container)
        let charRange = lm.characterRange(forGlyphRange: glyphRange, actualGlyphRange: nil)
        let fontSize = max(10, (tv.font?.pointSize ?? 13) - 2)
        let font = UIFont.monospacedDigitSystemFont(ofSize: fontSize, weight: .regular)
        let currentLine = tv.line(forOffset: tv.selectedRange.location)
        let ns = tv.text as NSString

        var line = tv.line(forOffset: charRange.location)
        var offset = tv.offset(forLine: line)
        let end = NSMaxRange(charRange)
        while offset <= end && offset <= ns.length {
            let glyphIndex = lm.glyphIndexForCharacter(at: min(offset, max(0, ns.length - 1)))
            var fragment: CGRect
            if ns.length == 0 || offset >= ns.length {
                fragment = lm.extraLineFragmentRect
                if fragment == .zero && ns.length > 0 {
                    fragment = lm.lineFragmentRect(forGlyphAt: max(0, lm.numberOfGlyphs - 1), effectiveRange: nil)
                    if offset >= ns.length && ns.character(at: ns.length - 1) != 10 { break }
                }
            } else {
                fragment = lm.lineFragmentRect(forGlyphAt: glyphIndex, effectiveRange: nil)
            }
            let y = fragment.minY + inset.top - tv.contentOffset.y
            if y > bounds.height { break }
            let isCurrent = line == currentLine
            let attrs: [NSAttributedString.Key: Any] = [
                .font: isCurrent ? UIFont.monospacedDigitSystemFont(ofSize: fontSize, weight: .semibold) : font,
                .foregroundColor: isCurrent ? (tv.tintColor ?? SyntaxPalette.gutterText) : SyntaxPalette.gutterText
            ]
            let label = "\(line)" as NSString
            let size = label.size(withAttributes: attrs)
            let lineHeight = (tv.font?.lineHeight ?? 16)
            label.draw(at: CGPoint(x: bounds.width - size.width - 7, y: y + (lineHeight - size.height) / 2), withAttributes: attrs)
            line += 1
            if line - 1 >= tv.lineStarts.count { break }
            offset = tv.offset(forLine: line)
        }
    }
}

/// Панель символов над клавиатурой.
final class KeyboardAccessoryBar: UIInputView {
    var onInsert: ((String) -> Void)?
    var onAction: ((String) -> Void)?

    init(tint: UIColor) {
        super.init(frame: CGRect(x: 0, y: 0, width: 320, height: 46), inputViewStyle: .keyboard)
        allowsSelfSizing = true
        let scroll = UIScrollView()
        scroll.showsHorizontalScrollIndicator = false
        scroll.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scroll)
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 5
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: trailingAnchor),
            scroll.topAnchor.constraint(equalTo: topAnchor),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor),
            heightAnchor.constraint(equalToConstant: 46),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 6),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -6),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 5),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -5),
            stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor, constant: -10)
        ])

        func button(title: String? = nil, symbol: String? = nil, accessibility: String, handler: @escaping () -> Void) -> UIButton {
            var config = UIButton.Configuration.filled()
            config.baseBackgroundColor = UIColor.secondarySystemBackground
            config.baseForegroundColor = symbol != nil ? tint : UIColor.label
            config.cornerStyle = .medium
            config.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 10, bottom: 4, trailing: 10)
            if let title {
                var attr = AttributedString(title)
                attr.font = UIFont.monospacedSystemFont(ofSize: 17, weight: .medium)
                config.attributedTitle = attr
            }
            if let symbol {
                config.image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 15, weight: .semibold))
            }
            let b = UIButton(configuration: config, primaryAction: UIAction { _ in
                UIDevice.current.playInputClick()
                handler()
            })
            b.accessibilityLabel = accessibility
            b.widthAnchor.constraint(greaterThanOrEqualToConstant: 38).isActive = true
            return b
        }

        stack.addArrangedSubview(button(symbol: "keyboard.chevron.compact.down", accessibility: "Скрыть клавиатуру") { [weak self] in self?.onAction?("hide") })
        stack.addArrangedSubview(button(symbol: "arrow.uturn.backward", accessibility: "Отменить") { [weak self] in self?.onAction?("undo") })
        stack.addArrangedSubview(button(symbol: "arrow.uturn.forward", accessibility: "Повторить") { [weak self] in self?.onAction?("redo") })
        stack.addArrangedSubview(button(symbol: "arrow.right.to.line", accessibility: "Табуляция") { [weak self] in self?.onAction?("tab") })
        for s in ["<", ">", "/", "{", "}", "[", "]", "(", ")", "=", ";", ":", "'", "\"", "$", "-", "_", "#", "&", "|", "!", "?", "*", "+", "%", "\\", "@", "`", "~", "^"] {
            stack.addArrangedSubview(button(title: s, accessibility: "Символ \(s)") { [weak self] in self?.onInsert?(s) })
        }
        stack.addArrangedSubview(button(symbol: "magnifyingglass", accessibility: "Найти и заменить") { [weak self] in self?.onAction?("find") })
    }

    required init?(coder: NSCoder) { fatalError() }
}

/// SwiftUI-обёртка редактора с подсветкой, автоотступами и панелью символов.
struct CodeTextView: UIViewRepresentable {
    @ObservedObject var document: EditorDocument
    var fontSize: CGFloat
    var wrapLines: Bool
    var showLineNumbers: Bool
    var highlighting: Bool
    var tabWidth: Int
    var useSpaces: Bool
    var tint: UIColor
    var command: Binding<EditorCommand?>

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> CodeEditorTextView {
        let tv = CodeEditorTextView()
        tv.delegate = context.coordinator
        tv.tintColor = tint
        let bar = KeyboardAccessoryBar(tint: tint)
        bar.onInsert = { [weak tv] s in tv?.insertText(s) }
        bar.onAction = { [weak tv, weak coordinator = context.coordinator] action in
            guard let tv else { return }
            switch action {
            case "hide": tv.resignFirstResponder()
            case "undo": tv.undoManager?.undo()
            case "redo": tv.undoManager?.redo()
            case "tab": tv.insertText(coordinator?.indentUnit ?? "    ")
            case "find": tv.findInteraction?.presentFindNavigator(showingReplace: true)
            default: break
            }
        }
        tv.inputAccessoryView = bar
        context.coordinator.textView = tv
        context.coordinator.apply(settingsFrom: self, to: tv, force: true)
        context.coordinator.loadText(into: tv)
        return tv
    }

    func updateUIView(_ tv: CodeEditorTextView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.apply(settingsFrom: self, to: tv, force: false)
        if context.coordinator.loadedToken != document.reloadToken {
            context.coordinator.loadText(into: tv)
        }
        tv.isEditable = !document.readOnly
        if let cmd = command.wrappedValue {
            DispatchQueue.main.async {
                command.wrappedValue = nil
                context.coordinator.perform(cmd, in: tv)
            }
        }
    }

    static func dismantleUIView(_ tv: CodeEditorTextView, coordinator: Coordinator) {
        coordinator.flush()
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: CodeTextView
        weak var textView: CodeEditorTextView?
        var loadedToken: UUID?
        private var highlightWork: DispatchWorkItem?
        private var syncWork: DispatchWorkItem?
        private var lastFontSize: CGFloat = 0
        private var lastWrap: Bool?
        private var lastHighlight: Bool?
        private var lastLanguage: SyntaxLanguage?
        private var highlightGeneration = 0
        private static let highlightQueue = DispatchQueue(label: "twinftp.highlight", qos: .userInitiated)

        init(_ parent: CodeTextView) { self.parent = parent }

        var indentUnit: String {
            parent.useSpaces ? String(repeating: " ", count: max(1, parent.tabWidth)) : "\t"
        }

        var baseFont: UIFont { UIFont.monospacedSystemFont(ofSize: parent.fontSize, weight: .regular) }

        var baseAttributes: [NSAttributedString.Key: Any] {
            let para = NSMutableParagraphStyle()
            para.lineHeightMultiple = 1.08
            let tabStop = ("    " as NSString).size(withAttributes: [.font: baseFont]).width / 4 * CGFloat(max(1, parent.tabWidth))
            para.defaultTabInterval = tabStop
            para.tabStops = []
            return [.font: baseFont, .foregroundColor: SyntaxPalette.text, .paragraphStyle: para]
        }

        func apply(settingsFrom p: CodeTextView, to tv: CodeEditorTextView, force: Bool) {
            var needsRestyle = force
            if p.fontSize != lastFontSize {
                lastFontSize = p.fontSize
                tv.font = baseFont
                needsRestyle = true
            }
            if p.wrapLines != lastWrap {
                lastWrap = p.wrapLines
                tv.textContainer.widthTracksTextView = p.wrapLines
                if !p.wrapLines {
                    tv.textContainer.size = CGSize(width: 100_000, height: CGFloat.greatestFiniteMagnitude)
                }
                tv.layoutManager.invalidateLayout(forCharacterRange: NSRange(location: 0, length: (tv.text as NSString).length), actualCharacterRange: nil)
            }
            if p.highlighting != lastHighlight || p.document.language != lastLanguage {
                lastHighlight = p.highlighting
                lastLanguage = p.document.language
                needsRestyle = true
            }
            tv.showsLineNumbers = p.showLineNumbers
            tv.typingAttributes = baseAttributes
            if needsRestyle && !force { scheduleHighlight(immediate: true) }
        }

        func loadText(into tv: CodeEditorTextView) {
            loadedToken = parent.document.reloadToken
            tv.textStorage.setAttributedString(NSAttributedString(string: parent.document.text, attributes: baseAttributes))
            tv.undoManager?.removeAllActions()
            tv.recomputeLineStarts()
            tv.selectedRange = NSRange(location: 0, length: 0)
            tv.setContentOffset(.zero, animated: false)
            scheduleHighlight(immediate: true)
            updateCursor(tv)
        }

        func flush() {
            syncWork?.perform()
        }

        // MARK: Делегат

        func textViewDidChange(_ textView: UITextView) {
            guard let tv = textView as? CodeEditorTextView else { return }
            tv.recomputeLineStarts()
            syncWork?.cancel()
            let work = DispatchWorkItem { [weak self, weak tv] in
                guard let self, let tv else { return }
                self.parent.document.textDidChange(tv.text)
                self.parent.document.lineCount = tv.lineStarts.count
            }
            syncWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: work)
            // Быстрая отметка «изменён» без ожидания.
            if !parent.document.isDirty { parent.document.isDirty = true }
            scheduleHighlight(immediate: false)
            updateCursor(tv)
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard let tv = textView as? CodeEditorTextView else { return }
            updateCursor(tv)
            tv.gutter.setNeedsDisplay()
        }

        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            guard let tv = scrollView as? CodeEditorTextView else { return }
            tv.gutter.frame.origin = CGPoint(x: tv.contentOffset.x, y: tv.contentOffset.y)
            tv.gutter.setNeedsDisplay()
            if (tv.text as NSString).length > Self.fullHighlightLimit { scheduleHighlight(immediate: false) }
        }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
            // Автоотступ: новая строка наследует отступ текущей (+1 уровень после «{», «[», «(» или «:»).
            guard text == "\n", let tv = textView as? CodeEditorTextView else { return true }
            let ns = tv.text as NSString
            let lineRange = ns.lineRange(for: NSRange(location: range.location, length: 0))
            let lineText = ns.substring(with: NSRange(location: lineRange.location, length: max(0, range.location - lineRange.location)))
            let indent = String(lineText.prefix { $0 == " " || $0 == "\t" })
            var insertion = "\n" + indent
            let trimmed = lineText.trimmingCharacters(in: .whitespaces)
            if let last = trimmed.last, "{[(".contains(last) {
                insertion += indentUnit
                // Между скобками: переносим закрывающую на отдельную строку.
                if range.location < ns.length {
                    let next = Character(UnicodeScalar(ns.character(at: range.location)) ?? " ")
                    if (last == "{" && next == "}") || (last == "[" && next == "]") || (last == "(" && next == ")") {
                        tv.insertText(insertion + "\n" + indent)
                        let caret = range.location + (insertion as NSString).length
                        tv.selectedRange = NSRange(location: caret, length: 0)
                        return false
                    }
                }
            }
            if insertion == "\n" { return true }
            tv.insertText(insertion)
            return false
        }

        private func updateCursor(_ tv: CodeEditorTextView) {
            let loc = tv.selectedRange.location
            let line = tv.line(forOffset: loc)
            let col = loc - tv.offset(forLine: line) + 1
            let doc = parent.document
            if doc.cursorLine != line { doc.cursorLine = line }
            if doc.cursorColumn != col { doc.cursorColumn = col }
        }

        // MARK: Подсветка

        static let fullHighlightLimit = 250_000

        func scheduleHighlight(immediate: Bool) {
            highlightWork?.cancel()
            let work = DispatchWorkItem { [weak self] in self?.highlightNow() }
            highlightWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + (immediate ? 0 : 0.18), execute: work)
        }

        private func highlightNow() {
            guard let tv = textView else { return }
            if tv.markedTextRange != nil { scheduleHighlight(immediate: false); return }
            let storage = tv.textStorage
            let text = tv.text ?? ""
            let length = (text as NSString).length
            let language = parent.document.language
            let enabled = parent.highlighting && language != .plain
            highlightGeneration += 1
            let generation = highlightGeneration
            let base = baseAttributes

            // Для больших файлов — только видимая область (с запасом).
            var range = NSRange(location: 0, length: length)
            if length > Self.fullHighlightLimit {
                let visible = CGRect(x: 0, y: tv.contentOffset.y - 800, width: tv.bounds.width, height: tv.bounds.height + 1600)
                let glyphs = tv.layoutManager.glyphRange(forBoundingRect: visible, in: tv.textContainer)
                let chars = tv.layoutManager.characterRange(forGlyphRange: glyphs, actualGlyphRange: nil)
                range = (text as NSString).lineRange(for: chars)
            }
            guard enabled else {
                storage.beginEditing()
                storage.setAttributes(base, range: NSRange(location: 0, length: length))
                storage.endEditing()
                return
            }
            let substring = (text as NSString).substring(with: range)
            Self.highlightQueue.async { [weak self] in
                let tokens = SyntaxHighlighter.shared.tokens(for: substring, language: language)
                DispatchQueue.main.async {
                    guard let self, let tv = self.textView, generation == self.highlightGeneration,
                          (tv.text as NSString).length == length else { return }
                    let storage = tv.textStorage
                    storage.beginEditing()
                    storage.setAttributes(base, range: range)
                    for t in tokens {
                        let r = NSRange(location: t.range.location + range.location, length: t.range.length)
                        if NSMaxRange(r) <= length {
                            storage.addAttribute(.foregroundColor, value: SyntaxPalette.color(t.kind), range: r)
                            if t.kind == .comment {
                                storage.addAttribute(.font, value: UIFont.monospacedSystemFont(ofSize: self.parent.fontSize, weight: .regular).withTraits(.traitItalic), range: r)
                            }
                        }
                    }
                    storage.endEditing()
                    tv.typingAttributes = base
                }
            }
        }

        // MARK: Команды

        func perform(_ cmd: EditorCommand, in tv: CodeEditorTextView) {
            switch cmd {
            case .find(let replace):
                tv.findInteraction?.presentFindNavigator(showingReplace: replace)
            case .goToLine(let line):
                let offset = tv.offset(forLine: line)
                tv.becomeFirstResponder()
                tv.selectedRange = NSRange(location: offset, length: 0)
                tv.scrollRangeToVisible(NSRange(location: offset, length: 0))
                Haptics.selection()
            case .undo: tv.undoManager?.undo()
            case .redo: tv.undoManager?.redo()
            case .dismissKeyboard: tv.resignFirstResponder()
            case .reload: loadText(into: tv)
            }
        }
    }
}

enum EditorCommand: Equatable {
    case find(replace: Bool)
    case goToLine(Int)
    case undo, redo, dismissKeyboard, reload
}

extension UIFont {
    func withTraits(_ traits: UIFontDescriptor.SymbolicTraits) -> UIFont {
        guard let d = fontDescriptor.withSymbolicTraits(fontDescriptor.symbolicTraits.union(traits)) else { return self }
        return UIFont(descriptor: d, size: pointSize)
    }
}
