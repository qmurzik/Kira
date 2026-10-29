extends Node2D
## Кира: персонаж, собранный из векторных слоёв (assets/kira).
## Эмоции меняют глаза, брови, рот, румянец, позу рук и наклон головы.
## Живость: дыхание, моргание, покачивание волос, «говорящий» рот во время печати текста.
## Точка (0, 0) узла — между стопами, поэтому персонажа удобно ставить на «пол».

signal clicked(part: String)

const DIR := "res://assets/kira/"
const FEET := Vector2(500, 1945)

const EMOTIONS := {
	"neutral": {"eyes": "open", "brows": "neutral", "mouth": "smile", "talk": "talk", "pose": "down", "tilt": 0.0},
	"happy": {"eyes": "open", "brows": "soft", "mouth": "grin", "talk": "talk", "blush": true, "pose": "behind", "tilt": 2.5},
	"joy": {"eyes": "happy", "brows": "soft", "mouth": "grin", "talk": "grin", "blush": true, "pose": "behind", "tilt": 3.5, "hop": true},
	"shy": {"eyes": "aside", "brows": "sad", "mouth": "wavy", "talk": "talk", "blush": true, "pose": "behind", "tilt": -4.0},
	"thoughtful": {"eyes": "up", "brows": "raise", "mouth": "neutral", "talk": "talk", "pose": "chin", "tilt": -3.0},
	"surprised": {"eyes": "wide", "brows": "up", "mouth": "o", "talk": "o", "pose": "down", "tilt": 0.0, "hop": true},
	"sad": {"eyes": "teary", "brows": "sad", "mouth": "frown", "talk": "talk", "pose": "down", "tilt": -2.0, "droop": true},
	"pout": {"eyes": "half", "brows": "angry", "mouth": "pout", "talk": "pout", "blush": true, "pose": "down", "tilt": 5.0, "shake": true},
	"sleepy": {"eyes": "sleepy", "brows": "soft", "mouth": "yawn", "talk": "talk", "pose": "down", "tilt": 5.0},
	"sly": {"eyes": "half", "brows": "raise", "mouth": "cat", "talk": "talk", "pose": "behind", "tilt": 3.0},
	"warm": {"eyes": "happy", "brows": "soft", "mouth": "smile", "talk": "talk", "blush": true, "pose": "behind", "tilt": -2.0},
	"worried": {"eyes": "open", "brows": "sad", "mouth": "frown", "talk": "talk", "pose": "down", "tilt": -1.5},
	"determined": {"eyes": "open", "brows": "angry", "mouth": "smile", "talk": "talk", "pose": "down", "tilt": 0.0},
}
const EMOTION_NAMES := {
	"neutral": "Нейтральная", "happy": "Счастливая", "joy": "Радостная", "shy": "Смущённая", "thoughtful": "Задумчивая",
	"surprised": "Удивлённая", "sad": "Грустная", "pout": "Игриво-недовольная", "sleepy": "Сонная", "sly": "Хитрая",
	"warm": "Нежная", "worried": "Встревоженная", "determined": "Решительная",
}
const BLINKABLE := ["open", "aside", "up", "wide", "teary", "half"]

var parts := {}
var emotion := "neutral"
var outfit := "casual"
var talking := false
var interactive := true

var _meta := {}
var _rig: Node2D
var _torso: Node2D
var _head: Node2D
var _hair_f_pivot: Node2D
var _hair_b_pivot: Node2D
var _spr := {}
var _t := 0.0
var _blink_timer := 3.0
var _blinking := 0.0
var _talk_timer := 0.0
var _talk_open := false
var _tilt := 0.0
var _droop := 0.0
var _head_rot_prev := 0.0
var _hair_swing := 0.0
var _bob := 0.0
var _walking := false
var _react_tween: Tween
var _shadow: Sprite2D


func _ready() -> void:
	var f := FileAccess.open(DIR + "parts.json", FileAccess.READ)
	_meta = JSON.parse_string(f.get_as_text()) if f else {}
	parts = _meta.get("parts", {})
	_build()
	set_outfit(InventoryManager.outfit)
	set_emotion("neutral", true)
	InventoryManager.outfit_changed.connect(set_outfit)
	_blink_timer = randf_range(1.5, 4.0)


