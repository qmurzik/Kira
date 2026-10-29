extends Node
## Игровой мир: текущая локация, Кира, интерфейс, исполнение сюжетных команд,
## занятия, клики по объектам, «живые» реплики Киры и случайные события.

const KIRA := preload("res://scripts/characters/kira.gd")
const DIRECTOR := preload("res://scripts/game/story_director.gd")
const LOCATIONS := {
	"room": "res://scripts/locations/room.gd",
	"city": "res://scripts/locations/city.gd",
	"rooftop": "res://scripts/locations/rooftop.gd",
	"library": "res://scripts/locations/library.gd",
	"train": "res://scripts/locations/train.gd",
	"edge": "res://scripts/locations/edge.gd",
}
const ACTIVITIES := ["stars", "cooking", "memory", "decorate", "talk", "rest"]

var loc_root: Node2D
var location: Node2D
var kira: Node2D
var hud: Control
var dlg: Control
var bubble: PanelContainer
var menu_layer: CanvasLayer
var director
var busy := false
var talk_pool: Array = []
var idle_data := {}
var _speaker := ""
var _idle_timer := 60.0
var _event_timer := 150.0
var _clip_clicks := 0
var _modal: Control = null
var _recipe_i := 0
var _const_i := 0


func _ready() -> void:
	GameManager.in_game = true
	get_tree().paused = false
	loc_root = Node2D.new()
	add_child(loc_root)
	kira = KIRA.new()
	kira.clicked.connect(_on_kira_clicked)
	var ui := CanvasLayer.new()
	ui.layer = 10
	add_child(ui)
	hud = preload("res://scripts/ui/hud.gd").new()
	hud.action.connect(_on_hud_action)
	ui.add_child(hud)
	bubble = preload("res://scripts/ui/bubble.gd").new()
	ui.add_child(bubble)
	var dl := CanvasLayer.new()
	dl.layer = 20
	add_child(dl)
	dlg = preload("res://scripts/ui/dialogue_box.gd").new()
	dlg.typing_changed.connect(func(on): kira.set_talking(on and _speaker == "kira"))
	dlg.history_requested.connect(func(): open_modal(preload("res://scripts/ui/history_panel.gd").new()))
	dl.add_child(dlg)
	menu_layer = CanvasLayer.new()
	menu_layer.layer = 30
	menu_layer.process_mode = Node.PROCESS_MODE_ALWAYS
	add_child(menu_layer)
	if DialogueManager.ui == null or not is_instance_valid(DialogueManager.ui):
		DialogueManager.ui = dlg
	DialogueManager.handler = self
	MinigameManager.started.connect(func(_id): _set_world_interactive(false))
	_load_json_data()
	director = DIRECTOR.new(self)
	InventoryManager.outfit_changed.connect(func(_o): pass)
	TimeManager.phase_changed.connect(func(_p): _update_music())
	var loaded := not GameManager.pending_load.is_empty()
	GameManager.pending_load = {}
	await go_to(GameManager.location, "none")
	if not loaded:
		GameManager.set_flag("_started", "")
	start_flow()


func _load_json_data() -> void:
	for pair in [["res://data/talk.json", "talk"], ["res://data/idle.json", "idle"]]:
		var f := FileAccess.open(pair[0], FileAccess.READ)
		if f == null:
			continue
		var d = JSON.parse_string(f.get_as_text())
		if pair[1] == "talk" and d is Dictionary:
			talk_pool = d.get("topics", [])
		elif d is Dictionary:
			idle_data = d


func start_flow() -> void:
	if GameManager.free_mode:
		_set_world_interactive(true)
		return
	await director.check()


func on_director_idle() -> void:
	if not busy:
		_set_world_interactive(true)
	hud.refresh()


