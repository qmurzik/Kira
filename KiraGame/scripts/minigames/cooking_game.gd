extends "res://scripts/minigames/minigame_base.gd"
## «Готовить вместе»: рецепт из последовательных шагов —
## выбрать ингредиенты, размешать, разогреть, поймать момент, налить.

const WRONG := {
	"pepper": "Перец? В десерт? Ты что-то знаешь о кулинарии, чего не знаю я?",
	"pickle": "Огурчик… смело. Очень смело. Нет.",
	"ketchup": "Кетчуп?! Я сделаю вид, что этого не видела.",
	"fish": "Рыбка останется для кота. Если у нас появится кот.",
	"salt_bag": "Соль — это не сахар. Проверено на собственном горьком опыте.",
	"flour": "Мука здесь лишняя — получится клейстер.",
	"cocoa_powder": "Какао в онигири? Звучит как эксперимент века.",
	"marshmallow": "Маршмеллоу потом! Сейчас серьёзная готовка.",
	"berries": "Чернику прибережём для блинчиков.",
	"sugar": "Без сахара обойдёмся.",
	"milk": "Молоко сюда не нужно.",
	"egg": "Яйцо тут не пригодится.",
	"rice": "Рис — это в другой раз.",
	"nori": "Нори пока отложим.",
	"honey_jar": "Мёд не нужен.",
}

var recipe := {}
var ing_names := {}
var steps: Array = []
var step_i := -1
var mistakes := 0
var _work: Control
var _card: VBoxContainer
var _step_labels: Array = []
var _pick_grid: GridContainer
var _picked: Array = []
var _value := 0.0
var _holding := false
var _dir := 1.0
var _angle_acc := 0.0
var _last_angle := 0.0
var _stir_active := false
var _swirl := 0.0
var _action_btn: Button
var _t := 0.0
var _flash := 0.0
var _flash_col := Color.WHITE


func setup() -> void:
	var f := FileAccess.open("res://data/recipes.json", FileAccess.READ)
	var d: Dictionary = JSON.parse_string(f.get_as_text()) if f else {}
	recipe = d.get("recipes", {}).get(str(params.get("recipe", "pancakes")), {})
	ing_names = d.get("ingredients", {})
	steps = recipe.get("steps", [])
	title_text = "Готовим: " + str(recipe.get("name", ""))
	rules_text = str(recipe.get("desc", ""))
	var bg := Control.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bg.draw.connect(func(): _draw_kitchen(bg))
	add_child(bg)
	var card := PanelContainer.new()
	card.position = Vector2(40, 200)
	card.custom_minimum_size = Vector2(420, 0)
	card.add_theme_stylebox_override("panel", UIKit.box(Color("#fff7ec"), 0.95, Color("#c99a7a"), 1.0, 18, 10))
	add_child(card)
	_card = VBoxContainer.new()
	_card.add_theme_constant_override("separation", 10)
	card.add_child(_card)
	_card.add_child(UIKit.label("Рецепт", 30, Color("#7a4a5a"), UIKit.font_script))
	for i in steps.size():
		var l := UIKit.label("%d. %s" % [i + 1, steps[i].text], 21, Color("#6a5060"))
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.custom_minimum_size.x = 380
		_card.add_child(l)
		_step_labels.append(l)
	_work = Control.new()
	_work.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_work.mouse_filter = Control.MOUSE_FILTER_PASS
	_work.draw.connect(_draw_work)
	_work.gui_input.connect(_on_work_input)
	add_child(_work)
	_pick_grid = GridContainer.new()
	_pick_grid.columns = 3
	_pick_grid.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	_pick_grid.offset_left = -330
	_pick_grid.offset_top = -160
	_pick_grid.add_theme_constant_override("h_separation", 18)
	_pick_grid.add_theme_constant_override("v_separation", 18)
	add_child(_pick_grid)
	_action_btn = UIKit.button("", Callable())
	_action_btn.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM)
	_action_btn.offset_left = -200
	_action_btn.offset_right = 200
	_action_btn.offset_top = -170
	_action_btn.offset_bottom = -100
	_action_btn.button_down.connect(func(): _holding = true)
	_action_btn.button_up.connect(_on_release)
	_action_btn.focus_mode = Control.FOCUS_NONE
	add_child(_action_btn)


