extends PanelContainer
## Облачко реплики над головой Киры (для коротких фраз вне диалогов).

var _label: Label


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	var sb := UIKit.box(Color("#fbf7ff"), 0.96, UIKit.accent, 0.9, 22, 10)
	sb.content_margin_left = 22
	sb.content_margin_right = 22
	sb.content_margin_top = 12
	sb.content_margin_bottom = 12
	add_theme_stylebox_override("panel", sb)
	_label = UIKit.label("", 25, Color("#3a2a55"), UIKit.font_bold)
	_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_label.custom_minimum_size.x = 380
	_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	add_child(_label)
	visible = false


## Показать фразу над точкой (в координатах экрана).
func say(text: String, at: Vector2, time := 3.5) -> void:
	_label.text = text.replace("💜", "♥")
	visible = true
	size = Vector2.ZERO
	await get_tree().process_frame
	position = at - Vector2(size.x / 2.0, size.y + 20)
	var vs := get_viewport_rect().size
	position.x = clampf(position.x, 10, vs.x - size.x - 10)
	position.y = maxf(position.y, 10)
	modulate.a = 0.0
	scale = Vector2(0.9, 0.9)
	pivot_offset = Vector2(size.x / 2.0, size.y)
	var t := create_tween().set_parallel(true)
	t.tween_property(self, "modulate:a", 1.0, 0.2)
	t.tween_property(self, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.chain().tween_interval(time)
	t.chain().tween_property(self, "modulate:a", 0.0, 0.5)
	t.chain().tween_callback(func(): visible = false)
