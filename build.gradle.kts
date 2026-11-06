plugins { id("com.eventslooped.klyph") version "1.0.0" }
klyph { entryPoint = "slimy.main" }
kotlin {
    sourceSets.nativeMain.dependencies { implementation("com.eventslooped.klyph:klyph:1.0.0") }
    sourceSets.nativeTest.dependencies { implementation(kotlin("test")) }
}