func start() -> void:
	comment("Фартук надет. Шеф-повар готов!", "happy")
	_next_step()


func _cur() -> Dictionary:
	return steps[step_i] if step_i >= 0 and step_i < steps.size() else {}


func _next_step() -> void:
	step_i += 1
	for i in _step_labels.size():
		_step_labels[i].add_theme_color_override("font_color", Color("#c9b0b8") if i < step_i else (Color("#b03a6a") if i == step_i else Color("#6a5060")))
	for c in _pick_grid.get_children():
		c.queue_free()
	_pick_grid.visible = false
	_action_btn.visible = false
	_value = 0.0
	_holding = false
	_angle_acc = 0.0
	if step_i >= steps.size():
		var score := stars_for(mistakes, 1, 4)
		set_status("Готово! " + "★".repeat(score) + "☆".repeat(3 - score))
		AudioManager.play_sfx("success")
		comment(["Ну… первый блин комом. Зато он наш.", "Вкусно! Правда-правда.", "Идеально! Нам пора открывать кафе."][score - 1], ["shy", "happy", "joy"][score - 1])
		finish({"score": score, "mistakes": mistakes}, 2.8)
		return
	var s := _cur()
	set_status(str(s.text))
	match str(s.type):
		"pick":
			_picked.clear()
			_pick_grid.visible = true
			var pool: Array = s.pool.duplicate()
			pool.shuffle()
			for id in pool:
				var b: Button = UIKit.button(str(ing_names.get(id, id)), Callable(), "ing_" + str(id))
				b.custom_minimum_size = Vector2(210, 110)
				b.vertical_icon_alignment = VERTICAL_ALIGNMENT_TOP
				b.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
				b.add_theme_font_size_override("font_size", 21)
				b.pressed.connect(func(): pick(str(id), b))
				_pick_grid.add_child(b)
		"heat":
			_action_btn.visible = true
			_action_btn.text = "Удерживай: нагреть"
		"pour":
			_action_btn.visible = true
			_action_btn.text = "Удерживай: налить"
		"timing":
			_action_btn.visible = true
			_action_btn.text = "Сейчас!"
			_value = 0.0
		"stir":
			comment("Крути мышкой по кругу внутри миски!", "neutral")


## Выбор ингредиента на шаге «pick».
func pick(id: String, btn: Button = null) -> void:
	var s := _cur()
	if str(s.get("type", "")) != "pick":
		return
	if id in s.need:
		if id in _picked:
			return
		_picked.append(id)
		AudioManager.play_sfx("plop", randf_range(0.9, 1.15))
		if btn:
			btn.disabled = true
		_flash_ok()
		if _picked.size() >= s.need.size():
			_next_step()
	else:
		mistakes += 1
		AudioManager.play_sfx("fail", 1.0, -4.0)
		_flash_bad()
		comment(str(WRONG.get(id, "Хм, это сюда не подходит.")), "pout")
		if btn:
			btn.disabled = true


func _on_release() -> void:
	_holding = false
	var s := _cur()
	match str(s.get("type", "")):
		"heat", "pour":
			var z: Array = s.zone
			if _value >= z[0] and _value <= z[1]:
				AudioManager.play_sfx("sizzle" if s.type == "heat" else "pour_done")
				_flash_ok()
				comment(["Отлично!", "Прямо как надо.", "Мастер!"].pick_random(), "happy")
				_next_step()
			elif _value > z[1]:
				mistakes += 1
				_flash_bad()
				AudioManager.play_sfx("fail", 1.0, -4.0)
				comment("Ой-ой, перебор! Давай ещё раз, аккуратнее.", "surprised")
				_value = 0.25
		"timing":
			press_timing()


