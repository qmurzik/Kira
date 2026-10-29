extends RefCounted
## Сюжетный «режиссёр»: ведёт игрока по шагам глав из data/story.json.
## Шаг: {"id", "start": сценарий, "objective": текст, "until": условие, "end": сценарий,
##       "on": {"локация.объект": сценарий}, "travel": [локации], "activities": [занятия]}
## Шаг начинается сценарием start, показывает цель и завершается, когда выполнено условие until.

var chapters := {}
var world: Node
var _running := false


func _init(w: Node) -> void:
	world = w
	var f := FileAccess.open("res://data/story.json", FileAccess.READ)
	if f:
		var d = JSON.parse_string(f.get_as_text())
		if d is Dictionary:
			chapters = d.get("chapters", {})


func steps(c := -1) -> Array:
	return chapters.get(str(GameManager.chapter if c < 0 else c), {}).get("steps", [])


func current() -> Dictionary:
	if GameManager.free_mode:
		return {}
	var s := steps()
	if GameManager.step < s.size():
		return s[GameManager.step]
	return {}


## Подставить счётчики в текст цели: {c:флаг} -> значение флага.
func format_objective(text: String) -> String:
	var re := RegEx.new()
	re.compile("\\{c:([a-z_0-9]+)\\}")
	for m in re.search_all(text):
		text = text.replace(m.get_string(0), str(GameManager.count(m.get_string(1))))
	return text


func refresh_objective() -> void:
	var s := current()
	if s.has("objective"):
		GameManager.set_objective(format_objective(str(s.objective)))


func is_running() -> bool:
	return _running


## Проверить продвижение сюжета; запускает сценарии шагов по мере выполнения условий.
func check() -> void:
	if _running or GameManager.free_mode:
		return
	_running = true
	var guard := 0
	while guard < 50 and not GameManager.free_mode:
		guard += 1
		var list := steps()
		if GameManager.step >= list.size():
			if GameManager.chapter >= 5 or list.is_empty():
				break
			GameManager.set_chapter(GameManager.chapter + 1)
			await world.chapter_card(GameManager.chapter)
			GameManager.request_autosave()
			continue
		var s: Dictionary = list[GameManager.step]
		var key := "%d:%d" % [GameManager.chapter, GameManager.step]
		if GameManager.get_flag("_started", "") != key:
			GameManager.set_flag("_started", key)
			GameManager.set_objective(format_objective(str(s.get("objective", ""))))
			if s.has("start"):
				await world.run_script(str(s.start))
				if GameManager.free_mode:
					break
		if str(s.get("until", "")) == "" or GameManager.eval_cond(str(s.until)):
			if s.has("end"):
				await world.run_script(str(s.end))
			GameManager.step += 1
			GameManager.set_objective("")
			GameManager.request_autosave()
			continue
		refresh_objective()
		break
	_running = false
	world.on_director_idle()
