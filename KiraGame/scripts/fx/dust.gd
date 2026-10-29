extends Node2D
## Пылинки / светлячки / огоньки воспоминаний, медленно плывущие в воздухе.

@export var area := Rect2(0, 0, 1920, 1080)
@export var count := 60
@export var color := Color(1.0, 0.92, 0.75, 0.6)
@export var size_range := Vector2(1.0, 3.0)
@export var drift := Vector2(6, -10)
@export var glow := false

var _p: Array = []
var _t := 0.0


func _ready() -> void:
	var n := count if Settings.get_value("display", "particles", "high") == "high" else count / 2
	for i in n:
		_p.append({
			"p": Vector2(randf_range(area.position.x, area.end.x), randf_range(area.position.y, area.end.y)),
			"r": randf_range(size_range.x, size_range.y),
			"ph": randf() * TAU,
			"sp": randf_range(0.3, 1.0),
		})


func _process(delta: float) -> void:
	_t += delta
	for d in _p:
		d.p += (drift * d.sp + Vector2(sin(_t * d.sp + d.ph) * 8.0, cos(_t * 0.7 * d.sp + d.ph) * 4.0)) * delta
		if d.p.y < area.position.y - 10: d.p.y = area.end.y + 5
		if d.p.y > area.end.y + 10: d.p.y = area.position.y - 5
		if d.p.x < area.position.x - 10: d.p.x = area.end.x + 5
		if d.p.x > area.end.x + 10: d.p.x = area.position.x - 5
	queue_redraw()


func _draw() -> void:
	for d in _p:
		var a: float = color.a * (0.5 + 0.5 * sin(_t * 1.7 * d.sp + d.ph))
		if glow:
			draw_circle(d.p, d.r * 3.5, Color(color.r, color.g, color.b, a * 0.18))
		draw_circle(d.p, d.r, Color(color.r, color.g, color.b, a))
