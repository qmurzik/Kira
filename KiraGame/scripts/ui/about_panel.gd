extends "res://scripts/ui/modal.gd"
## Об игре и авторах.


func _init() -> void:
	panel_size = Vector2(1100, 800)


func build() -> void:
	set_title("Об игре")
	var v := scroll_area()
	var t := UIKit.label("KIRA.exe — По ту сторону экрана", 36, Color.WHITE, UIKit.font_title)
	v.add_child(t)
	for line in [
		"Уютная романтическая приключенческая история о Кире, мире забытых историй и человеке по ту сторону экрана.",
		"«Даже маленький вымышленный мир может подарить настоящие эмоции».",
		"",
		"Игра создана на движке Godot 4 (лицензия MIT). Вся графика, музыка и звуки — оригинальные, созданы процедурно специально для этой игры.",
		"Шрифты: Nunito, Comfortaa, Marck Script, JetBrains Mono — SIL Open Font License 1.1.",
		"",
		"Игра не собирает данные, не требует интернета и не трогает ваши файлы. Все «странности» с экраном происходят только внутри игры.",
		"",
		"Версия " + str(ProjectSettings.get_setting("application/config/version", "1.0")),
	]:
		v.add_child(wrap_label(line, 25, UIKit.TEXT if line.begins_with("«") == false else UIKit.accent2.lightened(0.3)))
