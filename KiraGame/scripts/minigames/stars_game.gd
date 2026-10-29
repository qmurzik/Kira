extends "res://scripts/minigames/minigame_base.gd"
## «Смотреть на звёзды»: соедини звёзды в созвездие. Клик по звезде, затем по соседней.
## Неверная линия гаснет, верная — загорается. Подсказка показывает контур.

const STARS := preload("res://scripts/fx/starfield.gd")

var data := {}
var queue: Array = []
var current := {}
var cur_id := ""
var points: Array = []      ## Vector2 в координатах доски
var distractors: Array = []
var done_edges: Array = []
var selected := -1
var mistakes := 0
var total_mistakes := 0
var completed: Array = []
var _board: Control
var _hint_t := 0.0
var _fail_line := {}
var _complete_t := -1.0
var _name_label: Label
var _t := 0.0
var _board_scale := 1.0
var _board_origin := Vector2.ZERO


func setup() -> void:
	title_text = "Смотреть на звёзды"
	rules_text = "Нажми на звезду, затем на соседнюю, чтобы провести линию. Собери созвездие целиком."
	var f := FileAccess.open("res://data/constellations.json", FileAccess.READ)
	data = JSON.parse_string(f.get_as_text()) if f else {}
	var bg := TextureRect.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var g := GradientTexture2D.new()
	g.fill_from = Vector2(0.5, 0)
	g.fill_to = Vector2(0.5, 1)
	var grad := Gradient.new()
	grad.offsets = PackedFloat32Array([0.0, 0.6, 1.0])
	grad.colors = PackedColorArray([Color("#070820"), Color("#1c1850"), Color("#45306e")])
	g.gradient = grad
	bg.texture = g
	bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)
	var sf := STARS.new()
	sf.area = Rect2(0, 0, 2600, 1500)
	sf.count = 260
	sf.seed_value = randi()
	add_child(sf)
	_board = Control.new()
	_board.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_board.mouse_filter = Control.MOUSE_FILTER_STOP
	_board.draw.connect(_draw_board)
	_board.gui_input.connect(_on_input)
	add_child(_board)
	_name_label = UIKit.label("", 70, Color("#fff3c9"), UIKit.font_script)
	_name_label.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM)
	_name_label.offset_top = -190
	_name_label.offset_left = -600
	_name_label.offset_right = 600
	_name_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_name_label.add_theme_color_override("font_shadow_color", Color(0.8, 0.6, 1.0, 0.8))
	_name_label.add_theme_constant_override("shadow_outline_size", 20)
	_name_label.modulate.a = 0.0
	add_child(_name_label)
	var hint := UIKit.button("Подсказка", func():
		_hint_t = 2.5
		comment("Смотри внимательно — вот примерно такой контур.", "sly"), "sparkle")
	hint.anchor_top = 1.0
	hint.anchor_bottom = 1.0
	hint.offset_left = 30
	hint.offset_top = -90
	hint.offset_bottom = -26
	hint.focus_mode = Control.FOCUS_NONE
	add_child(hint)


func start() -> void:
	queue = params.get("ids", ["cat", "lyra"]).duplicate()
	_next()
	comment("Давай нарисуем что-нибудь на небе!", "happy")


func _next() -> void:
	if queue.is_empty():
		var score := stars_for(total_mistakes, 2, 6)
		set_status("Все созвездия собраны!")
		comment(["Красиво… Будто небо стало немного нашим.", "Мы отличная команда звездочётов.", "Хочу запомнить это небо."][score - 1], "warm")
		AudioManager.play_sfx("success")
		finish({"score": score, "mistakes": total_mistakes, "completed": completed}, 2.5)
		return
	cur_id = queue.pop_front()
	current = data.get("constellations", {}).get(cur_id, {})
	points.clear()
	for p in current.get("points", []):
		points.append(Vector2(p[0], p[1]))
	done_edges.clear()
	selected = -1
	mistakes = 0
	_complete_t = -1.0
	_name_label.modulate.a = 0.0
	# Звёзды-обманки, не слишком близко к настоящим
	distractors.clear()
	var tries := 0
	while distractors.size() < 12 and tries < 400:
		tries += 1
		var p := Vector2(randf_range(80, 1120), randf_range(80, 640))
		var ok := true
		for q in points + distractors:
			if p.distance_to(q) < 90:
				ok = false
				break
		if ok:
			distractors.append(p)
	_update_status()


func _update_status() -> void:
	var total: int = current.get("edges", []).size()
	set_status("Линий: %d из %d" % [done_edges.size(), total])


func _layout() -> void:
	var vs := size
	_board_scale = minf((vs.x - 160) / 1200.0, (vs.y - 330) / 700.0)
	_board_origin = Vector2((vs.x - 1200 * _board_scale) / 2.0, 190 + (vs.y - 330 - 700 * _board_scale) / 2.0)


func _to_screen(p: Vector2) -> Vector2:
	return _board_origin + p * _board_scale


