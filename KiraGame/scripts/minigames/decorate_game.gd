extends "res://scripts/minigames/minigame_base.gd"
## «Украшать комнату»: выбери предмет на полке снизу и нажми на подсвеченное место в комнате.
## Нажатие на занятое место без выбранного предмета убирает декор обратно.

const ITEM_LINES := {
	"cat_plush": "Пончик одобряет. Я точно видела, как он подмигнул.",
	"garland": "Гирлянда! Теперь вечером будет совсем волшебно.",
	"moon_lamp": "Своя луна. Ну всё, теперь мне не страшно засыпать.",
	"poster_whale": "Кит смотрит так, будто знает что-то важное.",
	"cactus": "Колючка выглядит гордо. Ему идёт.",
	"dreamcatcher": "Пусть ловит только плохие сны. Хорошие — нам.",
	"star_jar": "Звёзды в банке… Они немного щекочутся светом.",
	"crystal": "Он тихо шелестит. Как страницы.",
	"kite": "Змей долетел. Пусть висит на почётном месте.",
	"lantern": "Фонарь Проводника. Светит даже там, где ничего нет.",
}

var selected := ""
var _tray: HBoxContainer
var _slots := {}
var _overlay: Control
var _t := 0.0
var _hover_slot := ""
var _item_buttons := {}


func setup() -> void:
	show_kira = false
	title_text = "Украшаем комнату"
	rules_text = "Выбери предмет на полке, затем нажми на светящееся место. Нажми на занятое место, чтобы убрать предмет."
	_overlay = Control.new()
	_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_overlay.mouse_filter = Control.MOUSE_FILTER_STOP
	_overlay.draw.connect(_draw_slots)
	_overlay.gui_input.connect(_on_input)
	add_child(_overlay)
	var tray_panel := PanelContainer.new()
	tray_panel.anchor_left = 0.5
	tray_panel.anchor_right = 0.5
	tray_panel.anchor_top = 1.0
	tray_panel.anchor_bottom = 1.0
	tray_panel.offset_left = -820
	tray_panel.offset_right = 820
	tray_panel.offset_top = -190
	tray_panel.offset_bottom = -20
	tray_panel.add_theme_stylebox_override("panel", UIKit.box(UIKit.panel, 0.92, UIKit.accent, 0.7, 24, 18))
	add_child(tray_panel)
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 14)
	tray_panel.add_child(h)
	var sc := ScrollContainer.new()
	sc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	h.add_child(sc)
	_tray = HBoxContainer.new()
	_tray.add_theme_constant_override("separation", 12)
	sc.add_child(_tray)
	var done := UIKit.button("Готово", func(): _finish())
	done.custom_minimum_size = Vector2(200, 90)
	done.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	h.add_child(done)
	_build_tray()


func start() -> void:
	_bg.color.a = 0.12
	AudioManager.play_music("room")
	comment("Куда поставим? Я доверяю твоему вкусу. Почти полностью.")
	_update_status()


func comment(text: String, _emotion := "") -> void:
	var vs := get_viewport_rect().size
	_bubble.say(text, Vector2(vs.x * 0.5, vs.y - 210), 3.0)


func _build_tray() -> void:
	for c in _tray.get_children():
		c.queue_free()
	_item_buttons.clear()
	for id in InventoryManager.decor_owned:
		var b := Button.new()
		b.custom_minimum_size = Vector2(130, 130)
		b.toggle_mode = true
		b.icon = load("res://assets/decor/%s.svg" % id) if ResourceLoader.exists("res://assets/decor/%s.svg" % id) else null
		b.expand_icon = true
		b.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
		b.tooltip_text = str(InventoryManager.info("decor", id).get("name", id)) + "\n" + str(InventoryManager.info("decor", id).get("desc", ""))
		b.focus_mode = Control.FOCUS_NONE
		var placed := false
		for s in InventoryManager.placements.keys():
			if InventoryManager.placements[s] == id:
				placed = true
		b.modulate = Color(1, 1, 1, 0.5) if placed else Color.WHITE
		b.pressed.connect(func(): select(id))
		_tray.add_child(b)
		_item_buttons[id] = b