## Нажатие на шаге «timing».
func press_timing() -> void:
	var s := _cur()
	if str(s.get("type", "")) != "timing":
		return
	var z: Array = s.zone
	if _value >= z[0] and _value <= z[1]:
		AudioManager.play_sfx("flip")
		_flash_ok()
		comment(["Оп! Красота.", "Вот это реакция!", "Идеально!"].pick_random(), "joy")
		_next_step()
	else:
		mistakes += 1
		_flash_bad()
		AudioManager.play_sfx("fail", 1.0, -4.0)
		comment("Чуть-чуть мимо. Ещё попытка!", "worried")


func _flash_ok() -> void:
	_flash = 0.5
	_flash_col = Color(0.5, 1.0, 0.7)


func _flash_bad() -> void:
	_flash = 0.5
	_flash_col = Color(1.0, 0.45, 0.5)


func _bowl_center() -> Vector2:
	return Vector2(size.x * 0.55, size.y * 0.55)


func _on_work_input(event: InputEvent) -> void:
	if str(_cur().get("type", "")) != "stir":
		return
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		_stir_active = event.pressed and event.position.distance_to(_bowl_center()) < 240
		_last_angle = (event.position - _bowl_center()).angle()
	elif event is InputEventMouseMotion and _stir_active:
		var a: float = (event.position - _bowl_center()).angle()
		var d := wrapf(a - _last_angle, -PI, PI)
		_last_angle = a
		stir(absf(d))


## Размешивание: добавить угол (в радианах).
func stir(radians: float) -> void:
	if str(_cur().get("type", "")) != "stir":
		return
	_angle_acc += radians
	_swirl += radians
	if randf() < 0.05:
		AudioManager.play_sfx("stir", randf_range(0.9, 1.1), -6.0)
	var need: float = float(_cur().amount) * TAU
	set_status("%s — %d%%" % [_cur().text, int(minf(_angle_acc / need, 1.0) * 100)])
	if _angle_acc >= need:
		_stir_active = false
		_flash_ok()
		comment("Ни одного комочка!", "happy")
		_next_step()


## Для автотестов: пройти рецепт без ошибок.
func solve_all() -> void:
	var guard := 0
	while step_i < steps.size() and guard < 50:
		guard += 1
		var s := _cur()
		match str(s.type):
			"pick":
				for id in s.need:
					pick(str(id))
			"stir":
				stir(float(s.amount) * TAU + 0.1)
			"heat", "pour":
				_value = (s.zone[0] + s.zone[1]) / 2.0
				_on_release()
			"timing":
				_value = (s.zone[0] + s.zone[1]) / 2.0
				press_timing()


func _process(delta: float) -> void:
	_t += delta
	_flash = maxf(_flash - delta, 0.0)
	var s := _cur()
	match str(s.get("type", "")):
		"heat":
			_value = clampf(_value + (0.32 if _holding else -0.08) * delta, 0.0, 1.05)
			if _value >= 1.05:
				_holding = false
				mistakes += 1
				_flash_bad()
				comment("Горит! Горит! Снимай!", "surprised")
				_value = 0.3
		"pour":
			if _holding:
				_value = clampf(_value + 0.3 * delta, 0.0, 1.0)
				if _value >= 1.0:
					_holding = false
					mistakes += 1
					_flash_bad()
					comment("Ой, через край! Вытираю…", "pout")
					_value = 0.2
		"timing":
			_value += _dir * delta * float(s.get("speed", 1.0)) * 0.9
			if _value >= 1.0:
				_value = 1.0
				_dir = -1.0
			elif _value <= 0.0:
				_value = 0.0
				_dir = 1.0
	_work.queue_redraw()


func _unhandled_input(event: InputEvent) -> void:
	super(event)
	if event.is_action_pressed("advance") and str(_cur().get("type", "")) == "timing":
		press_timing()


