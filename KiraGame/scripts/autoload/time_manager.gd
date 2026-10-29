extends Node
## Время суток управляется игровым процессом (занятия, сюжет), а не реальными часами.

signal phase_changed(phase: String)

const PHASES := ["morning", "day", "evening", "night"]
const NAMES := {"morning": "Утро", "day": "День", "evening": "Вечер", "night": "Ночь"}
## Цвет общего освещения сцены для каждой фазы
const TINTS := {
	"morning": Color(1.0, 0.95, 0.93),
	"day": Color(1.0, 1.0, 1.0),
	"evening": Color(1.0, 0.84, 0.82),
	"night": Color(0.56, 0.56, 0.82),
}

var phase := "evening"
var day := 1


func reset() -> void:
	phase = "evening"
	day = 1


func set_phase(p: String) -> void:
	if not p in PHASES:
		return
	if p == phase:
		return
	phase = p
	phase_changed.emit(phase)


## Перейти к следующей фазе; после ночи начинается новый день.
func advance() -> void:
	var i := PHASES.find(phase)
	if i == PHASES.size() - 1:
		day += 1
		set_phase(PHASES[0])
	else:
		set_phase(PHASES[i + 1])


func phase_name() -> String:
	return NAMES.get(phase, "")


func is_dark() -> bool:
	return phase == "evening" or phase == "night"


func get_state() -> Dictionary:
	return {"phase": phase, "day": day}


func set_state(d: Dictionary) -> void:
	phase = str(d.get("phase", "evening"))
	if not phase in PHASES:
		phase = "evening"
	day = int(d.get("day", 1))
	phase_changed.emit(phase)
