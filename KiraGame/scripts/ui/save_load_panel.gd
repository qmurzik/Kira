extends "res://scripts/ui/modal.gd"
## Сохранение и загрузка: автосохранение, быстрый слот и шесть ручных слотов с миниатюрами.

signal loaded(slot: String)

var mode := "save"
var thumbnail: Image
var _grid: GridContainer


func _init() -> void:
	panel_size = Vector2(1500, 900)


func build() -> void:
	set_title("Сохранить игру" if mode == "save" else "Загрузить игру")
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(sc)
	_grid = GridContainer.new()
	_grid.columns = 2
	_grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_grid.add_theme_constant_override("h_separation", 18)
	_grid.add_theme_constant_override("v_separation", 18)
	sc.add_child(_grid)
	_fill()


func _fill() -> void:
	for c in _grid.get_children():
		c.queue_free()
	var slots: Array = SaveManager.MANUAL_SLOTS.duplicate()
	if mode == "load":
		slots = ["auto", "quick"] + slots
	for s in slots:
		_grid.add_child(_slot_card(s))


func _slot_card(slot: String) -> Control:
	var c := card(190)
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 18)
	c.add_child(h)
	var thumb := TextureRect.new()
	thumb.custom_minimum_size = Vector2(256, 144)
	thumb.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	thumb.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	thumb.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	h.add_child(thumb)
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	h.add_child(v)
	var name = {"auto": "Автосохранение", "quick": "Быстрое сохранение"}.get(slot, "Слот " + slot)
	v.add_child(UIKit.label(name, 26, UIKit.accent.lightened(0.3), UIKit.font_title))
	var info := SaveManager.slot_info(slot)
	var exists := not info.is_empty()
	if info.get("corrupted", false):
		v.add_child(wrap_label("Файл повреждён и не может быть загружен.", 21, Color("#ff9aa8")))
	elif exists:
		if FileAccess.file_exists(SaveManager.thumb_path(slot)):
			var img := Image.load_from_file(ProjectSettings.globalize_path(SaveManager.thumb_path(slot)))
			if img:
				thumb.texture = ImageTexture.create_from_image(img)
		var title := "Свободный режим" if info.get("free_mode", false) else "Глава %d · %s" % [int(info.get("chapter", 1)), info.get("chapter_title", "")]
		v.add_child(wrap_label(title, 22))
		v.add_child(wrap_label("%s · %s, день %d" % [info.get("location", ""), info.get("phase", ""), int(info.get("day", 1))], 20, UIKit.TEXT_DIM))
		var pt := int(info.get("playtime", 0))
		v.add_child(wrap_label("%s · в игре %d ч %02d мин" % [str(info.get("date", "")).replace("T", " "), pt / 3600, (pt / 60) % 60], 19, UIKit.TEXT_DIM))
	else:
		v.add_child(wrap_label("Пусто", 22, UIKit.TEXT_DIM))
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	v.add_child(row)
	if mode == "save":
		var b := UIKit.button("Сохранить", func(): _save(slot, exists))
		b.add_theme_font_size_override("font_size", 21)
		row.add_child(b)
	else:
		var b2 := UIKit.button("Загрузить", func(): _load(slot))
		b2.add_theme_font_size_override("font_size", 21)
		b2.disabled = not exists or info.get("corrupted", false)
		row.add_child(b2)
	if exists and slot != "auto":
		var d := UIKit.button("Удалить", func(): _delete(slot))
		d.add_theme_font_size_override("font_size", 21)
		row.add_child(d)
	return c


func _ask(q: String) -> bool:
	var d = preload("res://scripts/ui/confirm_dialog.gd").new()
	d.question = q
	add_child(d)
	return await d.answered


func _save(slot: String, exists: bool) -> void:
	if exists and not await _ask("Перезаписать слот %s?" % slot):
		return
	if SaveManager.save_game(slot, thumbnail):
		AudioManager.play_sfx("save")
	_fill()


func _load(slot: String) -> void:
	loaded.emit(slot)


func _delete(slot: String) -> void:
	if await _ask("Удалить сохранение? Это нельзя отменить."):
		SaveManager.delete_slot(slot)
		_fill()
