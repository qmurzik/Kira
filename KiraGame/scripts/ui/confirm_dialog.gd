extends "res://scripts/ui/modal.gd"
## Подтверждение действия.

signal answered(yes: bool)

var question := ""
var yes_text := "Да"
var no_text := "Нет"


func _init() -> void:
	panel_size = Vector2(760, 330)


func build() -> void:
	set_title("Подтверждение")
	var l := wrap_label(question, 28)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.size_flags_vertical = Control.SIZE_EXPAND_FILL
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	body.add_child(l)
	var h := HBoxContainer.new()
	h.alignment = BoxContainer.ALIGNMENT_CENTER
	h.add_theme_constant_override("separation", 24)
	body.add_child(h)
	var y := UIKit.button(yes_text, func(): answered.emit(true); close())
	y.custom_minimum_size = Vector2(220, 60)
	h.add_child(y)
	var n := UIKit.button(no_text, func(): answered.emit(false); close())
	n.custom_minimum_size = Vector2(220, 60)
	h.add_child(n)
	n.grab_focus.call_deferred()