# ============================================================= ЛОКАЦИИ
func go_to(id: String, trans := "fade") -> void:
	if not LOCATIONS.has(id):
		id = "room"
	match trans:
		"glitch":
			SceneManager.glitch(0.9, 1.0)
			await SceneManager.fade_out(0.7, Color(0.7, 0.6, 1.0))
		"white":
			await SceneManager.fade_out(1.2, Color(1, 1, 1))
		"none":
			pass
		_:
			await SceneManager.fade_out(0.6)
	if kira.get_parent():
		kira.get_parent().remove_child(kira)
	if location:
		location.queue_free()
	location = load(LOCATIONS[id]).new()
	GameManager.location = id
	GameManager.set_flag("visited_" + id, true)
	loc_root.add_child(location)
	location.hotspot_clicked.connect(_on_hotspot)
	location.hotspot_hover.connect(func(_id, title): hud.show_tooltip(title))
	location.kira_layer.add_child(kira)
	var kd: Dictionary = location.data.get("kira", {"x": 960, "y": 1000, "scale": 0.5})
	kira.position = Vector2(kd.x, kd.y)
	kira.scale = Vector2.ONE * float(kd.scale)
	kira.visible = true
	location.input_enabled = not busy
	_update_music()
	AudioManager.play_ambience(str(location.data.get("amb", "")))
	if id == "city" and GameManager.get_flag("city_dawn"):
		AudioManager.play_ambience("")
	hud.refresh()
	if trans != "none":
		await SceneManager.fade_in(0.7)


func _update_music() -> void:
	if location == null:
		return
	var m: Dictionary = location.data.get("music", {})
	var track: String = m.get(TimeManager.phase, m.get("default", ""))
	if GameManager.location == "city" and GameManager.get_flag("city_dawn"):
		track = "dawn"
	if not GameManager.get_flag("music_override", "").is_empty() and GameManager.location == "room":
		track = GameManager.get_flag("music_override", "")
	AudioManager.play_music(track)


# ======================================================== ИСПОЛНЕНИЕ
func run_script(id: String) -> void:
	if not DialogueManager.has_script(id):
		return
	var was_busy := busy
	busy = true
	_set_world_interactive(false)
	await DialogueManager.run(id)
	if not was_busy:
		busy = false
	kira.set_talking(false)
	hud.refresh()


## Первый существующий сценарий из списка кандидатов по правилам именования.
func resolve(base: String) -> String:
	var c := GameManager.chapter
	var cands: Array = []
	if not GameManager.free_mode:
		cands.append("%s_ch%d_first" % [base, c])
	cands.append(base + "_first")
	if GameManager.free_mode:
		cands.append(base + "_free")
	else:
		cands.append("%s_ch%d" % [base, c])
	cands.append("%s_%s" % [base, TimeManager.phase])
	cands.append(base)
	for id in cands:
		if DialogueManager.has_script(id):
			if id.ends_with("_first") and GameManager.seen_script(id):
				continue
			return id
	return ""


func _set_world_interactive(on: bool) -> void:
	if location:
		location.input_enabled = on
		if not on:
			location.hints_visible = false
	kira.interactive = on
	hud.set_interactive(on)
	if not on:
		hud.show_tooltip("")


# ------------------------------------------------------ команды из JSON
func on_line(who: String, cmd: Dictionary) -> void:
	_speaker = who
	if cmd.has("e"):
		kira.set_emotion(str(cmd.e))
	if cmd.has("act"):
		_kira_act(str(cmd.act))


func on_line_end(_who: String) -> void:
	kira.set_talking(false)


func notify_unlock(kind: String, id: String) -> void:
	match kind:
		"memory":
			hud.toast("Новое воспоминание: " + str(InventoryManager.info("memories", id).get("title", "")), "star")
			AudioManager.play_sfx("unlock")
		"outfit":
			hud.toast("Новый наряд: " + str(InventoryManager.info("outfits", id).get("name", "")), "shirt")
		"decor":
			hud.toast("Новый декор: " + str(InventoryManager.info("decor", id).get("name", "")), "sparkle")
		"wall":
			hud.toast("Новые обои: " + str(InventoryManager.info("walls", id).get("name", "")), "sparkle")


func _kira_act(a: String) -> void:
	match a:
		"hop": kira.hop()
		"shake": kira.shake()
		"nod": kira.nod()


