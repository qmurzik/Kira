extends Node
## Общий визуальный стиль интерфейса: шрифты, тема, цвета, фабрики виджетов.

signal theme_rebuilt

const FONT_UI := preload("res://assets/fonts/Nunito.ttf")
const FONT_TITLE := preload("res://assets/fonts/Comfortaa.ttf")
const FONT_SCRIPT := preload("res://assets/fonts/MarckScript.ttf")
const FONT_MONO := preload("res://assets/fonts/JetBrainsMono.ttf")

const TEXT := Color("#f1ebff")
const TEXT_DIM := Color("#b9aed8")

var font_regular: FontVariation
var font_bold: FontVariation
var font_title: FontVariation
var font_script: Font
var font_mono: FontVariation
var theme: Theme
var accent := Color("#b49aff")
var accent2 := Color("#ff9ecf")
var panel := Color("#1d1838")


func _ready() -> void:
	font_regular = _var(FONT_UI, 560)
	font_bold = _var(FONT_UI, 800)
	font_title = _var(FONT_TITLE, 700)
	font_mono = _var(FONT_MONO, 500)
	font_script = FONT_SCRIPT
	rebuild()
	InventoryManager.theme_changed.connect(func(_id): rebuild())


func _var(base: Font, weight: int) -> FontVariation:
	var f := FontVariation.new()
	f.base_font = base
	f.variation_opentype = {"wght": weight}
	return f


func icon(name: String) -> Texture2D:
	var p := "res://assets/ui/%s.svg" % name
	return load(p) if ResourceLoader.exists(p) else null


func rebuild() -> void:
	var t: Dictionary = InventoryManager.info("themes", InventoryManager.ui_theme)
	if not t.is_empty():
		accent = Color(t.accent)
		accent2 = Color(t.accent2)
		panel = Color(t.panel)
	theme = Theme.new()
	theme.default_font = font_regular
	theme.default_font_size = 26
	# Кнопки
	theme.set_stylebox("normal", "Button", box(panel.lightened(0.06), 0.78, accent, 0.45, 18))
	theme.set_stylebox("hover", "Button", box(panel.lightened(0.16), 0.9, accent, 1.0, 18, 14))
	theme.set_stylebox("pressed", "Button", box(panel.darkened(0.2), 0.95, accent2, 1.0, 18))
	theme.set_stylebox("focus", "Button", box(Color(0, 0, 0, 0), 0.0, accent2, 0.9, 18))
	theme.set_stylebox("disabled", "Button", box(panel, 0.45, Color("#6a6080"), 0.4, 18))
	theme.set_color("font_color", "Button", TEXT)
	theme.set_color("font_hover_color", "Button", Color.WHITE)
	theme.set_color("font_pressed_color", "Button", accent2.lightened(0.4))
	theme.set_color("font_disabled_color", "Button", Color("#8a80a8"))
	theme.set_color("font_focus_color", "Button", Color.WHITE)
	theme.set_font("font", "Button", font_bold)
	theme.set_font_size("font_size", "Button", 26)
	# Панели
	theme.set_stylebox("panel", "PanelContainer", box(panel, 0.93, accent, 0.55, 26, 24))
	theme.set_stylebox("panel", "Panel", box(panel, 0.93, accent, 0.55, 26, 24))
	theme.set_color("font_color", "Label", TEXT)
	theme.set_color("default_color", "RichTextLabel", TEXT)
	theme.set_font("normal_font", "RichTextLabel", font_regular)
	theme.set_font("bold_font", "RichTextLabel", font_bold)
	theme.set_font("italics_font", "RichTextLabel", font_regular)
	theme.set_font_size("normal_font_size", "RichTextLabel", 28)
	theme.set_font_size("bold_font_size", "RichTextLabel", 28)
	theme.set_font_size("italics_font_size", "RichTextLabel", 28)
	# Ползунки
	var slider_bg := box(Color("#0e0b1e"), 0.9, accent, 0.4, 8)
	slider_bg.content_margin_top = 6
	slider_bg.content_margin_bottom = 6
	theme.set_stylebox("slider", "HSlider", slider_bg)
	theme.set_stylebox("grabber_area", "HSlider", box(accent, 0.85, accent, 0.0, 8))
	theme.set_stylebox("grabber_area_highlight", "HSlider", box(accent2, 0.9, accent2, 0.0, 8))
	var g := icon("grabber")
	if g:
		theme.set_icon("grabber", "HSlider", g)
		theme.set_icon("grabber_highlight", "HSlider", g)
	# Флажки
	var on := icon("check_on")
	var off := icon("check_off")
	if on and off:
		for cls in ["CheckBox", "CheckButton"]:
			theme.set_icon("checked", cls, on)
			theme.set_icon("unchecked", cls, off)
			theme.set_icon("checked_disabled", cls, on)
			theme.set_icon("unchecked_disabled", cls, off)
			theme.set_stylebox("normal", cls, StyleBoxEmpty.new())
			theme.set_stylebox("hover", cls, StyleBoxEmpty.new())
			theme.set_stylebox("pressed", cls, StyleBoxEmpty.new())
			theme.set_stylebox("focus", cls, StyleBoxEmpty.new())
			theme.set_color("font_color", cls, TEXT)
			theme.set_color("font_hover_color", cls, Color.WHITE)
			theme.set_color("font_pressed_color", cls, TEXT)
	# Выпадающие списки и меню
	theme.set_stylebox("panel", "PopupMenu", box(panel, 0.97, accent, 0.6, 14, 6))
	theme.set_stylebox("hover", "PopupMenu", box(accent, 0.35, accent, 0.0, 10))
	theme.set_color("font_color", "PopupMenu", TEXT)
	theme.set_font_size("font_size", "PopupMenu", 24)
	theme.set_stylebox("normal", "OptionButton", box(panel.lightened(0.06), 0.8, accent, 0.45, 14))
	theme.set_stylebox("hover", "OptionButton", box(panel.lightened(0.16), 0.9, accent, 1.0, 14))
	theme.set_stylebox("pressed", "OptionButton", box(panel.darkened(0.2), 0.95, accent2, 1.0, 14))
	theme.set_stylebox("focus", "OptionButton", StyleBoxEmpty.new())
	theme.set_font_size("font_size", "OptionButton", 24)
	# Поле ввода
	theme.set_stylebox("normal", "LineEdit", box(Color("#0e0b1e"), 0.9, accent, 0.6, 14))
	theme.set_stylebox("focus", "LineEdit", box(Color("#0e0b1e"), 0.95, accent2, 1.0, 14))
	theme.set_color("font_color", "LineEdit", TEXT)
	theme.set_color("caret_color", "LineEdit", accent2)
	theme.set_font_size("font_size", "LineEdit", 32)
	# Прокрутка
	theme.set_stylebox("scroll", "VScrollBar", box(Color("#0e0b1e"), 0.5, accent, 0.0, 6))
	theme.set_stylebox("grabber", "VScrollBar", box(accent, 0.6, accent, 0.0, 6))
	theme.set_stylebox("grabber_highlight", "VScrollBar", box(accent2, 0.8, accent, 0.0, 6))
	theme.set_stylebox("grabber_pressed", "VScrollBar", box(accent2, 1.0, accent, 0.0, 6))
	# Вкладки
	theme.set_stylebox("tab_selected", "TabBar", box(accent, 0.35, accent, 1.0, 14))
	theme.set_stylebox("tab_unselected", "TabBar", box(panel, 0.6, accent, 0.25, 14))
	theme.set_stylebox("tab_hovered", "TabBar", box(panel.lightened(0.15), 0.8, accent, 0.7, 14))
	theme.set_font_size("font_size", "TabBar", 24)
	theme.set_stylebox("tooltip", "TooltipPanel", box(panel, 0.95, accent, 0.8, 10, 4))
	theme.set_stylebox("panel", "TooltipPanel", box(panel, 0.95, accent, 0.8, 10, 4))
	theme.set_font_size("font_size", "TooltipLabel", 22)
	get_tree().root.theme = theme
	# Узлы CanvasLayer прерывают наследование темы, поэтому вливаем стиль в тему по умолчанию
	ThemeDB.get_default_theme().merge_with(theme)
	ThemeDB.fallback_font = font_regular
	ThemeDB.fallback_font_size = 26
	theme_rebuilt.emit()


