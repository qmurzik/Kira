extends Node
## Сохранения: автосохранение, быстрые и ручные слоты, защита от повреждённых файлов.
## Формат файла: {"format", "version", "checksum", "payload"}, где payload — JSON-строка,
## а checksum — её MD5. Запись атомарная (через .tmp), предыдущая версия хранится в .bak.
## Также здесь хранятся «глобальные» данные: прочитанные реплики, галерея, увиденные концовки.

signal saved(slot: String)
signal load_failed(slot: String, reason: String)

const DIR := "user://saves/"
const GLOBAL_PATH := "user://global.json"
const VERSION := 1
const MANUAL_SLOTS := ["1", "2", "3", "4", "5", "6"]

var global := {"read": {}, "gallery": [], "endings": [], "finished": false}
var last_error := ""
var _global_dirty := false
var _global_timer := 0.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	DirAccess.make_dir_recursive_absolute(DIR)
	_load_global()
	DialogueManager.read_lines = global.read
	InventoryManager.memory_unlocked.connect(_on_memory)
	GameManager.autosave_requested.connect(func(): save_game("auto"))


func _process(delta: float) -> void:
	if _global_dirty:
		_global_timer -= delta
		if _global_timer <= 0.0:
			save_global()


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_EXIT_TREE:
		save_global()


func _on_memory(id: String) -> void:
	if not id in global.gallery:
		global.gallery.append(id)
	mark_global_dirty()


func mark_global_dirty() -> void:
	_global_dirty = true
	_global_timer = 2.0


func record_ending(id: String) -> void:
	if not id in global.endings:
		global.endings.append(id)
	global.finished = true
	save_global()


# ------------------------------------------------------------- запись
func slot_path(slot: String) -> String:
	return DIR + "slot_%s.json" % slot


func thumb_path(slot: String) -> String:
	return DIR + "slot_%s.png" % slot


func collect() -> Dictionary:
	return {
		"meta": {
			"chapter": GameManager.chapter,
			"chapter_title": GameManager.chapter_title(),
			"location": GameManager.LOCATION_NAMES.get(GameManager.location, ""),
			"phase": TimeManager.phase_name(),
			"day": TimeManager.day,
			"playtime": GameManager.playtime,
			"timestamp": Time.get_unix_time_from_system(),
			"date": Time.get_datetime_string_from_system(false, true),
			"player": GameManager.player_name,
			"free_mode": GameManager.free_mode,
		},
		"game": GameManager.get_state(),
		"rel": RelationshipManager.get_state(),
		"inv": InventoryManager.get_state(),
		"time": TimeManager.get_state(),
	}


func save_game(slot: String, thumbnail: Image = null) -> bool:
	if not GameManager.in_game:
		return false
	var payload := JSON.stringify(collect())
	var file := {"format": "kira-save", "version": VERSION, "checksum": payload.md5_text(), "payload": payload}
	var ok := _atomic_write(slot_path(slot), JSON.stringify(file, "\t"))
	if ok:
		if thumbnail == null:
			thumbnail = capture_thumbnail()
		if thumbnail:
			thumbnail.save_png(thumb_path(slot))
		saved.emit(slot)
	save_global()
	return ok


func capture_thumbnail() -> Image:
	if DisplayServer.get_name() == "headless":
		return null
	var tex := get_viewport().get_texture()
	if tex == null:
		return null
	var img := tex.get_image()
	if img == null or img.is_empty():
		return null
	img.resize(384, 216, Image.INTERPOLATE_BILINEAR)
	return img


func _atomic_write(path: String, text: String) -> bool:
	var tmp := path + ".tmp"
	var f := FileAccess.open(tmp, FileAccess.WRITE)
	if f == null:
		last_error = "Не удалось открыть файл для записи"
		return false
	f.store_string(text)
	f.close()
	# Проверяем, что записанное читается
	var check := FileAccess.get_file_as_string(tmp)
	if check != text:
		last_error = "Ошибка проверки записи"
		return false
	if FileAccess.file_exists(path):
		if FileAccess.file_exists(path + ".bak"):
			DirAccess.remove_absolute(path + ".bak")
		DirAccess.rename_absolute(path, path + ".bak")
	return DirAccess.rename_absolute(tmp, path) == OK


# ------------------------------------------------------------- чтение
## Прочитать и проверить файл. Возвращает {} при повреждении.
func read_slot(slot: String) -> Dictionary:
	var d := _read_verified(slot_path(slot))
	if d.is_empty() and FileAccess.file_exists(slot_path(slot) + ".bak"):
		d = _read_verified(slot_path(slot) + ".bak")
		if not d.is_empty():
			last_error = "Основной файл повреждён — восстановлена предыдущая версия."
	return d


func _read_verified(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		last_error = "Слот пуст"
		return {}
	var text := FileAccess.get_file_as_string(path)
	var file = JSON.parse_string(text)
	if not file is Dictionary or file.get("format", "") != "kira-save":
		last_error = "Файл сохранения повреждён"
		return {}
	var payload: String = str(file.get("payload", ""))
	if payload.md5_text() != str(file.get("checksum", "")):
		last_error = "Контрольная сумма не совпадает — файл повреждён"
		return {}
	var data = JSON.parse_string(payload)
	if not data is Dictionary or not data.has("game") or not data.has("meta"):
		last_error = "Неверная структура сохранения"
		return {}
	return data


func slot_info(slot: String) -> Dictionary:
	if not FileAccess.file_exists(slot_path(slot)):
		return {}
	var d := read_slot(slot)
	if d.is_empty():
		return {"corrupted": true}
	return d.meta


func has_any_save() -> bool:
	return latest_slot() != ""


func latest_slot() -> String:
	var best := ""
	var best_t := -1.0
	for slot in ["auto", "quick"] + MANUAL_SLOTS:
		if not FileAccess.file_exists(slot_path(slot)):
			continue
		var info := slot_info(slot)
		if info.is_empty() or info.get("corrupted", false):
			continue
		var t := float(info.get("timestamp", 0))
		if t > best_t:
			best_t = t
			best = slot
	return best


func delete_slot(slot: String) -> void:
	for p in [slot_path(slot), slot_path(slot) + ".bak", thumb_path(slot)]:
		if FileAccess.file_exists(p):
			DirAccess.remove_absolute(p)


## Применить сохранение ко всем менеджерам. Возвращает false, если файл повреждён.
func load_game(slot: String) -> bool:
	var d := read_slot(slot)
	if d.is_empty():
		load_failed.emit(slot, last_error)
		return false
	apply(d)
	return true


func apply(d: Dictionary) -> void:
	TimeManager.set_state(d.get("time", {}))
	RelationshipManager.set_state(d.get("rel", {}))
	InventoryManager.set_state(d.get("inv", {}))
	GameManager.set_state(d.get("game", {}))
	GameManager.pending_load = d


# ---------------------------------------------------- глобальные данные
func _load_global() -> void:
	if not FileAccess.file_exists(GLOBAL_PATH):
		return
	var d = JSON.parse_string(FileAccess.get_file_as_string(GLOBAL_PATH))
	if not d is Dictionary:
		push_warning("global.json повреждён — используются значения по умолчанию")
		return
	if d.get("read") is Dictionary:
		global.read = d.read
	if d.get("gallery") is Array:
		global.gallery = d.gallery
	if d.get("endings") is Array:
		global.endings = d.endings
	global.finished = bool(d.get("finished", false))


func save_global() -> void:
	_global_dirty = false
	global.read = DialogueManager.read_lines
	_atomic_write(GLOBAL_PATH, JSON.stringify(global))
