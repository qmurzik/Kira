extends "res://scripts/locations/location_base.gd"
## «По ту сторону экрана»: белое пространство, обрывки слов, стекло экрана изнутри.

const WORDS := ["привет", "звёзды", "ты пришёл?", "глава 1", "кафе", "дождь", "билет", "не забывай",
	"блинчики", "крыша", "Кира", "обещание", "ключ", "страница", "рассвет", "конец?", "...", "ещё чуть-чуть"]

var _words: Array = []
var _glass: Node2D
var _cursor_t := 0.0
var glass_visible := false


func _init() -> void:
	setup("edge")


func build() -> void:
	layer("edge/white.svg")
	for i in 26:
		var l := Label.new()
		l.text = WORDS[i % WORDS.size()]
		if l.text == "Кира" and GameManager.player_name != "":
			l.text = GameManager.player_name
		l.add_theme_font_override("font", UIKit.font_script if i % 3 == 0 else UIKit.font_regular)
		l.add_theme_font_size_override("font_size", randi_range(26, 54))
		l.add_theme_color_override("font_color", Color(0.45, 0.35, 0.75, randf_range(0.15, 0.4)))
		l.position = Vector2(randf_range(40, 1800), randf_range(40, 1000))
		l.set_meta("v", Vector2(randf_range(-12, 12), randf_range(-18, -6)))
		content.add_child(l)
		_words.append(l)
	_glass = Node2D.new()
	_glass.draw.connect(_draw_glass)
	_glass.modulate.a = 0.0
	content.add_child(_glass)


func apply_time(_phase: String, instant := false) -> void:
	set_tint(Color.WHITE, instant)


func tick(delta: float) -> void:
	for l in _words:
		l.position += l.get_meta("v") * delta
		if l.position.y < -60:
			l.position.y = 1100
			l.position.x = randf_range(40, 1800)
	_cursor_t += delta
	_glass.queue_redraw()


func _draw_glass() -> void:
	var r := Rect2(360, 140, 1200, 700)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.85, 0.8, 1.0, 0.18)
	sb.border_color = Color(0.6, 0.45, 1.0, 0.8)
	sb.set_border_width_all(6)
	sb.set_corner_radius_all(30)
	sb.shadow_color = Color(0.6, 0.45, 1.0, 0.35)
	sb.shadow_size = 40
	_glass.draw_style_box(sb, r)
	# блики на стекле
	_glass.draw_line(r.position + Vector2(80, 60), r.position + Vector2(260, 60), Color(1, 1, 1, 0.6), 6.0)
	_glass.draw_line(r.position + Vector2(80, 90), r.position + Vector2(160, 90), Color(1, 1, 1, 0.4), 6.0)
	# «курсор» игрока по ту сторону
	var c := r.get_center() + Vector2(cos(_cursor_t * 0.7) * 300, sin(_cursor_t * 1.1) * 160)
	var arrow := PackedVector2Array([c, c + Vector2(0, 36), c + Vector2(9, 27), c + Vector2(17, 42), c + Vector2(23, 39), c + Vector2(15, 25), c + Vector2(27, 25)])
	_glass.draw_colored_polygon(arrow, Color(0.25, 0.18, 0.45, 0.85))


func event(name: String, _args := {}) -> void:
	match name:
		"glass":
			glass_visible = true
			create_tween().tween_property(_glass, "modulate:a", 1.0, 2.0)
		"glass_off":
			create_tween().tween_property(_glass, "modulate:a", 0.0, 1.5)
