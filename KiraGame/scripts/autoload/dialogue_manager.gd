extends Node
## Исполнитель сюжетных сценариев из data/dialogue/*.json.
##
## Сценарий — массив команд. Основные формы:
##   "Текст"                                  — реплика Киры
##   {"kira": "Текст", "e": "happy", "pose": "behind"}
##   {"n": "Текст"}  {"p": "Текст"}  {"sys": "Текст"}  {"say": "Текст", "who": "conductor"}
##   {"choice": [{"t": "Вариант", "if": "условие", "rel": {...}, "set": {...}, "do": [...], "go": "метка"}]}
##   {"if": "условие", "then": [...], "else": [...]}
##   {"label": "метка"}  {"go": "метка"}  {"call": "сценарий"}  {"end": true}
##   {"set": {...}}  {"inc": "флаг"}  {"rel": {"trust": 1}}  {"remember": "ключ", "text": "..."}
##   {"objective": "..."}  {"give": "предмет"}  {"unlock": {"memory": "id"}}  {"music": "..."}  {"sfx": "..."}
## Остальные команды (смена локации, эффекты, мини-игры) передаются обработчику — игровому миру.

signal dialogue_started(id: String)
signal dialogue_finished(id: String)
signal history_added(entry: Dictionary)

const DIR := "res://data/dialogue/"

const SPEAKERS := {
	"kira": {"name": "Кира", "color": Color("#d2b4ff"), "voice": "kira"},
	"player": {"name": "{name}", "color": Color("#9fd6ff"), "voice": ""},
	"narrator": {"name": "", "color": Color("#e8e0ff"), "voice": "", "style": "narration"},
	"system": {"name": "KIRA.exe", "color": Color("#7ff5d6"), "voice": "system", "style": "system"},
	"conductor": {"name": "Проводник", "color": Color("#f2cf86"), "voice": "low"},
	"kite_girl": {"name": "Девочка со змеем", "color": Color("#ffb3d1"), "voice": "high"},
	"knight": {"name": "Рыцарь без дракона", "color": Color("#b8c4ff"), "voice": "low"},
	"robot": {"name": "Робот-почтальон", "color": Color("#9af0ff"), "voice": "system"},
	"cat": {"name": "Кот", "color": Color("#ffe28a"), "voice": "high"},
	"barista": {"name": "Бариста-тень", "color": Color("#ffcf9a"), "voice": "low"},
	"unknown": {"name": "???", "color": Color("#c0c0d8"), "voice": "low"},
	"author": {"name": "Почерк в тетради", "color": Color("#ffe9c4"), "voice": "", "style": "narration"},
}

var scripts := {}
var ui: Node = null
var handler: Node = null
var running := false
var current_id := ""
var history: Array = []
var read_lines := {}
var last_choice := -1
var last_result := {}
var _jump := ""
var _stop := false


func _ready() -> void:
	load_all()


func load_all() -> void:
	scripts.clear()
	var dir := DirAccess.open(DIR)
	if dir == null:
		push_error("Нет папки с диалогами: " + DIR)
		return
	for file in dir.get_files():
		var fname := file.trim_suffix(".remap")
		if not fname.ends_with(".json"):
			continue
		var f := FileAccess.open(DIR + fname, FileAccess.READ)
		if f == null:
			continue
		var j := JSON.new()
		if j.parse(f.get_as_text()) != OK:
			push_error("Ошибка JSON в %s, строка %d: %s" % [fname, j.get_error_line(), j.get_error_message()])
			continue
		var data = j.data
		if data is Dictionary:
			for id in data.keys():
				if data[id] is Array:
					scripts[id] = data[id]


func has_script(id: String) -> bool:
	return scripts.has(id)


func speaker_info(who: String) -> Dictionary:
	return SPEAKERS.get(who, {"name": who, "color": Color.WHITE, "voice": ""})


## Запустить сценарий и дождаться его завершения.
func run(id: String) -> void:
	if not scripts.has(id):
		push_warning("Сценарий не найден: " + id)
		return
	if running:
		# Вложенный запуск — выполняем как подпрограмму
		await _run_script(id)
		return
	running = true
	current_id = id
	dialogue_started.emit(id)
	await _run_script(id)
	running = false
	_stop = false
	_jump = ""
	if ui and ui.has_method("close"):
		ui.close()
	dialogue_finished.emit(id)


func _run_script(id: String) -> void:
	GameManager.mark_seen(id)
	var list: Array = scripts[id]
	var i := 0
	while i < list.size():
		await _exec(list[i], id, i)
		if _stop:
			return
		if _jump != "":
			var target := _find_label(list, _jump)
			if target < 0:
				push_warning("Метка '%s' не найдена в %s" % [_jump, id])
				_jump = ""
			else:
				_jump = ""
				i = target
				continue
		i += 1


func _find_label(list: Array, label: String) -> int:
	for i in list.size():
		if list[i] is Dictionary and list[i].get("label", "") == label:
			return i
	return -1


func _run_block(block, sid: String, base: int) -> void:
	if block is String or block is Dictionary:
		block = [block]
	if not block is Array:
		return
	for j in block.size():
		await _exec(block[j], sid, base * 100 + j + 1)
		if _stop or _jump != "":
			return