func _draw_kitchen(c: Control) -> void:
	var s := c.size
	c.draw_rect(Rect2(Vector2.ZERO, s), Color("#3a2a4e"))
	for x in range(0, int(s.x), 80):
		for y in range(0, int(s.y * 0.62), 80):
			c.draw_rect(Rect2(x + 3, y + 3, 74, 74), Color("#46335e") if (x / 80 + y / 80) % 2 == 0 else Color("#4d3866"))
	c.draw_rect(Rect2(0, s.y * 0.62, s.x, s.y * 0.38), Color("#6a4a5e"))
	c.draw_rect(Rect2(0, s.y * 0.62, s.x, 18), Color("#f3e3d3"))
	for i in 3:
		var lx := s.x * (0.3 + i * 0.25)
		c.draw_circle(Vector2(lx, 40), 140, Color(1.0, 0.85, 0.6, 0.05))


func _draw_work() -> void:
	var s := _cur()
	var c := _bowl_center()
	var t := str(s.get("type", ""))
	if _flash > 0.0:
		_work.draw_circle(c, 300, Color(_flash_col, _flash * 0.25))
	if t == "stir" or t == "pick":
		_work.draw_circle(c + Vector2(0, 20), 230, Color(0, 0, 0, 0.2))
		_work.draw_circle(c, 220, Color("#f4ece4"))
		_work.draw_circle(c, 190, Color("#e8d4b8") if t == "stir" else Color("#f8f2ea"))
		if t == "stir":
			for i in 5:
				var a := _swirl * 0.5 + i * TAU / 5.0
				_work.draw_arc(c, 60 + i * 24, a, a + 2.2, 24, Color("#d2b48c"), 6, true)
			_work.draw_line(c + Vector2.from_angle(_swirl * 0.5) * 30, c + Vector2.from_angle(_swirl * 0.5) * 170 + Vector2(0, -160), Color("#c9a06a"), 14, true)
	elif t == "heat":
		_work.draw_circle(c + Vector2(0, 20), 200, Color(0, 0, 0, 0.25))
		_work.draw_circle(c, 190, Color("#3a3446"))
		_work.draw_circle(c, 160, Color("#4a4458").lerp(Color("#c8604a"), clampf(_value, 0, 1)))
		_work.draw_line(c + Vector2(180, 0), c + Vector2(420, 40), Color("#2a2436"), 30, true)
		_meter(s.zone, Color("#ff9a6a"))
	elif t == "pour":
		var r := Rect2(c - Vector2(110, 150), Vector2(220, 300))
		_work.draw_rect(r.grow(14), Color("#f4ece4"))
		_work.draw_rect(r, Color("#3a2f44"))
		var h := r.size.y * _value
		_work.draw_rect(Rect2(r.position.x, r.end.y - h, r.size.x, h), Color("#c08a5a") if str(params.get("recipe", "")) != "onigiri" else Color("#f0e6c8"))
		var z: Array = s.zone
		for zz in z:
			var y: float = r.end.y - r.size.y * float(zz)
			_work.draw_line(Vector2(r.position.x - 30, y), Vector2(r.end.x + 30, y), Color(0.5, 1.0, 0.6, 0.9), 3)
	elif t == "timing":
		_work.draw_circle(c + Vector2(0, 20), 200, Color(0, 0, 0, 0.25))
		_work.draw_circle(c, 190, Color("#3a3446"))
		var lift := sin(_value * PI) * 40.0
		_work.draw_circle(c + Vector2(0, -lift), 140, Color("#f2c27a") if str(params.get("recipe", "")) != "onigiri" else Color("#fbf7ee"))
		_meter(s.zone, Color("#9fe0ff"))


func _meter(zone: Array, col: Color) -> void:
	var r := Rect2(size.x * 0.55 - 400, size.y * 0.55 + 240, 800, 34)
	_work.draw_rect(r, Color(0, 0, 0, 0.45))
	_work.draw_rect(Rect2(r.position.x + r.size.x * float(zone[0]), r.position.y, r.size.x * (float(zone[1]) - float(zone[0])), r.size.y), Color(0.4, 0.9, 0.55, 0.85))
	var x := r.position.x + r.size.x * clampf(_value, 0, 1)
	_work.draw_rect(Rect2(x - 6, r.position.y - 12, 12, r.size.y + 24), col)
