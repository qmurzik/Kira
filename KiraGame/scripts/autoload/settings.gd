extends Node
## Настройки игры: звук, экран, текст и управление.
## Хранятся в user://settings.cfg отдельно от сохранений.

signal changed(section: String, key: String)

const PATH := "user://settings.cfg"

const ACTIONS := {
	"advance": {"title": "Продолжить диалог", "keys": [KEY_ENTER, KEY_SPACE, KEY_KP_ENTER]},
	"pause": {"title": "Меню паузы", "keys": [KEY_ESCAPE]},
	"show_hotspots": {"title": "Показать объекты", "keys": [KEY_TAB]},
	"toggle_fullscreen": {"title": "Полноэкранный режим", "keys": [KEY_F11]},
	"history": {"title": "История диалогов", "keys": [KEY_H]},
	"skip": {"title": "Пропуск прочитанного (удерживать)", "keys": [KEY_CTRL]},
	"auto": {"title": "Авточтение", "keys": [KEY_A]},
	"hide_ui": {"title": "Скрыть интерфейс", "keys": [KEY_V]},
	"quick_save": {"title": "Быстрое сохранение", "keys": [KEY_F5]},
	"quick_load": {"title": "Быстрая загрузка", "keys": [KEY_F9]},
}

const WINDOW_MODES := ["windowed", "borderless", "fullscreen"]
const WINDOW_MODE_NAMES := ["Оконный", "Безрамочное окно", "Полноэкранный"]
const RESOLUTIONS := [Vector2i(1280, 720), Vector2i(1366, 768), Vector2i(1600, 900), Vector2i(1920, 1080), Vector2i(2560, 1440)]

var data := {
	"audio": {"master": 0.85, "music": 0.7, "sfx": 0.8, "ambience": 0.6, "voice": 0.6},
	"display": {"window_mode": "windowed", "resolution": 2, "vsync": true, "particles": "high", "shake": true, "glitch": 1.0},
	"text": {"speed": 45.0, "auto_delay": 1.6, "skip_unread": false},
	"controls": {},
}


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	load_settings()
	_setup_actions()
	call_deferred("apply_display")


func get_value(section: String, key: String, default = null):
	if data.has(section) and data[section].has(key):
		return data[section][key]
	return default


func set_value(section: String, key: String, value) -> void:
	if not data.has(section):
		data[section] = {}
	data[section][key] = value
	if section == "display":
		apply_display()
	changed.emit(section, key)
	save_settings()


func load_settings() -> void:
	var cfg := ConfigFile.new()
	if cfg.load(PATH) != OK:
		return
	for section in data.keys():
		if not cfg.has_section(section):
			continue
		for key in cfg.get_section_keys(section):
			var v = cfg.get_value(section, key)
			# Защита от мусора в файле настроек: тип должен совпадать
			if data[section].has(key) and typeof(data[section][key]) != typeof(v):
				if typeof(data[section][key]) in [TYPE_FLOAT, TYPE_INT] and typeof(v) in [TYPE_FLOAT, TYPE_INT]:
					v = float(v) if typeof(data[section][key]) == TYPE_FLOAT else int(v)
				else:
					continue
			data[section][key] = v


func save_settings() -> void:
	var cfg := ConfigFile.new()
	for section in data.keys():
		for key in data[section].keys():
			cfg.set_value(section, key, data[section][key])
	cfg.save(PATH)


# ------------------------------------------------------------------ экран
func apply_display() -> void:
	if DisplayServer.get_name() == "headless":
		return
	var mode: String = data.display.window_mode
	var win := get_window()
	match mode:
		"fullscreen":
			win.borderless = false
			win.mode = Window.MODE_FULLSCREEN
		"borderless":
			win.mode = Window.MODE_WINDOWED
			win.borderless = true
			var screen := DisplayServer.window_get_current_screen()
			win.size = DisplayServer.screen_get_size(screen)
			win.position = DisplayServer.screen_get_position(screen)
		_:
			win.borderless = false
			win.mode = Window.MODE_WINDOWED
			var idx := clampi(int(data.display.resolution), 0, RESOLUTIONS.size() - 1)
			var res: Vector2i = RESOLUTIONS[idx]
			var screen_size := DisplayServer.screen_get_size(DisplayServer.window_get_current_screen())
			if res.x > screen_size.x or res.y > screen_size.y:
				res = Vector2i(int(screen_size.x * 0.85), int(screen_size.y * 0.85))
			win.size = res
			win.move_to_center()
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_ENABLED if data.display.vsync else DisplayServer.VSYNC_DISABLED)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("toggle_fullscreen"):
		toggle_fullscreen()
		get_viewport().set_input_as_handled()


func toggle_fullscreen() -> void:
	var mode: String = data.display.window_mode
	set_value("display", "window_mode", "windowed" if mode == "fullscreen" else "fullscreen")


# -------------------------------------------------------------- управление
func _setup_actions() -> void:
	for action in ACTIONS.keys():
		if not InputMap.has_action(action):
			InputMap.add_action(action)
		InputMap.action_erase_events(action)
		var keys: Array = data.controls.get(action, ACTIONS[action].keys)
		for k in keys:
			var ev := InputEventKey.new()
			ev.physical_keycode = int(k)
			InputMap.action_add_event(action, ev)
	# Мышь: левый клик тоже продвигает диалог (обрабатывается в UI), колесо вверх — история
	var wheel := InputEventMouseButton.new()
	wheel.button_index = MOUSE_BUTTON_WHEEL_UP
	InputMap.action_add_event("history", wheel)


func get_keys(action: String) -> Array:
	return data.controls.get(action, ACTIONS[action].keys)


func rebind(action: String, keycode: int) -> void:
	# Убираем эту клавишу из других действий, чтобы не было конфликтов
	for other in ACTIONS.keys():
		if other == action:
			continue
		var keys: Array = get_keys(other).duplicate()
		if keycode in keys:
			keys.erase(keycode)
			data.controls[other] = keys
	data.controls[action] = [keycode]
	_setup_actions()
	save_settings()
	changed.emit("controls", action)


func reset_controls() -> void:
	data.controls = {}
	_setup_actions()
	save_settings()
	changed.emit("controls", "")


func key_name(action: String) -> String:
	var names: Array[String] = []
	for k in get_keys(action):
		names.append(OS.get_keycode_string(int(k)))
	return ", ".join(names)
