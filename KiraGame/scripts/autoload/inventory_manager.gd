extends Node
## Кастомизация и коллекции: наряды, обои, цветовые темы, освещение,
## декор и его расстановка, воспоминания, сюжетные предметы.

signal outfit_changed(id: String)
signal room_changed
signal theme_changed(id: String)
signal memory_unlocked(id: String)
signal item_unlocked(kind: String, id: String)
signal items_changed

const ITEMS_PATH := "res://data/items.json"

## Точки для декора в комнате: id -> {pos, type, name}
const DECOR_SLOTS := {
	"ceiling": {"pos": Vector2(960, 40), "type": "ceiling", "name": "Под потолком"},
	"wall_right": {"pos": Vector2(1700, 250), "type": "wall", "name": "Стена над столом"},
	"wall_top": {"pos": Vector2(1225, 150), "type": "wall", "name": "Над постером"},
	"sill": {"pos": Vector2(660, 486), "type": "surface", "name": "Подоконник"},
	"shelf_top": {"pos": Vector2(1420, 142), "type": "surface", "name": "Верх книжного шкафа"},
	"bed": {"pos": Vector2(600, 700), "type": "bed", "name": "Кровать"},
	"floor_a": {"pos": Vector2(820, 950), "type": "floor", "name": "Ковёр"},
	"floor_b": {"pos": Vector2(1500, 930), "type": "floor", "name": "Пол у шкафа"},
}

var db := {}
var outfit := "casual"
var outfits: Array = []
var walls: Array = []
var wall := "lavender"
var ui_theme := "lavender"
var lighting := "ceiling"
var decor_owned: Array = []
var placements := {}
var memories: Array = []
var key_items := {}


func _ready() -> void:
	var f := FileAccess.open(ITEMS_PATH, FileAccess.READ)
	if f:
		var parsed = JSON.parse_string(f.get_as_text())
		if parsed is Dictionary:
			db = parsed
	reset()


func reset() -> void:
	outfit = "casual"
	outfits = _default_unlocked("outfits")
	walls = _default_unlocked("walls")
	decor_owned = _default_unlocked("decor")
	wall = "lavender"
	ui_theme = "lavender"
	lighting = "ceiling"
	placements = {"ceiling": "garland"}
	memories = []
	key_items = {}


func _default_unlocked(kind: String) -> Array:
	var out := []
	for id in db.get(kind, {}).keys():
		if db[kind][id].get("unlocked", false):
			out.append(id)
	return out


func info(kind: String, id: String) -> Dictionary:
	return db.get(kind, {}).get(id, {})


# -------------------------------------------------------------- наряды
func set_outfit(id: String) -> void:
	if not id in outfits:
		return
	outfit = id
	outfit_changed.emit(id)


func unlock(kind: String, id: String) -> bool:
	var arr: Array
	match kind:
		"outfits": arr = outfits
		"walls": arr = walls
		"decor": arr = decor_owned
		_: return false
	if id in arr or not db.get(kind, {}).has(id):
		return false
	arr.append(id)
	item_unlocked.emit(kind, id)
	return true


# ------------------------------------------------------------- комната
func set_wall(id: String) -> void:
	if id in walls:
		wall = id
		room_changed.emit()


func set_lighting(id: String) -> void:
	lighting = id
	room_changed.emit()


func set_theme(id: String) -> void:
	if db.get("themes", {}).has(id):
		ui_theme = id
		theme_changed.emit(id)


func place(slot: String, item: String) -> void:
	# Предмет может стоять только в одном месте
	for s in placements.keys():
		if placements[s] == item:
			placements.erase(s)
	if item == "":
		placements.erase(slot)
	else:
		placements[slot] = item
	room_changed.emit()


func can_place(slot: String, item: String) -> bool:
	var t: String = DECOR_SLOTS[slot].type
	return t in info("decor", item).get("types", [])


func placed_count() -> int:
	return placements.size()


# ----------------------------------------------------------- воспоминания
func unlock_memory(id: String) -> bool:
	if id in memories or not db.get("memories", {}).has(id):
		return false
	memories.append(id)
	memory_unlocked.emit(id)
	return true


func has_memory(id: String) -> bool:
	return id in memories


# ----------------------------------------------------------- предметы
func give(id: String, n := 1) -> void:
	key_items[id] = int(key_items.get(id, 0)) + n
	if key_items[id] <= 0:
		key_items.erase(id)
	items_changed.emit()


func has_item(id: String) -> bool:
	return int(key_items.get(id, 0)) > 0


func count(id: String) -> int:
	return int(key_items.get(id, 0))


func get_state() -> Dictionary:
	return {
		"outfit": outfit, "outfits": outfits.duplicate(), "walls": walls.duplicate(), "wall": wall,
		"ui_theme": ui_theme, "lighting": lighting, "decor_owned": decor_owned.duplicate(),
		"placements": placements.duplicate(), "memories": memories.duplicate(), "key_items": key_items.duplicate(),
	}


func set_state(d: Dictionary) -> void:
	reset()
	outfits = _valid_list(d.get("outfits", outfits), "outfits")
	walls = _valid_list(d.get("walls", walls), "walls")
	decor_owned = _valid_list(d.get("decor_owned", decor_owned), "decor")
	memories = _valid_list(d.get("memories", []), "memories")
	outfit = str(d.get("outfit", "casual")) if str(d.get("outfit", "")) in outfits else "casual"
	wall = str(d.get("wall", "lavender")) if str(d.get("wall", "")) in walls else "lavender"
	ui_theme = str(d.get("ui_theme", "lavender")) if db.themes.has(str(d.get("ui_theme", ""))) else "lavender"
	lighting = str(d.get("lighting", "ceiling"))
	placements = {}
	var p: Dictionary = d.get("placements", {})
	for slot in p.keys():
		if DECOR_SLOTS.has(slot) and str(p[slot]) in decor_owned:
			placements[slot] = str(p[slot])
	key_items = {}
	var k: Dictionary = d.get("key_items", {})
	for id in k.keys():
		key_items[id] = int(k[id])
	outfit_changed.emit(outfit)
	room_changed.emit()
	theme_changed.emit(ui_theme)


func _valid_list(src, kind: String) -> Array:
	var out := []
	if src is Array:
		for id in src:
			if db.get(kind, {}).has(str(id)) and not str(id) in out:
				out.append(str(id))
	return out