func handle_command(cmd: Dictionary) -> void:
	if cmd.has("emo"):
		kira.set_emotion(str(cmd.emo))
	if cmd.has("act"):
		_kira_act(str(cmd.act))
	if cmd.has("time"):
		TimeManager.set_phase(str(cmd.time))
	if cmd.has("advance_time"):
		TimeManager.advance()
	if cmd.has("outfit"):
		InventoryManager.unlock("outfits", str(cmd.outfit))
		InventoryManager.set_outfit(str(cmd.outfit))
	if cmd.has("lighting"):
		InventoryManager.set_lighting(str(cmd.lighting))
	if cmd.has("autosave"):
		GameManager.request_autosave()
	if cmd.has("bubble"):
		say_bubble(DialogueManager._fmt(str(cmd.bubble)))
	if cmd.has("hide_dialogue"):
		dlg.close()
	if cmd.has("kira_show"):
		var show: bool = cmd.kira_show
		var t := create_tween()
		t.tween_property(kira, "modulate:a", 1.0 if show else 0.0, 0.8)
		await t.finished
	if cmd.has("kira_move"):
		await kira.walk_to(float(cmd.kira_move))
	if cmd.has("npc"):
		location.event(str(cmd.npc), {"id": str(cmd.get("id", ""))})
	if cmd.has("event"):
		location.event(str(cmd.event), cmd.get("args", {}))
		if cmd.has("wait"):
			await get_tree().create_timer(float(cmd.wait)).timeout
			return
	if cmd.has("fx"):
		await _fx(str(cmd.fx), float(cmd.get("dur", 0.6)))
	if cmd.has("loc"):
		dlg.close()
		await go_to(str(cmd.loc), str(cmd.get("trans", "fade")))
	if cmd.has("input_name"):
		dlg.close()
		var box: Node = DialogueManager.ui
		box.ask_name()
		GameManager.player_name = await box.name_entered
	if cmd.has("minigame"):
		dlg.close()
		var res: Dictionary = await MinigameManager.play(str(cmd.minigame), cmd.get("params", {}))
		_store_result(res)
	if cmd.has("card"):
		dlg.close()
		await show_card(str(cmd.card), str(cmd.get("sub", "")))
	if cmd.has("ending"):
		dlg.close()
		await play_ending(str(cmd.ending))
	if cmd.has("free_mode"):
		GameManager.free_mode = true
		GameManager.set_objective("")
	if cmd.has("travel_unlock"):
		GameManager.set_flag("travel_unlocked", true)
	if cmd.has("wait"):
		await get_tree().create_timer(float(cmd.wait)).timeout
	hud.refresh()


func _store_result(res: Dictionary) -> void:
	GameManager.set_flag("last_score", int(res.get("score", 0)))
	GameManager.set_flag("last_mistakes", int(res.get("mistakes", 0)))
	GameManager.set_flag("last_skipped", bool(res.get("skipped", false)))


func _fx(name: String, dur: float) -> void:
	match name:
		"glitch":
			SceneManager.glitch(dur, 0.9)
			await get_tree().create_timer(dur * 0.6).timeout
		"shake":
			location.shake(1.0)
			AudioManager.play_sfx("rumble")
		"flash":
			await SceneManager.flash(Color(1, 1, 1), dur)
		"flash_violet":
			await SceneManager.flash(Color(0.75, 0.6, 1.0), dur)
		"fade_out":
			await SceneManager.fade_out(dur)
		"fade_out_white":
			await SceneManager.fade_out(dur, Color(1, 1, 1))
		"fade_in":
			await SceneManager.fade_in(dur)


func show_card(top: String, main: String) -> void:
	var c := preload("res://scripts/ui/chapter_card.gd").new()
	c.top_text = top
	c.main_text = main
	menu_layer.add_child(c)
	await c.done


func chapter_card(c: int) -> void:
	await show_card("Глава %d" % c, GameManager.chapter_title(c))


func play_ending(id: String) -> void:
	GameManager.ending = id
	SaveManager.record_ending(id)
	InventoryManager.unlock_memory("ending_" + id)
	var cr := preload("res://scripts/ui/credits.gd").new()
	cr.ending_id = id
	menu_layer.add_child(cr)
	await cr.done
	GameManager.free_mode = true
	GameManager.set_objective("")
	InventoryManager.unlock("decor", "lantern")
	for o in InventoryManager.db.get("outfits", {}).keys():
		InventoryManager.unlock("outfits", o)
	for w in InventoryManager.db.get("walls", {}).keys():
		InventoryManager.unlock("walls", w)
	await go_to("room" if id != "new_dawn" else "city", "fade")
	if id == "new_dawn":
		location.event("dawn")
	TimeManager.set_phase("morning" if id != "our_world" else "night")
	var intro := "free_intro_" + id
	if DialogueManager.has_script(intro):
		await DialogueManager.run(intro)
	GameManager.request_autosave()


func say_bubble(text: String) -> void:
	var head = get_viewport().get_canvas_transform() * kira.get_head_global()
	bubble.say(text, head)


