extends Control
## Титульная карточка главы / концовки с плавным появлением.

signal done

var top_text := ""
var main_text := ""
var hold := 2.6


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	var bg := ColorRect.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.color = Color(0.04, 0.03, 0.1, 0.0)
	add_child(bg)
	var v := VBoxContainer.new()
	v.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	v.grow_horizontal = Control.GROW_DIRECTION_BOTH
	v.grow_vertical = Control.GROW_DIRECTION_BOTH
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	add_child(v)
	var a := UIKit.label(top_text, 34, UIKit.accent.lightened(0.3), UIKit.font_title)
	a.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(a)
	var b := UIKit.label(main_text, 84, Color.WHITE, UIKit.font_script)
	b.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	b.add_theme_color_override("font_shadow_color", Color(UIKit.accent, 0.8))
	b.add_theme_constant_override("shadow_outline_size", 24)
	b.add_theme_constant_override("shadow_offset_x", 0)
	b.add_theme_constant_override("shadow_offset_y", 0)
	v.add_child(b)
	var line := ColorRect.new()
	line.custom_minimum_size = Vector2(0, 2)
	line.color = Color(UIKit.accent2, 0.8)
	v.add_child(line)
	a.modulate.a = 0.0
	b.modulate.a = 0.0
	AudioManager.play_sfx("chapter")
	var t := create_tween()
	t.tween_property(bg, "color:a", 0.88, 0.6)
	t.tween_property(a, "modulate:a", 1.0, 0.6)
	t.tween_property(b, "modulate:a", 1.0, 0.9)
	t.tween_interval(hold)
	t.tween_property(self, "modulate:a", 0.0, 0.9)
	t.tween_callback(func(): done.emit(); queue_free())
