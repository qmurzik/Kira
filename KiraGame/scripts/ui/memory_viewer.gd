extends "res://scripts/ui/modal.gd"
## Просмотр одного воспоминания: иллюстрация и текст.

var memory_id := ""


func _init() -> void:
	panel_size = Vector2(1100, 860)


func build() -> void:
	var m: Dictionary = InventoryManager.info("memories", memory_id)
	set_title(str(m.get("title", "")))
	var img := TextureRect.new()
	img.custom_minimum_size = Vector2(1000, 560)
	img.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	img.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	var p := "res://assets/memories/%s.svg" % m.get("image", "")
	if ResourceLoader.exists(p):
		img.texture = load(p)
	body.add_child(img)
	var l := wrap_label(str(m.get("text", "")), 27)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	body.add_child(l)
