extends Node
## Автоматические проверки игры. Запуск:
##   godot --headless --path . res://tests/test_runner.tscn
## Проверяет данные (сценарии, условия, ссылки), мини-игры, сохранения
## и полностью проходит сюжет до каждой из трёх концовок.

const VALID_EMOTIONS := ["neutral", "happy", "joy", "shy", "thoughtful", "surprised", "sad", "pout", "sleepy", "sly", "warm", "worried", "determined"]
const WORLD_KEYS := ["emo", "act", "time", "advance_time", "outfit", "lighting", "autosave", "bubble", "hide_dialogue", "kira_show",
	"kira_move", "npc", "event", "fx", "loc", "input_name", "minigame", "card", "ending", "free_mode", "travel_unlock", "wait", "args", "dur", "trans", "params", "id"]
const KNOWN_KEYS := ["kira", "n", "p", "sys", "say", "who", "e", "label", "if", "then", "else", "go", "end", "call", "random", "choice",
	"set", "inc", "rel", "remember", "text", "objective", "give", "take", "n", "unlock", "music", "amb", "sfx"]

## Действия игрока для каждого шага сюжета: [тип, значение]
const ACTIONS := {
	"1:look": [["hot", "window"], ["hot", "bed"], ["hot", "bookshelf"], ["hot", "plant"]],
	"1:cook": [["act", "cooking"]],
	"1:stars": [["act", "stars"]],
	"2:decor": [["act", "decorate"]],
	"2:city": [["hot", "cafe"], ["hot", "cat"], ["hot", "puddle"], ["hot", "phone"], ["hot", "posters"]],
	"3:stars": [["hot", "telescope"]],
	"3:bench": [["hot", "bench"]],
	"3:stairs": [["hot", "stairs"]],
	"3:clues": [["hot", "archive"], ["hot", "catalog"], ["hot", "book"], ["hot", "books_pile"]],
	"3:archive": [["hot", "archive"]],
	"4:passengers": [["hot", "next_door"], ["hot", "kite_girl"], ["hot", "knight"], ["hot", "robot"]],
	"4:door": [["hot", "conductor"], ["hot", "next_door"]],
}

var errors: Array = []
var checks := 0
var ending_target := ""
var choice_policy := 0


class StubUI:
	extends Node
	signal advanced
	signal chosen(index: int)
	signal name_entered(text: String)
	var runner
	var lines := 0

	func show_line(_data: Dictionary) -> void:
		lines += 1
		advanced.emit.call_deferred()

	func show_choices(options: Array) -> void:
		chosen.emit.call_deferred(runner.pick_choice(options))

	func ask_name() -> void:
		name_entered.emit.call_deferred("Тестер")

	func close() -> void:
		pass


func _ready() -> void:
	print("=== KIRA.exe: автотесты ===")
	Engine.time_scale = 25.0
	MinigameManager.autoplay = true
	await get_tree().process_frame
	test_data()
	await test_minigames()
	test_saves()
	for target in ["our_world", "new_dawn", "constellation"]:
		await test_playthrough(target)
	print("")
	print("Проверок: %d, ошибок: %d" % [checks, errors.size()])
	for e in errors:
		print("  ✗ " + str(e))
	print("РЕЗУЛЬТАТ: " + ("УСПЕХ" if errors.is_empty() else "ЕСТЬ ОШИБКИ"))
	get_tree().quit(0 if errors.is_empty() else 1)


var _watch_ms := 0
var _last_hist := ""


func _process(_d: float) -> void:
	# Сторожевой таймер: раз в 30 секунд реального времени печатает состояние, если ничего не меняется
	var now := Time.get_ticks_msec()
	if now - _watch_ms < 30000:
		return
	_watch_ms = now
	var h := ""
	if DialogueManager.history.size() > 0:
		h = str(DialogueManager.history[-1].text).left(70)
	if h == _last_hist:
		var w = get_tree().root.get_node_or_null("Game")
		print("   [сторож] глава=%d шаг=%d лок=%s сценарий=%s dm=%s mg=%s busy=%s посл.реплика=%s" % [GameManager.chapter, GameManager.step,
			GameManager.location, DialogueManager.current_id, DialogueManager.running, MinigameManager.active_id,
			w.busy if w else "-", h])
	_last_hist = h


func check(cond: bool, msg: String) -> void:
	checks += 1
	if not cond:
		errors.append(msg)
		print("  ✗ " + msg)


