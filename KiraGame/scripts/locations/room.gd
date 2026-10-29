extends "res://scripts/locations/location_base.gd"
## Комната Киры — домашняя локация. Обои, небо в окне по времени суток,
## режимы освещения, декор в слотах, сюжетные события (исчезающее фото, дверь).

const WIN := Rect2(400, 110, 380, 360)
const RAIN := preload("res://scripts/fx/rain.gd")
const STARS := preload("res://scripts/fx/starfield.gd")
const DUST := preload("res://scripts/fx/dust.gd")

var _back: Node2D
var _wall: Sprite2D
var _sky_a: Sprite2D
var _sky_b: Sprite2D
var _stars: Node2D
var _rain: Node2D
var _photo: Sprite2D
var _door: Sprite2D
var _door_glow: Sprite2D
var _vinyl: Sprite2D
var _decor_root: Node2D
var _garland_glows: Array = []
var _glow_pendant: Sprite2D
var _glow_desk: Sprite2D
var _glow_monitor: Sprite2D
var _glow_moon: Sprite2D
var _glow_door_light: Sprite2D
var _dust: Node2D
var _fore: Sprite2D
var vinyl_spinning := false
var dawn_mode := false


func _init() -> void:
	setup("room")


func build() -> void:
	_back = Node2D.new()
	content.add_child(_back)
	_parallax.append({"node": _back, "depth": 0.12, "base": Vector2.ZERO})
	_wall = layer("", 0.0, _back)
	_sky_a = layer("", 0.0, _back)
	_sky_b = layer("", 0.0, _back)
	_sky_b.modulate.a = 0.0
	_stars = STARS.new()
	_stars.area = WIN
	_stars.count = 50
	_stars.seed_value = 7
	_back.add_child(_stars)
	_rain = RAIN.new()
	_rain.area = WIN
	_rain.ground_y = WIN.end.y
	_rain.count = 90
	_rain.splashes = false
	_rain.visible = false
	_back.add_child(_rain)
	layer("room/furniture.svg", 0.0, _back)
	_glow_moon = glow(Vector2(590, 300), Color(0.7, 0.75, 1.0, 0.0), 300, _back)
	_vinyl = layer("room/vinyl.svg", 0.0, _back)
	_vinyl.centered = true
	_vinyl.position = Vector2(840, 606)
	_vinyl.scale = Vector2(0.62, 0.62)
	_photo = layer("room/photo_sea.svg", 0.0, _back, Vector2(1238, 598))
	_door_glow = glow(Vector2(1225, 470), Color(0.7, 0.55, 1.0, 0.0), 360, _back, 0.0, 0.25)
	_door = layer("room/door.svg", 0.0, _back, Vector2(1115, 190))
	_door.modulate.a = 0.0
	_glow_door_light = glow(Vector2(1225, 800), Color(0.75, 0.6, 1.0, 0.0), 260, _back)
	_decor_root = Node2D.new()
	_back.add_child(_decor_root)
	_glow_monitor = glow(Vector2(1720, 480), Color(0.55, 0.45, 1.0, 0.35), 220, _back, 0.0, 0.08)
	_glow_desk = glow(Vector2(1810, 540), Color(1.0, 0.82, 0.5, 0.0), 260, _back)
	_dust = DUST.new()
	_dust.area = Rect2(380, 200, 700, 700)
	_dust.count = 35
	_back.add_child(_dust)
	InventoryManager.room_changed.connect(_queue_refresh)
	TimeManager.phase_changed.connect(func(p): apply_time(p))
	_refresh_room()


func build_front() -> void:
	_fore = layer("room/fore.svg", 0.6)
	_glow_pendant = glow(Vector2(960, 140), Color(1.0, 0.86, 0.6, 0.0), 300)


var _refresh_queued := false


func _queue_refresh() -> void:
	if not _refresh_queued:
		_refresh_queued = true
		call_deferred("_refresh_room")


func _refresh_room() -> void:
	_refresh_queued = false
	_wall.texture = load(BG + "room/wall_%s.svg" % InventoryManager.wall)
	# Декор
	for c in _decor_root.get_children():
		c.queue_free()
	_garland_glows.clear()
	for slot in InventoryManager.placements.keys():
		var item: String = InventoryManager.placements[slot]
		var tex_path := "res://assets/decor/%s.svg" % item
		if not ResourceLoader.exists(tex_path):
			continue
		var s := Sprite2D.new()
		s.texture = load(tex_path)
		var info: Dictionary = InventoryManager.DECOR_SLOTS[slot]
		var size := s.texture.get_size()
		match info.type:
			"ceiling":
				s.position = info.pos + Vector2(0, size.y / 2.0)
			"wall":
				s.position = info.pos
			_:
				s.position = info.pos - Vector2(0, size.y / 2.0)
		_decor_root.add_child(s)
		if item == "garland":
			for i in 13:
				var x := 300.0 + i * 110.0
				var y := 60.0 + 34.0 * sin(i / 12.0 * PI)
				var g := glow(Vector2(x, y + 18), [Color(1, 0.85, 0.55), Color(1, 0.7, 0.85), Color(0.75, 0.7, 1.0)][i % 3], 70, _decor_root, 0.0, 0.3)
				g.pulse_speed = 1.2 + (i % 4) * 0.4
				_garland_glows.append(g)
		elif item == "moon_lamp" or item == "lantern" or item == "star_jar" or item == "crystal":
			glow(s.position, Color(1.0, 0.9, 0.7, 0.45) if item != "crystal" else Color(0.7, 0.6, 1.0, 0.45), 120, _decor_root, 0.0, 0.15)
	apply_time(TimeManager.phase)
	_refresh_story()


