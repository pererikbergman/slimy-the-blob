package slimy.render

import klyph.physics.Outline
import klyph.platform.Color
import klyph.render.Canvas
import klyph.render.DrawSpace
import klyph.render.TextureRegion
import slimy.physics.Slime
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws the slime on the canvas, in world space, from its particles: the inside filled one pixel
 * row at a time, a thin outline along every edge of the ring, and a face on top. klyph has no
 * polygons, so rows of [pixel] stretched into rectangles stand in for one.
 */
class SlimePainter(private val pixel: TextureRegion, private val face: TextureRegion) {
    private var spanLeft = FloatArray(0)
    private var spanRight = FloatArray(0)

    /** Draws [slime]; with [showParticles] also a dot on every particle, red for the core. */
    fun paint(canvas: Canvas, slime: Slime, showParticles: Boolean) {
        val body = slime.body
        if (spanLeft.size < body.ringCount) {
            spanLeft = FloatArray(body.ringCount)
            spanRight = FloatArray(body.ringCount)
        }
        val top = floor(body.minY()).toInt()
        val bottom = ceil(body.maxY()).toInt()
        val height = (bottom - top).coerceAtLeast(1)

        for (row in top until bottom) {
            val count = Outline.spans(body, row + 0.5f, spanLeft, spanRight)
            val shade = (row - top).toFloat() / height
            val color = if (shade < 0.25f) LIGHT else if (shade < 0.7f) MIDDLE else DARK
            for (k in 0 until count) {
                val left = floor(spanLeft[k])
                val right = ceil(spanRight[k])
                canvas.drawRect(x = left, y = row.toFloat(), width = right - left, height = 1f, color = color, layer = FILL_LAYER, space = DrawSpace.WORLD)
            }
        }

        val n = body.ringCount
        for (i in 0 until n) {
            val j = body.next(i)
            canvas.drawLine(pixel, body.positionX(i), body.positionY(i), body.positionX(j), body.positionY(j), 1.5f, OUTLINE, OUTLINE_LAYER)
        }

        canvas.drawSprite(
            face,
            x = body.centerX() + slime.facing * 3f,
            y = body.centerY() - 1f,
            layer = FACE_LAYER,
            space = DrawSpace.WORLD,
        )

        if (showParticles) {
            for (i in 0 until body.size) {
                canvas.drawRect(body.positionX(i) - 1f, body.positionY(i) - 1f, 2f, 2f, if (body.isCore(i)) RED else WHITE, DEBUG_LAYER, DrawSpace.WORLD)
            }
        }
    }

    private companion object {
        const val FILL_LAYER = 10
        const val OUTLINE_LAYER = 11
        const val FACE_LAYER = 12
        const val DEBUG_LAYER = 13
        val LIGHT = Color(0x9BE37AFFu)
        val MIDDLE = Color(0x62C45CFFu)
        val DARK = Color(0x3F9A53FFu)
        val OUTLINE = Color(0x1D5A3AFFu)
        val RED = Color(0xFF3030FFu)
        val WHITE = Color(0xFFFFFFFFu)
    }
}
