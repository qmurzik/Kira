extends Control
## Главное меню: ночное небо с луной, параллакс-город, Кира, анимированный заголовок.

const KIRA := preload("res://scripts/characters/kira.gd")
const STARS := preload("res://scripts/fx/starfield.gd")
const DUST := preload("res://scripts/fx/dust.gd")
const LINES := [
	"Ты вернулся! Ну… то есть, привет. Я совсем не ждала. Почти.",
	"Нажми «Новая игра», если хочешь начать всё сначала. Я сделаю вид, что мы незнакомы.",
	"Сегодня луна какая-то особенно круглая. Тебе не кажется?",
	"Эй, не тыкай так настойчиво. Щекотно же!",
	"Я тут подумала… а какого цвета небо у тебя за окном?",
	"Если что, настройки — вон там. Я не обижусь, если сделаешь музыку потише.",
]

var _world: Node2D
var _layers: Array = []
var _kira: Node2D
var _title: Label
var _subtitle: Label
var _bubble: PanelContainer
var _buttons: VBoxContainer
var _t := 0.0
var _glitch_t := 3.0
var _line_i := 0


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	get_tree().paused = false
	GameManager.in_game = false
	_world = Node2D.new()
	add_child(_world)
	_add_layer("res://assets/bg/menu/sky.svg", 0.05)
	var stars := STARS.new()
	stars.area = Rect2(0, 0, 1920, 760)
	stars.count = 200
	_world.add_child(stars)
	_add_layer("res://assets/bg/menu/far.svg", 0.2)
	_add_layer("res://assets/bg/menu/near.svg", 0.45)
	var dust := DUST.new()
	dust.area = Rect2(0, 200, 1920, 880)
	dust.count = 40
	dust.color = Color(0.85, 0.75, 1.0, 0.7)
	dust.glow = true
	_world.add_child(dust)
	_kira = KIRA.new()
	_kira.position = Vector2(1450, 1340)
	_kira.scale = Vector2(0.62, 0.62)
	_world.add_child(_kira)
	_kira.clicked.connect(_on_kira)
	get_viewport().size_changed.connect(_fit)
	_fit()

	var shade := ColorRect.new()
	shade.set_anchors_and_offsets_preset(Control.PRESET_LEFT_WIDE)
	shade.offset_right = 900
	shade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var g := GradientTexture2D.new()
	g.fill_from = Vector2(0, 0)
	g.fill_to = Vector2(1, 0)
	var grad := Gradient.new()
	grad.set_color(0, Color(0.04, 0.03, 0.12, 0.75))
	grad.set_color(1, Color(0.04, 0.03, 0.12, 0.0))
	g.gradient = grad
	var tr := TextureRect.new()
	tr.texture = g
	tr.set_anchors_and_offsets_preset(Control.PRESET_LEFT_WIDE)
	tr.offset_right = 1000
	tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(tr)

	_title = UIKit.label("KIRA.exe", 150, Color.WHITE, UIKit.font_title)
	_title.position = Vector2(110, 90)
	_title.add_theme_color_override("font_shadow_color", Color(0.75, 0.55, 1.0, 0.8))
	_title.add_theme_constant_override("shadow_outline_size", 30)
	_title.add_theme_constant_override("shadow_offset_x", 0)
	_title.add_theme_constant_override("shadow_offset_y", 0)
	add_child(_title)
	_subtitle = UIKit.label("По ту сторону экрана", 62, Color("#ffd6ef"), UIKit.font_script)
	_subtitle.position = Vector2(130, 270)
	_subtitle.add_theme_color_override("font_shadow_color", Color(1.0, 0.45, 0.75, 0.6))
	_subtitle.add_theme_constant_override("shadow_outline_size", 16)
	add_child(_subtitle)

	_buttons = VBoxContainer.new()
	_buttons.position = Vector2(120, 410)
	_buttons.add_theme_constant_override("separation", 14)
	add_child(_buttons)
	if SaveManager.has_any_save():
		_menu_button("Продолжить", _continue)
	_menu_button("Новая игра", _new_game)
	_menu_button("Загрузить", _open_load)
	_menu_button("Настройки", func(): _open(preload("res://scripts/ui/settings_panel.gd").new()))
	_menu_button("Галерея", func(): _open(preload("res://scripts/ui/gallery_panel.gd").new()))
	_menu_button("Об игре", func(): _open(preload("res://scripts/ui/about_panel.gd").new()))
	_menu_button("Выход", func(): get_tree().quit())
	for i in _buttons.get_child_count():
		var b: Control = _buttons.get_child(i)
		b.modulate.a = 0.0
		var t := create_tween()
		t.tween_interval(0.3 + i * 0.08)
		t.tween_property(b, "modulate:a", 1.0, 0.4)
	(_buttons.get_child(0) as Button).grab_focus.call_deferred()

	var footer := UIKit.label("Сделано с ♥ · v" + str(ProjectSettings.get_setting("application/config/version", "1.0")) + "  ·  F11 — полный экран", 20, Color(1, 1, 1, 0.45))
	footer.anchor_top = 1.0
	footer.anchor_bottom = 1.0
	footer.offset_left = 120
	footer.offset_top = -50
	add_child(footer)
	_bubble = preload("res://scripts/ui/bubble.gd").new()
	add_child(_bubble)
	AudioManager.play_music("menu")
	AudioManager.play_ambience("")
	_kira.set_emotion("happy")
	await get_tree().create_timer(1.6).timeout
	if is_inside_tree():
		_kira.set_emotion("neutral")


