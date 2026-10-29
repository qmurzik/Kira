extends "res://scripts/ui/modal.gd"
## История диалогов текущей сессии.


func _init() -> void:
	panel_size = Vector2(1300, 860)


func build() -> void:
	set_title("История диалогов")
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(sc)
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_theme_constant_override("separation", 10)
	sc.add_child(v)
	if DialogueManager.history.is_empty():
		v.add_child(wrap_label("Здесь появятся ваши разговоры.", 24, UIKit.TEXT_DIM))
	for e in DialogueManager.history:
		var r := RichTextLabel.new()
		r.bbcode_enabled = true
		r.fit_content = true
		r.scroll_active = false
		r.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		r.add_theme_font_size_override("normal_font_size", 25)
		r.add_theme_font_size_override("bold_font_size", 25)
		r.add_theme_font_size_override("italics_font_size", 25)
		var text: String = str(e.text).replace("💜", "♥").replace("[", "(").replace("]", ")")
		var col: Color = e.color
		match str(e.style):
			"narration": r.text = "[i][color=#cbbff0]%s[/color][/i]" % text
			"choice": r.text = "[color=#%s]%s[/color]" % [UIKit.accent2.to_html(false), text]
			_: r.text = "[b][color=#%s]%s:[/color][/b] %s" % [col.to_html(false), e.name, text] if str(e.name) != "" else text
		v.add_child(r)
	await get_tree().process_frame
	await get_tree().process_frame
	sc.scroll_vertical = int(sc.get_v_scroll_bar().max_value)
