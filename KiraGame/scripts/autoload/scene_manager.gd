extends CanvasLayer
## Переходы между сценами и экранные эффекты (затемнение, вспышка, «сбой»).

signal transition_finished

const GLITCH_SHADER := preload("res://shaders/glitch.gdshader")

var _fade: ColorRect
var _glitch: ColorRect
var _glitch_mat: ShaderMaterial
var _glitch_time := 0.0
var _glitch_amount := 0.0
var busy := false


func _ready() -> void:
	layer = 100
	process_mode = Node.PROCESS_MODE_ALWAYS
	_glitch = ColorRect.new()
	_glitch.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_glitch.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_glitch_mat = ShaderMaterial.new()
	_glitch_mat.shader = GLITCH_SHADER
	_glitch.material = _glitch_mat
	_glitch.visible = false
	add_child(_glitch)
	_fade = ColorRect.new()
	_fade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_fade.color = Color(0.04, 0.03, 0.1, 0.0)
	_fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_fade)


func _process(delta: float) -> void:
	if _glitch_time > 0.0:
		_glitch_time -= delta
		var a = _glitch_amount * Settings.get_value("display", "glitch", 1.0)
		if _glitch_time <= 0.0 or a <= 0.01:
			_glitch.visible = false
			_glitch_time = 0.0
		else:
			_glitch.visible = true
			_glitch_mat.set_shader_parameter("amount", a * clampf(_glitch_time * 3.0, 0.0, 1.0))
			_glitch_mat.set_shader_parameter("seed", Time.get_ticks_msec() / 1000.0)


func change_scene(path: String, color := Color(0.04, 0.03, 0.1)) -> void:
	busy = true
	await fade_out(0.6, color)
	get_tree().paused = false
	get_tree().change_scene_to_file(path)
	await get_tree().process_frame
	await get_tree().process_frame
	await fade_in(0.7)
	busy = false
	transition_finished.emit()


func fade_out(time := 0.5, color := Color(0.04, 0.03, 0.1)) -> void:
	_fade.mouse_filter = Control.MOUSE_FILTER_STOP
	var c := color
	c.a = _fade.color.a
	_fade.color = c
	var t := create_tween()
	t.tween_property(_fade, "color:a", 1.0, time)
	await t.finished


func fade_in(time := 0.5) -> void:
	var t := create_tween()
	t.tween_property(_fade, "color:a", 0.0, time)
	await t.finished
	_fade.mouse_filter = Control.MOUSE_FILTER_IGNORE


func flash(color := Color(1, 1, 1), time := 0.6) -> void:
	_fade.color = Color(color.r, color.g, color.b, 0.9)
	var t := create_tween()
	t.tween_property(_fade, "color:a", 0.0, time)
	await t.finished


func glitch(duration := 0.6, amount := 0.8) -> void:
	if Settings.get_value("display", "glitch", 1.0) <= 0.01:
		return
	_glitch_time = duration
	_glitch_amount = amount
	AudioManager.play_sfx("glitch", randf_range(0.9, 1.1))