func _build() -> void:
	_shadow = Sprite2D.new()
	var g := GradientTexture2D.new()
	g.fill = GradientTexture2D.FILL_RADIAL
	g.fill_from = Vector2(0.5, 0.5)
	g.fill_to = Vector2(1.0, 0.5)
	var grad := Gradient.new()
	grad.set_color(0, Color(0.05, 0.02, 0.12, 0.45))
	grad.set_color(1, Color(0.05, 0.02, 0.12, 0.0))
	g.gradient = grad
	g.width = 256
	g.height = 64
	_shadow.texture = g
	_shadow.scale = Vector2(1.6, 1.0)
	_shadow.position = Vector2(0, -8)
	add_child(_shadow)

	_rig = Node2D.new()
	add_child(_rig)
	_torso = Node2D.new()
	_torso.position = Vector2.ZERO
	_rig.add_child(_torso)
	var neck: Array = _meta.get("neck_pivot", [500, 520])
	var neck_v := Vector2(neck[0], neck[1])
	# Слои (порядок = порядок отрисовки)
	_spr.arm_l_behind = _part(_torso, "", -FEET, -2)
	_spr.arm_r_behind = _part(_torso, "", -FEET, -2)
	_spr.body = _part(_torso, "", -FEET, 0)
	_spr.arm_l = _part(_torso, "", -FEET, 0)
	_spr.arm_r = _part(_torso, "", -FEET, 0)
	_head = Node2D.new()
	_head.position = neck_v - FEET
	_head.scale = Vector2.ONE * float(_meta.get("head_scale", 0.87))
	_torso.add_child(_head)
	var hair_pivot := Vector2(500, 230)
	_hair_b_pivot = Node2D.new()
	_hair_b_pivot.position = hair_pivot - neck_v
	_hair_b_pivot.z_index = -1
	_head.add_child(_hair_b_pivot)
	_spr.hair_back = _part(_hair_b_pivot, "hair_back", -hair_pivot, 0)
	_spr.head = _part(_head, "head", -neck_v, 0)
	_spr.eyes = _part(_head, "eyes_open", -neck_v, 0)
	_spr.mouth = _part(_head, "mouth_smile", -neck_v, 0)
	_spr.blush = _part(_head, "blush", -neck_v, 0)
	_hair_f_pivot = Node2D.new()
	_hair_f_pivot.position = hair_pivot - neck_v
	_head.add_child(_hair_f_pivot)
	_spr.hair_front = _part(_hair_f_pivot, "hair_front", -hair_pivot, 0)
	_spr.brows = _part(_head, "brows_neutral", -neck_v, 0)
	_spr.arm_chin = _part(_torso, "", -FEET, 1)
	_spr.blush.modulate.a = 0.0


func _part(parent: Node, name: String, offset: Vector2, z: int) -> Sprite2D:
	var s := Sprite2D.new()
	s.centered = false
	s.z_index = z
	s.set_meta("offset", offset)
	parent.add_child(s)
	if name != "":
		_set_tex(s, name)
	return s


func _set_tex(s: Sprite2D, name: String) -> void:
	if name == "" or not parts.has(name):
		s.texture = null
		return
	var p: Dictionary = parts[name]
	s.texture = load(DIR + name + ".svg")
	s.position = Vector2(p.x, p.y) + s.get_meta("offset")
	# Текстура может быть импортирована в другом масштабе — подгоняем под размер в данных
	if s.texture:
		s.scale = Vector2(float(p.w) / s.texture.get_width(), float(p.h) / s.texture.get_height())


func set_outfit(id: String) -> void:
	outfit = id
	_set_tex(_spr.body, "body_" + id)
	_apply_pose(EMOTIONS.get(emotion, EMOTIONS.neutral).get("pose", "down"))


func _apply_pose(pose: String) -> void:
	_set_tex(_spr.arm_l_behind, "armL_behind_" + outfit if pose == "behind" else "")
	_set_tex(_spr.arm_r_behind, "armR_behind_" + outfit if pose == "behind" else "")
	_set_tex(_spr.arm_l, "armL_down_" + outfit if pose in ["down", "chin"] else "")
	_set_tex(_spr.arm_r, "armR_down_" + outfit if pose == "down" else "")
	_set_tex(_spr.arm_chin, "armR_chin_" + outfit if pose == "chin" else "")


func set_emotion(e: String, instant := false) -> void:
	if not EMOTIONS.has(e):
		e = "neutral"
	var changed := e != emotion
	emotion = e
	var d: Dictionary = EMOTIONS[e]
	_set_tex(_spr.eyes, "eyes_" + d.eyes)
	_set_tex(_spr.brows, "brows_" + d.brows)
	_set_tex(_spr.mouth, "mouth_" + d.mouth)
	_apply_pose(d.get("pose", "down"))
	var blush_target := 1.0 if d.get("blush", false) else 0.0
	if instant:
		_spr.blush.modulate.a = blush_target
	else:
		create_tween().tween_property(_spr.blush, "modulate:a", blush_target, 0.35)
	_droop = 8.0 if d.get("droop", false) else 0.0
	if changed and not instant:
		if d.get("hop", false):
			hop()
		elif d.get("shake", false):
			shake()


