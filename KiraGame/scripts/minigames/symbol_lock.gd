extends "res://scripts/minigames/minigame_base.gd"
## Замок архива: четыре кольца с символами. Подсказка спрятана в записках библиотеки.

const SYMBOLS := ["moon", "star", "cat", "key", "heart", "note"]
const NAMES := {"moon": "Луна", "star": "Звезда", "cat": "Кошка", "key": "Ключ", "heart": "Сердце", "note": "Нота"}

var solution: Array = ["moon", "cat", "key", "star"]
var rings: Array = [0, 0, 0, 0]
var attempts := 0
var _icons: Array = []
var _labels: Array = []
var _hints_given := 0


func setup() -> void:
	title_text = "Замок архива"
	rules_text = "«Луна смотрит на Кошку, Кошка стережёт Ключ, а Ключ ведёт к Звезде». Поверни кольца и нажми «Открыть»."
	solution = params.get("solution", solution)
	var bg := ColorRect.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.color = Color("#120e2a")
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)
	var dust := preload("res://scripts/fx/dust.gd").new()
	dust.area = Rect2(0, 0, 2400, 1400)
	dust.count = 40
	dust.color = Color(0.8, 0.7, 1.0, 0.5)
	dust.glow = true
	add_child(dust)
	var box := HBoxContainer.new()
	box.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	box.offset_left = -560
	box.offset_right = 560
	box.offset_top = -170
	box.offset_bottom = 170
	box.alignment = BoxContainer.ALIGNMENT_CENTER
	box.add_theme_constant_override("separation", 30)
	add_child(box)
	for i in 4:
		var v := VBoxContainer.new()
		v.alignment = BoxContainer.ALIGNMENT_CENTER
		v.add_theme_constant_override("separation", 10)
		box.add_child(v)
		var up := UIKit.button("▲", func(): turn(i, -1))
		up.custom_minimum_size = Vector2(200, 52)
		up.focus_mode = Control.FOCUS_NONE
		v.add_child(up)
		var p := PanelContainer.new()
		p.add_theme_stylebox_override("panel", UIKit.box(Color("#2a1f50"), 1.0, UIKit.accent, 0.9, 100, 20))
		p.custom_minimum_size = Vector2(200, 200)
		v.add_child(p)
		var ic := TextureRect.new()
		ic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		ic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		ic.custom_minimum_size = Vector2(150, 150)
		p.add_child(ic)
		_icons.append(ic)
		var l := UIKit.label("", 24, UIKit.TEXT_DIM)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(l)
		_labels.append(l)
		var down := UIKit.button("▼", func(): turn(i, 1))
		down.custom_minimum_size = Vector2(200, 52)
		down.focus_mode = Control.FOCUS_NONE
		v.add_child(down)
	var open := UIKit.button("Открыть", check, "key")
	open.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM)
	open.offset_left = -170
	open.offset_right = 170
	open.offset_top = -200
	open.offset_bottom = -126
	add_child(open)
	_refresh()


func start() -> void:
	comment("Записки говорили что-то про луну и кошку… Помнишь?", "thoughtful")


func turn(i: int, d: int) -> void:
	rings[i] = posmod(rings[i] + d, SYMBOLS.size())
	AudioManager.play_sfx("tick", 0.9 + i * 0.05)
	_refresh()


func _refresh() -> void:
	for i in 4:
		var s: String = SYMBOLS[rings[i]]
		_icons[i].texture = UIKit.icon("sym_" + s)
		_labels[i].text = NAMES[s]


func check() -> void:
	if _done:
		return
	attempts += 1
	var right := 0
	for i in 4:
		if SYMBOLS[rings[i]] == solution[i]:
			right += 1
	if right == 4:
		AudioManager.play_sfx("unlock")
		set_status("Замок щёлкнул!")
		comment("Получилось! Ты гений. Ну, или я хорошо подсказывала.", "joy")
		finish({"score": 3 if attempts <= 2 else (2 if attempts <= 5 else 1), "mistakes": attempts - 1}, 1.8)
		return
	AudioManager.play_sfx("fail")
	set_status("Кольца на своих местах: %d из 4" % right)
	if attempts % 3 == 0 and _hints_given < 4:
		var idx := _hints_given
		_hints_given += 1
		comment("Подсказка: %s-й символ — %s." % [["первый", "второй", "третий", "четвёртый"][idx], NAMES[solution[idx]]], "sly")
	else:
		comment(["Не то… Но мы близко!", "Хм. Замок упрямится.", "Ещё попытка!"].pick_random(), "worried")


## Для автотестов.
func solve_all() -> void:
	for i in 4:
		rings[i] = SYMBOLS.find(solution[i])
	check()