# ====================================================== ВЗАИМОДЕЙСТВИЕ
func _on_hotspot(id: String) -> void:
	if busy or _modal != null or MinigameManager.is_active():
		return
	_reset_idle()
	busy = true
	_set_world_interactive(false)
	var loc := GameManager.location
	var h: Dictionary = location.hotspot(id)
	if location.data.get("walk", false):
		var target: float = clampf(location.hotspot_center(id).x + (140.0 if location.hotspot_center(id).x < 960 else -140.0), 200.0, 1720.0)
		await kira.walk_to(target)
	var key := "%s.%s" % [loc, id]
	var step: Dictionary = director.current()
	var script_id := ""
	if step.get("on", {}).has(key):
		script_id = str(step.on[key])
		if script_id.ends_with("!") :
			script_id = script_id.trim_suffix("!")
	if script_id == "":
		script_id = resolve("%s_%s" % [loc, id])
	if script_id != "":
		await DialogueManager.run(script_id)
	GameManager.set_flag("looked_%s_%s" % [loc, id], true)
	var action: String = h.get("action", "")
	if action != "" and GameManager.location == loc:
		await _hotspot_action(action)
	busy = false
	kira.set_talking(false)
	await director.check()
	_set_world_interactive(not busy)


func _hotspot_action(action: String) -> void:
	match action:
		"wardrobe":
			if not GameManager.get_flag("block_wardrobe"):
				await _await_modal(preload("res://scripts/ui/wardrobe_panel.gd").new())
		"music":
			await _music_menu()
		"lighting":
			var order := ["ceiling", "lamp", "garland", "moon"]
			var i := (order.find(InventoryManager.lighting) + 1) % order.size()
			InventoryManager.set_lighting(order[i])
			AudioManager.play_sfx("switch")
			hud.toast("Освещение: " + str(InventoryManager.info("lighting", order[i]).get("name", "")), "sparkle")
		"door":
			if GameManager.free_mode or GameManager.get_flag("travel_unlocked"):
				await _travel_menu()
		"archive":
			pass


func _on_kira_clicked(part: String) -> void:
	if busy or _modal != null:
		return
	_reset_idle()
	if part == "clip":
		_clip_clicks += 1
		if _clip_clicks >= 5 and DialogueManager.has_script("kira_clip_secret") and not GameManager.seen_script("kira_clip_secret"):
			busy = true
			await run_script("kira_clip_secret")
			busy = false
			_set_world_interactive(true)
			return
		kira.set_emotion("pout")
		say_bubble(["Эй! Заколки не трогать.", "Это моя любимая заколка, между прочим.", "Ещё раз — и я обижусь. Понарошку."][_clip_clicks % 3])
		return
	if part == "head" and GameManager.location == "room":
		kira.set_emotion(["shy", "happy", "warm"].pick_random())
		say_bubble(_pick_idle("pat"))
		return
	if GameManager.location == "room":
		await activity("talk")
	else:
		kira.set_emotion(["happy", "thoughtful", "sly"].pick_random())
		say_bubble(_pick_idle("explore"))


func _on_hud_action(a: String) -> void:
	if busy or _modal != null:
		return
	_reset_idle()
	match a:
		"menu":
			open_pause()
		"journal":
			open_modal(preload("res://scripts/ui/journal_panel.gd").new())
		"wardrobe":
			await _await_modal(preload("res://scripts/ui/wardrobe_panel.gd").new())
		"activities":
			await _activities_menu()
		"travel":
			await _travel_menu()


# ========================================================== ЗАНЯТИЯ
func activity_allowed(id: String) -> bool:
	var step: Dictionary = director.current()
	if step.has("activities"):
		return id in step.activities or id in ["talk"]
	return true


func _activities_menu() -> void:
	var p = preload("res://scripts/ui/options_panel.gd").new()
	p.heading = "Чем займёмся?"
	var dark := TimeManager.is_dark()
	var mem_count := _puzzle_memories().size()
	p.options = [
		{"id": "stars", "title": "Смотреть на звёзды", "desc": "Соединять звёзды в созвездия.", "icon": "star",
			"note": "" if dark else "Лучше вечером или ночью", "enabled": activity_allowed("stars")},
		{"id": "cooking", "title": "Готовить вместе", "desc": "Блинчики, какао или онигири.", "icon": "cup", "enabled": activity_allowed("cooking")},
		{"id": "memory", "title": "Собирать воспоминания", "desc": "Сложить картину из фрагментов.", "icon": "puzzle",
			"note": "Пока нечего собирать" if mem_count == 0 else "", "enabled": activity_allowed("memory") and mem_count > 0},
		{"id": "decorate", "title": "Украшать комнату", "desc": "Расставить декор по местам.", "icon": "sparkle", "enabled": activity_allowed("decorate")},
		{"id": "talk", "title": "Поговорить", "desc": "Просто поболтать с Кирой.", "icon": "chat"},
		{"id": "rest", "title": "Отдохнуть", "desc": "Посидеть в тишине, пока время течёт.", "icon": "moon", "enabled": activity_allowed("rest")},
	]
	var picked := [""]
	p.picked.connect(func(id): picked[0] = id)
	await _await_modal(p)
	if picked[0] != "":
		await activity(picked[0])


