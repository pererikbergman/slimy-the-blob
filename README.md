# Slimy the Blob

A test level for a soft-body slime, made with [klyph](https://github.com/inemtsev/klyph), a 2D game
engine in Kotlin/Native on SDL3. The slime is a ring of Verlet particles around a heavy core that
squashes on landing, stretches in the air and squeezes through gaps narrower than itself. The
level has four zones, each built to test one thing.

## Requirements

- JDK 17 or newer, for Gradle. The wrapper downloads Gradle itself.
- SDL3 3.4 or newer. On macOS and Windows the klyph plugin downloads it when none is installed;
  `brew install sdl3` avoids the download on macOS. On Linux install your distribution's package
  (`libsdl3-dev` on Debian 13 and Ubuntu 25.04 or newer, `sdl3` on Arch, `SDL3-devel` on Fedora).

## Play

```
./gradlew run          # builds the debug executable and plays it
./gradlew distribute   # zips the release executable, assets/ and SDL3 in build/distributions/
```

| Key | Does |
|---|---|
| A / D or the arrows | move |
| Space, W or Up | jump (hold for a higher jump, tap for a hop) |
| 1 to 4 | go to zone A to D |
| R | restart the current zone |
| F2 | show the slime's particles (the red one is the core) |
| F3 | klyph's frame stats |
| Esc | quit |

Falling out of the level puts you back at the start of the zone you were in.

## The zones

| Zone | Columns | Tests |
|---|---|---|
| **A** The Calibration Chamber | 0-39 | Flat ground, a one-tile step and a two-tile block: movement, friction and the resting shape. |
| **B** The Vertical Ascent | 40-99 | Pits two, three and four tiles wide, steps up and down, a low wall: jump height and air control. |
| **C** The Squeeze Corridor | 100-159 | Three tunnels two tiles, one tile and half a tile high (60% of the slime's height), steps up to a deck with a one-tile shaft (60% of its width), and a corridor out under the deck. |
| **D** The Kinetic Gauntlet | 160-239 | A six-tile 45 degree hill, two bounce pads under a ledge only a bounce reaches (with a curtain of red slime hanging under it that you can push through), and a crusher that comes down to eight units above the ground. |

Hop onto the deck's shaft in zone C and the slime sinks through under its own weight; the deck
ends in a wall, so that is the way on. A slime walking across it bridges the gap, and one that is
stopped dead on it perches on the rim, so land on it from a jump.

## Try it without a keyboard

```
./gradlew run -Pklyph.frames=120 -Pklyph.screenshot=100:shot.png     # save frame 100 as a PNG
./gradlew run -Pklyph.autopilot=1                                    # the slime runs right and hops by itself
SLIME_ZONE=C ./gradlew run -Pklyph.autopilot=1                       # start in zone C
```

## Tests

```
./gradlew allTests
```

| Tests | Check |
|---|---|
| `physics/AcceptanceTest` | The PRD's criteria: no blow-up or tearing in a one-tile shaft, passing a gap 60% of its size and recovering its shape, no particle left inside a solid after long falls or corner landings. |
| `physics/SlimeTest` | A jump pressed this step is rising by the end of it, jump height, buffered jumps, top speed, bounce pads. |
| `physics/CurtainTest` | The hanging red slime hangs straight, is pushed out of the slime's body, swings when it is walked through and settles again. |
| `physics/SoftBodyTest`, `SolidsTest` | The constraints, and pushing particles out of boxes, slopes and stubs. |
| `level/LevelTest`, `TestLevelTest` | Reading the tile map; the shape of the four zones. |
| `level/PlaythroughTest` | A bot plays every zone on the real level and physics: hold right, jump at walls and pits. |
| `render/ScanlineTest` | The rows used to fill the slime add up to its area. |

## Layout

| Path | Contents |
|---|---|
| `src/nativeMain/kotlin/slimy/Main.kt`, `Game.kt` | The window, camera, input and HUD. |
| `src/nativeMain/kotlin/slimy/physics/` | `Tuning` (every number that decides how the slime feels), `SoftBody`, `Solids`, `Slime`, `Curtain` (the hanging red slime). |
| `src/nativeMain/kotlin/slimy/level/` | `LevelMap` and `Level` (tile characters in, solids and sprites out) and `TestLevel`. |
| `src/nativeMain/kotlin/slimy/render/` | `SlimePainter`, `CurtainPainter`, `Scanline`, `Lines`. |
| `assets/` | `tiles.png` and `tiles.json`, `crusher.png`, `face.png`, `bg.png`. |
| `art/tiles/draw.py` | Draws all the art; every render is kept as `art/<set>/v<N>_*.png`. |
| `docs/superpowers/plans/` | The implementation plan this was built from. |

## How the slime works

Twenty-two particles form a half-disc outline around one core particle. Stiff distance constraints
hold the outline, soft ones tie it to the core and an area constraint keeps the volume, all XPBD,
so how soft they are does not depend on the iteration count. The core is thirty-four times heavier than
an outline particle and stands for the slime's insides: when the outline is held on the rim of a
gap, its weight drags the body through. `Tuning.kt` explains each number and what moving it does.

To rename the game, change `rootProject.name` in `settings.gradle.kts`, the package in the sources
and `entryPoint` in `build.gradle.kts`.
