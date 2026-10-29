extends "res://scripts/ui/modal.gd"
## Дневник: отношения (без «счётчика любви»), решения, которые помнит Кира,
## коллекция воспоминаний, сюжетные предметы.

var start_tab := 0


func _init() -> void:
	panel_size = Vector2(1500, 900)


func build() -> void:
	set_title("Дневник")
	var tabs := TabContainer.new()
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	tabs.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	body.add_child(tabs)
	tabs.add_child(_relations())
	tabs.add_child(_memories())
	tabs.add_child(_items())
	for i in tabs.get_tab_count():
		tabs.set_tab_title(i, ["Мы с Кирой", "Воспоминания", "Вещи"][i])
	tabs.current_tab = start_tab


func _scroll(name: String) -> VBoxContainer:
	var sc := ScrollContainer.new()
	sc.name = name
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_theme_constant_override("separation", 14)
	sc.add_child(v)
	return v


func _relations() -> Control:
	var v := _scroll("rel")
	var head := card()
	var hv := VBoxContainer.new()
	head.add_child(hv)
	hv.add_child(UIKit.label("Кем ты стал для Киры", 22, UIKit.TEXT_DIM))
	hv.add_child(UIKit.label(RelationshipManager.stage_name(), 34, Color.WHITE, UIKit.font_title))
	v.add_child(head)
	for s in RelationshipManager.STATS.keys():
		var c := card()
		var h := HBoxContainer.new()
		h.add_theme_constant_override("separation", 20)
		c.add_child(h)
		var left := VBoxContainer.new()
		left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		h.add_child(left)
		left.add_child(UIKit.label(RelationshipManager.STATS[s].name, 27, UIKit.accent.lightened(0.3), UIKit.font_bold))
		left.add_child(wrap_label(RelationshipManager.STATS[s].desc, 20, UIKit.TEXT_DIM))
		var right := VBoxContainer.new()
		right.custom_minimum_size.x = 420
		h.add_child(right)
		right.add_child(UIKit.label(RelationshipManager.level_text(s), 24))
		var stars := HBoxContainer.new()
		var val := RelationshipManager.get_stat(s)
		for i in 5:
			var st := TextureRect.new()
			st.texture = UIKit.icon("star")
			st.custom_minimum_size = Vector2(30, 30)
			st.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
			st.modulate = Color(1, 1, 1, 1) if val >= (i + 1) * 2 else Color(1, 1, 1, 0.2)
			stars.add_child(st)
		right.add_child(stars)
		v.add_child(c)
	v.add_child(UIKit.label("Кира помнит:", 28, Color.WHITE, UIKit.font_title))
	if RelationshipManager.memories.is_empty():
		v.add_child(wrap_label("Пока ничего особенного. Но вы ведь только начали.", 22, UIKit.TEXT_DIM))
	for k in RelationshipManager.memories.keys():
		var m: Dictionary = RelationshipManager.memories[k]
		v.add_child(wrap_label("♥  " + str(m.text), 23))
	return v.get_parent()


func _memories() -> Control:
	var v := _scroll("mem")
	var grid := GridContainer.new()
	grid.columns = 3
	grid.add_theme_constant_override("h_separation", 16)
	grid.add_theme_constant_override("v_separation", 16)
	v.add_child(grid)
	var all: Dictionary = InventoryManager.db.get("memories", {})
	for id in all.keys():
		var unlocked := InventoryManager.has_memory(id)
		var b: Button = UIKit.button("")
		b.custom_minimum_size = Vector2(440, 300)
		b.hover_scale = 1.02
		var vv := VBoxContainer.new()
		vv.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		vv.offset_left = 14
		vv.offset_right = -14
		vv.offset_top = 14
		vv.offset_bottom = -10
		vv.mouse_filter = Control.MOUSE_FILTER_IGNORE
		b.add_child(vv)
		var img := TextureRect.new()
		img.custom_minimum_size = Vector2(400, 225)
		img.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		img.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		var path := "res://assets/memories/%s.svg" % all[id].image
		if unlocked and ResourceLoader.exists(path):
			img.texture = load(path)
		else:
			img.texture = UIKit.icon("locked")
			img.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
			img.modulate = Color(1, 1, 1, 0.35)
		vv.add_child(img)
		var t := UIKit.label(all[id].title if unlocked else "???", 23, Color.WHITE, UIKit.font_bold)
		t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		vv.add_child(t)
		b.disabled = not unlocked
		b.pressed.connect(func(): _view_memory(id))
		grid.add_child(b)
	return v.get_parent()


func _view_memory(id: String) -> void:
	var m := preload("res://scripts/ui/memory_viewer.gd").new()
	m.memory_id = id
	add_child(m)


func _items() -> Control:
	var v := _scroll("items")
	if InventoryManager.key_items.is_empty():
		v.add_child(wrap_label("Карманы пока пусты.", 22, UIKit.TEXT_DIM))
	for id in InventoryManager.key_items.keys():
		var info := InventoryManager.info("key_items", id)
		var c := card()
		var vv := VBoxContainer.new()
		c.add_child(vv)
		var n := int(InventoryManager.key_items[id])
		vv.add_child(UIKit.label(str(info.get("name", id)) + ("  ×%d" % n if n > 1 else ""), 26, Color.WHITE, UIKit.font_bold))
		vv.add_child(wrap_label(str(info.get("desc", "")), 21, UIKit.TEXT_DIM))
		v.add_child(c)
	return v.get_parent()
