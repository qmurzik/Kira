# KiraPvP

Lightweight client-side PvP/QoL mod for Minecraft Forge **1.8.9**. Freelook,
a minimal always-on HUD, a ClickGUI, and a safe Performance mode - built
around one priority: as little frame-time and GC pressure as possible.

## Project structure

```
KiraPvP/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/               (Gradle 2.14 wrapper - matches ForgeGradle 2.1's Java 8 requirement)
└── src/main/
    ├── java/com/qmods/kirapvp/
    │   ├── KiraPvPMod.java              - @Mod entry point, wiring only
    │   ├── core/                        - Module, ModuleManager, Category, EventBus
    │   │   └── events/                  - ModuleToggleEvent, SettingChangeEvent
    │   ├── config/                      - ConfigManager (debounced JSON persistence), GuiState
    │   ├── keybind/                     - Keybind, KeybindManager
    │   ├── settings/                    - Setting, BooleanSetting, SliderSetting
    │   ├── hud/                         - HudRenderer, NotificationManager, Notification
    │   ├── gui/clickgui/                - ClickGui, ModuleRow, ClickGuiTheme
    │   ├── handler/                     - ClientEventHandler (all Forge event hooks)
    │   ├── module/impl/                 - one package per Category, one class per module
    │   └── util/                        - MathUtil, RenderUtils, ClickTracker
    └── resources/
        └── mcmod.info
```

## Building

**Verified building, not just written**: `.github/workflows/build-kirapvp.yml`
builds this project on every push via GitHub Actions (Temurin JDK 8 on
`ubuntu-latest`, `./gradlew build`, jar uploaded as the `KiraPvP-jar`
artifact). The sandbox this mod was originally authored in has neither a
JDK 8 nor network access to Mojang/Forge's servers (see "Local sandbox
build limitation" below), so CI is the actual proof this compiles - see
its run history for the green build and the produced jar.

To build locally on a normal dev machine:

- JDK 8 (ForgeGradle 2.1 does not run on JDK 9+)
- Internet access to `maven.minecraftforge.net`, `libraries.minecraft.net`
  and Mojang's launcher-meta/asset servers (ForgeGradle downloads
  Minecraft, Forge's userdev artifacts and MCP mappings on first run)

```bash
cd KiraPvP
./gradlew build
```

The compiled mod jar will be produced at `build/libs/KiraPvP-1.0.1.jar`.

To run a dev client for manual testing (ClickGUI, Freelook, HUD, config
round-trip, reconnect/world-change behavior):

```bash
./gradlew setupDecompWorkspace   # first time only
./gradlew runClient
```

## Modules

| Category | Module | Notes |
|---|---|---|
| Movement | **Freelook** (`C`) | Decouples the camera from body facing. See design rationale in `FreelookModule`'s Javadoc. |
| Render | **Zoom** (`Z`, hold) | Scales `gameSettings.fovSetting` while held. |
| Render | **FPS Display** | Self-counted, refreshed twice a second. |
| Render | **CPS Display** | Fixed-size ring buffer per mouse button. |
| Render | **Coordinates** | Recomputed on tick, not per frame. |
| Render | **Ping Display** | Reads the network handler at most once a second. |
| Render | **Keystrokes** | WASD + LMB/RMB indicator. |
| Player | **Armor Status** | Durability bars for equipped armor. |
| Combat | **Potion Timers** | Remaining duration of active effects. |
| Misc | **Performance** | Toggles clouds/AO/fancy graphics/particles safely, restores exact prior values on disable. |

Open the ClickGUI with `Right Shift`. Every module's keybind and every
setting (sliders, booleans) is rebindable/editable from there; module state,
settings, keybinds and GUI position/scale/theme persist to
`config/KiraPvP/{modules,settings,keybinds,guistate}.json`.

## Why Freelook doesn't use raw mouse interception (and doesn't need to)

A naive "steal the mouse delta before vanilla" implementation needs a hook
between `Minecraft.runTick()`'s internal mouse handling and its own call to
`Mouse.getDX()/getDY()` - there is no public Forge event at that exact point
in 1.8.9 (this is why the historical Ivorius "Freelook" mod for this MC
version used a coremod). Instead, `FreelookModule` *observes* the rotation
change vanilla already applies each render frame, accumulates it into an
independent camera-only yaw/pitch pair rendered via
`EntityViewRenderEvent.CameraSetup`, and snaps the real
`rotationYaw`/`rotationPitch` back to the locked facing once per tick
(`ClientTickEvent`, before that tick's movement is processed) so WASD
movement and interaction stay tied to the actual body direction. The
bookkeeping baseline is re-synced in the same instant as that snap, so the
periodic correction never appears as a false jump in the camera - the
camera itself is only ever written to from `CameraSetup`, so it stays
perfectly smooth. No ASM, no mixins, no reflection into private fields.

## Performance notes / self-audit

- **Render path**: every HUD element only ever reads/writes primitive
  fields and pre-built label strings on the actual per-frame call; string
  formatting happens on the client tick (or slower) and is skipped
  entirely when the underlying value hasn't changed (FPS: twice/sec,
  Coordinates: on integer-block change, Ping: once/sec, Potion Timers:
  once/tick). `ScaledResolution` is computed exactly once per frame
  (`RenderUtils.refreshScaledResolution`) and shared by every HUD module
  and the notification stack, instead of once per module.
