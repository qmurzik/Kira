shader_type canvas_item;
render_mode blend_add;
// Мягкое пульсирующее свечение (для подсказок и огоньков).
uniform vec4 glow_color : source_color = vec4(0.8, 0.65, 1.0, 1.0);
uniform float pulse = 1.0;

void fragment() {
	float d = distance(UV, vec2(0.5));
	float a = smoothstep(0.5, 0.0, d);
	a = a * a * pulse;
	COLOR = vec4(glow_color.rgb, glow_color.a * a);
}
