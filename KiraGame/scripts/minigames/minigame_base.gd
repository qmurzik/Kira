extends Control
## Основа мини-игр: фон, заголовок, правила, маленькая Кира с комментариями, кнопка выхода.

signal finished(result: Dictionary)

const KIRA := preload("res://scripts/characters/kira.gd")

var params := {}
var title_text := ""
var rules_text := ""
var show_kira := true
var kira: Node2D
var _kira_holder: Node2D
var _bubble: PanelContainer
var _rules: Label
var _status: Label
var _done := false
var _bg: ColorRect


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	_bg = ColorRect.new()
	_bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_bg.color = Color(0.04, 0.03, 0.1, 0.0)
	_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_bg)
	setup()
	var top := PanelContainer.new()
	top.anchor_left = 0.5
	top.anchor_right = 0.5
	top.offset_left = -620
	top.offset_right = 620
	top.offset_top = 22
	var sb := UIKit.box(UIKit.panel, 0.85, UIKit.accent, 0.6, 22, 14)
	top.add_theme_stylebox_override("panel", sb)
	top.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(top)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 2)
	top.add_child(v)
	var t := UIKit.title(title_text, 36)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(t)
	_rules = UIKit.label(rules_text, 22, UIKit.TEXT_DIM)
	_rules.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_rules.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_rules)
	_status = UIKit.label("", 24, UIKit.accent2.lightened(0.3), UIKit.font_bold)
	_status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(_status)
	var quit := UIKit.button("Закончить", func(): _quit())
	quit.anchor_left = 1.0
	quit.anchor_right = 1.0
	quit.offset_left = -230
	quit.offset_right = -24
	quit.offset_top = 26
	quit.offset_bottom = 84
	quit.add_theme_font_size_override("font_size", 22)
	quit.focus_mode = Control.FOCUS_NONE
	add_child(quit)
	if show_kira:
		_kira_holder = Node2D.new()
		add_child(_kira_holder)
		kira = KIRA.new()
		kira.interactive = false
		kira.scale = Vector2(0.36, 0.36)
		_kira_holder.add_child(kira)
		_place_kira()
		get_viewport().size_changed.connect(_place_kira)
	_bubble = preload("res://scripts/ui/bubble.gd").new()
	add_child(_bubble)
	modulate.a = 0.0
	create_tween().tween_property(self, "modulate:a", 1.0, 0.4)
	AudioManager.play_music("minigame" if params.get("music", "") == "" else str(params.music))
	start()


func _place_kira() -> void:
	var vs := get_viewport_rect().size
	kira.position = Vector2(vs.x - 150, vs.y + 120)


## Переопределяется: построить игру (до заголовка).
func setup() -> void:
	pass


## Переопределяется: запуск после построения.
func start() -> void:
	pass


func set_status(t: String) -> void:
	if _status == null:
		_pending_status = t
		call_deferred("_apply_pending_status")
		return
	_status.text = t


var _pending_status := ""


func _apply_pending_status() -> void:
	if _status and _pending_status != "":
		_status.text = _pending_status


func comment(text: String, emotion := "") -> void:
	if kira == null:
		return
	if emotion != "":
		kira.set_emotion(emotion)
	kira.set_talking(true)
	get_tree().create_timer(0.9).timeout.connect(func(): if is_instance_valid(kira): kira.set_talking(false))
	var head: Vector2 = kira.get_global_transform_with_canvas() * (Vector2(500, 200) - kira.FEET)
	_bubble.say(text, head + Vector2(-120, -10), 2.8)


func finish(result: Dictionary, delay := 1.8) -> void:
	if _done:
		return
	_done = true
	await get_tree().create_timer(delay).timeout
	var t := create_tween()
	t.tween_property(self, "modulate:a", 0.0, 0.4)
	await t.finished
	finished.emit(result)


func _quit() -> void:
	if _done:
		return
	_done = true
	finished.emit({"score": 0, "skipped": true})


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("pause"):
		get_viewport().set_input_as_handled()
		_quit()


## Расчёт оценки 1..3 по количеству ошибок.
static func stars_for(mistakes: int, good: int, ok: int) -> int:
	if mistakes <= good:
		return 3
	if mistakes <= ok:
		return 2
	return 1
