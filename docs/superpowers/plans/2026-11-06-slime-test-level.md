# Slimy the Blob: Soft-Body Test Level Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A playable klyph test level where a semi-circular Verlet soft-body slime walks, jumps, squeezes through gaps and survives Zones A–D from the PRD, with the four acceptance criteria covered by automated tests.

**Architecture:** The slime is a ring of Verlet particles plus one core particle, held together by stiff ring edges, soft spokes to the core and a gas-pressure term; it collides against a tile grid of axis-aligned blocks and 45° slopes with position-based push-out. All physics is plain Kotlin with no engine dependency, so it runs under `kotlin.test`; klyph supplies the loop (fixed step at 60 Hz), input, camera, tile sprites and the canvas the slime is painted on each frame.

**Tech Stack:** klyph 1.0.0 (Kotlin/Native, SDL3), Gradle, `kotlin("test")` on the native test source set, pixel-graphics skill (Pillow) for art.

---

## Why these decisions

| PRD says | What klyph gives us | Decision |
|---|---|---|
| Godot, GDScript physics loop | `Phase.FIXED_UPDATE` at 60 Hz, `Interpolated` not needed since we draw from the sim directly | All simulation in one FIXED_UPDATE system, 4 substeps per step for stability |
| Verlet ring + cross-springs or pressure | Nothing built in | Ring edges (stiffness 1) + spokes to a core particle (stiffness ≈0.15, lets it elongate) + pressure (keeps volume, drives the squeeze). Rest shape is a half-disc: arc on top, flat base |
| Static tilemap geometry | Only `Box`/`Circle` overlap events, no solid response; queries read last frame | Own `SolidGrid`: one `Solid` per 16-unit tile (`Block` or `Slope`) plus a small dynamic list (crusher). Each particle checks only its 3×3 neighbouring cells |
| Deforming visual | Sprites, rects and text only, no polygons | Scanline-fill the ring polygon with 1-unit `drawRect` rows in `DrawSpace.WORLD` (reads as pixel art at zoom 3), outline with thin rotated white-pixel quads, a small pixel-art face sprite riding the centroid |
| Keyboard only | `app.input.keyboard` | Arrows/WASD move, Space/Up/W jump. Keys 1–4 teleport to zones, R resets, F2 shows particles, F3 is klyph's overlay |

**Sizes.** Tiles are 16 world units, camera zoom 3 (view ≈ 427×240 units ≈ 27×15 tiles). Slime radius 13.3 → resting width 26.6, height 13.3. A 1-tile shaft is 16 wide = 60% of the width; a half-tile ceiling stub leaves 8 units = 60% of the height. Those two numbers are what the squeeze tests measure.

**Tuning lives in one place** (`Tuning.kt`): gravity, acceleration, max speed, jump speed, extra bottom-particle lift on jump, short-hop gravity, bounce speed, friction, damping, substeps, relax iterations, max particle step per substep (kept under half a tile so nothing tunnels through a corner), coyote and jump-buffer seconds, stiffnesses, pressure. Tasks 5 and 10 are allowed to change these numbers; nothing else is.

## File structure

