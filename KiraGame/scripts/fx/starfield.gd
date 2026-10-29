extends Node2D
## Мерцающие звёзды и редкие падающие звёзды.

@export var area := Rect2(0, 0, 1920, 700)
@export var count := 220
@export var shooting := true
@export var seed_value := 42
@export var tint := Color(1, 1, 1)

var _stars: Array = []
var _shoot: Dictionary = {}
var _shoot_timer := 4.0
var _t := 0.0


func _ready() -> void:
	var rnd := RandomNumberGenerator.new()
	rnd.seed = seed_value
	var n := count if Settings.get_value("display", "particles", "high") == "high" else count / 2
	for i in n:
		_stars.append({
			"p": Vector2(rnd.randf_range(area.position.x, area.end.x), rnd.randf_range(area.position.y, area.end.y)),
			"r": rnd.randf_range(0.6, 2.2) if rnd.randf() < 0.9 else rnd.randf_range(2.2, 3.4),
			"ph": rnd.randf() * TAU,
			"sp": rnd.randf_range(0.6, 2.4),
		})


func _process(delta: float) -> void:
	_t += delta
	if shooting:
		_shoot_timer -= delta
		if _shoot_timer <= 0.0 and _shoot.is_empty():
			_shoot_timer = randf_range(5.0, 12.0)
			var start := Vector2(randf_range(area.position.x + 200, area.end.x), randf_range(area.position.y, area.position.y + area.size.y * 0.4))
			_shoot = {"p": start, "v": Vector2(-randf_range(700, 1000), randf_range(250, 400)), "t": 0.0}
		if not _shoot.is_empty():
			_shoot.t += delta
			_shoot.p += _shoot.v * delta
			if _shoot.t > 0.9:
				_shoot = {}
	queue_redraw()


func _draw() -> void:
	for s in _stars:
		var a: float = 0.55 + 0.45 * sin(_t * s.sp + s.ph)
		var c := Color(tint.r, tint.g, tint.b, a)
		draw_circle(s.p, s.r, c)
		if s.r > 2.2:
			draw_line(s.p - Vector2(s.r * 3, 0), s.p + Vector2(s.r * 3, 0), Color(c, a * 0.5), 1.0)
			draw_line(s.p - Vector2(0, s.r * 3), s.p + Vector2(0, s.r * 3), Color(c, a * 0.5), 1.0)
	if not _shoot.is_empty():
		var k: float = 1.0 - _shoot.t / 0.9
		var dir: Vector2 = _shoot.v.normalized()
		for i in 12:
			var p: Vector2 = _shoot.p - dir * i * 9.0
			draw_circle(p, 2.4 - i * 0.18, Color(1, 0.95, 0.85, k * (1.0 - i / 12.0)))
