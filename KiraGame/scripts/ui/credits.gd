extends Control
## Финальный экран: название концовки, эпилог и титры.

signal done

const ENDINGS := {
	"our_world": {"title": "Наш маленький мир", "color": Color("#ffc9a8"),
		"text": "Мир стал размером с одну комнату, крышу и кусочек неба. Но в нём горит гирлянда, пахнет какао, и у окна всегда есть место для двоих."},
	"new_dawn": {"title": "Новый рассвет", "color": Color("#ffe7a8"),
		"text": "Над городом впервые взошло солнце. Забытые истории вернулись домой, а Кира впервые пишет продолжение сама."},
	"constellation": {"title": "Созвездие воспоминаний", "color": Color("#c9d6ff"),
		"text": "Воспоминания поднялись в небо и сложились в новое созвездие. Его видно из любого мира — нужно только знать, куда смотреть."},
}
const CREDITS := [
	["KIRA.exe — По ту сторону экрана", ""],
	["Сценарий и диалоги", "Создано с помощью Claude"],
	["Программирование", "Godot 4 · GDScript"],
	["Графика", "Процедурные векторные иллюстрации"],
	["Музыка и звук", "Оригинальные синтезированные композиции"],
	["Шрифты", "Nunito, Comfortaa, Marck Script, JetBrains Mono (SIL OFL)"],
	["Главная роль", "Кира"],
	["И самое важное", "{name} — по ту сторону экрана"],
	["", ""],
	["Даже маленький вымышленный мир", "может подарить настоящие эмоции."],
]

var ending_id := "our_world"
var _scroll: VBoxContainer
var _speed := 60.0
var _t := 0.0
var _finished := false


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	var bg := ColorRect.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.color = Color(0.03, 0.02, 0.08, 0.0)
	add_child(bg)
	create_tween().tween_property(bg, "color:a", 0.93, 1.5)
	var e: Dictionary = ENDINGS.get(ending_id, ENDINGS.our_world)
	_scroll = VBoxContainer.new()
	_scroll.anchor_left = 0.5
	_scroll.anchor_right = 0.5
	_scroll.offset_left = -650
	_scroll.offset_right = 650
	_scroll.offset_top = get_viewport_rect().size.y * 0.35
	_scroll.add_theme_constant_override("separation", 26)
	add_child(_scroll)
	_center(UIKit.label("Концовка", 32, UIKit.TEXT_DIM, UIKit.font_title))
	var t := UIKit.label(e.title, 96, e.color, UIKit.font_script)
	t.add_theme_color_override("font_shadow_color", Color(e.color, 0.5))
	t.add_theme_constant_override("shadow_outline_size", 28)
	t.add_theme_constant_override("shadow_offset_x", 0)
	t.add_theme_constant_override("shadow_offset_y", 0)
	_center(t)
	var d := UIKit.label(e.text, 30)
	d.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_center(d)
	var sp := Control.new()
	sp.custom_minimum_size.y = 260
	_scroll.add_child(sp)
	for c in CREDITS:
		if c[0] != "":
			_center(UIKit.label(c[0], 26, UIKit.accent.lightened(0.3), UIKit.font_bold))
		if c[1] != "":
			_center(UIKit.label(c[1].replace("{name}", GameManager.display_name().capitalize()), 34, Color.WHITE, UIKit.font_title))
		var s2 := Control.new()
		s2.custom_minimum_size.y = 40
		_scroll.add_child(s2)
	var hint := UIKit.label("Клик — быстрее", 20, Color(1, 1, 1, 0.4))
	hint.anchor_top = 1.0
	hint.anchor_bottom = 1.0
	hint.anchor_left = 1.0
	hint.anchor_right = 1.0
	hint.offset_left = -220
	hint.offset_top = -50
	add_child(hint)
	AudioManager.play_music("ending", 3.0)
	AudioManager.play_ambience("")


func _center(l: Label) -> void:
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_scroll.add_child(l)


func _process(delta: float) -> void:
	_t += delta
	if _t < 4.0 or _finished:
		return
	var fast := Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT) or Input.is_action_pressed("advance")
	_scroll.offset_top -= delta * (_speed * (6.0 if fast else 1.0))
	if _scroll.offset_top + _scroll.size.y < get_viewport_rect().size.y * 0.45:
		_finished = true
		var t := create_tween()
		t.tween_interval(1.5)
		t.tween_property(self, "modulate:a", 0.0, 1.5)
		t.tween_callback(func(): done.emit(); queue_free())
