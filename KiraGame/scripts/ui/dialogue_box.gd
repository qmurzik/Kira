extends Control
## Диалоговое окно: печать текста с «голосом», варианты ответов, авто-режим,
## пропуск прочитанного, скрытие интерфейса, ввод имени игрока.

signal advanced
signal chosen(index: int)
signal name_entered(text: String)
signal typing_changed(on: bool)
signal history_requested

const HEART := "[img=30x30]res://assets/ui/heart.svg[/img]"

var _panel: PanelContainer
var _name_plate: PanelContainer
var _name_label: Label
var _text: RichTextLabel
var _next: Label
var _choices: VBoxContainer
var _toolbar: HBoxContainer
var _auto_btn: Button
var _skip_btn: Button
var _name_box: PanelContainer
var _name_edit: LineEdit
var _dim: ColorRect

var active := false
var typing := false
var waiting_choice := false
var auto_mode := false
var skip_mode := false
var hidden_ui := false
var _line := {}
var _chars := 0.0
var _total := 0
var _blip_acc := 0
var _auto_timer := 0.0
var _skip_timer := 0.0
var _t := 0.0


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_dim = ColorRect.new()
	_dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_dim.color = Color(0.03, 0.02, 0.08, 0.0)
	_dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_dim)

	_panel = PanelContainer.new()
	_panel.anchor_left = 0.5
	_panel.anchor_right = 0.5
	_panel.anchor_top = 1.0
	_panel.anchor_bottom = 1.0
	_panel.offset_left = -780
	_panel.offset_right = 780
	_panel.offset_top = -290
	_panel.offset_bottom = -28
	var sb := UIKit.box(UIKit.panel, 0.88, UIKit.accent, 0.7, 30, 26)
	sb.content_margin_left = 56
	sb.content_margin_right = 56
	sb.content_margin_top = 44
	sb.content_margin_bottom = 30
	_panel.add_theme_stylebox_override("panel", sb)
	_panel.mouse_filter = Control.MOUSE_FILTER_STOP
	_panel.gui_input.connect(_on_panel_input)
	add_child(_panel)
	_text = RichTextLabel.new()
	_text.bbcode_enabled = true
	_text.scroll_active = false
	_text.fit_content = false
	_text.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_text.add_theme_font_size_override("normal_font_size", 31)
	_text.add_theme_font_size_override("italics_font_size", 31)
	_text.add_theme_font_size_override("bold_font_size", 31)
	_text.add_theme_constant_override("line_separation", 8)
	_panel.add_child(_text)

	_name_plate = PanelContainer.new()
	_name_plate.anchor_left = 0.5
	_name_plate.anchor_right = 0.5
	_name_plate.anchor_top = 1.0
	_name_plate.anchor_bottom = 1.0
	_name_plate.offset_left = -740
	_name_plate.offset_right = -740
	_name_plate.offset_top = -322
	_name_plate.offset_bottom = -322
	_name_plate.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_name_plate)
	_name_label = UIKit.label("", 30, Color.WHITE, UIKit.font_title)
	_name_plate.add_child(_name_label)

	_next = UIKit.label("▾", 34, UIKit.accent2, UIKit.font_bold)
	_next.anchor_left = 0.5
	_next.anchor_right = 0.5
	_next.anchor_top = 1.0
	_next.anchor_bottom = 1.0
	_next.offset_left = 720
	_next.offset_right = 760
	_next.offset_top = -86
	_next.offset_bottom = -40
	_next.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_next)

	_toolbar = HBoxContainer.new()
	_toolbar.anchor_left = 0.5
	_toolbar.anchor_right = 0.5
	_toolbar.anchor_top = 1.0
	_toolbar.anchor_bottom = 1.0
	_toolbar.offset_left = 260
	_toolbar.offset_right = 760
	_toolbar.offset_top = -330
	_toolbar.offset_bottom = -294
	_toolbar.alignment = BoxContainer.ALIGNMENT_END
	_toolbar.add_theme_constant_override("separation", 10)
	add_child(_toolbar)
	_auto_btn = _tool("Авто", func(): set_auto(not auto_mode))
	_skip_btn = _tool("Пропуск", func(): set_skip(not skip_mode))
	_tool("История", func(): history_requested.emit())
	_tool("Скрыть", func(): set_hidden(true))

	_choices = VBoxContainer.new()
	_choices.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	_choices.anchor_top = 0.42
	_choices.anchor_bottom = 0.42
	_choices.offset_left = -480
	_choices.offset_right = 480
	_choices.grow_vertical = Control.GROW_DIRECTION_BOTH
	_choices.add_theme_constant_override("separation", 16)
	_choices.alignment = BoxContainer.ALIGNMENT_CENTER
	add_child(_choices)

	_build_name_box()
	_set_visible_all(false)


