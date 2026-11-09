package slimy

import klyph.app.App
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.entity.Transform
import klyph.input.axis
import klyph.math.Rect
import klyph.platform.Color
import klyph.platform.SamplerFilter
import klyph.platform.Scancode
import klyph.platform.Sdl
import klyph.render.DrawSpace
import klyph.render.Sprite
import klyph.render.asRegion
import klyph.render.follow
import klyph.render.lookAt
import klyph.text.Text
import klyph.text.TextAlign
import klyph.text.TextShadow
import slimy.level.Level
import slimy.level.TestLevel
import slimy.render.CurtainPainter
import slimy.render.SlimePainter

/** Camera zoom: 3 points per world unit shows 427 x 240 units, 27 x 15 tiles, in a 1280 x 720 window. */
private const val ZOOM = 3f

/** How much of the camera's movement the sky follows: less than 1 so it seems far away. */
private const val SKY_PARALLAX = 0.4f

/** The keys that go to zone 1, 2, ...; a level with more zones than keys leaves the rest unbound. */
private val ZONE_KEYS = listOf(
    Scancode.DIGIT_1, Scancode.DIGIT_2, Scancode.DIGIT_3, Scancode.DIGIT_4, Scancode.DIGIT_5,
    Scancode.DIGIT_6, Scancode.DIGIT_7, Scancode.DIGIT_8, Scancode.DIGIT_9,
)

private const val SKY_WIDTH = 480f
private const val SKY_HEIGHT = 240f

