extends "res://scripts/ui/modal.gd"
## Галерея из главного меню: воспоминания, открытые во всех прохождениях, и увиденные концовки.

const ENDINGS := {
	"our_world": ["Наш маленький мир", "Тёплая романтическая концовка."],
	"new_dawn": ["Новый рассвет", "Мир меняется и открывает новую жизнь."],
	"constellation": ["Созвездие воспоминаний", "Светлая, немного грустная концовка."],
}


func _init() -> void:
	panel_size = Vector2(1500, 880)


func build() -> void:
	set_title("Галерея")
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(sc)
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_theme_constant_override("separation", 16)
	sc.add_child(v)
	v.add_child(UIKit.label("Концовки", 30, Color.WHITE, UIKit.font_title))
	var eh := HBoxContainer.new()
	eh.add_theme_constant_override("separation", 16)
	v.add_child(eh)
	for id in ENDINGS.keys():
		var seen: bool = id in SaveManager.global.endings
		var c := card(120)
		c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var vv := VBoxContainer.new()
		c.add_child(vv)
		vv.add_child(UIKit.label(ENDINGS[id][0] if seen else "???", 26, UIKit.accent.lightened(0.3) if seen else UIKit.TEXT_DIM, UIKit.font_bold))
		vv.add_child(wrap_label(ENDINGS[id][1] if seen else "Ещё не открыта.", 20, UIKit.TEXT_DIM))
		eh.add_child(c)
	v.add_child(UIKit.label("Воспоминания", 30, Color.WHITE, UIKit.font_title))
	var grid := GridContainer.new()
	grid.columns = 4
	grid.add_theme_constant_override("h_separation", 14)
	grid.add_theme_constant_override("v_separation", 14)
	v.add_child(grid)
	var all: Dictionary = InventoryManager.db.get("memories", {})
	for id in all.keys():
		var ok: bool = id in SaveManager.global.gallery
		var vv := VBoxContainer.new()
		var img := TextureRect.new()
		img.custom_minimum_size = Vector2(320, 180)
		img.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		img.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		var p := "res://assets/memories/%s.svg" % all[id].image
		if ok and ResourceLoader.exists(p):
			img.texture = load(p)
		else:
			img.texture = UIKit.icon("locked")
			img.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
			img.modulate.a = 0.3
		vv.add_child(img)
		var l := UIKit.label(all[id].title if ok else "???", 21)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		vv.add_child(l)
		grid.add_child(vv)
