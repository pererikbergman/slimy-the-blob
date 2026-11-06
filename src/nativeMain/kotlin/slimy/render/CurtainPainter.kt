package slimy.render

import klyph.platform.Color
import klyph.render.Canvas
import klyph.render.DrawSpace
import klyph.render.TextureRegion
import slimy.physics.Curtain

/**
 * Draws a [Curtain] as dripping red slime: a translucent band between each pair of strands for the
 * cloth, a dark-edged strand down each, and a round drop on the end of every strand.
 */
class CurtainPainter(private val pixel: TextureRegion, private val drop: TextureRegion, private val dropEdge: TextureRegion) {
    fun paint(canvas: Canvas, curtain: Curtain) {
        val columns = curtain.columns
        val rows = curtain.rows
        for (r in 1 until rows) for (c in 0 until columns - 1) {
            val a = r * columns + c
            val b = a + 1
            canvas.drawLine(pixel, curtain.x[a], curtain.y[a], curtain.x[b], curtain.y[b], BAND_THICKNESS, BAND, BAND_LAYER)
        }
        for (c in 0 until columns) {
            for (r in 0 until rows - 1) {
                val a = r * columns + c
                val b = a + columns
                canvas.drawLine(pixel, curtain.x[a], curtain.y[a], curtain.x[b], curtain.y[b], 6.5f, EDGE, EDGE_LAYER)
            }
            for (r in 0 until rows - 1) {
                val a = r * columns + c
                val b = a + columns
                canvas.drawLine(pixel, curtain.x[a], curtain.y[a], curtain.x[b], curtain.y[b], 4f, FILL, FILL_LAYER)
            }
            val tip = (rows - 1) * columns + c
            canvas.drawSprite(dropEdge, curtain.x[tip], curtain.y[tip] + 2f, color = EDGE, layer = EDGE_LAYER, space = DrawSpace.WORLD)
            canvas.drawSprite(drop, curtain.x[tip], curtain.y[tip] + 2f, color = FILL, layer = FILL_LAYER, space = DrawSpace.WORLD)
        }
    }

    private companion object {
        const val BAND_LAYER = 6
        const val EDGE_LAYER = 7
        const val FILL_LAYER = 8
        const val BAND_THICKNESS = 11f
        val BAND = Color(0xB82C38C8u)
        val EDGE = Color(0x7A1B22FFu)
        val FILL = Color(0xE9453CFFu)
    }
}
