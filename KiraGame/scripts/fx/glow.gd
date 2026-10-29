extends Sprite2D
## Мягкое аддитивное свечение (лампы, неон, огоньки) с необязательным мерцанием.

@export var color := Color(1.0, 0.85, 0.6, 0.6)
@export var radius := 160.0
@export var flicker := 0.0
@export var pulse := 0.0
@export var pulse_speed := 1.5

var base_alpha := 1.0
var _t := 0.0

static var _tex: GradientTexture2D


func _ready() -> void:
	if _tex == null:
		_tex = GradientTexture2D.new()
		_tex.fill = GradientTexture2D.FILL_RADIAL
		_tex.fill_from = Vector2(0.5, 0.5)
		_tex.fill_to = Vector2(1.0, 0.5)
		var g := Gradient.new()
		g.offsets = PackedFloat32Array([0.0, 0.35, 1.0])
		g.colors = PackedColorArray([Color(1, 1, 1, 1), Color(1, 1, 1, 0.35), Color(1, 1, 1, 0)])
		_tex.gradient = g
		_tex.width = 256
		_tex.height = 256
	texture = _tex
	var m := CanvasItemMaterial.new()
	m.blend_mode = CanvasItemMaterial.BLEND_MODE_ADD
	material = m
	apply()


func apply() -> void:
	modulate = color
	scale = Vector2.ONE * (radius / 128.0)


func _process(delta: float) -> void:
	if flicker <= 0.0 and pulse <= 0.0:
		return
	_t += delta
	var a := base_alpha
	if pulse > 0.0:
		a *= 1.0 - pulse * (0.5 + 0.5 * sin(_t * pulse_speed))
	if flicker > 0.0 and randf() < 0.08:
		a *= 1.0 - randf() * flicker
	modulate = Color(color.r, color.g, color.b, color.a * a)