func _puzzle_memories() -> Array:
	var out := []
	for id in InventoryManager.memories:
		if id in ["first_meeting", "first_stars", "pancakes", "sea_photo", "childs_drawing", "rain_cafe", "rooftop_promise", "author_desk", "ticket", "kite"]:
			out.append(id)
	return out


func activity(id: String) -> void:
	if busy:
		return
	busy = true
	_set_world_interactive(false)
	match id:
		"stars":
			if not TimeManager.is_dark():
				await DialogueManager.run(resolve("act_stars_day"))
				if not GameManager.get_flag("wait_evening"):
					_end_activity()
					return
				GameManager.set_flag("wait_evening", false)
				await SceneManager.fade_out(0.8)
				TimeManager.set_phase("evening")
				await SceneManager.fade_in(0.8)
			await _run_opt("act_stars_pre")
			var consts := _pick_constellations()
			var res: Dictionary = await MinigameManager.play("stars", {"ids": consts})
			_store_result(res)
			GameManager.inc_flag("did_stars")
			if "cat" in consts:
				GameManager.set_flag("drew_cat", true)
			await _run_opt("act_stars_post")
			if InventoryManager.unlock_memory("first_stars"):
				notify_unlock("memory", "first_stars")
			if TimeManager.phase == "evening":
				TimeManager.advance()
		"cooking":
			await _run_opt("act_cooking_pre")
			var recipes := ["pancakes", "cocoa", "onigiri"]
			var r: String = recipes[_recipe_i % recipes.size()] if GameManager.count("did_cooking") > 0 else "pancakes"
			_recipe_i += 1
			var res2: Dictionary = await MinigameManager.play("cooking", {"recipe": r})
			_store_result(res2)
			GameManager.set_flag("last_recipe", r)
			GameManager.inc_flag("did_cooking")
			await _run_opt("act_cooking_post")
			if InventoryManager.unlock_memory("pancakes"):
				notify_unlock("memory", "pancakes")
			if InventoryManager.unlock("outfits", "pajama"):
				notify_unlock("outfit", "pajama")
			TimeManager.advance()
		"memory":
			var list := _puzzle_memories()
			if list.size() > 0:
				var p = preload("res://scripts/ui/options_panel.gd").new()
				p.heading = "Какое воспоминание собрать?"
				p.columns = 2
				for m in list:
					p.options.append({"id": m, "title": InventoryManager.info("memories", m).title, "desc": "", "icon": "puzzle"})
				var picked := [""]
				if MinigameManager.autoplay:
					picked[0] = list[0]
				else:
					p.picked.connect(func(x): picked[0] = x)
					await _await_modal(p)
				if picked[0] != "":
					await _run_opt("act_memory_pre")
					var res3: Dictionary = await MinigameManager.play("memory", {"memory": picked[0], "grid": 3 + randi() % 2})
					_store_result(res3)
					GameManager.inc_flag("did_memory")
					await _run_opt("act_memory_post")
		"decorate":
			await _run_opt("act_decorate_pre")
			var res4: Dictionary = await MinigameManager.play("decorate", {})
			_store_result(res4)
			GameManager.inc_flag("did_decorate")
			await _run_opt("act_decorate_post")
		"talk":
			await DialogueManager.run(_pick_talk())
		"rest":
			if TimeManager.phase == "night":
				await _run_opt("act_sleep")
				await SceneManager.fade_out(1.2)
				TimeManager.advance()
				await get_tree().create_timer(0.6).timeout
				await SceneManager.fade_in(1.2)
				await _run_opt("act_wake")
			else:
				await _run_opt("act_rest")
				await SceneManager.fade_out(0.8)
				TimeManager.advance()
				await SceneManager.fade_in(0.8)
	_end_activity()


