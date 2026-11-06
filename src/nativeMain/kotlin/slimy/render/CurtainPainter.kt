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
        fun line(r0: Int, c0: Int, r1: Int, c1: Int, thickness: Float, color: Color, layer: Int) {
            canvas.drawLine(pixel, curtain.particleX(r0, c0), curtain.particleY(r0, c0), curtain.particleX(r1, c1), curtain.particleY(r1, c1), thickness, color, layer)
        }
        for (r in 1 until rows) for (c in 0 until columns - 1) line(r, c, r, c + 1, BAND_THICKNESS, BAND, BAND_LAYER)
        for (c in 0 until columns) {
            for (r in 0 until rows - 1) line(r, c, r + 1, c, 6.5f, EDGE, EDGE_LAYER)
            for (r in 0 until rows - 1) line(r, c, r + 1, c, 4f, FILL, FILL_LAYER)
            val tipX = curtain.particleX(rows - 1, c)
            val tipY = curtain.particleY(rows - 1, c) + 2f
            canvas.drawSprite(dropEdge, tipX, tipY, color = EDGE, layer = EDGE_LAYER, space = DrawSpace.WORLD)
            canvas.drawSprite(drop, tipX, tipY, color = FILL, layer = FILL_LAYER, space = DrawSpace.WORLD)
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
