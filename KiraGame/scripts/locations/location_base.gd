extends Node2D
## Базовая локация: многослойный фон 1920x1080 (масштаб «cover» под любое окно),
## параллакс от мыши, интерактивные зоны из data/locations/<id>.json, смена освещения.

signal hotspot_clicked(id: String)
signal hotspot_hover(id: String, title: String)

const BG := "res://assets/bg/"
const GLOW := preload("res://scripts/fx/glow.gd")
const BASE := Vector2(1920, 1080)

var loc_id := ""
var data := {}
var content: Node2D          ## всё содержимое в координатах 1920x1080
var kira_layer: Node2D       ## слой, куда мир кладёт Киру
var tint: CanvasModulate
var hints_visible := false
var input_enabled := true
var hovered := ""
var _parallax: Array = []    ## [{node, depth}]
var _hint_draw: Node2D
var _t := 0.0
var _shake := 0.0


func setup(id: String) -> void:
	loc_id = id
	var f := FileAccess.open("res://data/locations/%s.json" % id, FileAccess.READ)
	if f:
		var d = JSON.parse_string(f.get_as_text())
		if d is Dictionary:
			data = d


func _ready() -> void:
	tint = CanvasModulate.new()
	add_child(tint)
	content = Node2D.new()
	add_child(content)
	build()
	kira_layer = Node2D.new()
	kira_layer.name = "KiraLayer"
	content.add_child(kira_layer)
	_parallax.append({"node": kira_layer, "depth": float(data.get("kira_depth", 0.3)), "base": Vector2.ZERO})
	build_front()
	_hint_draw = Node2D.new()
	_hint_draw.z_index = 50
	_hint_draw.draw.connect(_draw_hints)
	content.add_child(_hint_draw)
	get_viewport().size_changed.connect(_fit)
	_fit()
	apply_time(TimeManager.phase, true)


## Переопределяется: построить фоновые слои (до Киры).
func build() -> void:
	pass


## Переопределяется: передний план (после Киры).
func build_front() -> void:
	pass


func layer(path: String, depth := 0.0, parent: Node = null, pos := Vector2.ZERO) -> Sprite2D:
	var s := Sprite2D.new()
	s.centered = false
	s.position = pos
	if path != "":
		s.texture = load(BG + path)
	(parent if parent else content).add_child(s)
	if depth > 0.0:
		_parallax.append({"node": s, "depth": depth, "base": pos})
	return s


func glow(pos: Vector2, color: Color, radius: float, parent: Node = null, flicker := 0.0, pulse := 0.0) -> Sprite2D:
	var g: Sprite2D = GLOW.new()
	g.color = color
	g.radius = radius
	g.flicker = flicker
	g.pulse = pulse
	g.position = pos
	(parent if parent else content).add_child(g)
	return g


func _fit() -> void:
	var vs := get_viewport_rect().size
	var s: float = maxf(vs.x / BASE.x, vs.y / BASE.y)
	content.scale = Vector2(s, s)
	content.position = (vs - BASE * s) / 2.0


func _process(delta: float) -> void:
	_t += delta
	# Параллакс от положения мыши: создаёт ощущение глубины
	var vs := get_viewport_rect().size
	var m := (get_viewport().get_mouse_position() - vs / 2.0) / vs
	for p in _parallax:
		var target: Vector2 = p.base - m * 40.0 * p.depth
		p.node.position = p.node.position.lerp(target, delta * 3.0)
	if _shake > 0.0:
		_shake = maxf(_shake - delta * 2.0, 0.0)
		content.position += Vector2(randf_range(-1, 1), randf_range(-1, 1)) * _shake * 12.0
		if _shake == 0.0:
			_fit()
	if hints_visible or hovered != "":
		_hint_draw.queue_redraw()
	tick(delta)


## Переопределяется для анимаций локации.
func tick(_delta: float) -> void:
	pass


func shake(strength := 1.0) -> void:
	if Settings.get_value("display", "shake", true):
		_shake = strength