| Path | Responsibility |
|---|---|
| `build.gradle.kts` | Add `kotlin("test")` to `nativeTest` |
| `src/nativeMain/kotlin/slimy/Main.kt` | `runApp` + `installGame()` (already exists, shrinks) |
| `src/nativeMain/kotlin/slimy/Game.kt` | Wires level, slime, input, camera, HUD, autopilot and env vars into systems |
| `src/nativeMain/kotlin/slimy/physics/Tuning.kt` | Every tunable constant |
| `src/nativeMain/kotlin/slimy/physics/SoftBody.kt` | Particles, constraints, pressure, integrate/relax, shape queries (area, centroid, bounds), `SlimeShape.semiDisc` |
| `src/nativeMain/kotlin/slimy/physics/Solids.kt` | `Solid` (`Block`, `Slope`), `SolidGrid`, `Contacts`, `collide()` push-out |
| `src/nativeMain/kotlin/slimy/physics/Slime.kt` | `SlimeInput`, `Slime.step()`: substeps, forces, jump/coyote/buffer, friction, grounded, bounce |
| `src/nativeMain/kotlin/slimy/level/LevelMap.kt` | Char-grid builder (`put`, `fill`, `clear`, `rows()`) |
| `src/nativeMain/kotlin/slimy/level/Level.kt` | Parses rows → `SolidGrid`, tile sprite list, start, zone markers, crushers; `Crusher` motion |
| `src/nativeMain/kotlin/slimy/level/TestLevel.kt` | The four zones, built with `LevelMap` |
| `src/nativeMain/kotlin/slimy/render/SlimePainter.kt` | Fill, outline, face, debug particles on `app.canvas` |
| `src/nativeTest/kotlin/slimy/physics/*Test.kt` | Unit tests per physics file |
| `src/nativeTest/kotlin/slimy/physics/AcceptanceTest.kt` | The PRD §4 criteria |
| `src/nativeTest/kotlin/slimy/level/*Test.kt` | Parser and TestLevel sanity |
| `art/tiles/`, `art/face/`, `art/bg/` | pixel-graphics scripts + versioned renders |
| `assets/tiles.png`, `assets/face.png`, `assets/bg.png`, `assets/tiles.json` | Shipped art and manifest |