## Скруглённая панель с рамкой и мягкой тенью-свечением.
func box(bg: Color, alpha: float, border: Color, border_alpha: float, radius := 18, shadow := 0) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = Color(bg.r, bg.g, bg.b, alpha)
	s.set_corner_radius_all(radius)
	s.border_color = Color(border.r, border.g, border.b, border_alpha)
	s.set_border_width_all(2 if border_alpha > 0.0 else 0)
	s.content_margin_left = 22
	s.content_margin_right = 22
	s.content_margin_top = 12
	s.content_margin_bottom = 12
	s.anti_aliasing = true
	if shadow > 0:
		s.shadow_color = Color(accent.r, accent.g, accent.b, 0.28)
		s.shadow_size = shadow
	return s


func label(text: String, size := 26, color := TEXT, font: Font = null) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	if font:
		l.add_theme_font_override("font", font)
	return l


func title(text: String, size := 44) -> Label:
	var l := label(text, size, Color.WHITE, font_title)
	l.add_theme_color_override("font_shadow_color", Color(accent.r, accent.g, accent.b, 0.6))
	l.add_theme_constant_override("shadow_offset_x", 0)
	l.add_theme_constant_override("shadow_offset_y", 0)
	l.add_theme_constant_override("shadow_outline_size", 14)
	return l


func button(text: String, callback: Callable = Callable(), icon_name := "") -> Button:
	var b: Button = preload("res://scripts/ui/fancy_button.gd").new()
	b.text = text
	if icon_name != "":
		b.icon = icon(icon_name)
		b.expand_icon = false
		b.add_theme_constant_override("h_separation", 12)
	if callback.is_valid():
		b.pressed.connect(callback)
	return b


func hex(c: Color) -> String:
	return c.to_html(false)
