shader_type canvas_item;
// Эффект «сбоя» поверх экрана: сдвиг полос, хроматическая аберрация, сканлайны.
uniform sampler2D screen_tex : hint_screen_texture, filter_linear_mipmap;
uniform float amount : hint_range(0.0, 1.0) = 0.0;
uniform float seed = 0.0;

float rnd(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void fragment() {
	vec2 uv = SCREEN_UV;
	float t = floor(seed * 18.0);
	float band = floor(uv.y * 26.0);
	float r = rnd(vec2(band, t));
	if (r > 1.0 - amount * 0.55) {
		uv.x += (rnd(vec2(band, t + 3.0)) - 0.5) * 0.09 * amount;
	}
	float ca = 0.007 * amount;
	vec3 c;
	c.r = texture(screen_tex, uv + vec2(ca, 0.0)).r;
	c.g = texture(screen_tex, uv).g;
	c.b = texture(screen_tex, uv - vec2(ca, 0.0)).b;
	c *= 1.0 - 0.1 * amount * step(0.5, fract(uv.y * 300.0));
	float blk = step(1.0 - amount * 0.04, rnd(vec2(floor(uv.x * 40.0), floor(uv.y * 30.0) + t)));
	c = mix(c, vec3(0.75, 0.6, 1.0), blk * 0.8);
	COLOR = vec4(c, 1.0);
}