/** Builds the test level, the slime and everything that runs them into this app. */
fun App.installGame() {
    val level = TestLevel.level
    val start = checkNotNull(level.start) { "The test level has no start" }
    val tile = Level.TILE

    // The level's tiles, drawn once as sprites.
    val sheet = assets.spriteSheet("tiles.png", columns = 6, rows = 1, filter = SamplerFilter.NEAREST)
    for (placed in level.tiles) {
        val entity = world.create()
        world.add(entity, Transform, Transform(x = (placed.col + 0.5f) * tile, y = (placed.row + 0.5f) * tile))
        world.add(entity, Sprite, Sprite(sheet[placed.tile]))
    }

    // Crushers move, so each keeps hold of its transform to update.
    val crusherRegion = assets.texture("crusher.png", SamplerFilter.NEAREST).asRegion()
    val crusherTransforms = level.crushers.map { crusher ->
        val entity = world.create()
        val transform = Transform()
        world.add(entity, Transform, transform)
        world.add(entity, Sprite, Sprite(crusherRegion, layer = 1))
        transform to crusher
    }

    // Signs for the zones and the finish: a post and a name above it.
    val post = Color(0x6B4A3CFFu)
    val shadow = TextShadow()
    // A world label is drawn at the camera's zoom, so its shadow must be small to stay crisp.
    val signShadow = TextShadow(offsetX = 0.5f, offsetY = 0.5f)
    fun sign(x: Float, y: Float, text: String, flag: Color) {
        val postEntity = world.create()
        world.add(postEntity, Transform, Transform(x = x, y = y - 12f))
        world.add(postEntity, Sprite, Sprite(assets.whitePixel, width = 2f, height = 24f, color = post, layer = 2))
        val flagEntity = world.create()
        world.add(flagEntity, Transform, Transform(x = x + 5f, y = y - 20f))
        world.add(flagEntity, Sprite, Sprite(assets.whitePixel, width = 8f, height = 5f, color = flag, layer = 2))
        val label = world.create()
        world.add(label, Transform, Transform(x = x - 4f, y = y - 46f))
        world.add(label, Text, Text(assets.defaultFont, text, scale = 1f, align = TextAlign.LEFT, layer = 3, shadow = signShadow))
    }
    for (zone in level.zones) sign(zone.x, zone.y, "ZONE ${zone.id}\n${zone.name}", Color(0xE0702FFFu))
    level.finish?.let { sign(it.x, it.y, "FINISH", Color(0xFFD36BFFu)) }

    val sky = assets.texture("bg.png", SamplerFilter.NEAREST).asRegion()
    val session = GameSession(level)
    val slime = session.slime
    val painter = SlimePainter(assets.whitePixel, assets.texture("face.png", SamplerFilter.NEAREST).asRegion())

    // Curtains of red slime that the player can push through.
    val curtainPainter = CurtainPainter(assets.whitePixel, assets.disc(9), assets.disc(12))

    camera.zoom = ZOOM
    camera.bounds = Rect(x = 0f, y = 0f, width = level.width, height = level.height)

    fun restartInZone(id: Char?) {
        session.restartInZone(id)
        camera.lookAt(slime.body.centerX(), slime.body.centerY())
    }
    restartInZone(Sdl.environmentVariable("SLIME_ZONE")?.firstOrNull()?.uppercaseChar())

    val isAutopilot = Sdl.environmentVariable("KLYPH_AUTOPILOT") == "1"
    var showParticles = false
    var autopilotSteps = 0

    addSystem(Phase.INPUT) { app ->
        val keyboard = app.input.keyboard
        if (keyboard.isJustPressed(Scancode.ESCAPE)) app.quit()
        if (keyboard.isJustPressed(Scancode.F2)) showParticles = !showParticles
        if (keyboard.isJustPressed(Scancode.R)) restartInZone(session.zone?.id)
        for ((index, key) in ZONE_KEYS.withIndex()) {
            if (keyboard.isJustPressed(key)) level.zoneByNumber(index + 1)?.let { restartInZone(it.id) }
        }

        session.setMove((keyboard.axis(Scancode.LEFT, Scancode.RIGHT) + keyboard.axis(Scancode.A, Scancode.D)).coerceIn(-1f, 1f))
        session.setJumpHeld(keyboard.isDown(Scancode.SPACE) || keyboard.isDown(Scancode.UP) || keyboard.isDown(Scancode.W))
        if (keyboard.isJustPressed(Scancode.SPACE) || keyboard.isJustPressed(Scancode.UP) || keyboard.isJustPressed(Scancode.W)) {
            session.pressJump()
        }
    }

    addSystem(Phase.FIXED_UPDATE) { app ->
        if (isAutopilot) {
            // Runs right and hops every 1.2 seconds, holding jump, which is enough to see each zone.
            autopilotSteps++
            session.setMove(1f)
            session.setJumpHeld(true)
            if (slime.isGrounded && autopilotSteps % 72 == 0) session.pressJump()
        }
        // A slime that fell out of the level was put back; the camera goes with it rather than gliding there.
        if (session.step(app.time.deltaSeconds)) app.camera.lookAt(slime.body.centerX(), slime.body.centerY())
    }

    addSystem(Phase.UPDATE) {
        for ((transform, crusher) in crusherTransforms) {
            transform.x = (crusher.solid.left + crusher.solid.right) / 2f
            transform.y = (crusher.solid.top + crusher.solid.bottom) / 2f
        }
    }

    addSystem(Phase.LATE_UPDATE) { app ->
        app.camera.follow(slime.body.centerX(), slime.body.centerY(), smoothing = 6f, delta = app.time.delta)
    }

    addSystem(Phase.RENDER) { app ->
        val canvas = app.canvas
        val camera = app.camera
        val font = app.assets.defaultFont

        // The sky, drawn in the world a little behind the camera's own movement so it scrolls slowly.
        val shift = (camera.x * SKY_PARALLAX) % SKY_WIDTH
        for (copy in -1..1) {
            canvas.drawSprite(
                sky,
                x = camera.x - shift + copy * SKY_WIDTH,
                y = camera.y,
                width = SKY_WIDTH,
                height = SKY_HEIGHT,
                layer = -100,
                space = DrawSpace.WORLD,
            )
        }

        for (curtain in session.curtains) curtainPainter.paint(canvas, curtain)
        painter.paint(canvas, slime, showParticles)

        val zone = session.zone
        if (zone != null) canvas.drawText(font, "ZONE ${zone.id}: ${zone.name}", x = 16f, y = 14f, scale = 3f, layer = 1, shadow = shadow)
        canvas.drawText(font, "A/D or arrows: move   Space/W/Up: jump   Esc: quit", x = 16f, y = app.window.height - 56f, scale = 2f, layer = 1, shadow = shadow)
        canvas.drawText(font, "1-${level.zones.size}: go to zone   R: restart zone   F2: particles   F3: stats", x = 16f, y = app.window.height - 32f, scale = 2f, layer = 1, shadow = shadow)
        if (session.isFinished) {
            canvas.drawText(font, "LEVEL COMPLETE", x = app.window.width / 2f, y = app.window.height / 2f - 24f, scale = 6f, align = TextAlign.CENTER, layer = 2, shadow = shadow)
        }
    }
}
