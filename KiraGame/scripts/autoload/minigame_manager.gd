extends Node
## Запуск мини-игр поверх текущей сцены и получение результата.

signal started(id: String)
signal ended(id: String, result: Dictionary)

const GAMES := {
	"stars": "res://scripts/minigames/stars_game.gd",
	"cooking": "res://scripts/minigames/cooking_game.gd",
	"memory": "res://scripts/minigames/memory_puzzle.gd",
	"decorate": "res://scripts/minigames/decorate_game.gd",
	"lock": "res://scripts/minigames/symbol_lock.gd",
}
const NAMES := {
	"stars": "Смотреть на звёзды",
	"cooking": "Готовить вместе",
	"memory": "Собирать воспоминания",
	"decorate": "Украшать комнату",
	"lock": "Замок архива",
}

var active: Node = null
## Для автотестов: мини-игры решаются автоматически
var autoplay := false
var active_id := ""
var _layer: CanvasLayer


func _ready() -> void:
	_layer = CanvasLayer.new()
	_layer.layer = 40
	add_child(_layer)


func is_active() -> bool:
	return active != null


## Запускает мини-игру и ждёт её окончания. Возвращает словарь результата.
func play(id: String, params := {}) -> Dictionary:
	if not GAMES.has(id) or active != null:
		return {}
	var game: Control = load(GAMES[id]).new()
	game.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	game.set("params", params)
	active = game
	active_id = id
	started.emit(id)
	_layer.add_child(game)
	if autoplay:
		game.call_deferred("solve_all")
	var result: Dictionary = await game.finished
	active = null
	active_id = ""
	game.queue_free()
	ended.emit(id, result)
	return result