**Tile characters** (`TestLevel` / parser): `#` solid, `v` ceiling stub (top half of the tile solid), `/` and `\` 45° slopes, `J` bounce pad, `M` crusher anchor (2×2 moving block), `S` start, `1`–`4` zone markers (sign + teleport target), `F` finish sign, `.` empty.

**Test command:** `./gradlew allTests` (host target; `macosArm64Test` on this Mac). Visual checks: `./gradlew run -Pklyph.frames=N -Pklyph.screenshot=M:path.png`, with `SLIME_ZONE=C` to start in a zone and `SLIME_AUTOPILOT=1` to hold right and jump periodically so screenshots need no keyboard.

---

## Tasks

### Task 1: Test wiring and Tuning
- [x] Add `kotlin("test")` to `nativeTest` in `build.gradle.kts`
- [x] Create `Tuning.kt` with the constants listed above and KDoc on each saying what it does in game terms
- [x] Write one smoke test that reads a Tuning constant; run `./gradlew allTests` and confirm the test task exists and passes
- [x] Commit: "Wire up native tests and physics tuning"

### Task 2: SoftBody (TDD)
Tests first, in `SoftBodyTest`:
- [x] `semiDisc(radius, arcCount, baseCount)` yields `arcCount + baseCount` points, width `2r`, height `r`
- [x] `placeAt(x, y)` puts the ring centroid there with zero velocity
- [x] `integrate` with gravity moves every particle down; the per-substep step is capped at `maxStep`
- [x] `relax` on a shape squashed to half height restores ≥85% of the rest area within 60 passes and stays finite
- [x] Implement: particle arrays (`x`, `y`, `prevX`, `prevY`), rest edge/spoke lengths, rest area, winding sign for outward normals, `integrate`, `relax` (edges, spokes, pressure), `area`, `centroidX/Y`, `min/max X/Y`, `edgeLength`, `isFinite`, `addVelocity`, `setVelocityY`
- [x] Commit: "Add Verlet soft body with pressure"

### Task 3: Solids and collision (TDD)
Tests first, in `SolidsTest`, each placing one particle with a known previous position:
- [x] Particle entering a block from above lands on its top, gets a floor contact, keeps its horizontal velocity, loses its vertical one
- [x] From the side: pushed out sideways, no floor contact
- [x] Particle that started inside is pushed out through the nearest face
- [x] Outside particle untouched
- [x] Bouncy block sets the bouncy contact flag
- [x] `/` slope pushes along its up-left normal onto the surface; `\` mirrors it
- [x] A dynamic solid (not in a cell) is checked too
- [x] Implement `Block`, `Slope`, `SolidGrid` (cells + `dynamic`, `block()`, `fillBlocks()`, bounds-safe `get`), `Contacts`, `collide(body, grid, contacts)` that visits the 3×3 cells around each particle. Push-out removes only the inward normal velocity so particles slide along walls
- [x] Commit: "Add tile solids and particle push-out"

### Task 4: Slime controller (TDD)
Tests first, in `SlimeTest`, on a 40×20 grid with a floor:
- [x] Dropped slime settles on the floor: grounded, no ring particle below the floor top
- [x] **Input latency:** grounded slime with `jumpPressed` set rises on the very next `step()`, and the press is consumed
- [x] Jump lifts the base at least two tiles with jump held
- [x] Holding right moves the centroid right; releasing stops it within ~1.5 s
- [x] A bounce pad throws the slime well above jump height and sets `hasBounced`
- [x] Implement `SlimeInput`, `Slime.step()`: jump buffer/coyote evaluated once per step, impulse to all particles with extra lift for floor-contact particles (PRD §2.2), then N substeps of integrate → clamp speeds → interleaved relax/collide → grounded + friction + bounce detection; `facing`, `hasJumped/Landed/Bounced`, `reset(x, y)`
- [x] Commit: "Add slime movement, jumping and bouncing"

### Task 5: Acceptance tests (PRD §4) and tuning
In `AcceptanceTest`, geometry built from `SolidGrid`:
- [x] **Deformation stability:** slime dropped into a 1-tile shaft for 1200 steps stays finite, inside the shaft walls, and no ring edge exceeds 3× its rest length
- [x] **Squeeze success (vertical):** slime above a 16-wide, 15-tile-deep shaft reaches the open chamber below; after 300 resting steps its width and area are within 15% of rest
- [x] **Squeeze success (horizontal):** slime pushed right under a tunnel with 8 units of clearance comes out the far side; shape within 15% of rest afterwards
- [x] **No clipping:** slime falling 430 units onto a floor never has a ring particle below the floor top on any step; same when landing half on a tile corner, no particle ends inside the block
- [x] If a squeeze test fails, tune in this order and re-run: lower `SPOKE_STIFFNESS`, raise `PRESSURE`, raise `RELAX_ITERATIONS`, raise `MOVE_ACCEL`. Record the final numbers in Tuning's KDoc
- [x] Commit: "Cover the PRD acceptance criteria with tests"

### Task 6: Level parser (TDD)
- [x] Tests: `LevelMap.rows()` has equal-length rows; a 6×4 sample map parses `#`, `v` (bottom at tile top + 8), `/`, `\`, `J` (bouncy), `S`, `1`, `M` into the right solids, start, zone and crusher; ragged rows throw; `#` with air above gets the grass-top tile index, otherwise fill
- [x] Crusher test: after half a period its top has moved by its full travel; the block is in `grid.dynamic`
- [x] Implement `LevelMap`, `Level.parse`, `Tile`, `Zone` (id, PRD name, x, y), `Crusher` (cosine oscillation, 2×2 tiles, lowest point leaves 8 units above the floor), `Level.advance(dt)`, `zoneAt(x)`, `zoneOrNull(id)`, `width/height`
- [x] Commit: "Parse tile maps into solids, tiles and zones"

