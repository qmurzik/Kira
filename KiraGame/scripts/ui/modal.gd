extends Control
## Базовое модальное окно: затемнение, панель с заголовком и кнопкой закрытия.

signal closed

var panel: PanelContainer
var body: VBoxContainer
var title_label: Label
var panel_size := Vector2(1200, 760)
var closable := true


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	process_mode = Node.PROCESS_MODE_ALWAYS
	var dim := ColorRect.new()
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.color = Color(0.03, 0.02, 0.08, 0.6)
	dim.gui_input.connect(func(e):
		if closable and e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT:
			close())
	add_child(dim)
	panel = PanelContainer.new()
	panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	panel.offset_left = -panel_size.x / 2
	panel.offset_right = panel_size.x / 2
	panel.offset_top = -panel_size.y / 2
	panel.offset_bottom = panel_size.y / 2
	var sb := UIKit.box(UIKit.panel, 0.96, UIKit.accent, 0.7, 28, 30)
	sb.content_margin_left = 40
	sb.content_margin_right = 40
	sb.content_margin_top = 28
	sb.content_margin_bottom = 30
	panel.add_theme_stylebox_override("panel", sb)
	add_child(panel)
	var outer := VBoxContainer.new()
	outer.add_theme_constant_override("separation", 18)
	panel.add_child(outer)
	var head := HBoxContainer.new()
	outer.add_child(head)
	title_label = UIKit.title("", 40)
	title_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(title_label)
	if closable:
		var x := UIKit.button("✕", close)
		x.custom_minimum_size = Vector2(56, 52)
		x.focus_mode = Control.FOCUS_NONE
		head.add_child(x)
	var line := ColorRect.new()
	line.custom_minimum_size = Vector2(0, 2)
	line.color = Color(UIKit.accent, 0.35)
	outer.add_child(line)
	body = VBoxContainer.new()
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_theme_constant_override("separation", 14)
	outer.add_child(body)
	panel.pivot_offset = panel_size / 2
	panel.scale = Vector2(0.94, 0.94)
	modulate.a = 0.0
	var t := create_tween().set_parallel(true).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_property(self, "modulate:a", 1.0, 0.18)
	t.tween_property(panel, "scale", Vector2.ONE, 0.25)
	AudioManager.play_sfx("open", 1.0, -4.0)
	build()


## Переопределяется наследниками.
func build() -> void:
	pass


func set_title(t: String) -> void:
	title_label.text = t


func close() -> void:
	if not is_inside_tree():
		return
	AudioManager.play_sfx("close", 1.0, -6.0)
	var t := create_tween()
	t.tween_property(self, "modulate:a", 0.0, 0.15)
	await t.finished
	closed.emit()
	queue_free()


func _unhandled_input(event: InputEvent) -> void:
	if closable and event.is_action_pressed("pause"):
		get_viewport().set_input_as_handled()
		close()


## Прокручиваемая область для длинных списков.
func scroll_area() -> VBoxContainer:
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(sc)
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_theme_constant_override("separation", 12)
	sc.add_child(v)
	return v


func card(min_h := 0) -> PanelContainer:
	var p := PanelContainer.new()
	var sb := UIKit.box(UIKit.panel.lightened(0.07), 0.85, UIKit.accent, 0.3, 18)
	sb.content_margin_left = 20
	sb.content_margin_right = 20
	p.add_theme_stylebox_override("panel", sb)
	p.custom_minimum_size.y = min_h
	return p


func wrap_label(text: String, size := 24, color := UIKit.TEXT) -> Label:
	var l := UIKit.label(text, size, color)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return l
