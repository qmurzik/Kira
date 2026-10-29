extends "res://scripts/locations/location_base.gd"
## Поезд между мирами: бесконечное звёздное небо за окнами, покачивание вагона, пассажиры.

var _space_a: Sprite2D
var _space_b: Sprite2D
var _streaks: Node2D
var _streak_data: Array = []
var _interior: Node2D
var _npcs := {}
var speed := 70.0


func _init() -> void:
	setup("train")


func build() -> void:
	_space_a = layer("train/space.svg")
	_space_b = layer("train/space.svg")
	_space_b.position.x = 3840
	_streaks = Node2D.new()
	_streaks.draw.connect(_draw_streaks)
	content.add_child(_streaks)
	for i in 40:
		_streak_data.append({"p": Vector2(randf_range(0, 1920), randf_range(150, 560)), "s": randf_range(300, 900), "l": randf_range(30, 120)})
	_interior = Node2D.new()
	content.add_child(_interior)
	layer("train/interior.svg", 0.0, _interior)
	for x in [380.0, 850.0, 1320.0, 1760.0]:
		var g := glow(Vector2(x, 100), Color(1.0, 0.86, 0.6, 0.55), 220, _interior, 0.04, 0.1)
		g.pulse_speed = 0.8
	var npcs := {"kite_girl": Vector2(170, 360), "knight": Vector2(640, 340), "robot": Vector2(1110, 360)}
	for id in npcs.keys():
		var s := layer("train/%s.svg" % id, 0.0, _interior, npcs[id])
		_npcs[id] = s
	var cond := layer("train/conductor.svg", 0.0, _interior, Vector2(1420, 40))
	cond.scale = Vector2(0.85, 0.85)
	_npcs["conductor"] = cond
	_refresh()


func _refresh() -> void:
	_npcs.kite_girl.visible = not GameManager.get_flag("kite_done")
	_npcs.knight.visible = true
	_npcs.robot.visible = true
	_npcs.conductor.visible = GameManager.get_flag("conductor_here")


func apply_time(_phase: String, instant := false) -> void:
	set_tint(Color(0.97, 0.94, 1.0), instant)


func tick(delta: float) -> void:
	for s in [_space_a, _space_b]:
		s.position.x -= speed * delta
		if s.position.x <= -3840:
			s.position.x += 7680
	for d in _streak_data:
		d.p.x -= d.s * delta
		if d.p.x < -150:
			d.p.x = 2000
			d.p.y = randf_range(150, 560)
	_streaks.queue_redraw()
	# лёгкое покачивание вагона
	_interior.position.y = sin(_t * 9.0) * 0.8 + (sin(_t * 1.7) * 1.2)


func _draw_streaks() -> void:
	for d in _streak_data:
		_streaks.draw_line(d.p, d.p + Vector2(d.l, 0), Color(0.9, 0.85, 1.0, 0.35), 1.5)


func event(name: String, args := {}) -> void:
	match name:
		"refresh":
			_refresh()
		"show":
			var n: Sprite2D = _npcs.get(str(args.get("id", "")))
			if n:
				n.visible = true
				n.modulate.a = 0.0
				create_tween().tween_property(n, "modulate:a", 1.0, 1.0)
		"hide":
			var n2: Sprite2D = _npcs.get(str(args.get("id", "")))
			if n2:
				var t := create_tween()
				t.tween_property(n2, "modulate:a", 0.0, 1.2)
				t.tween_callback(func(): n2.visible = false; n2.modulate.a = 1.0)
		"stop":
			create_tween().tween_property(self, "speed", 0.0, 3.0)