func pick_choice(options: Array) -> int:
	# Финальный выбор концовки
	for i in options.size():
		var t: String = options[i]
		if ending_target == "our_world" and t.begins_with("«Наш маленький мир»"):
			return i
		if ending_target == "new_dawn" and t.begins_with("«Новый рассвет»"):
			return i
		if ending_target == "constellation" and t.begins_with("«Созвездие воспоминаний»"):
			return i
		if t == "«Да. Пишем»":
			return i
	return choice_policy % options.size()


# ================================================================ ДАННЫЕ
func test_data() -> void:
	print("— Проверка данных")
	var dm := DialogueManager
	check(dm.scripts.size() > 100, "Слишком мало сценариев: %d" % dm.scripts.size())
	var conds: Array = []
	for id in dm.scripts.keys():
		_walk(dm.scripts[id], id, dm.scripts[id], conds)
	for c in conds:
		var e := Expression.new()
		var ok := e.parse(str(c[0]), PackedStringArray(["chapter", "step", "trust", "understanding", "spirit", "time", "day", "free", "loc", "ending"]))
		check(ok == OK, "Условие не разбирается в %s: %s" % [c[1], c[0]])
	# Сюжет
	var f := FileAccess.open("res://data/story.json", FileAccess.READ)
	var story: Dictionary = JSON.parse_string(f.get_as_text())
	for ch in story.chapters.keys():
		for s in story.chapters[ch].steps:
			for k in ["start", "end"]:
				if s.has(k):
					check(dm.has_script(s[k]), "Глава %s: нет сценария %s" % [ch, s[k]])
			for key in s.get("on", {}).keys():
				check(dm.has_script(s.on[key]), "Глава %s: нет сценария %s" % [ch, s.on[key]])
			if s.has("until"):
				conds.append([s.until, "story"])
	# Локации
	for loc in ["room", "city", "rooftop", "library", "train", "edge"]:
		var lf := FileAccess.open("res://data/locations/%s.json" % loc, FileAccess.READ)
		check(lf != null, "Нет данных локации " + loc)
		if lf:
			var d: Dictionary = JSON.parse_string(lf.get_as_text())
			for h in d.get("hotspots", []):
				check(h.has("rect") and h.rect.size() == 4, "Плохой прямоугольник %s.%s" % [loc, h.id])
				var base := "%s_%s" % [loc, h.id]
				var any := dm.has_script(base) or dm.has_script(base + "_first")
				check(any, "Нет сценария для объекта " + base)
	# Темы для разговоров
	var tf := FileAccess.open("res://data/talk.json", FileAccess.READ)
	for t in JSON.parse_string(tf.get_as_text()).topics:
		check(dm.has_script(t.id), "Нет сценария темы " + t.id)
	var idf := FileAccess.open("res://data/idle.json", FileAccess.READ)
	for ev in JSON.parse_string(idf.get_as_text()).events:
		check(dm.has_script(ev.script), "Нет сценария события " + ev.script)
	for c in ["act_stars_day", "act_stars_pre", "act_stars_post", "act_cooking_pre", "act_cooking_post", "act_memory_pre",
			"act_memory_post", "act_decorate_pre", "act_decorate_post", "act_rest", "act_sleep", "act_wake", "talk_fallback",
			"free_intro_our_world", "free_intro_new_dawn", "free_intro_constellation", "kira_clip_secret"]:
		check(dm.has_script(c), "Нет сценария " + c)
	# Ассеты
	for id in InventoryManager.db.memories.keys():
		check(ResourceLoader.exists("res://assets/memories/%s.svg" % InventoryManager.db.memories[id].image), "Нет картинки воспоминания " + id)
	for id in InventoryManager.db.decor.keys():
		check(ResourceLoader.exists("res://assets/decor/%s.svg" % id), "Нет картинки декора " + id)
	for t in ["room", "night", "city", "library", "train", "edge", "ending", "menu", "minigame", "dawn"]:
		check(ResourceLoader.exists("res://assets/audio/music/%s.ogg" % t), "Нет музыки " + t)