func _refresh_story() -> void:
	var photo_visible: bool = not GameManager.get_flag("photo_gone") or GameManager.get_flag("photo_restored")
	_photo.modulate.a = 1.0 if photo_visible else 0.0
	var door: bool = GameManager.get_flag("door_open")
	_door.modulate.a = 1.0 if door else 0.0
	_door_glow.base_alpha = 1.0 if door else 0.0
	_door_glow.color.a = 0.55 if door else 0.0
	_door_glow.apply()
	_glow_door_light.color.a = 0.25 if door else 0.0
	_glow_door_light.apply()
	_rain.visible = GameManager.get_flag("rain_room")


func apply_time(phase: String, instant := false) -> void:
	if _sky_a == null:
		return
	var sky_phase := phase
	if dawn_mode:
		sky_phase = "morning"
	var tex: Texture2D = load(BG + "room/sky_%s.svg" % sky_phase)
	if instant or _sky_a.texture == null:
		_sky_a.texture = tex
		_sky_b.modulate.a = 0.0
	elif _sky_a.texture != tex:
		_sky_b.texture = tex
		_sky_b.modulate.a = 0.0
		var t := create_tween()
		t.tween_property(_sky_b, "modulate:a", 1.0, 1.2)
		t.tween_callback(func():
			_sky_a.texture = tex
			_sky_b.modulate.a = 0.0)
	_stars.visible = phase == "night" or phase == "evening"
	_stars.modulate.a = 1.0 if phase == "night" else 0.4
	var light := InventoryManager.lighting
	var dark := phase == "evening" or phase == "night"
	var c: Color = TimeManager.TINTS.get(phase, Color.WHITE)
	if phase == "night":
		c = {"ceiling": Color(0.88, 0.85, 0.97), "lamp": Color(0.74, 0.62, 0.7), "garland": Color(0.56, 0.52, 0.8), "moon": Color(0.44, 0.46, 0.74)}.get(light, c)
	elif phase == "evening":
		c = {"ceiling": Color(1.0, 0.88, 0.86), "lamp": Color(0.95, 0.8, 0.76), "garland": Color(0.84, 0.74, 0.86), "moon": Color(0.74, 0.7, 0.86)}.get(light, c)
	set_tint(c, instant)
	_set_glow(_glow_pendant, 0.5 if dark and light == "ceiling" else 0.0, instant)
	_set_glow(_glow_desk, 0.75 if dark and light == "lamp" else 0.0, instant)
	_set_glow(_glow_moon, 0.35 if phase == "night" and light == "moon" else (0.12 if phase == "night" else 0.0), instant)
	_set_glow(_glow_monitor, 0.5 if dark else 0.25, instant)
	for g in _garland_glows:
		_set_glow(g, 0.85 if dark and light in ["garland", "lamp", "moon"] else (0.45 if dark else 0.15), instant)
	_dust.visible = not dark


func _set_glow(g: Sprite2D, a: float, instant: bool) -> void:
	if g == null:
		return
	g.color.a = a
	if instant:
		g.apply()
	else:
		create_tween().tween_property(g, "modulate:a", a, 1.0)


func tick(delta: float) -> void:
	if vinyl_spinning:
		_vinyl.rotation += delta * 3.5


func event(name: String, _args := {}) -> void:
	match name:
		"photo_vanish":
			var t := create_tween()
			for i in 4:
				t.tween_property(_photo, "modulate:a", 0.2, 0.08)
				t.tween_property(_photo, "modulate:a", 0.9, 0.08)
			t.tween_property(_photo, "modulate:a", 0.0, 0.6)
		"photo_restore":
			create_tween().tween_property(_photo, "modulate:a", 1.0, 1.2)
		"door_appear":
			shake(0.8)
			_door.modulate.a = 0.0
			var t := create_tween().set_parallel(true)
			t.tween_property(_door, "modulate:a", 1.0, 2.0)
			_door_glow.base_alpha = 1.0
			_door_glow.color.a = 0.55
			_door_glow.apply()
			_door_glow.modulate.a = 0.0
			t.tween_property(_door_glow, "modulate:a", 0.55, 2.0)
			_glow_door_light.color.a = 0.25
			t.tween_property(_glow_door_light, "modulate:a", 0.25, 2.0)
		"rain_on":
			_rain.visible = true
		"rain_off":
			_rain.visible = false
		"dawn":
			dawn_mode = true
			apply_time("morning")
		"refresh":
			_refresh_room()
