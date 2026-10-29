extends "res://scripts/minigames/minigame_base.gd"
## «Собирать воспоминания»: картинка разбита на фрагменты. Нажми на два фрагмента, чтобы поменять их местами.

var memory_id := ""
var n := 3
var order: Array = []        ## order[позиция] = номер фрагмента
var moves := 0
var _tex: Texture2D
var _grid: GridContainer
var _tiles: Array = []
var _sel := -1
var _frame: PanelContainer
var _full: TextureRect


func setup() -> void:
	memory_id = str(params.get("memory", "first_meeting"))
	n = clampi(int(params.get("grid", 3)), 2, 5)
	var info := InventoryManager.info("memories", memory_id)
	title_text = "Воспоминание: " + str(info.get("title", "???"))
	rules_text = "Нажми на два фрагмента, чтобы поменять их местами. Верные фрагменты засветятся."
	var p := "res://assets/memories/%s.svg" % info.get("image", memory_id)
	_tex = load(p) if ResourceLoader.exists(p) else load("res://assets/memories/first_meeting.svg")
	var bg := ColorRect.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.color = Color("#151030")
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)
	var dust := preload("res://scripts/fx/dust.gd").new()
	dust.area = Rect2(0, 0, 2400, 1400)
	dust.count = 50
	dust.color = Color(0.85, 0.75, 1.0, 0.6)
	dust.glow = true
	add_child(dust)
	_frame = PanelContainer.new()
	_frame.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	var w := 1120.0
	var h := 630.0
	_frame.offset_left = -w / 2 - 14
	_frame.offset_right = w / 2 + 14
	_frame.offset_top = -h / 2 + 40
	_frame.offset_bottom = h / 2 + 68
	var sb := UIKit.box(Color("#fff7ec"), 1.0, UIKit.accent, 1.0, 10, 30)
	sb.set_content_margin_all(12)
	_frame.add_theme_stylebox_override("panel", sb)
	add_child(_frame)
	_grid = GridContainer.new()
	_grid.columns = n
	_grid.add_theme_constant_override("h_separation", 4)
	_grid.add_theme_constant_override("v_separation", 4)
	_frame.add_child(_grid)
	_full = TextureRect.new()
	_full.texture = _tex
	_full.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_full.stretch_mode = TextureRect.STRETCH_SCALE
	_full.modulate.a = 0.0
	_full.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var tile_size := Vector2((w - 4 * (n - 1)) / n, (h - 4 * (n - 1)) / n)
	var tw := _tex.get_width() / float(n)
	var th := _tex.get_height() / float(n)
	for i in n * n:
		var b := TextureButton.new()
		var at := AtlasTexture.new()
		at.atlas = _tex
		at.region = Rect2((i % n) * tw, (i / n) * th, tw, th)
		b.texture_normal = at
		b.ignore_texture_size = true
		b.stretch_mode = TextureButton.STRETCH_SCALE
		b.custom_minimum_size = tile_size
		b.pressed.connect(_on_tile.bind(i))
		b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		_tiles.append(b)
		_grid.add_child(b)
	order = range(n * n)
	while _is_solved():
		order.shuffle()
	_apply()


func start() -> void:
	comment("Оно ещё тёплое… Давай соберём его вместе.", "thoughtful")


func _apply() -> void:
	var tw := _tex.get_width() / float(n)
	var th := _tex.get_height() / float(n)
	for pos in n * n:
		var piece: int = order[pos]
		(_tiles[pos].texture_normal as AtlasTexture).region = Rect2((piece % n) * tw, (piece / n) * th, tw, th)
		var ok := piece == pos
		_tiles[pos].modulate = Color(1.08, 1.04, 1.12) if ok else Color(0.82, 0.8, 0.9)
		if pos == _sel:
			_tiles[pos].modulate = Color(1.4, 1.2, 1.6)
	var correct := 0
	for pos in n * n:
		if order[pos] == pos:
			correct += 1
	set_status("На месте: %d из %d · ходов: %d" % [correct, n * n, moves])


func _on_tile(pos: int) -> void:
	if _done:
		return
	if _sel < 0:
		_sel = pos
		AudioManager.play_sfx("tick")
		_apply()
		return
	if _sel != pos:
		swap(_sel, pos)
	_sel = -1
	_apply()


## Поменять местами фрагменты на двух позициях.
func swap(a: int, b: int) -> void:
	var t: int = order[a]
	order[a] = order[b]
	order[b] = t
	moves += 1
	AudioManager.play_sfx("swap", randf_range(0.95, 1.1))
	if order[a] == a or order[b] == b:
		AudioManager.play_sfx("twinkle", 1.2, -6.0)
	if moves == 6:
		comment("Кажется, я начинаю вспоминать…", "thoughtful")
	_apply()
	if _is_solved():
		_win()


func _is_solved() -> bool:
	for i in order.size():
		if order[i] != i:
			return false
	return true


func _win() -> void:
	_done = false
	_grid.add_theme_constant_override("h_separation", 0)
	_grid.add_theme_constant_override("v_separation", 0)
	for t in _tiles:
		t.modulate = Color.WHITE
	AudioManager.play_sfx("success")
	var info := InventoryManager.info("memories", memory_id)
	set_status("«%s»" % info.get("title", ""))
	_rules.text = str(info.get("text", ""))
	comment("Вот оно… Я помню.", "warm")
	var score := 3 if moves <= n * n else (2 if moves <= n * n * 2 else 1)
	finish({"score": score, "mistakes": maxi(moves - n * n, 0), "memory": memory_id}, 4.0)


## Для автотестов.
func solve_all() -> void:
	for i in order.size():
		if order[i] != i:
			var j := order.find(i)
			swap(i, j)
