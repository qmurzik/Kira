extends "res://scripts/ui/modal.gd"
## Универсальное окно выбора: карточки с названием, описанием и пометкой о доступности.
## Используется для занятий, прогулок, выбора музыки и освещения.

signal picked(id: String)

var heading := ""
var options: Array = []
var columns := 2


func build() -> void:
	set_title(heading)
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(sc)
	var grid := GridContainer.new()
	grid.columns = columns
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	grid.add_theme_constant_override("h_separation", 16)
	grid.add_theme_constant_override("v_separation", 16)
	sc.add_child(grid)
	for o in options:
		var b: Button = UIKit.button("", Callable(), str(o.get("icon", "")))
		b.custom_minimum_size = Vector2((panel_size.x - 100) / columns - 10, 120)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.text = str(o.title)
		b.hover_scale = 1.02
		b.disabled = not o.get("enabled", true)
		var v := VBoxContainer.new()
		v.mouse_filter = Control.MOUSE_FILTER_IGNORE
		v.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		v.offset_left = 80 if o.get("icon", "") != "" else 24
		v.offset_top = 12
		v.offset_right = -16
		v.alignment = BoxContainer.ALIGNMENT_CENTER
		b.text = ""
		var t := UIKit.label(str(o.title), 27, Color.WHITE if not b.disabled else UIKit.TEXT_DIM, UIKit.font_bold)
		v.add_child(t)
		var desc := str(o.get("desc", ""))
		if o.has("note") and str(o.note) != "":
			desc += ("\n" if desc != "" else "") + "⟡ " + str(o.note)
		if desc != "":
			var d := UIKit.label(desc, 20, UIKit.TEXT_DIM)
			d.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			v.add_child(d)
		b.add_child(v)
		var id: String = str(o.id)
		b.pressed.connect(func():
			picked.emit(id)
			close())
		grid.add_child(b)
