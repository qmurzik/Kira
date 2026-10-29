extends Control
## Игровой интерфейс: глава, время суток, цель, кнопки действий, подсказки, уведомления.

signal action(name: String)

var _chapter: Label
var _time: Label
var _objective: Label
var _obj_panel: PanelContainer
var _buttons: HBoxContainer
var _btn := {}
var _tooltip: PanelContainer
var _tooltip_label: Label
var _toasts: VBoxContainer
var _hint: Label
var _loc_label: Label
var hub := true


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	# Левый верхний блок
	var info := PanelContainer.new()
	info.position = Vector2(28, 24)
	var sb := UIKit.box(UIKit.panel, 0.72, UIKit.accent, 0.45, 22, 12)
	sb.content_margin_left = 24
	sb.content_margin_right = 28
	info.add_theme_stylebox_override("panel", sb)
	info.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(info)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 2)
	info.add_child(v)
	_chapter = UIKit.label("", 22, UIKit.accent.lightened(0.35), UIKit.font_title)
	v.add_child(_chapter)
	_loc_label = UIKit.label("", 30, Color.WHITE, UIKit.font_bold)
	v.add_child(_loc_label)
	_time = UIKit.label("", 22, UIKit.TEXT_DIM)
	v.add_child(_time)
	_obj_panel = PanelContainer.new()
	_obj_panel.position = Vector2(28, 168)
	var sb2 := UIKit.box(UIKit.panel.darkened(0.2), 0.7, UIKit.accent2, 0.55, 18)
	sb2.content_margin_left = 20
	_obj_panel.add_theme_stylebox_override("panel", sb2)
	_obj_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_obj_panel)
	_objective = UIKit.label("", 23, Color("#ffe9f6"))
	_objective.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_objective.custom_minimum_size.x = 440
	_obj_panel.add_child(_objective)
	# Кнопки
	_buttons = HBoxContainer.new()
	_buttons.anchor_left = 1.0
	_buttons.anchor_right = 1.0
	_buttons.offset_left = -1300
	_buttons.offset_right = -28
	_buttons.offset_top = 24
	_buttons.alignment = BoxContainer.ALIGNMENT_END
	_buttons.add_theme_constant_override("separation", 12)
	add_child(_buttons)
	for b in [["activities", "Занятия", "sparkle"], ["wardrobe", "Гардероб", "shirt"], ["travel", "Прогулка", "map"],
			["journal", "Дневник", "book"], ["menu", "Меню", "gear"]]:
		var btn := UIKit.button(b[1], func(): action.emit(b[0]), b[2])
		btn.add_theme_font_size_override("font_size", 22)
		btn.focus_mode = Control.FOCUS_NONE
		btn.custom_minimum_size.y = 58
		_buttons.add_child(btn)
		_btn[b[0]] = btn
	# Подсказка и уведомления
	_hint = UIKit.label("Tab — показать объекты  ·  Esc — меню", 20, Color(1, 1, 1, 0.45))
	_hint.anchor_top = 1.0
	_hint.anchor_bottom = 1.0
	_hint.offset_left = 30
	_hint.offset_top = -44
	add_child(_hint)
	_toasts = VBoxContainer.new()
	_toasts.anchor_left = 0.5
	_toasts.anchor_right = 0.5
	_toasts.offset_left = -330
	_toasts.offset_right = 330
	_toasts.offset_top = 110
	_toasts.add_theme_constant_override("separation", 10)
	_toasts.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_toasts)
	_tooltip = PanelContainer.new()
	var sb3 := UIKit.box(UIKit.panel, 0.9, UIKit.accent, 0.9, 14, 8)
	sb3.content_margin_top = 6
	sb3.content_margin_bottom = 6
	_tooltip.add_theme_stylebox_override("panel", sb3)
	_tooltip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_tooltip.visible = false
	add_child(_tooltip)
	_tooltip_label = UIKit.label("", 24, Color.WHITE, UIKit.font_bold)
	_tooltip.add_child(_tooltip_label)
	GameManager.objective_changed.connect(func(_t): refresh())
	TimeManager.phase_changed.connect(func(_p): refresh())
	RelationshipManager.changed.connect(_on_rel)
	RelationshipManager.memory_added.connect(func(_k): toast("Кира это запомнит.", "heart"))
	refresh()


func refresh() -> void:
	if GameManager.free_mode:
		_chapter.text = "Свободный режим"
	else:
		_chapter.text = "Глава %d · %s" % [GameManager.chapter, GameManager.chapter_title()]
	_loc_label.text = GameManager.LOCATION_NAMES.get(GameManager.location, "")
	_time.text = "%s · день %d" % [TimeManager.phase_name(), TimeManager.day]
	_objective.text = "✦ " + GameManager.objective if GameManager.objective != "" else ""
	_obj_panel.visible = GameManager.objective != ""
	var in_room := GameManager.location == "room"
	_btn.activities.visible = in_room
	_btn.wardrobe.visible = in_room
	_btn.travel.visible = GameManager.free_mode or GameManager.get_flag("travel_unlocked")


func set_interactive(on: bool) -> void:
	_buttons.visible = on
	_hint.visible = on
	if not on:
		_tooltip.visible = false


func show_tooltip(text: String) -> void:
	_tooltip_label.text = text
	_tooltip.visible = text != ""
	_tooltip.size = Vector2.ZERO


func _process(_d: float) -> void:
	if _tooltip.visible:
		var p := get_viewport().get_mouse_position() + Vector2(24, 26)
		var vs := get_viewport_rect().size
		p.x = minf(p.x, vs.x - _tooltip.size.x - 10)
		p.y = minf(p.y, vs.y - _tooltip.size.y - 10)
		_tooltip.position = p


func _on_rel(stat: String, delta: int) -> void:
	if delta == 0:
		return
	var n: String = RelationshipManager.STATS[stat].name
	toast(("%s ↑" if delta > 0 else "%s ↓") % n, "heart")


func toast(text: String, icon_name := "sparkle") -> void:
	var p := PanelContainer.new()
	var sb := UIKit.box(UIKit.panel, 0.88, UIKit.accent2, 0.8, 18, 10)
	sb.content_margin_top = 8
	sb.content_margin_bottom = 8
	p.add_theme_stylebox_override("panel", sb)
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var h := HBoxContainer.new()
	h.alignment = BoxContainer.ALIGNMENT_CENTER
	h.add_theme_constant_override("separation", 10)
	p.add_child(h)
	var ic := TextureRect.new()
	ic.texture = UIKit.icon(icon_name)
	ic.custom_minimum_size = Vector2(28, 28)
	ic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	ic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	h.add_child(ic)
	h.add_child(UIKit.label(text, 23, Color.WHITE, UIKit.font_bold))
	_toasts.add_child(p)
	p.modulate.a = 0.0
	AudioManager.play_sfx("toast", 1.0, -6.0)
	var t := create_tween()
	t.tween_property(p, "modulate:a", 1.0, 0.3)
	t.tween_interval(2.6)
	t.tween_property(p, "modulate:a", 0.0, 0.6)
	t.tween_callback(p.queue_free)