func _tool(text: String, cb: Callable) -> Button:
	var b := UIKit.button(text, cb)
	b.add_theme_font_size_override("font_size", 18)
	var s := UIKit.box(UIKit.panel, 0.75, UIKit.accent, 0.4, 12)
	s.content_margin_left = 12
	s.content_margin_right = 12
	s.content_margin_top = 4
	s.content_margin_bottom = 4
	b.add_theme_stylebox_override("normal", s)
	var h := UIKit.box(UIKit.panel.lightened(0.2), 0.9, UIKit.accent, 1.0, 12)
	h.content_margin_left = 12
	h.content_margin_right = 12
	h.content_margin_top = 4
	h.content_margin_bottom = 4
	b.add_theme_stylebox_override("hover", h)
	b.add_theme_stylebox_override("pressed", h)
	b.focus_mode = Control.FOCUS_NONE
	b.hover_scale = 1.08
	_toolbar.add_child(b)
	return b


func _build_name_box() -> void:
	_name_box = PanelContainer.new()
	_name_box.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	_name_box.offset_left = -420
	_name_box.offset_right = 420
	_name_box.offset_top = -170
	_name_box.offset_bottom = 170
	_name_box.visible = false
	add_child(_name_box)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 22)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	_name_box.add_child(v)
	var t := UIKit.title("Как тебя зовут?", 40)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(t)
	var hint := UIKit.label("Кира будет обращаться к тебе по имени. Его можно не указывать.", 22, UIKit.TEXT_DIM)
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(hint)
	_name_edit = LineEdit.new()
	_name_edit.max_length = 20
	_name_edit.placeholder_text = "Твоё имя"
	_name_edit.alignment = HORIZONTAL_ALIGNMENT_CENTER
	_name_edit.custom_minimum_size = Vector2(0, 64)
	_name_edit.text_submitted.connect(func(_t): _submit_name())
	v.add_child(_name_edit)
	var b := UIKit.button("Это я", _submit_name)
	b.custom_minimum_size = Vector2(260, 60)
	b.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(b)


func _submit_name() -> void:
	var n := _name_edit.text.strip_edges().replace("[", "").replace("]", "")
	if n == "":
		n = "Незнакомец"
	_name_box.visible = false
	name_entered.emit(n)


func ask_name() -> void:
	active = true
	_name_box.visible = true
	_name_edit.text = ""
	_name_edit.grab_focus()


func _set_visible_all(on: bool) -> void:
	_panel.visible = on
	_name_plate.visible = on and _name_label.text != ""
	_next.visible = false
	_toolbar.visible = on


# ----------------------------------------------------------------- строки
func show_line(data: Dictionary) -> void:
	_line = data
	active = true
	hidden_ui = false
	_set_visible_all(true)
	_clear_choices()
	_name_label.text = data.name
	_name_plate.visible = data.name != ""
	var col: Color = data.color
	var ns := UIKit.box(UIKit.panel.darkened(0.2), 0.95, col, 1.0, 16, 10)
	ns.content_margin_left = 26
	ns.content_margin_right = 26
	ns.content_margin_top = 6
	ns.content_margin_bottom = 6
	_name_plate.add_theme_stylebox_override("panel", ns)
	_name_label.add_theme_color_override("font_color", col.lightened(0.25))
	var text: String = data.text.replace("💜", HEART)
	match data.style:
		"narration":
			_text.add_theme_font_override("normal_font", UIKit.font_regular)
			text = "[color=#%s][i]%s[/i][/color]" % [Color("#d8ccff").to_html(false), text]
		"system":
			_text.add_theme_font_override("normal_font", UIKit.font_mono)
			text = "[color=#%s]> %s[/color]" % [col.to_html(false), text]
		_:
			_text.add_theme_font_override("normal_font", UIKit.font_regular)
	_text.text = text
	_total = _text.get_total_character_count()
	_chars = 0.0
	_text.visible_characters = 0
	typing = true
	_auto_timer = 0.0
	_skip_timer = 0.0
	typing_changed.emit(true)
	var speed: float = Settings.get_value("text", "speed", 45.0)
	if speed >= 200.0 or _can_skip():
		_finish_typing()


func _can_skip() -> bool:
	if not (skip_mode or Input.is_action_pressed("skip")):
		return false
	return _line.get("read", false) or Settings.get_value("text", "skip_unread", false)


func _finish_typing() -> void:
	_chars = _total
	_text.visible_characters = -1
	if typing:
		typing = false
		typing_changed.emit(false)


func close() -> void:
	active = false
	typing = false
	_set_visible_all(false)
	_clear_choices()
	typing_changed.emit(false)
	create_tween().tween_property(_dim, "color:a", 0.0, 0.3)