- **CPS**: `ClickTracker` is a fixed 64-slot ring buffer per button; clicks
  are detected on the render loop (to not miss anything faster than the
  20 Hz tick) but never allocate - no growing list, no per-click object.
- **Notifications**: five pooled `Notification` slots, reused in place;
  pushing one only allocates the (tiny, infrequent) message string itself.
- **Config I/O**: every module toggle / setting edit only sets a dirty flag
  and a timestamp; the actual JSON write happens at most once per 1.5s of
  inactivity (checked with a cheap `System.currentTimeMillis()` compare on
  the client tick), plus an immediate flush on disconnect and a JVM
  shutdown hook. No disk I/O on the hot path, ever.
- **Threading**: none beyond the single JVM shutdown hook used to flush a
  pending config save on exit - there is no background thread, timer, or
  thread pool anywhere in the mod.
- **Keybinds**: polled once per client tick from a plain `Entry[]` built
  once at startup (`KeybindManager.lock()`); press/release edge detection
  only, no per-tick allocation.
- **ClickGUI**: `ModuleRow` objects are rebuilt only when the GUI opens or
  the active category changes - never per frame. GUI scale is a real GL
  transform with mouse coordinates de-scaled once per input callback, so
  hit-testing and rendering always agree.
- **Lifecycle safety**: `ModuleManager.resetAll()` (which calls every
  module's `onReset()`) runs on world load/unload, player respawn, death,
  and server disconnect, so nothing (camera state, cached player/world
  references) survives across a world change or reconnect. `Freelook` and
  `Zoom` are also unregistered from Forge's event buses on disable, so a
  disabled module costs nothing until re-enabled.

## Local sandbox build limitation (why CI, not a local run, is the proof)

The environment this mod was originally authored in has neither a JDK 8
nor network access to Mojang/Forge's servers:

1. **No JDK 8.** Only JDK 21 is installed. ForgeGradle 2.1 (required for
   MC 1.8.9) crashes immediately under it:
   `Could not determine java version from '21.0.10'` (Gradle 2.14, the
   version ForgeGradle 2.1 needs, cannot parse a JDK 9+ version string).
2. **Network policy blocks Mojang/Forge's servers.** `maven.minecraftforge.net`,
   `libraries.minecraft.net` and `launchermeta.mojang.com` are all
   rejected by that environment's egress proxy (`connect_rejected` /
   `403 Forbidden`), which are exactly the servers ForgeGradle needs to
   fetch Minecraft, Forge's userdev artifacts and MCP mappings.

Both are independently sufficient to block a build there; neither applies
to a GitHub Actions runner (or a normal dev machine), which is why the
build lives in CI - see `.github/workflows/build-kirapvp.yml` and its run
history for the actual green build and jar.

### Real compiler feedback the sandbox couldn't give (fixed against ground truth, not memory)

The first few CI runs did fail, and every fix below came from reading the
actual compiler output or the actual downloaded `forgeBin` jar, not from
re-guessing:

- ForgeGradle 2.1's plugin id is `net.minecraftforge.gradle.forge`, not the
  legacy short alias `forge` (the resolved `2.1-20211118` snapshot rebuild
  doesn't register the alias).
- Passing a field to `super(...)` in its own initializer doesn't compile
  (JLS: field initializers run after the superclass constructor returns) -
  `FreelookModule`, `ZoomModule` and `PerformanceModule` now build their
  settings as constructor parameters instead.
- `(float) someSliderSetting.getValue()` doesn't compile: casting a boxed
  `Double` straight to `float` needs unboxing *then* a narrowing
  conversion, and a single cast only composes unboxing with a *widening*
  conversion (JLS 5.5). Fixed with `.getValue().floatValue()`.
- `PlayerEvent.PlayerRespawnEvent` doesn't exist under
  `net.minecraftforge.event.entity.player.PlayerEvent` - it's nested under
  a *different*, legacy `net.minecraftforge.fml.common.gameevent.PlayerEvent`
  that happens to share the simple name. Confirmed by unzipping the actual
  `forgeBin-1.8.9-11.15.1.2318-1.8.9.jar` and listing its `PlayerEvent*`
  entries.
- `GuiScreen`'s mouse-release hook is `mouseReleased(int, int, int)` -
  there is no `mouseMovedOrUp` in 1.8.9. Confirmed by running `javap` on
  the actual decompiled-mapping `GuiScreen.class` from that same jar.

Everything else guessed from memory in the initial pass - `EntityPlayerSP
.sendQueue`, `GameSettings.clouds`/`.ambientOcclusion`/`.particleSetting`/
`.fancyGraphics`, `Potion.potionTypes`, `NetworkPlayerInfo
.getResponseTime()`, every other Forge event class used - compiled clean
on the first real attempt.

## Changelog

- **1.0.1** - fixed the 12 real compile errors surfaced by the first
  GitHub Actions runs (ForgeGradle plugin id, three constructor-ordering
  bugs, two Double-to-float casts, a wrong `PlayerEvent` import, a
  nonexistent `GuiScreen` override); CI is green and produces a jar.
- **1.0.0** - initial implementation: Freelook, Zoom, full HUD (FPS/CPS/
  Coordinates/Ping/Keystrokes), Armor Status, Potion Timers, Performance
  mode, ClickGUI, debounced JSON config, custom keybind system.