func _star_at(screen: Vector2) -> int:
	var best := -1
	var bd := 40.0 * maxf(_board_scale, 0.7)
	for i in points.size():
		var d := _to_screen(points[i]).distance_to(screen)
		if d < bd:
			bd = d
			best = i
	for j in distractors.size():
		var d2 := _to_screen(distractors[j]).distance_to(screen)
		if d2 < bd:
			bd = d2
			best = 1000 + j
	return best


func _on_input(event: InputEvent) -> void:
	if _done or _complete_t >= 0.0:
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		var s := _star_at(event.position)
		if s < 0:
			selected = -1
			return
		if selected < 0:
			selected = s
			AudioManager.play_sfx("twinkle", randf_range(0.9, 1.2), -4.0)
			return
		if s == selected:
			selected = -1
			return
		try_connect(selected, s)
		selected = s if s < 1000 else -1


## Попытка соединить две звезды (индексы ≥1000 — обманки).
func try_connect(a: int, b: int) -> bool:
	var edges: Array = current.get("edges", [])
	for e in edges:
		if (e[0] == a and e[1] == b) or (e[0] == b and e[1] == a):
			if _has_edge(a, b):
				return true
			done_edges.append([a, b])
			AudioManager.play_sfx("star_connect", 1.0 + done_edges.size() * 0.04)
			_update_status()
			if done_edges.size() == 1 and completed.is_empty():
				comment("Да! Вот так!", "happy")
			if done_edges.size() >= edges.size():
				_complete()
			return true
	mistakes += 1
	total_mistakes += 1
	_fail_line = {"a": _pos(a), "b": _pos(b), "t": 0.6}
	AudioManager.play_sfx("fail", 1.0, -4.0)
	if mistakes == 2:
		comment("Кажется, эти звёзды не дружат. Попробуй другую.", "thoughtful")
	elif mistakes == 5:
		comment("Нажми «Подсказка» — я никому не скажу.", "sly")
	return false


func _has_edge(a: int, b: int) -> bool:
	for e in done_edges:
		if (e[0] == a and e[1] == b) or (e[0] == b and e[1] == a):
			return true
	return false


func _pos(i: int) -> Vector2:
	return points[i] if i < 1000 else distractors[i - 1000]


func _complete() -> void:
	selected = -1
	_complete_t = 0.0
	completed.append(cur_id)
	_name_label.text = str(current.get("name", ""))
	create_tween().tween_property(_name_label, "modulate:a", 1.0, 0.8)
	AudioManager.play_sfx("star_complete")
	comment(str(current.get("desc", "")), "joy")
	get_tree().create_timer(3.2).timeout.connect(func(): if is_inside_tree() and not _done: _next())


## Для автотестов: собрать все созвездия без ошибок.
func solve_all() -> void:
	while not _done:
		for e in current.get("edges", []).duplicate():
			try_connect(e[0], e[1])
		if not _done:
			_next()


func _process(delta: float) -> void:
	_t += delta
	_hint_t = maxf(_hint_t - delta, 0.0)
	if _complete_t >= 0.0:
		_complete_t += delta
	if not _fail_line.is_empty():
		_fail_line.t -= delta
		if _fail_line.t <= 0.0:
			_fail_line = {}
	_layout()
	_board.queue_redraw()


func _draw_board() -> void:
	var edges: Array = current.get("edges", [])
	if _hint_t > 0.0:
		for e in edges:
			_board.draw_line(_to_screen(points[e[0]]), _to_screen(points[e[1]]), Color(0.8, 0.7, 1.0, 0.25 * minf(_hint_t, 1.0)), 2.0, true)
	var glow := 1.0 if _complete_t < 0.0 else 1.0 + 0.5 * sin(_complete_t * 6.0)
	for e in done_edges:
		var a := _to_screen(_pos(e[0]))
		var b := _to_screen(_pos(e[1]))
		_board.draw_line(a, b, Color(0.75, 0.6, 1.0, 0.35), 10.0 * glow, true)
		_board.draw_line(a, b, Color(1.0, 0.95, 1.0, 0.95), 2.5, true)
	if not _fail_line.is_empty():
		_board.draw_line(_to_screen(_fail_line.a), _to_screen(_fail_line.b), Color(1.0, 0.45, 0.55, _fail_line.t), 3.0, true)
	if selected >= 0:
		var sp := _to_screen(_pos(selected))
		_board.draw_line(sp, _board.get_local_mouse_position(), Color(1, 1, 1, 0.35), 2.0, true)
	var all := points.size()
	for i in all + distractors.size():
		var idx := i if i < all else 1000 + i - all
		var p := _to_screen(_pos(idx))
		var tw := 0.75 + 0.25 * sin(_t * 2.0 + i * 1.7)
		var r := (7.0 if i < all else 5.5) * maxf(_board_scale, 0.7)
		_board.draw_circle(p, r * 3.2, Color(0.85, 0.75, 1.0, 0.12 * tw))
		_board.draw_circle(p, r, Color(1, 0.98, 0.9, tw))
		if idx == selected:
			_board.draw_arc(p, r * 2.6, 0, TAU, 32, Color(1.0, 0.8, 0.95, 0.9), 3.0, true)
