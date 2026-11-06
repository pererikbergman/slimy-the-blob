package slimy

import klyph.app.AppConfig
import klyph.app.runApp

/**
 * Slimy the Blob, a test level for a soft-body slime. Keys: A/D or the arrows to move, Space, W or
 * Up to jump, 1 to 4 to jump to a zone, R to restart the zone, F2 for the slime's particles, F3 for
 * klyph's frame stats, Escape to quit.
 *
 * `AppConfig.fromEnvironment` reads the `KLYPH_*` variables, so `./gradlew run -Pklyph.frames=60`
 * stops after 60 frames and `-Pklyph.screenshot=30:shot.png` saves frame 30; `-Pklyph.autopilot=1`
 * lets the slime run and jump by itself, and `SLIME_ZONE=C` starts in zone C.
 */
fun main() {
    val config = AppConfig.fromEnvironment(AppConfig(title = "Slimy the Blob", width = 1280, height = 720))
    runApp(config) { installGame() }
}