func _exec(cmd, sid: String, idx: int) -> void:
	if cmd is String:
		await _say("kira", cmd, {}, sid)
		return
	if not cmd is Dictionary:
		return
	if cmd.has("if") and not cmd.has("then") and not cmd.has("go"):
		# Условная реплика или команда: {"if": "...", "kira": "..."}
		if not GameManager.eval_cond(str(cmd.if)):
			return
		cmd = cmd.duplicate()
		cmd.erase("if")
	# ----- реплики
	for key in ["kira", "n", "p", "sys"]:
		if cmd.has(key):
			var who := {"kira": "kira", "n": "narrator", "p": "player", "sys": "system"}[key] as String
			_apply_effects(cmd)
			await _say(who, str(cmd[key]), cmd, sid)
			return
	if cmd.has("say"):
		_apply_effects(cmd)
		await _say(str(cmd.get("who", "unknown")), str(cmd.say), cmd, sid)
		return
	# ----- управление потоком
	if cmd.has("label"):
		return
	if cmd.has("if"):
		var ok := GameManager.eval_cond(str(cmd.if))
		if cmd.has("go"):
			if ok:
				_jump = str(cmd.go)
			return
		await _run_block(cmd.get("then", []) if ok else cmd.get("else", []), sid, idx)
		return
	if cmd.has("go"):
		_jump = str(cmd.go)
		return
	if cmd.has("end"):
		_stop = true
		return
	if cmd.has("call"):
		var saved_stop := _stop
		await _run_script(str(cmd.call))
		_stop = saved_stop
		_jump = ""
		return
	if cmd.has("random"):
		var opts: Array = cmd.random
		if opts.size() > 0:
			await _run_block(opts[randi() % opts.size()], sid, idx)
		return
	if cmd.has("choice"):
		await _choice(cmd, sid, idx)
		return
	# ----- состояние
	_apply_effects(cmd)
	if cmd.has("objective"):
		GameManager.set_objective(_fmt(str(cmd.objective)))
	if cmd.has("wait") and handler == null:
		return
	# ----- всё остальное — в игровой мир
	if handler and handler.has_method("handle_command"):
		await handler.handle_command(cmd)


func _apply_effects(cmd: Dictionary) -> void:
	if cmd.has("set"):
		for k in cmd.set.keys():
			GameManager.set_flag(k, cmd.set[k])
	if cmd.has("inc"):
		GameManager.inc_flag(str(cmd.inc))
	if cmd.has("rel"):
		for k in cmd.rel.keys():
			RelationshipManager.add(k, int(cmd.rel[k]))
	if cmd.has("remember"):
		RelationshipManager.remember(str(cmd.remember), _fmt(str(cmd.get("text", ""))))
	if cmd.has("give"):
		InventoryManager.give(str(cmd.give), int(cmd.get("n", 1)))
	if cmd.has("take"):
		InventoryManager.give(str(cmd.take), -int(cmd.get("n", 1)))
	if cmd.has("unlock"):
		var u: Dictionary = cmd.unlock
		for kind in u.keys():
			var ids = u[kind]
			if not ids is Array:
				ids = [ids]
			for id in ids:
				var ok := false
				match kind:
					"memory": ok = InventoryManager.unlock_memory(str(id))
					"outfit": ok = InventoryManager.unlock("outfits", str(id))
					"decor": ok = InventoryManager.unlock("decor", str(id))
					"wall": ok = InventoryManager.unlock("walls", str(id))
				if ok and handler and handler.has_method("notify_unlock"):
					handler.notify_unlock(kind, str(id))
	if cmd.has("music"):
		AudioManager.play_music(str(cmd.music))
	if cmd.has("amb"):
		AudioManager.play_ambience(str(cmd.amb))
	if cmd.has("sfx") and not (cmd.has("kira") or cmd.has("n") or cmd.has("p") or cmd.has("sys") or cmd.has("say")):
		AudioManager.play_sfx(str(cmd.sfx))


func _fmt(text: String) -> String:
	return text.replace("{name}", GameManager.display_name()).replace("{Name}", GameManager.display_name().capitalize())


func _say(who: String, text: String, cmd: Dictionary, sid: String) -> void:
	if cmd.has("sfx"):
		AudioManager.play_sfx(str(cmd.sfx))
	if handler and handler.has_method("on_line") :
		handler.on_line(who, cmd)
	var info := speaker_info(who)
	var t := _fmt(text)
	var key := "%s:%d" % [sid, text.hash()]
	var data := {
		"who": who,
		"name": _fmt(str(info.name)),
		"color": info.color,
		"voice": info.get("voice", ""),
		"style": info.get("style", "normal"),
		"text": t,
		"read": read_lines.has(key),
	}
	_add_history({"name": data.name, "text": t, "color": info.color, "style": data.style})
	if ui and ui.has_method("show_line"):
		ui.show_line(data)
		await ui.advanced
	read_lines[key] = true
	if handler and handler.has_method("on_line_end"):
		handler.on_line_end(who)


func _choice(cmd: Dictionary, sid: String, idx: int) -> void:
	var options: Array = []
	for o in cmd.choice:
		if o is Dictionary and GameManager.eval_cond(str(o.get("if", ""))):
			options.append(o)
	if options.is_empty():
		return
	var labels: Array = []
	for o in options:
		labels.append(_fmt(str(o.get("t", "…"))))
	var pick := 0
	if ui and ui.has_method("show_choices"):
		ui.show_choices(labels)
		pick = await ui.chosen
	pick = clampi(pick, 0, options.size() - 1)
	last_choice = pick
	var o: Dictionary = options[pick]
	_add_history({"name": GameManager.display_name(), "text": "→ " + labels[pick], "color": SPEAKERS.player.color, "style": "choice"})
	_apply_effects(o)
	if o.has("do"):
		await _run_block(o.do, sid, idx)
	if o.has("go") and not _stop:
		_jump = str(o.go)


func _add_history(entry: Dictionary) -> void:
	history.append(entry)
	if history.size() > 300:
		history.pop_front()
	history_added.emit(entry)


func clear_history() -> void:
	history.clear()
