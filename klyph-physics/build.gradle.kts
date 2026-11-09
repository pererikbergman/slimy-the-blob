// Soft-body physics for klyph games, kept apart from the game so it can move into the engine as is.
// Pure Kotlin: it depends on neither the game nor klyph, so everything lives in commonMain. The
// targets are the ones the klyph plugin builds for; Kotlin comes from the root project's classpath.
plugins { kotlin("multiplatform") }

kotlin {
    macosArm64()
    linuxX64()
    linuxArm64()
    mingwX64()
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