# ---------------------------------------------------------- освещение
func apply_time(phase: String, instant := false) -> void:
	var c: Color
	if data.has("tint"):
		var t: Array = data.tint
		c = Color(t[0], t[1], t[2])
	else:
		c = TimeManager.TINTS.get(phase, Color.WHITE)
	set_tint(c, instant)


func set_tint(c: Color, instant := false) -> void:
	if instant:
		tint.color = c
	else:
		create_tween().tween_property(tint, "color", c, 1.2)


# ------------------------------------------------------- интерактивность
func hotspots() -> Array:
	var out := []
	for h in data.get("hotspots", []):
		if GameManager.eval_cond(str(h.get("if", ""))):
			out.append(h)
	return out


func hotspot(id: String) -> Dictionary:
	for h in data.get("hotspots", []):
		if h.id == id:
			return h
	return {}


func _hit(local: Vector2) -> Dictionary:
	var list := hotspots()
	# Последние в списке — «выше», поэтому проверяем с конца
	for i in range(list.size() - 1, -1, -1):
		var r: Array = list[i].rect
		if Rect2(r[0], r[1], r[2], r[3]).has_point(local):
			return list[i]
	return {}


func _unhandled_input(event: InputEvent) -> void:
	if not input_enabled:
		if hovered != "":
			hovered = ""
			hotspot_hover.emit("", "")
		return
	if event is InputEventMouseMotion:
		var h := _hit(content.to_local(get_global_mouse_position()))
		var id: String = h.get("id", "")
		if id != hovered:
			hovered = id
			hotspot_hover.emit(id, h.get("name", ""))
			if id != "":
				AudioManager.play_sfx("hover", 1.2, -10.0)
			_hint_draw.queue_redraw()
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		var h := _hit(content.to_local(get_global_mouse_position()))
		if not h.is_empty():
			get_viewport().set_input_as_handled()
			hotspot_clicked.emit(h.id)


func hotspot_center(id: String) -> Vector2:
	var h := hotspot(id)
	if h.is_empty():
		return Vector2(960, 540)
	var r: Array = h.rect
	return Vector2(r[0] + r[2] / 2.0, r[1] + r[3] / 2.0)


func _draw_hints() -> void:
	var pulse := 0.5 + 0.5 * sin(_t * 4.0)
	for h in hotspots():
		var r: Array = h.rect
		var rect := Rect2(r[0], r[1], r[2], r[3])
		var is_hover: bool = h.id == hovered
		if is_hover:
			_rounded(rect.grow(6), Color(UIKit.accent, 0.10 + 0.08 * pulse), true)
			_rounded(rect.grow(6), Color(UIKit.accent.lightened(0.3), 0.85), false, 3.0)
		elif hints_visible:
			var c := rect.get_center()
			_hint_draw.draw_circle(c, 26 + 6 * pulse, Color(UIKit.accent, 0.25))
			_hint_draw.draw_circle(c, 12, Color(1, 1, 1, 0.9))
			_hint_draw.draw_arc(c, 34 + 8 * pulse, 0, TAU, 32, Color(UIKit.accent2, 0.7), 3.0)
			_hint_draw.draw_string(UIKit.font_bold, c + Vector2(-200, 70), h.name, HORIZONTAL_ALIGNMENT_CENTER, 400, 26, Color(1, 1, 1, 0.95))


func _rounded(rect: Rect2, color: Color, filled: bool, width := 2.0) -> void:
	var sb := StyleBoxFlat.new()
	sb.set_corner_radius_all(22)
	if filled:
		sb.bg_color = color
	else:
		sb.draw_center = false
		sb.border_color = color
		sb.set_border_width_all(int(width))
	sb.anti_aliasing = true
	_hint_draw.draw_style_box(sb, rect)


## Сюжетные события конкретной локации (появление двери и т.п.).
func event(_name: String, _args := {}) -> void:
	pass