func _add_layer(path: String, depth: float) -> void:
	var s := Sprite2D.new()
	s.texture = load(path)
	s.centered = false
	_world.add_child(s)
	_layers.append({"node": s, "depth": depth})


func _fit() -> void:
	var vs := get_viewport_rect().size
	var s: float = maxf(vs.x / 1920.0, vs.y / 1080.0)
	_world.scale = Vector2(s, s)
	_world.position = (vs - Vector2(1920, 1080) * s) / 2.0


func _menu_button(text: String, cb: Callable) -> void:
	var b := UIKit.button(text, cb)
	b.custom_minimum_size = Vector2(420, 66)
	b.add_theme_font_size_override("font_size", 30)
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	_buttons.add_child(b)


func _process(delta: float) -> void:
	_t += delta
	var vs := get_viewport_rect().size
	var m := (get_viewport().get_mouse_position() - vs / 2.0) / vs
	for l in _layers:
		l.node.position = l.node.position.lerp(-m * 50.0 * l.depth, delta * 3.0)
	_title.position.y = 90 + sin(_t * 1.2) * 4.0
	# Иногда заголовок «сбоит» — маленький намёк на тайну
	_glitch_t -= delta
	if _glitch_t <= 0.0:
		_glitch_t = randf_range(4.0, 9.0)
		_title.text = ["K1RA.exe", "KIRA.ехе", "KI_A.exe", "КИРА.exe"].pick_random()
		_title.position.x = 110 + randf_range(-6, 6)
		await get_tree().create_timer(0.12).timeout
		_title.text = "KIRA.exe"
		_title.position.x = 110


func _on_kira(part: String) -> void:
	var line: String
	if part == "clip":
		line = "Это заколки. Их нельзя трогать. Это закон."
		_kira.set_emotion("pout")
	else:
		line = LINES[_line_i % LINES.size()]
		_line_i += 1
		_kira.set_emotion(["happy", "sly", "thoughtful", "shy", "neutral"].pick_random())
	AudioManager.play_sfx("blip_kira")
	var head = get_viewport().get_canvas_transform() * _kira.get_head_global()
	_bubble.say(line, head + Vector2(0, -20))


func _open(m: Control) -> void:
	add_child(m)


func _continue() -> void:
	var slot := SaveManager.latest_slot()
	if slot == "" or not SaveManager.load_game(slot):
		_error("Не удалось загрузить сохранение: " + SaveManager.last_error)
		return
	SceneManager.change_scene("res://scenes/game.tscn")


func _new_game() -> void:
	if SaveManager.has_any_save():
		var d = preload("res://scripts/ui/confirm_dialog.gd").new()
		d.question = "Начать новую историю? Старые сохранения останутся в слотах."
		add_child(d)
		if not await d.answered:
			return
	GameManager.new_game()
	DialogueManager.clear_history()
	SceneManager.change_scene("res://scenes/game.tscn")


func _open_load() -> void:
	var p = preload("res://scripts/ui/save_load_panel.gd").new()
	p.mode = "load"
	p.loaded.connect(func(slot):
		if SaveManager.load_game(slot):
			DialogueManager.clear_history()
			SceneManager.change_scene("res://scenes/game.tscn")
		else:
			_error("Сохранение повреждено: " + SaveManager.last_error))
	add_child(p)


func _error(text: String) -> void:
	var d = preload("res://scripts/ui/confirm_dialog.gd").new()
	d.question = text
	d.yes_text = "Понятно"
	d.no_text = "Закрыть"
	add_child(d)

