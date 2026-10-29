extends "res://scripts/ui/modal.gd"
## Гардероб и оформление: наряды Киры, обои, освещение, цветовая тема интерфейса.

signal outfit_picked(id: String)


func _init() -> void:
	panel_size = Vector2(1400, 860)


func build() -> void:
	set_title("Гардероб и комната")
	var tabs := TabContainer.new()
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	tabs.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	body.add_child(tabs)
	tabs.add_child(_grid_page("outfits", "outfits", InventoryManager.outfits, InventoryManager.outfit, func(id):
		InventoryManager.set_outfit(id)
		outfit_picked.emit(id)))
	tabs.add_child(_grid_page("walls", "walls", InventoryManager.walls, InventoryManager.wall, func(id): InventoryManager.set_wall(id)))
	var lights: Array = InventoryManager.db.get("lighting", {}).keys()
	tabs.add_child(_grid_page("lighting", "lighting", lights, InventoryManager.lighting, func(id): InventoryManager.set_lighting(id)))
	var themes: Array = InventoryManager.db.get("themes", {}).keys()
	tabs.add_child(_grid_page("themes", "themes", themes, InventoryManager.ui_theme, func(id):
		InventoryManager.set_theme(id)
		close()))
	for i in tabs.get_tab_count():
		tabs.set_tab_title(i, ["Наряды Киры", "Обои", "Освещение", "Тема интерфейса"][i])


func _grid_page(name: String, kind: String, unlocked: Array, current: String, cb: Callable) -> Control:
	var sc := ScrollContainer.new()
	sc.name = name
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var grid := GridContainer.new()
	grid.columns = 3
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	grid.add_theme_constant_override("h_separation", 16)
	grid.add_theme_constant_override("v_separation", 16)
	sc.add_child(grid)
	var all: Dictionary = InventoryManager.db.get(kind, {})
	var buttons := []
	for id in all.keys():
		var info: Dictionary = all[id]
		var ok = id in unlocked
		var b: Button = UIKit.button("")
		b.custom_minimum_size = Vector2(410, 150 if kind != "outfits" else 200)
		b.hover_scale = 1.02
		b.toggle_mode = true
		b.button_pressed = id == current
		b.disabled = not ok
		var v := VBoxContainer.new()
		v.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		v.offset_left = 20
		v.offset_right = -20
		v.alignment = BoxContainer.ALIGNMENT_CENTER
		v.mouse_filter = Control.MOUSE_FILTER_IGNORE
		b.add_child(v)
		if kind == "themes":
			var sw := HBoxContainer.new()
			for c in [info.accent, info.accent2, info.panel]:
				var r := ColorRect.new()
				r.color = Color(c)
				r.custom_minimum_size = Vector2(46, 30)
				sw.add_child(r)
			v.add_child(sw)
		var title := str(info.get("name", id)) + ("  ✓" if id == current else "")
		v.add_child(UIKit.label(title if ok else "🔒 " + str(info.get("name", id)), 25, Color.WHITE, UIKit.font_bold))
		var desc := str(info.get("desc", "")) if ok else str(info.get("hint", "Пока недоступно."))
		if desc != "":
			var d := wrap_label(desc, 19, UIKit.TEXT_DIM)
			v.add_child(d)
		b.pressed.connect(func():
			cb.call(id)
			for o in buttons:
				o.button_pressed = o == b)
		buttons.append(b)
		grid.add_child(b)
	return sc