func _walk(list, sid: String, root, conds: Array) -> void:
	if list is Dictionary:
		list = [list]
	if not list is Array:
		return
	for cmd in list:
		if cmd is String:
			continue
		if not cmd is Dictionary:
			check(false, "Странная команда в " + sid)
			continue
		for k in cmd.keys():
			check(k in KNOWN_KEYS or k in WORLD_KEYS, "Неизвестный ключ '%s' в %s" % [k, sid])
		if cmd.has("if"):
			conds.append([cmd.if, sid])
		if cmd.has("e"):
			check(cmd.e in VALID_EMOTIONS, "Неизвестная эмоция %s в %s" % [cmd.e, sid])
		if cmd.has("emo"):
			check(cmd.emo in VALID_EMOTIONS, "Неизвестная эмоция %s в %s" % [cmd.emo, sid])
		if cmd.has("call"):
			check(DialogueManager.has_script(cmd.call), "Вызов несуществующего %s в %s" % [cmd.call, sid])
		if cmd.has("go"):
			check(_has_label(root, cmd.go), "Нет метки %s в %s" % [cmd.go, sid])
		if cmd.has("minigame"):
			check(MinigameManager.GAMES.has(cmd.minigame), "Неизвестная мини-игра в " + sid)
		if cmd.has("loc"):
			check(GameManager.LOCATION_NAMES.has(cmd.loc), "Неизвестная локация %s в %s" % [cmd.loc, sid])
		if cmd.has("outfit"):
			check(InventoryManager.db.outfits.has(cmd.outfit), "Неизвестный наряд в " + sid)
		if cmd.has("unlock"):
			for kind in cmd.unlock.keys():
				var db_key: String = {"memory": "memories", "outfit": "outfits", "decor": "decor", "wall": "walls"}.get(kind, "")
				var ids = cmd.unlock[kind]
				if not ids is Array:
					ids = [ids]
				for id in ids:
					check(InventoryManager.db.get(db_key, {}).has(id), "Неизвестный %s/%s в %s" % [kind, id, sid])
		for sub in ["then", "else", "do"]:
			if cmd.has(sub):
				_walk(cmd[sub], sid, root, conds)
		if cmd.has("random"):
			for r in cmd.random:
				_walk(r, sid, root, conds)
		if cmd.has("choice"):
			for o in cmd.choice:
				if o.has("if"):
					conds.append([o.if, sid])
				if o.has("go"):
					check(_has_label(root, o.go), "Нет метки %s в %s" % [o.go, sid])
				if o.has("do"):
					_walk(o.do, sid, root, conds)


func _has_label(root: Array, label: String) -> bool:
	for c in root:
		if c is Dictionary and c.get("label", "") == label:
			return true
	return false


# ============================================================ МИНИ-ИГРЫ
func test_minigames() -> void:
	print("— Проверка мини-игр")
	GameManager.new_game()
	for g in [["stars", {"ids": ["cat", "lyra", "whale"]}], ["cooking", {"recipe": "pancakes"}], ["cooking", {"recipe": "cocoa"}],
			["cooking", {"recipe": "onigiri"}], ["memory", {"memory": "author_desk", "grid": 4}], ["decorate", {}], ["lock", {}]]:
		var res: Dictionary = await MinigameManager.play(g[0], g[1])
		check(res.has("score") and int(res.score) >= 1, "Мини-игра %s не завершилась корректно: %s" % [g[0], res])
		check(not res.get("skipped", false), "Мини-игра %s была пропущена" % g[0])
	check(InventoryManager.placed_count() >= 3, "Декор не расставлен")


# ============================================================ СОХРАНЕНИЯ
func test_saves() -> void:
	print("— Проверка сохранений")
	GameManager.new_game()
	GameManager.in_game = true
	GameManager.player_name = "Проверка"
	GameManager.chapter = 3
	GameManager.set_flag("x_test", 42)
	RelationshipManager.add("trust", 5)
	InventoryManager.unlock_memory("first_meeting")
	InventoryManager.set_outfit("hoodie")
	TimeManager.set_phase("night")
	check(SaveManager.save_game("6"), "Сохранение не записалось")
	GameManager.new_game()
	check(SaveManager.load_game("6"), "Сохранение не загрузилось")
	check(GameManager.player_name == "Проверка", "Имя не восстановилось")
	check(GameManager.chapter == 3, "Глава не восстановилась")
	check(GameManager.count("x_test") == 42, "Флаг не восстановился")
	check(RelationshipManager.get_stat("trust") == 5, "Отношения не восстановились")
	check(InventoryManager.has_memory("first_meeting"), "Воспоминание не восстановилось")
	check(InventoryManager.outfit == "hoodie", "Наряд не восстановился")
	check(TimeManager.phase == "night", "Время суток не восстановилось")
	# Второе сохранение, затем порча основного файла: должна подняться резервная копия
	SaveManager.save_game("6")
	var f := FileAccess.open(SaveManager.slot_path("6"), FileAccess.WRITE)
	f.store_string("{ это повреждённый файл")
	f.close()
	var d := SaveManager.read_slot("6")
	check(not d.is_empty(), "Резервная копия не восстановилась после порчи файла")
	# Порча обоих файлов: загрузка должна аккуратно отказать
	for p in [SaveManager.slot_path("6"), SaveManager.slot_path("6") + ".bak"]:
		var g := FileAccess.open(p, FileAccess.WRITE)
		g.store_string('{"format":"kira-save","version":1,"checksum":"bad","payload":"{}"}')
		g.close()
	check(SaveManager.read_slot("6").is_empty(), "Повреждённое сохранение было принято")
	check(not SaveManager.load_game("6"), "Загрузка повреждённого сохранения не отказала")
	check(SaveManager.slot_info("6").get("corrupted", false), "Слот не помечен как повреждённый")
	SaveManager.delete_slot("6")
	GameManager.in_game = false


