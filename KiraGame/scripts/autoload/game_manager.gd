extends Node
## Глобальное состояние прохождения: глава, флаги, цель, имя игрока,
## текущая локация, свободный режим. Также вычисляет условия из данных (JSON).

signal flag_changed(key: String, value)
signal objective_changed(text: String)
signal chapter_changed(chapter: int)
signal autosave_requested
signal state_loaded

const CHAPTERS := {
	1: "Привет, незнакомец",
	2: "Дверь, которой не было",
	3: "Забытые звёзды",
	4: "Последний поезд",
	5: "По ту сторону экрана",
}
const LOCATION_NAMES := {
	"room": "Комната Киры",
	"city": "Ночной город",
	"rooftop": "Крыша под звёздами",
	"library": "Заброшенная библиотека",
	"train": "Поезд между мирами",
	"edge": "По ту сторону экрана",
}

var player_name := ""
var chapter := 1
var step := 0
var flags := {}
var seen := {}
var location := "room"
var free_mode := false
var ending := ""
var objective := ""
var playtime := 0.0
var in_game := false
## Загружаемое состояние, которое применит игровой мир после загрузки сцены
var pending_load := {}


func _process(delta: float) -> void:
	if in_game and not get_tree().paused:
		playtime += delta


func new_game() -> void:
	player_name = ""
	chapter = 1
	step = 0
	flags = {}
	seen = {}
	location = "room"
	free_mode = false
	ending = ""
	objective = ""
	playtime = 0.0
	pending_load = {}
	TimeManager.reset()
	RelationshipManager.reset()
	InventoryManager.reset()


# ----------------------------------------------------------------- флаги
func get_flag(key: String, default = false):
	return flags.get(key, default)


func set_flag(key: String, value) -> void:
	flags[key] = value
	flag_changed.emit(key, value)


func inc_flag(key: String, n := 1) -> int:
	var v := int(flags.get(key, 0)) + n
	set_flag(key, v)
	return v


func set_objective(text: String) -> void:
	objective = text
	objective_changed.emit(text)


func set_chapter(c: int) -> void:
	chapter = c
	step = 0
	chapter_changed.emit(c)


func chapter_title(c := -1) -> String:
	return CHAPTERS.get(chapter if c < 0 else c, "")


func display_name() -> String:
	return player_name if player_name != "" else "ты"


func mark_seen(script_id: String) -> void:
	seen[script_id] = int(seen.get(script_id, 0)) + 1


func request_autosave() -> void:
	autosave_requested.emit()


# ------------------------------------------------- условия из JSON-данных
# Доступно в выражениях: chapter, step, trust, understanding, spirit, time, day,
# free, loc, ending и функции flag(), count(), has(), mem(), remembered(), seen(), placed(), outfit_is()
func eval_cond(expr: String) -> bool:
	if expr.strip_edges() == "":
		return true
	var e := Expression.new()
	var names := PackedStringArray(["chapter", "step", "trust", "understanding", "spirit", "time", "day", "free", "loc", "ending"])
	var err := e.parse(expr, names)
	if err != OK:
		push_warning("Ошибка условия '%s': %s" % [expr, e.get_error_text()])
		return false
	var r = e.execute([chapter, step, RelationshipManager.get_stat("trust"), RelationshipManager.get_stat("understanding"),
		RelationshipManager.get_stat("spirit"), TimeManager.phase, TimeManager.day, free_mode, location, ending], self, false)
	if e.has_execute_failed():
		push_warning("Не удалось вычислить условие '%s'" % expr)
		return false
	return truthy(r)


## Истинность значения в стиле GDScript для любых типов.
static func truthy(v) -> bool:
	match typeof(v):
		TYPE_NIL: return false
		TYPE_BOOL: return v
		TYPE_INT, TYPE_FLOAT: return v != 0
		TYPE_STRING, TYPE_STRING_NAME: return str(v) != ""
		TYPE_ARRAY, TYPE_DICTIONARY: return not v.is_empty()
	return true


func flag(key: String):
	return flags.get(key, false)


func count(key: String) -> int:
	return int(flags.get(key, 0))


func has(item: String) -> bool:
	return InventoryManager.has_item(item)


func mem(id: String) -> bool:
	return InventoryManager.has_memory(id)


func remembered(key: String) -> bool:
	return RelationshipManager.has_memory(key)


func name_is_default() -> bool:
	return player_name == "" or player_name == "Незнакомец"


func seen_script(id: String) -> bool:
	return seen.has(id)


func placed() -> int:
	return InventoryManager.placed_count()


func outfit_is(id: String) -> bool:
	return InventoryManager.outfit == id


# ------------------------------------------------------------ состояние
func get_state() -> Dictionary:
	return {
		"player_name": player_name, "chapter": chapter, "step": step, "flags": flags.duplicate(true),
		"seen": seen.duplicate(), "location": location, "free_mode": free_mode, "ending": ending,
		"objective": objective, "playtime": playtime,
	}


func set_state(d: Dictionary) -> void:
	player_name = str(d.get("player_name", "")).left(24)
	chapter = clampi(int(d.get("chapter", 1)), 1, 5)
	step = maxi(int(d.get("step", 0)), 0)
	flags = d.get("flags", {}).duplicate(true) if d.get("flags") is Dictionary else {}
	seen = d.get("seen", {}).duplicate() if d.get("seen") is Dictionary else {}
	location = str(d.get("location", "room"))
	if not LOCATION_NAMES.has(location):
		location = "room"
	free_mode = bool(d.get("free_mode", false))
	ending = str(d.get("ending", ""))
	objective = str(d.get("objective", ""))
	playtime = float(d.get("playtime", 0.0))
	state_loaded.emit()
