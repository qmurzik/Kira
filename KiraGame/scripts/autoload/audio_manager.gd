extends Node
## Звук: музыка с плавной сменой треков, эмбиент (дождь, поезд), эффекты, «голосовые» сигналы диалогов.

const MUSIC_DIR := "res://assets/audio/music/"
const SFX_DIR := "res://assets/audio/sfx/"
const AMB_DIR := "res://assets/audio/amb/"
const BUSES := ["Music", "SFX", "Ambience", "Voice"]

var _music_a: AudioStreamPlayer
var _music_b: AudioStreamPlayer
var _music_current: AudioStreamPlayer
var _amb: AudioStreamPlayer
var _sfx_pool: Array[AudioStreamPlayer] = []
var _blip: AudioStreamPlayer
var _cache := {}
var current_music := ""
var current_amb := ""
var _music_tween: Tween
var _amb_tween: Tween
var music_duck := 1.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	for b in BUSES:
		if AudioServer.get_bus_index(b) == -1:
			AudioServer.add_bus()
			var idx := AudioServer.bus_count - 1
			AudioServer.set_bus_name(idx, b)
			AudioServer.set_bus_send(idx, "Master")
	_music_a = _make_player("Music")
	_music_b = _make_player("Music")
	_music_current = _music_a
	_amb = _make_player("Ambience")
	for i in 8:
		_sfx_pool.append(_make_player("SFX"))
	_blip = _make_player("Voice")
	apply_volumes()
	Settings.changed.connect(func(section, _k): if section == "audio": apply_volumes())


func _make_player(bus: String) -> AudioStreamPlayer:
	var p := AudioStreamPlayer.new()
	p.bus = bus
	add_child(p)
	return p


func apply_volumes() -> void:
	_set_bus("Master", Settings.get_value("audio", "master", 0.85))
	_set_bus("Music", Settings.get_value("audio", "music", 0.7) * music_duck)
	_set_bus("SFX", Settings.get_value("audio", "sfx", 0.8))
	_set_bus("Ambience", Settings.get_value("audio", "ambience", 0.6))
	_set_bus("Voice", Settings.get_value("audio", "voice", 0.6))


func _set_bus(bus: String, linear: float) -> void:
	var idx := AudioServer.get_bus_index(bus)
	if idx < 0:
		return
	AudioServer.set_bus_volume_db(idx, linear_to_db(maxf(linear, 0.0001)))
	AudioServer.set_bus_mute(idx, linear <= 0.001)


func _load(path: String, loop: bool) -> AudioStream:
	var key := path + str(loop)
	if _cache.has(key):
		return _cache[key]
	if not ResourceLoader.exists(path):
		return null
	var s: AudioStream = load(path)
	if s is AudioStreamOggVorbis:
		s = s.duplicate()
		s.loop = loop
	elif s is AudioStreamWAV:
		s = s.duplicate()
		s.loop_mode = AudioStreamWAV.LOOP_FORWARD if loop else AudioStreamWAV.LOOP_DISABLED
		if loop:
			s.loop_end = int(s.get_length() * s.mix_rate)
	_cache[key] = s
	return s


## Сменить фоновую музыку с плавным переходом. Пустое имя — тишина.
func play_music(track: String, fade := 1.5) -> void:
	if track == current_music:
		return
	current_music = track
	var old := _music_current
	var new_p := _music_b if old == _music_a else _music_a
	if _music_tween:
		_music_tween.kill()
	if old.playing:
		_music_tween = create_tween()
		_music_tween.tween_property(old, "volume_db", -50.0, fade)
		_music_tween.tween_callback(old.stop)
	if track != "":
		var s := _load(MUSIC_DIR + track + ".ogg", true)
		if s:
			new_p.stream = s
			new_p.volume_db = -40.0
			new_p.play()
			var t2 := create_tween()
			t2.tween_property(new_p, "volume_db", 0.0, fade)
	_music_current = new_p


func play_ambience(name: String, fade := 2.0) -> void:
	if name == current_amb:
		return
	current_amb = name
	if _amb_tween:
		_amb_tween.kill()
	if name == "" and not _amb.playing:
		return
	_amb_tween = create_tween()
	if _amb.playing:
		_amb_tween.tween_property(_amb, "volume_db", -50.0, fade * 0.5)
		_amb_tween.tween_callback(_amb.stop)
	if name != "":
		var s := _load(AMB_DIR + name + ".ogg", true)
		if s:
			_amb_tween.tween_callback(func():
				_amb.stream = s
				_amb.volume_db = -40.0
				_amb.play())
			_amb_tween.tween_property(_amb, "volume_db", 0.0, fade)


func play_sfx(name: String, pitch := 1.0, volume_db := 0.0) -> void:
	var s := _load(SFX_DIR + name + ".wav", false)
	if s == null:
		s = _load(SFX_DIR + name + ".ogg", false)
	if s == null:
		return
	for p in _sfx_pool:
		if not p.playing:
			p.stream = s
			p.pitch_scale = pitch
			p.volume_db = volume_db
			p.play()
			return
	_sfx_pool[0].stream = s
	_sfx_pool[0].play()


## Мягкий «голосовой» сигнал при печати текста.
func blip(voice: String) -> void:
	var s := _load(SFX_DIR + "blip_" + voice + ".wav", false)
	if s == null:
		return
	_blip.stream = s
	_blip.pitch_scale = randf_range(0.94, 1.08)
	_blip.play()


## Приглушить музыку (например, во время важной реплики).
func duck(amount := 0.45, time := 0.6) -> void:
	var t := create_tween()
	t.tween_method(func(v): music_duck = v; apply_volumes(), music_duck, amount, time)


func unduck(time := 0.8) -> void:
	duck(1.0, time)
