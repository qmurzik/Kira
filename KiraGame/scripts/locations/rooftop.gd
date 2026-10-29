extends "res://scripts/locations/location_base.gd"
## Крыша под звёздами: мерцающее небо, гирлянда, восстановленные созвездия, финальное созвездие.

const STARS := preload("res://scripts/fx/starfield.gd")
const DUST := preload("res://scripts/fx/dust.gd")

var _stars: Node2D
var _const_draw: Node2D
var _constellations: Array = []
var _reveal := 0.0
var _kira_const := 0.0
var _show_kira_const := false


func _init() -> void:
	setup("rooftop")


func build() -> void:
	layer("rooftop/sky.svg", 0.05)
	_stars = STARS.new()
	_stars.area = Rect2(0, 0, 1920, 720)
	_stars.count = 120
	content.add_child(_stars)
	_parallax.append({"node": _stars, "depth": 0.05, "base": Vector2.ZERO})
	_const_draw = Node2D.new()
	_const_draw.draw.connect(_draw_constellations)
	content.add_child(_const_draw)
	_parallax.append({"node": _const_draw, "depth": 0.05, "base": Vector2.ZERO})
	layer("rooftop/city.svg", 0.15)
	var main := layer("rooftop/main.svg", 0.35)
	for i in range(0, 41, 2):
		var x := 360.0 + i * 30.0
		var y := 338.0 + 60.0 * sin(i / 40.0 * PI)
		var g := glow(Vector2(x, y), [Color(1, 0.88, 0.6), Color(1, 0.7, 0.85), Color(0.75, 0.7, 1.0)][i / 2 % 3], 60, main, 0.0, 0.3)
		g.color.a = 0.8
		g.pulse_speed = 1.0 + (i % 5) * 0.3
		g.apply()
	glow(Vector2(1640, 234), Color(1.0, 0.4, 0.6, 0.6), 50, main, 0.0, 0.6)
	var flies := DUST.new()
	flies.area = Rect2(0, 500, 1920, 500)
	flies.count = 26
	flies.color = Color(1.0, 0.95, 0.6, 0.8)
	flies.glow = true
	content.add_child(flies)
	var f := FileAccess.open("res://data/constellations.json", FileAccess.READ)
	if f:
		var d = JSON.parse_string(f.get_as_text())
		if d is Dictionary:
			_constellations = d.get("sky", [])
	_reveal = 1.0 if GameManager.get_flag("roof_stars_restored") else 0.0
	_stars.modulate.a = 1.0 if _reveal > 0.0 else 0.55


func apply_time(_phase: String, instant := false) -> void:
	set_tint(Color(0.95, 0.93, 1.0), instant)


func tick(delta: float) -> void:
	if _show_kira_const:
		_kira_const = minf(_kira_const + delta * 0.25, 1.0)
	_const_draw.queue_redraw()


func _draw_constellations() -> void:
	if _reveal > 0.0:
		for c in _constellations:
			var pts: Array = c.points
			var a := _reveal * (0.55 + 0.25 * sin(_t * 1.5 + pts.size()))
			for e in c.edges:
				var p1 := Vector2(pts[e[0]][0], pts[e[0]][1])
				var p2 := Vector2(pts[e[1]][0], pts[e[1]][1])
				_const_draw.draw_line(p1, p2, Color(0.82, 0.72, 1.0, a * 0.7), 2.0, true)
			for p in pts:
				_const_draw.draw_circle(Vector2(p[0], p[1]), 4.0, Color(1, 0.97, 0.9, a + 0.2))
	if _kira_const > 0.0:
		_draw_kira_constellation()


## Финальное созвездие в форме девушки с заколкой — к концовке «Созвездие воспоминаний».
func _draw_kira_constellation() -> void:
	var pts := [Vector2(960, 120), Vector2(900, 170), Vector2(1020, 170), Vector2(880, 250), Vector2(1040, 250),
		Vector2(960, 300), Vector2(860, 380), Vector2(1060, 380), Vector2(920, 470), Vector2(1000, 470), Vector2(1090, 140)]
	var edges := [[0, 1], [0, 2], [1, 3], [2, 4], [3, 5], [4, 5], [5, 6], [5, 7], [5, 8], [5, 9], [2, 10]]
	var k := _kira_const
	var n := int(ceil(edges.size() * k))
	for i in n:
		var e: Array = edges[i]
		_const_draw.draw_line(pts[e[0]], pts[e[1]], Color(1.0, 0.85, 0.95, 0.85), 3.0, true)
	for i in pts.size():
		if float(i) / pts.size() <= k:
			var r := 5.0 + 2.0 * sin(_t * 3.0 + i)
			_const_draw.draw_circle(pts[i], r * 2.4, Color(0.85, 0.7, 1.0, 0.25))
			_const_draw.draw_circle(pts[i], r, Color(1, 0.98, 0.92, 1.0))


func event(name: String, _args := {}) -> void:
	match name:
		"restore_stars":
			GameManager.set_flag("roof_stars_restored", true)
			var t := create_tween().set_parallel(true)
			t.tween_property(self, "_reveal", 1.0, 3.0)
			t.tween_property(_stars, "modulate:a", 1.0, 3.0)
		"kira_constellation":
			_show_kira_const = true