func set_talking(on: bool) -> void:
	talking = on
	if not on:
		_talk_open = false
		_set_tex(_spr.mouth, "mouth_" + EMOTIONS[emotion].mouth)


# ------------------------------------------------------------ реакции
func hop(height := 26.0) -> void:
	if _react_tween:
		_react_tween.kill()
	_react_tween = create_tween()
	_react_tween.tween_property(_rig, "position:y", -height, 0.12).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
	_react_tween.tween_property(_rig, "position:y", 0.0, 0.22).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)


func shake() -> void:
	if _react_tween:
		_react_tween.kill()
	_react_tween = create_tween()
	for i in 3:
		_react_tween.tween_property(_rig, "position:x", 7.0, 0.05)
		_react_tween.tween_property(_rig, "position:x", -7.0, 0.05)
	_react_tween.tween_property(_rig, "position:x", 0.0, 0.05)


func nod() -> void:
	var t := create_tween()
	t.tween_property(_head, "position:y", _head.position.y + 10, 0.15)
	t.tween_property(_head, "position:y", _head.position.y, 0.2)


## Плавно перейти в точку x (в координатах родителя) с покачиванием шага.
func walk_to(x: float, speed := 420.0) -> void:
	var dist: float = absf(x - position.x)
	if dist < 4.0:
		return
	_walking = true
	var t := create_tween()
	t.tween_property(self, "position:x", x, dist / speed).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	await t.finished
	_walking = false


func _process(delta: float) -> void:
	_t += delta
	# Дыхание
	_torso.scale = Vector2(1.0 + sin(_t * 2.0) * 0.002, 1.0 + sin(_t * 2.0) * 0.0045)
	_torso.position.y = 0.0
	# Шаг при ходьбе
	if _walking:
		_bob += delta * 9.0
		_rig.position.y = -absf(sin(_bob)) * 14.0
		_rig.rotation = sin(_bob) * 0.02
	elif _rig.rotation != 0.0:
		_rig.rotation = lerpf(_rig.rotation, 0.0, delta * 8.0)
	# Наклон головы
	var target_tilt: float = deg_to_rad(float(EMOTIONS[emotion].get("tilt", 0.0)) + sin(_t * 0.7) * 1.1)
	_tilt = lerpf(_tilt, target_tilt, delta * 4.0)
	_head.rotation = _tilt
	var neck: Array = _meta.get("neck_pivot", [500, 520])
	_head.position = Vector2(neck[0], neck[1]) - FEET + Vector2(0, sin(_t * 2.0) * 1.5 + _droop)
	# Волосы запаздывают за головой
	var vel := (_head.rotation - _head_rot_prev) / maxf(delta, 0.001)
	_head_rot_prev = _head.rotation
	_hair_swing = lerpf(_hair_swing, -vel * 0.05 + sin(_t * 1.3) * 0.006, delta * 5.0)
	_hair_f_pivot.rotation = _hair_swing
	_hair_b_pivot.rotation = _hair_swing * 1.4
	# Моргание
	var eyes_kind: String = EMOTIONS[emotion].eyes
	if _blinking > 0.0:
		_blinking -= delta
		if _blinking <= 0.0:
			_set_tex(_spr.eyes, "eyes_" + eyes_kind)
	else:
		_blink_timer -= delta
		if _blink_timer <= 0.0:
			_blink_timer = randf_range(2.2, 5.5)
			if randf() < 0.15:
				_blink_timer = 0.25  # двойное моргание
			if eyes_kind in BLINKABLE:
				_set_tex(_spr.eyes, "eyes_closed")
				_blinking = 0.11
	# Рот во время речи
	if talking:
		_talk_timer -= delta
		if _talk_timer <= 0.0:
			_talk_timer = randf_range(0.07, 0.13)
			_talk_open = not _talk_open
			var d: Dictionary = EMOTIONS[emotion]
			_set_tex(_spr.mouth, "mouth_" + (d.talk if _talk_open else d.mouth))


# ------------------------------------------------------------ клики
func _unhandled_input(event: InputEvent) -> void:
	if not interactive or not visible:
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		var part := hit_part(get_global_mouse_position())
		if part != "":
			clicked.emit(part)
			get_viewport().set_input_as_handled()


## Определить, по какой части Киры пришёлся клик ("" — мимо).
func hit_part(global_pos: Vector2) -> String:
	var p := to_local(global_pos) + FEET
	# p — в координатах холста персонажа (1000x2000)
	if Rect2(560, 240, 90, 90).has_point(p):
		return "clip"
	if Rect2(340, 120, 320, 400).has_point(p):
		return "head"
	if Rect2(330, 520, 340, 1420).has_point(p):
		return "body"
	return ""


func get_head_global() -> Vector2:
	return to_global(Vector2(500, 200) - FEET)
