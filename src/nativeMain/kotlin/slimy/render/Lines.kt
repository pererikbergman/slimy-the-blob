package slimy.render

import klyph.platform.Color
import klyph.render.Canvas
import klyph.render.DrawSpace
import klyph.render.TextureRegion
import kotlin.math.atan2
import kotlin.math.sqrt

/** Draws a straight line of [thickness] world units between two points, as one stretched, turned quad of [pixel]. */
fun Canvas.drawLine(pixel: TextureRegion, x0: Float, y0: Float, x1: Float, y1: Float, thickness: Float, color: Color, layer: Int) {
    val dx = x1 - x0
    val dy = y1 - y0
    drawSprite(
        pixel,
        x = (x0 + x1) / 2f,
        y = (y0 + y1) / 2f,
        width = sqrt(dx * dx + dy * dy) + 1f,
        height = thickness,
        rotation = atan2(dy, dx),
        color = color,
        layer = layer,
        space = DrawSpace.WORLD,
    )
}
