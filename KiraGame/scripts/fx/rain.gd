extends Node2D
## Дождь: косые капли с параллаксом и брызги на «земле». Рисуется вручную — дёшево для слабых ПК.

@export var area := Rect2(0, 0, 1920, 1080)
@export var count := 260
@export var ground_y := 1000.0
@export var color := Color(0.78, 0.74, 1.0, 0.45)
@export var wind := -0.18
@export var speed := 1500.0
@export var splashes := true

var _drops: Array = []
var _splash: Array = []


func _ready() -> void:
	var n := count
	if Settings.get_value("display", "particles", "high") == "low":
		n = count / 3
	for i in n:
		_drops.append(_new_drop(true))


func _new_drop(random_y: bool) -> Dictionary:
	var depth := randf_range(0.4, 1.0)
	return {
		"p": Vector2(randf_range(area.position.x - 100, area.end.x + 100), randf_range(area.position.y, area.end.y) if random_y else area.position.y - randf_range(0, 200)),
		"d": depth,
	}


func _process(delta: float) -> void:
	for d in _drops:
		var v = Vector2(wind, 1.0) * speed * d.d
		d.p += v * delta
		var gy: float = lerpf(area.position.y + area.size.y * 0.6, ground_y, d.d)
		if d.p.y > gy:
			if splashes and d.d > 0.7 and _splash.size() < 60:
				_splash.append({"p": Vector2(d.p.x, gy), "t": 0.0})
			var nd := _new_drop(false)
			d.p = nd.p
			d.d = nd.d
	for s in _splash:
		s.t += delta
	_splash = _splash.filter(func(s): return s.t < 0.3)
	queue_redraw()


func _draw() -> void:
	for d in _drops:
		var len: float = 18.0 + 22.0 * d.d
		var c := color
		c.a *= d.d
		draw_line(d.p, d.p - Vector2(wind, 1.0).normalized() * len, c, 1.0 + d.d)
	for s in _splash:
		var k: float = s.t / 0.3
		var c2 := color
		c2.a *= 1.0 - k
		draw_arc(s.p, 3.0 + k * 10.0, PI, TAU, 8, c2, 1.2)
