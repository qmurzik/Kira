extends Button
## Кнопка с мягкой анимацией наведения/нажатия и звуками.

var hover_scale := 1.04
var _tween: Tween


func _ready() -> void:
	focus_mode = Control.FOCUS_ALL
	mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	mouse_entered.connect(_on_hover.bind(true))
	mouse_exited.connect(_on_hover.bind(false))
	focus_entered.connect(_on_hover.bind(true))
	focus_exited.connect(_on_hover.bind(false))
	pressed.connect(func(): AudioManager.play_sfx("click", randf_range(0.97, 1.05)))
	resized.connect(func(): pivot_offset = size / 2.0)
	pivot_offset = size / 2.0


func _on_hover(on: bool) -> void:
	if disabled:
		return
	if on and is_hovered():
		AudioManager.play_sfx("hover", randf_range(0.95, 1.1), -6.0)
	if _tween:
		_tween.kill()
	_tween = create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	_tween.tween_property(self, "scale", Vector2.ONE * (hover_scale if on else 1.0), 0.18)