func _end_activity() -> void:
	busy = false
	kira.set_talking(false)
	hud.refresh()
	GameManager.request_autosave()
	await director.check()
	_set_world_interactive(not busy)


func _run_opt(base: String) -> void:
	var id := resolve(base)
	if id != "":
		await DialogueManager.run(id)


func _pick_constellations() -> Array:
	if GameManager.count("did_stars") == 0:
		return ["cat", "lyra"]
	var pool := ["lyra", "heart", "whale", "train", "key", "lost_star", "cat"]
	_const_i += 1
	var a: String = pool[(_const_i * 2) % pool.size()]
	var b: String = pool[(_const_i * 2 + 1) % pool.size()]
	return [a, b]


func _pick_talk() -> String:
	var fresh := []
	var old := []
	for t in talk_pool:
		if not DialogueManager.has_script(str(t.id)):
			continue
		if not GameManager.eval_cond(str(t.get("if", ""))):
			continue
		if GameManager.seen_script(str(t.id)):
			if t.get("repeat", true):
				old.append(str(t.id))
		else:
			fresh.append(str(t.id))
	if fresh.size() > 0:
		return fresh[0]
	if old.size() > 0:
		return old.pick_random()
	return "talk_fallback"


func _music_menu() -> void:
	var p = preload("res://scripts/ui/options_panel.gd").new()
	p.heading = "Пластинки Киры"
	p.options = [
		{"id": "", "title": "По настроению", "desc": "Музыка меняется вместе со временем суток.", "icon": "moon"},
		{"id": "room", "title": "«Тёплый вечер»", "desc": "Лоу-фай для разговоров.", "icon": "music"},
		{"id": "night", "title": "«Колыбельная звёзд»", "desc": "Тихо и мечтательно.", "icon": "music"},
		{"id": "city", "title": "«Полночная чашка»", "desc": "Джаз под дождь.", "icon": "music", "enabled": GameManager.get_flag("visited_city")},
		{"id": "library", "title": "«Шелест страниц»", "desc": "Для загадок.", "icon": "music", "enabled": GameManager.get_flag("visited_library")},
		{"id": "train", "title": "«Последний поезд»", "desc": "Стук колёс среди звёзд.", "icon": "music", "enabled": GameManager.get_flag("visited_train")},
		{"id": "ending", "title": "«Тема Киры»", "desc": "Её мелодия.", "icon": "music", "enabled": SaveManager.global.finished},
	]
	var picked := ["-"]
	p.picked.connect(func(x): picked[0] = x)
	await _await_modal(p)
	if picked[0] == "-":
		return
	GameManager.set_flag("music_override", picked[0])
	if location and location.has_method("tick"):
		location.set("vinyl_spinning", picked[0] != "")
	_update_music()
	say_bubble("О, хороший выбор." if picked[0] != "" else "Пусть музыка сама решает.")


func _travel_menu() -> void:
	var p = preload("res://scripts/ui/options_panel.gd").new()
	p.heading = "Куда отправимся?"
	var step: Dictionary = director.current()
	var allowed: Array = step.get("travel", [])
	for id in ["room", "city", "rooftop", "library", "train"]:
		if id == GameManager.location:
			continue
		var ok: bool = GameManager.get_flag("visited_" + id) and (GameManager.free_mode or id in allowed)
		p.options.append({"id": id, "title": GameManager.LOCATION_NAMES[id], "icon": "map", "enabled": ok,
			"note": "" if ok else ("Ещё не открыто" if not GameManager.get_flag("visited_" + id) else "Сейчас не время")})
	var picked := [""]
	p.picked.connect(func(x): picked[0] = x)
	await _await_modal(p)
	if picked[0] != "":
		busy = true
		_set_world_interactive(false)
		await go_to(picked[0])
		var arrive := resolve("arrive_" + picked[0])
		if arrive != "":
			await DialogueManager.run(arrive)
		busy = false
		await director.check()
		_set_world_interactive(true)


# ============================================================ МЕНЮ
func open_modal(m: Control) -> void:
	if _modal != null and is_instance_valid(_modal):
		return
	_modal = m
	_set_world_interactive(false)
	m.closed.connect(func():
		_modal = null
		get_tree().paused = false
		if not busy:
			_set_world_interactive(true))
	menu_layer.add_child(m)


func _await_modal(m: Control) -> void:
	open_modal(m)
	await m.closed


