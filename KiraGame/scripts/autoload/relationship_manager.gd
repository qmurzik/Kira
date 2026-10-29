extends Node
## Отношения с Кирой — не один счётчик «любви», а несколько граней
## плюс память о значимых решениях, на которые Кира ссылается позже.

signal changed(stat: String, delta: int)
signal memory_added(key: String)

const STATS := {
	"trust": {"name": "Доверие", "desc": "Насколько Кира готова открываться тебе."},
	"understanding": {"name": "Взаимопонимание", "desc": "Насколько хорошо вы чувствуете друг друга."},
	"spirit": {"name": "Дух приключений", "desc": "Насколько смело вы вместе шагаете в неизвестность."},
}

const STAGES := [
	{"min": 0, "name": "Незнакомец по ту сторону экрана"},
	{"min": 4, "name": "Новый друг"},
	{"min": 9, "name": "Близкий человек"},
	{"min": 15, "name": "Тот, кому она доверяет звёзды"},
	{"min": 22, "name": "Её человек"},
]

var stats := {"trust": 0, "understanding": 0, "spirit": 0}
## Значимые решения: key -> {"text": описание, "day": день}
var memories := {}


func reset() -> void:
	stats = {"trust": 0, "understanding": 0, "spirit": 0}
	memories = {}


func add(stat: String, delta: int) -> void:
	if not stats.has(stat):
		return
	stats[stat] = int(stats[stat]) + delta
	changed.emit(stat, delta)


func get_stat(stat: String) -> int:
	return int(stats.get(stat, 0))


func total() -> int:
	return get_stat("trust") + get_stat("understanding") + get_stat("spirit")


func stage_name() -> String:
	var t := total()
	var n: String = STAGES[0].name
	for s in STAGES:
		if t >= int(s.min):
			n = s.name
	return n


## Доминирующая грань отношений — влияет на то, какую концовку предложит Кира.
func dominant() -> String:
	var best := "trust"
	for s in ["understanding", "spirit"]:
		if get_stat(s) > get_stat(best):
			best = s
	return best


## Описание уровня без цифр — для дневника.
func level_text(stat: String) -> String:
	var v := get_stat(stat)
	var levels := {
		"trust": ["Осторожно присматривается", "Начинает доверять", "Делится секретами", "Доверяет без оглядки"],
		"understanding": ["Пока угадывает", "Понимает с полуслова", "Чувствует твоё настроение", "Как будто читает мысли"],
		"spirit": ["Предпочитает уют", "Любопытство просыпается", "Готова к приключениям", "Не боится неизвестного"],
	}
	var idx := 0
	if v >= 3: idx = 1
	if v >= 6: idx = 2
	if v >= 10: idx = 3
	return levels[stat][idx]


func remember(key: String, text: String) -> void:
	memories[key] = {"text": text, "day": TimeManager.day}
	memory_added.emit(key)


func has_memory(key: String) -> bool:
	return memories.has(key)


func get_state() -> Dictionary:
	return {"stats": stats.duplicate(), "memories": memories.duplicate(true)}


func set_state(d: Dictionary) -> void:
	reset()
	var s: Dictionary = d.get("stats", {})
	for k in stats.keys():
		stats[k] = int(s.get(k, 0))
	memories = d.get("memories", {}).duplicate(true)