func set_auto(on: bool) -> void:
	auto_mode = on
	if on:
		skip_mode = false
	_update_tool_state()


func set_skip(on: bool) -> void:
	skip_mode = on
	if on:
		auto_mode = false
	_update_tool_state()


func _update_tool_state() -> void:
	_auto_btn.modulate = Color(1.3, 1.1, 1.5) if auto_mode else Color.WHITE
	_skip_btn.modulate = Color(1.3, 1.1, 1.5) if skip_mode else Color.WHITE


func set_hidden(on: bool) -> void:
	hidden_ui = on
	_panel.visible = not on and active
	_name_plate.visible = not on and active and _name_label.text != ""
	_toolbar.visible = not on and active
	_choices.visible = not on
	_next.visible = false


# ----------------------------------------------------------------- выборы
func show_choices(options: Array) -> void:
	waiting_choice = true
	active = true
	_clear_choices()
	_choices.visible = true
	create_tween().tween_property(_dim, "color:a", 0.35, 0.3)
	for i in options.size():
		var b := UIKit.button(str(options[i]).replace("💜", "♥"))
		b.custom_minimum_size = Vector2(900, 70)
		b.add_theme_font_size_override("font_size", 27)
		b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		b.modulate.a = 0.0
		b.pressed.connect(_pick.bind(i))
		_choices.add_child(b)
		var t := create_tween()
		t.tween_interval(0.07 * i)
		t.tween_property(b, "modulate:a", 1.0, 0.25)
	if skip_mode:
		set_skip(false)
	AudioManager.play_sfx("choice", 1.0, -4.0)
	await get_tree().process_frame
	if _choices.get_child_count() > 0:
		(_choices.get_child(0) as Button).grab_focus()


func _pick(i: int) -> void:
	if not waiting_choice:
		return
	waiting_choice = false
	_clear_choices()
	create_tween().tween_property(_dim, "color:a", 0.0, 0.25)
	chosen.emit(i)


func _clear_choices() -> void:
	for c in _choices.get_children():
		c.queue_free()


# ------------------------------------------------------------------ ввод
func _process(delta: float) -> void:
	_t += delta
	if not active:
		return
	if typing:
		var speed: float = Settings.get_value("text", "speed", 45.0)
		var before := int(_chars)
		_chars += delta * speed
		var now := int(_chars)
		if now != before:
			_text.visible_characters = now
			_blip_acc += now - before
			if _blip_acc >= 3 and _line.get("voice", "") != "":
				_blip_acc = 0
				AudioManager.blip(_line.voice)
		if _chars >= _total:
			_finish_typing()
		if _can_skip():
			_finish_typing()
	elif not waiting_choice and _panel.visible:
		_next.visible = true
		_next.offset_top = -86 + sin(_t * 5.0) * 4.0
		_next.modulate.a = 0.6 + 0.4 * sin(_t * 5.0)
		if _can_skip():
			_skip_timer += delta
			if _skip_timer > 0.06:
				_advance()
		elif skip_mode and not _line.get("read", false) and not Settings.get_value("text", "skip_unread", false):
			set_skip(false)
		elif auto_mode:
			_auto_timer += delta
			var wait: float = Settings.get_value("text", "auto_delay", 1.6) + _total * 0.025
			if _auto_timer >= wait:
				_advance()


func _advance() -> void:
	if waiting_choice or _name_box.visible:
		return
	if hidden_ui:
		set_hidden(false)
		return
	if typing:
		_finish_typing()
		return
	if not _panel.visible:
		return
	_next.visible = false
	AudioManager.play_sfx("tick", 1.0, -10.0)
	advanced.emit()


func _on_panel_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed:
		if event.button_index == MOUSE_BUTTON_LEFT:
			_advance()
			accept_event()
		elif event.button_index == MOUSE_BUTTON_RIGHT:
			set_hidden(true)
			accept_event()


## Клики мимо панели во время диалога тоже продвигают текст (вызывается миром).
func handle_world_click() -> bool:
	if not active or waiting_choice or _name_box.visible:
		return false
	_advance()
	return true


func _unhandled_input(event: InputEvent) -> void:
	if not active:
		return
	if event.is_action_pressed("advance") and not _name_box.visible:
		if waiting_choice:
			return
		_advance()
		get_viewport().set_input_as_handled()
	elif event.is_action_pressed("auto"):
		set_auto(not auto_mode)
		get_viewport().set_input_as_handled()
	elif event.is_action_pressed("hide_ui"):
		set_hidden(not hidden_ui)
		get_viewport().set_input_as_handled()
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_RIGHT:
		set_hidden(not hidden_ui)
		get_viewport().set_input_as_handled()