func select(id: String) -> void:
	selected = "" if selected == id else id
	for k in _item_buttons.keys():
		_item_buttons[k].button_pressed = k == selected
	if selected != "":
		AudioManager.play_sfx("tick")
		var types: Array = InventoryManager.info("decor", selected).get("types", [])
		set_status("%s — подходящие места подсвечены" % InventoryManager.info("decor", selected).get("name", ""))
		if types.is_empty():
			pass
	else:
		_update_status()


func _update_status() -> void:
	set_status("Расставлено предметов: %d" % InventoryManager.placed_count())


func _slot_screen(slot: String) -> Vector2:
	var vs := get_viewport_rect().size
	var s: float = maxf(vs.x / 1920.0, vs.y / 1080.0)
	var off := (vs - Vector2(1920, 1080) * s) / 2.0
	var p: Vector2 = InventoryManager.DECOR_SLOTS[slot].pos
	var t: String = InventoryManager.DECOR_SLOTS[slot].type
	if t in ["surface", "floor", "bed"]:
		p.y -= 40
	elif t == "ceiling":
		p.y += 40
	return off + p * s


func _slot_at(pos: Vector2) -> String:
	for slot in InventoryManager.DECOR_SLOTS.keys():
		if _slot_screen(slot).distance_to(pos) < 60:
			return slot
	return ""


func _on_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion:
		_hover_slot = _slot_at(event.position)
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		var slot := _slot_at(event.position)
		if slot != "":
			click_slot(slot)


## Клик по месту для декора.
func click_slot(slot: String) -> void:
	if selected == "":
		if InventoryManager.placements.has(slot):
			InventoryManager.place(slot, "")
			AudioManager.play_sfx("swap")
			_build_tray()
			_update_status()
		else:
			comment("Сначала выбери что-нибудь на полке внизу.")
		return
	if not InventoryManager.can_place(slot, selected):
		comment(["Хм, сюда это не встанет.", "Это место для чего-то другого.", "Попробуй другое место — то, что светится."].pick_random())
		AudioManager.play_sfx("fail", 1.0, -6.0)
		return
	InventoryManager.place(slot, selected)
	AudioManager.play_sfx("place")
	comment(str(ITEM_LINES.get(selected, "Мило!")))
	selected = ""
	_build_tray()
	_update_status()


func _finish() -> void:
	AudioManager.play_sfx("success")
	finish({"score": clampi(InventoryManager.placed_count(), 1, 3), "placed": InventoryManager.placed_count()}, 0.3)


func _process(delta: float) -> void:
	_t += delta
	_overlay.queue_redraw()


func _draw_slots() -> void:
	var pulse := 0.5 + 0.5 * sin(_t * 4.0)
	for slot in InventoryManager.DECOR_SLOTS.keys():
		var p := _slot_screen(slot)
		var occupied := InventoryManager.placements.has(slot)
		var fits := selected != "" and InventoryManager.can_place(slot, selected)
		var col := UIKit.accent2 if fits else (Color(0.6, 1.0, 0.75) if occupied else UIKit.accent)
		var a := 0.9 if fits else 0.45
		_overlay.draw_circle(p, 34 + 6 * pulse * (1.0 if fits else 0.3), Color(col, 0.18))
		_overlay.draw_arc(p, 34, 0, TAU, 40, Color(col, a), 3.0, true)
		_overlay.draw_string(UIKit.font_bold, p + Vector2(-12, 11), "✓" if occupied else "+", HORIZONTAL_ALIGNMENT_LEFT, -1, 30, Color(1, 1, 1, a))
		if slot == _hover_slot:
			_overlay.draw_string(UIKit.font_bold, p + Vector2(-150, 70), InventoryManager.DECOR_SLOTS[slot].name, HORIZONTAL_ALIGNMENT_CENTER, 300, 24, Color.WHITE)


## Для автотестов.
func solve_all() -> void:
	for id in ["cat_plush", "moon_lamp", "poster_whale"]:
		if id in InventoryManager.decor_owned:
			for slot in InventoryManager.DECOR_SLOTS.keys():
				if not InventoryManager.placements.has(slot) and InventoryManager.can_place(slot, id):
					selected = id
					click_slot(slot)
					break
	_finish()