func open_pause() -> void:
	var thumb := SaveManager.capture_thumbnail()
	var p = preload("res://scripts/ui/pause_menu.gd").new()
	open_modal(p)
	get_tree().paused = true
	p.request.connect(func(a): _pause_action(a, p, thumb))


func _pause_action(a: String, pause: Control, thumb: Image) -> void:
	match a:
		"save":
			var s = preload("res://scripts/ui/save_load_panel.gd").new()
			s.mode = "save"
			s.thumbnail = thumb
			pause.add_child(s)
		"load":
			var l = preload("res://scripts/ui/save_load_panel.gd").new()
			l.mode = "load"
			l.loaded.connect(_load_slot)
			pause.add_child(l)
		"settings":
			pause.add_child(preload("res://scripts/ui/settings_panel.gd").new())
		"journal":
			pause.add_child(preload("res://scripts/ui/journal_panel.gd").new())
		"history":
			pause.add_child(preload("res://scripts/ui/history_panel.gd").new())
		"main_menu":
			if not busy:
				SaveManager.save_game("auto", thumb)
			get_tree().paused = false
			GameManager.in_game = false
			DialogueManager.ui = null
			DialogueManager.handler = null
			SceneManager.change_scene("res://scenes/main_menu.tscn")
		"quit":
			if not busy:
				SaveManager.save_game("auto", thumb)
			SaveManager.save_global()
			get_tree().quit()


func _load_slot(slot: String) -> void:
	if SaveManager.load_game(slot):
		get_tree().paused = false
		DialogueManager.ui = null
		DialogueManager.handler = null
		DialogueManager.clear_history()
		SceneManager.change_scene("res://scenes/game.tscn")
	else:
		hud.toast("Не удалось загрузить: " + SaveManager.last_error, "gear")


# ========================================================== ЖИВОСТЬ
func _reset_idle() -> void:
	_idle_timer = randf_range(70.0, 110.0)


func _pick_idle(kind: String) -> String:
	var lines: Array = []
	for l in idle_data.get(kind, []):
		if l is String:
			lines.append(l)
		elif l is Dictionary and GameManager.eval_cond(str(l.get("if", ""))):
			lines.append(str(l.t))
	if lines.is_empty():
		return "…"
	return DialogueManager._fmt(lines.pick_random())


func _process(delta: float) -> void:
	if location == null:
		return
	location.hints_visible = Input.is_action_pressed("show_hotspots") and location.input_enabled
	if busy or _modal != null or MinigameManager.is_active() or DialogueManager.running:
		return
	_idle_timer -= delta
	_event_timer -= delta
	if _idle_timer <= 0.0:
		_reset_idle()
		var kind := "room" if GameManager.location == "room" else "explore"
		var e: String = ["neutral", "thoughtful", "happy", "sly"].pick_random()
		if TimeManager.phase == "night" and randf() < 0.4:
			e = "sleepy"
		kira.set_emotion(e)
		say_bubble(_pick_idle(kind))
	if _event_timer <= 0.0 and GameManager.location == "room":
		_event_timer = randf_range(160.0, 260.0)
		_random_event()


func _random_event() -> void:
	var list: Array = []
	for ev in idle_data.get("events", []):
		if GameManager.eval_cond(str(ev.get("if", ""))) and DialogueManager.has_script(str(ev.script)):
			list.append(ev)
	if list.is_empty():
		return
	var ev: Dictionary = list.pick_random()
	busy = true
	_set_world_interactive(false)
	await DialogueManager.run(str(ev.script))
	busy = false
	_set_world_interactive(true)


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		if dlg.handle_world_click():
			get_viewport().set_input_as_handled()
			return
	if event.is_action_pressed("pause") and _modal == null and not MinigameManager.is_active():
		open_pause()
		get_viewport().set_input_as_handled()
	elif event.is_action_pressed("history") and _modal == null and not MinigameManager.is_active():
		open_modal(preload("res://scripts/ui/history_panel.gd").new())
		get_viewport().set_input_as_handled()
	elif event.is_action_pressed("quick_save") and not busy:
		if SaveManager.save_game("quick"):
			hud.toast("Быстрое сохранение", "save")
	elif event.is_action_pressed("quick_load") and _modal == null:
		if FileAccess.file_exists(SaveManager.slot_path("quick")):
			_load_slot("quick")
	elif event is InputEventMouseMotion or event is InputEventKey:
		if event is InputEventKey:
			_reset_idle()