### Task 7: The test level
Built in `TestLevel` with `LevelMap`, 240×24 tiles, floor on rows 20–23, walls at both ends:
- [ ] **Zone A (cols 0–39):** flat, one 2-tall block to hop, start and `1` sign
- [ ] **Zone B (40–99):** pits of 2, 3 and 4 tiles; three step platforms each 2 tiles higher; a lower step and a short wall before the ground resumes (no dead-end pits)
- [ ] **Zone C (100–159):** three tunnels with 2 tiles, 1 tile and half a tile (`v`) of clearance; steps up to a deck with a 1-tile shaft through it, a wall that forces the drop, and a 2-tall corridor out to Zone D
- [ ] **Zone D (160–239):** 6-tile `/` hill, plateau, `\` down; two `J` pads in the floor with a ledge 7 tiles up only a bounce reaches; crusher `M` under a ceiling block; `F` sign
- [ ] Test: `TestLevel` parses, 240×24, zones `1234` in x order, start and finish present, > 1000 tiles
- [ ] Commit: "Lay out the four test zones"

### Task 8: Pixel art (pixel-graphics skill)
Invoke the `pixel-graphics` skill with this brief; keep scripts and versions under `art/`:
- [ ] `assets/tiles.png`: 7 cells of 16×16 in one row, light from top-left, 5-tone ramps: 0 grass-top ground, 1 dirt fill (seamless), 2 ceiling stub (top 8 px stone, bottom transparent), 3 bounce pad (orange spring, surface at tile top), 4 `/` slope, 5 `\` slope, 6 crusher metal block. Ship `assets/tiles.json` naming each index
- [ ] `assets/face.png`: 12×8 symmetric slime face (two eyes, small mouth), transparent background
- [ ] `assets/bg.png`: 480×240 sky with distant hills, tileable horizontally
- [ ] Verify sheet size = 7×16 by script; view every deliverable
- [ ] Commit: "Add tileset, face and background art"

### Task 9: SlimePainter
No unit test (needs a canvas); verified by screenshot in Task 10:
- [ ] Scanline fill: for each integer row between the ring's min and max y, intersect ring edges, sort crossings (reused scratch array, no per-frame allocation), draw rects between pairs in `DrawSpace.WORLD`
- [ ] Outline: one thin rotated `whitePixel` quad per ring edge, one layer above the fill
- [ ] Face sprite at the centroid, nudged toward `facing`
- [ ] Debug mode: 2×2 rects at every particle and the core
- [ ] Commit: "Paint the slime from its particles"

### Task 10: Game wiring and visual verification
- [ ] `Game.kt` `installGame()`: parse `TestLevel`, zoom 3, camera bounds = level, spawn tile sprites (NEAREST) and zone `Text` signs, create slime/input/painter
- [ ] INPUT: Escape quits, F2 debug, R reset, 1–4 teleport, move axis, jump pressed/held; autopilot overrides when `SLIME_AUTOPILOT=1`
- [ ] FIXED_UPDATE: `level.advance`, `slime.step`, respawn at the current zone when the slime falls below the level
- [ ] LATE_UPDATE: `camera.follow` the centroid
- [ ] RENDER: parallax background (world space, negative layer), crushers as 2×2 sprites, slime, HUD with zone name and controls
- [ ] `SLIME_ZONE=A..D` picks the start; env read via `platform.posix.getenv`
- [ ] `Main.kt` calls `installGame()`
- [ ] Verify with screenshots: one per zone using `SLIME_ZONE` + autopilot + `-Pklyph.screenshot`; look at each; confirm the slime squashes on landing (Zone A), clears the steps (B), goes under the half tunnel and down the shaft (C), rides the hill, bounces to the ledge and survives the crusher (D). Tune `Tuning` if a zone is impassable and re-run `allTests`
- [ ] Commit: "Wire the slime and test level into the game"

### Task 11: Docs and wrap-up
- [ ] Update `README.md`: controls, zone keys, env vars, test and screenshot commands, what each zone tests
- [ ] Run `./gradlew allTests` and `./gradlew run -Pklyph.frames=60` one last time; both must pass
- [ ] Commit: "Document the test level"

## Self-review against the PRD

- §2.1 structure/deformation → Tasks 2, 9. §2.2 movement/jump/bottom lift/squeeze → Tasks 4, 5.
- §3 zones A–D with every listed feature → Task 7; "dynamic obstacles" = crusher (Tasks 6, 7, 10); "bouncing pads" = `J`; "sloped surfaces" = `/` `\`.
- §4 input latency, stability, 60% squeeze, no clipping → Task 5 tests, each named after its criterion.
- Not in the PRD, added on purpose: zone teleport keys, autopilot and env vars, so a test level can be checked without hands on the keyboard.
