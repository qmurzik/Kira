extends "res://scripts/locations/location_base.gd"
## Ночной город: дождь, неон, отражения в лужах, осколки воспоминаний. Есть режим рассвета для концовки.

const RAIN := preload("res://scripts/fx/rain.gd")
const DUST := preload("res://scripts/fx/dust.gd")
const FRAGMENTS := {
	"frag_puddle": Vector2(620, 1000),
	"frag_phone": Vector2(1300, 735),
	"frag_posters": Vector2(1490, 610),
}

var _sky: Sprite2D
var _rain_back: Node2D
var _rain_front: Node2D
var _neon: Label
var _neon_glow: Sprite2D
var _frags := {}
var _sparkles: Node2D
var dawn := false


func _init() -> void:
	setup("city")


func build() -> void:
	_sky = layer("city/sky.svg", 0.08)
	layer("city/far.svg", 0.2)
	_rain_back = RAIN.new()
	_rain_back.count = 160
	_rain_back.ground_y = 860
	_rain_back.color = Color(0.7, 0.66, 0.95, 0.3)
	_rain_back.splashes = false
	content.add_child(_rain_back)
	var street := Node2D.new()
	content.add_child(street)
	_parallax.append({"node": street, "depth": 0.4, "base": Vector2.ZERO})
	layer("city/street.svg", 0.0, street)
	glow(Vector2(250, 680), Color(1.0, 0.75, 0.55, 0.35), 320, street, 0.05)
	glow(Vector2(1300, 700), Color(1.0, 0.85, 0.6, 0.25), 180, street, 0.1)
	_neon_glow = glow(Vector2(390, 466), Color(1.0, 0.45, 0.75, 0.55), 330, street, 0.25)
	_neon_glow.scale.y *= 0.35
	_neon = Label.new()
	_neon.text = "Полночная чашка"
	_neon.position = Vector2(110, 420)
	_neon.size = Vector2(560, 80)
	_neon.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_neon.add_theme_font_override("font", UIKit.font_script)
	_neon.add_theme_font_size_override("font_size", 58)
	_neon.add_theme_color_override("font_color", Color("#ffd1ea"))
	_neon.add_theme_color_override("font_outline_color", Color("#ff4fa3"))
	_neon.add_theme_constant_override("outline_size", 6)
	street.add_child(_neon)
	for id in FRAGMENTS.keys():
		var n := Node2D.new()
		n.position = FRAGMENTS[id]
		street.add_child(n)
		var g := glow(Vector2.ZERO, Color(0.8, 0.65, 1.0, 0.8), 90, n, 0.0, 0.4)
		g.pulse_speed = 3.0
		var shard := Polygon2D.new()
		shard.polygon = PackedVector2Array([Vector2(0, -22), Vector2(12, 0), Vector2(0, 22), Vector2(-12, 0)])
		shard.color = Color(0.92, 0.85, 1.0)
		n.add_child(shard)
		_frags[id] = n
	_sparkles = DUST.new()
	_sparkles.area = Rect2(0, 0, 1920, 900)
	_sparkles.count = 50
	_sparkles.color = Color(1.0, 0.85, 0.6, 0.7)
	_sparkles.glow = true
	_sparkles.visible = false
	content.add_child(_sparkles)
	_refresh()


func build_front() -> void:
	_rain_front = RAIN.new()
	_rain_front.count = 140
	_rain_front.ground_y = 1080
	content.add_child(_rain_front)
	var fore := layer("city/fore.svg", 0.9)
	glow(Vector2(1730, 150), Color(1.0, 0.86, 0.6, 0.5), 260, fore, 0.03)


func _refresh() -> void:
	for id in _frags.keys():
		_frags[id].visible = GameManager.get_flag("city_search") and not GameManager.get_flag(id)
	_set_dawn(GameManager.get_flag("city_dawn"))


func _set_dawn(on: bool) -> void:
	dawn = on
	_sky.texture = load(BG + ("city/sky_dawn.svg" if on else "city/sky.svg"))
	_rain_back.visible = not on
	if _rain_front:
		_rain_front.visible = not on
	_sparkles.visible = on
	_neon.modulate.a = 0.35 if on else 1.0
	apply_time(TimeManager.phase, true)
	AudioManager.play_ambience("" if on else "rain")


func apply_time(_phase: String, instant := false) -> void:
	set_tint(Color(1.0, 0.93, 0.9) if dawn else Color(0.95, 0.92, 1.0), instant)


func tick(_delta: float) -> void:
	if _neon and not dawn:
		# Неон иногда мигает — буква за буквой не трогаем, мигает вся вывеска
		if randf() < 0.01:
			_neon.modulate.a = 0.4
		else:
			_neon.modulate.a = lerpf(_neon.modulate.a, 1.0, 0.1)
	for id in _frags.keys():
		var n: Node2D = _frags[id]
		if n.visible:
			n.position.y = FRAGMENTS[id].y + sin(_t * 2.0 + n.position.x) * 6.0
			n.rotation = sin(_t * 1.3) * 0.2


func event(name: String, _args := {}) -> void:
	match name:
		"collect":
			_refresh()
		"refresh":
			_refresh()
		"dawn":
			GameManager.set_flag("city_dawn", true)
			_set_dawn(true)
