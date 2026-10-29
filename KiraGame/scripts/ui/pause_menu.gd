extends "res://scripts/ui/modal.gd"
## Меню паузы.

signal request(action: String)


func _init() -> void:
	panel_size = Vector2(560, 760)


func build() -> void:
	set_title("Пауза")
	body.alignment = BoxContainer.ALIGNMENT_CENTER
	for b in [["resume", "Продолжить"], ["save", "Сохранить"], ["load", "Загрузить"], ["settings", "Настройки"],
			["journal", "Дневник"], ["history", "История диалогов"], ["main_menu", "Главное меню"], ["quit", "Выйти из игры"]]:
		var btn := UIKit.button(b[1], func():
			if b[0] == "resume":
				close()
			else:
				request.emit(b[0]))
		btn.custom_minimum_size = Vector2(420, 62)
		btn.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		body.add_child(btn)
	(body.get_child(0) as Button).grab_focus.call_deferred()
