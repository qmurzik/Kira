extends "res://scripts/locations/location_base.gd"
## Заброшенная библиотека: лунные лучи, пылинки, парящие книги, свеча, дверь архива.

const DUST := preload("res://scripts/fx/dust.gd")

var _books: Array = []
var _door_glow: Sprite2D
var _door_open: ColorRect


func _init() -> void:
	setup("library")


func build() -> void:
	layer("library/back.svg", 0.08)
	for x in [660.0, 1260.0]:
		var d := DUST.new()
		d.area = Rect2(x - 260, 300, 560, 700)
		d.count = 40
		d.color = Color(0.85, 0.9, 1.0, 0.7)
		d.drift = Vector2(4, 8)
		content.add_child(d)
	var main := layer("library/main.svg", 0.3)
	glow(Vector2(1180, 615), Color(1.0, 0.8, 0.45, 0.6), 220, main, 0.35)
	_door_glow = glow(Vector2(1590, 520), Color(0.7, 0.6, 1.0, 0.5), 260, main, 0.0, 0.35)
	_door_open = ColorRect.new()
	_door_open.position = Vector2(1466, 384)
	_door_open.size = Vector2(248, 392)
	_door_open.color = Color(1.0, 0.95, 0.85, 0.0)
	main.add_child(_door_open)
	for i in 3:
		var b := Sprite2D.new()
		b.texture = load(BG + "library/book.svg")
		b.position = [Vector2(780, 300), Vector2(1000, 220), Vector2(1350, 160)][i]
		b.scale = Vector2.ONE * [0.9, 0.7, 0.6][i]
		b.set_meta("base", b.position)
		main.add_child(b)
		_books.append(b)
	_refresh()


func _refresh() -> void:
	if GameManager.get_flag("archive_open"):
		_door_open.color.a = 0.85
		_door_glow.color = Color(1.0, 0.9, 0.7, 0.8)
		_door_glow.apply()


func apply_time(_phase: String, instant := false) -> void:
	set_tint(Color(0.92, 0.9, 1.0), instant)


func tick(_delta: float) -> void:
	for i in _books.size():
		var b: Sprite2D = _books[i]
		var base: Vector2 = b.get_meta("base")
		b.position = base + Vector2(sin(_t * 0.5 + i) * 14.0, sin(_t * 0.9 + i * 2.0) * 18.0)
		b.rotation = sin(_t * 0.6 + i) * 0.12


func event(name: String, _args := {}) -> void:
	match name:
		"archive_open":
			GameManager.set_flag("archive_open", true)
			shake(0.6)
			create_tween().tween_property(_door_open, "color:a", 0.85, 1.5)
			_door_glow.color = Color(1.0, 0.9, 0.7, 0.8)
			_door_glow.apply()
