extends "res://scripts/ui/modal.gd"
## Настройки: звук, экран, текст, управление (с переназначением клавиш).

var _waiting_action := ""
var _bind_buttons := {}


func _init() -> void:
	panel_size = Vector2(1300, 860)


func build() -> void:
	set_title("Настройки")
	var tabs := TabContainer.new()
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	tabs.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	body.add_child(tabs)
	tabs.add_child(_audio_tab())
	tabs.add_child(_display_tab())
	tabs.add_child(_text_tab())
	tabs.add_child(_controls_tab())
	for i in tabs.get_tab_count():
		tabs.set_tab_title(i, ["Звук", "Экран", "Текст", "Управление"][i])


func _page(name: String) -> VBoxContainer:
	var sc := ScrollContainer.new()
	sc.name = name
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_theme_constant_override("separation", 18)
	sc.add_child(v)
	var spacer := Control.new()
	spacer.custom_minimum_size.y = 10
	v.add_child(spacer)
	return v


func _row(parent: Control, title: String) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 20)
	var l := UIKit.label(title, 25)
	l.custom_minimum_size.x = 430
	h.add_child(l)
	parent.add_child(h)
	return h


func _slider(parent: Control, title: String, section: String, key: String, minv: float, maxv: float, step := 0.01, fmt := "%d%%", mul := 100.0) -> void:
	var h := _row(parent, title)
	var s := HSlider.new()
	s.min_value = minv
	s.max_value = maxv
	s.step = step
	s.value = float(Settings.get_value(section, key, minv))
	s.custom_minimum_size = Vector2(480, 36)
	s.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	h.add_child(s)
	var val := UIKit.label(fmt % (s.value * mul), 23, UIKit.TEXT_DIM)
	val.custom_minimum_size.x = 120
	h.add_child(val)
	s.value_changed.connect(func(x):
		val.text = fmt % (x * mul)
		Settings.set_value(section, key, x)
		if section == "audio":
			AudioManager.play_sfx("tick", 1.0, -4.0))


func _check(parent: Control, title: String, section: String, key: String) -> void:
	var h := _row(parent, title)
	var c := CheckButton.new()
	c.button_pressed = bool(Settings.get_value(section, key, false))
	c.toggled.connect(func(on): Settings.set_value(section, key, on); AudioManager.play_sfx("click"))
	h.add_child(c)


func _option(parent: Control, title: String, items: Array, current: int, cb: Callable) -> void:
	var h := _row(parent, title)
	var o := OptionButton.new()
	for it in items:
		o.add_item(str(it))
	o.selected = current
	o.custom_minimum_size = Vector2(480, 54)
	o.item_selected.connect(cb)
	h.add_child(o)


func _audio_tab() -> Control:
	var v := _page("audio")
	_slider(v, "Общая громкость", "audio", "master", 0, 1)
	_slider(v, "Музыка", "audio", "music", 0, 1)
	_slider(v, "Звуковые эффекты", "audio", "sfx", 0, 1)
	_slider(v, "Атмосфера (дождь, ветер)", "audio", "ambience", 0, 1)
	_slider(v, "Голосовые сигналы текста", "audio", "voice", 0, 1)
	return v.get_parent()


func _display_tab() -> Control:
	var v := _page("display")
	var modes: Array = Settings.WINDOW_MODES
	_option(v, "Режим окна", Settings.WINDOW_MODE_NAMES, maxi(modes.find(Settings.get_value("display", "window_mode", "windowed")), 0),
		func(i): Settings.set_value("display", "window_mode", modes[i]))
	var res := []
	for r in Settings.RESOLUTIONS:
		res.append("%d × %d" % [r.x, r.y])
	_option(v, "Размер окна", res, int(Settings.get_value("display", "resolution", 2)),
		func(i): Settings.set_value("display", "resolution", i))
	_check(v, "Вертикальная синхронизация", "display", "vsync")
	_option(v, "Частицы (дождь, звёзды)", ["Меньше (для слабых ПК)", "Больше"], 1 if Settings.get_value("display", "particles", "high") == "high" else 0,
		func(i): Settings.set_value("display", "particles", ["low", "high"][i]))
	_check(v, "Тряска экрана", "display", "shake")
	_slider(v, "Эффекты «сбоя» (вспышки)", "display", "glitch", 0, 1)
	v.add_child(wrap_label("F11 — быстро переключить полноэкранный режим. Изменение частиц применяется при смене локации.", 21, UIKit.TEXT_DIM))
	return v.get_parent()


func _text_tab() -> Control:
	var v := _page("text")
	var speeds := [20.0, 35.0, 45.0, 70.0, 110.0, 400.0]
	var cur := 2
	for i in speeds.size():
		if absf(speeds[i] - float(Settings.get_value("text", "speed", 45.0))) < 1.0:
			cur = i
	_option(v, "Скорость текста", ["Очень медленно", "Медленно", "Обычно", "Быстро", "Очень быстро", "Мгновенно"], cur,
		func(i): Settings.set_value("text", "speed", speeds[i]))
	_slider(v, "Пауза в авто-режиме", "text", "auto_delay", 0.5, 4.0, 0.1, "%.1f с", 1.0)
	_check(v, "Пропускать и непрочитанный текст", "text", "skip_unread")
	v.add_child(wrap_label("Пропуск (Ctrl или кнопка «Пропуск») по умолчанию проматывает только уже прочитанные реплики и всегда останавливается на выборах.", 21, UIKit.TEXT_DIM))
	return v.get_parent()


func _controls_tab() -> Control:
	var v := _page("controls")
	v.add_child(wrap_label("Мышь — основные действия: клики по объектам, выбор ответов, продолжение диалога. Правая кнопка — скрыть окно диалога. Нажми на клавишу справа, чтобы переназначить её.", 21, UIKit.TEXT_DIM))
	for action in Settings.ACTIONS.keys():
		var h := _row(v, Settings.ACTIONS[action].title)
		var b := UIKit.button(Settings.key_name(action))
		b.custom_minimum_size = Vector2(300, 52)
		b.add_theme_font_size_override("font_size", 22)
		b.pressed.connect(func():
			_waiting_action = action
			b.text = "Нажми клавишу…")
		h.add_child(b)
		_bind_buttons[action] = b
	var r := UIKit.button("Сбросить управление", func():
		Settings.reset_controls()
		_refresh_binds())
	r.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	v.add_child(r)
	return v.get_parent()


func _refresh_binds() -> void:
	for a in _bind_buttons.keys():
		_bind_buttons[a].text = Settings.key_name(a)


func _input(event: InputEvent) -> void:
	if _waiting_action == "" or not event is InputEventKey or not event.pressed:
		return
	get_viewport().set_input_as_handled()
	if event.physical_keycode != KEY_ESCAPE or _waiting_action == "pause":
		Settings.rebind(_waiting_action, event.physical_keycode)
	_waiting_action = ""
	_refresh_binds()