# ============================================================ ПРОХОЖДЕНИЕ
func test_playthrough(target: String) -> void:
	print("— Прохождение до концовки: " + target)
	ending_target = target
	choice_policy = {"our_world": 0, "new_dawn": 2, "constellation": 1}[target]
	GameManager.new_game()
	DialogueManager.clear_history()
	var world: Node = load("res://scenes/game.tscn").instantiate()
	var stub := StubUI.new()
	stub.runner = self
	add_child(stub)
	DialogueManager.ui = stub
	get_tree().root.add_child(world)
	await get_tree().process_frame
	var guard := 0
	var last_key := ""
	var repeats := 0
	while not GameManager.free_mode and guard < 400:
		guard += 1
		await get_tree().create_timer(0.2).timeout
		if world.busy or world.director.is_running() or DialogueManager.running or MinigameManager.is_active():
			continue
		var step: Dictionary = world.director.current()
		if step.is_empty():
			continue
		var key := "%d:%s" % [GameManager.chapter, step.get("id", "")]
		if key == last_key:
			repeats += 1
			if repeats > 3:
				check(false, "[%s] Застряли на шаге %s" % [target, key])
				break
		else:
			repeats = 0
		if key != last_key:
			print("   шаг " + key)
		last_key = key
		for a in ACTIONS.get(key, []):
			if GameManager.free_mode:
				break
			if a[0] == "hot":
				if world.location.hotspot(a[1]).is_empty() or not GameManager.eval_cond(str(world.location.hotspot(a[1]).get("if", ""))):
					continue
				await world._on_hotspot(a[1])
			else:
				await world.activity(a[1])
			while world.busy or world.director.is_running() or DialogueManager.running:
				await get_tree().create_timer(0.1).timeout
	# ждём титры и вступление свободного режима
	var wait := 0
	while (DialogueManager.running or world.busy or not GameManager.free_mode) and wait < 300:
		wait += 1
		await get_tree().create_timer(0.2).timeout
	if not GameManager.free_mode:
		print("   ДИАГНОСТИКА: busy=%s director=%s dm=%s mg=%s script=%s" % [world.busy, world.director.is_running(), DialogueManager.running, MinigameManager.is_active(), DialogueManager.current_id])
		for h in DialogueManager.history.slice(-4):
			print("     " + str(h.text).left(80))
	check(GameManager.free_mode, "[%s] Не включился свободный режим" % target)
	check(GameManager.ending == target, "[%s] Получена концовка «%s»" % [target, GameManager.ending])
	check(InventoryManager.has_memory("ending_" + target), "[%s] Нет воспоминания концовки" % target)
	check(target in SaveManager.global.endings, "[%s] Концовка не записана в галерею" % target)
	check(InventoryManager.has_memory("childs_drawing") and InventoryManager.has_memory("author_desk"), "[%s] Пропущены сюжетные воспоминания" % target)
	check(stub.lines > 150, "[%s] Подозрительно мало реплик: %d" % [target, stub.lines])
	# Свободный режим: занятия и прогулка работают
	for a in ["talk", "cooking", "stars", "rest", "memory"]:
		if GameManager.location != "room":
			await world.go_to("room", "none")
		await world.activity(a)
		check(not world.busy, "[%s] Занятие %s зависло" % [target, a])
	await world.go_to("library", "none")
	await world._on_hotspot("globe")
	await world.go_to("train", "none")
	await world._on_hotspot("window")
	check(SaveManager.save_game("auto"), "[%s] Автосохранение в свободном режиме" % target)
	print("   реплик показано: %d, отношения: %s" % [stub.lines, RelationshipManager.stats])
	world.queue_free()
	stub.queue_free()
	DialogueManager.ui = null
	DialogueManager.handler = null
	await get_tree().process_frame
