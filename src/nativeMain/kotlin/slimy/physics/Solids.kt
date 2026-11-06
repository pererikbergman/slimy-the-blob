package slimy.physics

import kotlin.math.floor

private const val DIAGONAL = 0.70710678f

/** How a [Solid] fills its rectangle: all of it, or the half under a diagonal surface. */
enum class SolidShape {
    /** The whole rectangle. */
    BOX,

    /** The triangle under a surface that rises to the right, from the bottom-left corner to the top-right. */
    RISING,

    /** The triangle under a surface that falls to the right, from the top-left corner to the bottom-right. */
    FALLING,
}

/**
 * Something the slime cannot enter: a rectangle with the given edges (y grows down) filled as
 * [shape] says. A tile's solid knows its grid cell, so the grid can tell which faces are buried
 * against a neighbour; a moving solid has none. A bouncy one throws the slime back up.
 */
class Solid(
    val shape: SolidShape,
    val left: Float,
    var top: Float,
    val right: Float,
    var bottom: Float,
    val isBouncy: Boolean = false,
    val col: Int = -1,
    val row: Int = -1,
) {
    companion object {
        /** The solid filling grid cell ([col], [row]) as [shape]. */
        fun tile(col: Int, row: Int, shape: SolidShape = SolidShape.BOX, isBouncy: Boolean = false): Solid {
            val t = Tuning.TILE
            return Solid(shape, col * t, row * t, (col + 1) * t, (row + 1) * t, isBouncy, col, row)
        }

        /** A ceiling stub: only the top half of cell ([col], [row]) is solid. */
        fun stub(col: Int, row: Int): Solid {
            val t = Tuning.TILE
            return Solid(SolidShape.BOX, col * t, row * t, (col + 1) * t, row * t + t / 2f, false, col, row)
        }
    }
}

/**
 * The level's solids: one per tile in a grid of cells [Tuning.TILE] wide, plus [dynamic] ones that
 * move. A particle is a point, so it can only be inside the one tile it is in, and [resolve] looks
 * at that tile and at every dynamic solid.
 */
class SolidGrid(val cols: Int, val rows: Int) {
    private val cells = arrayOfNulls<Solid>(cols * rows)

    /** Solids that are not tied to a cell, such as a crusher. Checked every time. */
    val dynamic = ArrayList<Solid>()

    /** The solid in cell ([col], [row]), or `null` for air and for cells outside the grid. */
    fun at(col: Int, row: Int): Solid? = if (col in 0 until cols && row in 0 until rows) cells[row * cols + col] else null

    /** Puts [solid] in its cell. */
    fun add(solid: Solid) {
        cells[solid.row * cols + solid.col] = solid
    }

    /** Empties cell ([col], [row]). */
    fun removeAt(col: Int, row: Int) {
        if (col in 0 until cols && row in 0 until rows) cells[row * cols + col] = null
    }

    /**
     * Pushes every particle of [body] out of the solids, along the way of least penetration. The
     * speed going into the surface is removed and the rest kept, so particles slide along walls and
     * floors. Faces buried against a neighbouring solid are never exits, so a floor made of many
     * tiles has no seams.
     */
    fun resolve(body: SoftBody, contacts: Contacts) {
        val tile = Tuning.TILE
        for (i in 0 until body.size) {
            val col = floor(body.x[i] / tile).toInt()
            val row = floor(body.y[i] / tile).toInt()
            val solid = at(col, row)
            if (solid != null) push(body, i, solid, contacts)
            for (d in dynamic) push(body, i, d, contacts)
        }
    }

    private fun push(body: SoftBody, i: Int, solid: Solid, contacts: Contacts) {
        val px = body.x[i]
        val py = body.y[i]
        var nx: Float
        var ny: Float
        var depth: Float
        when (solid.shape) {
            SolidShape.BOX -> {
                if (px <= solid.left || px >= solid.right || py <= solid.top || py >= solid.bottom) return
                var exit = nearestExit(solid, px, py, respectBuried = true)
                // Buried on every side, such as under the foot of a wall: step toward the nearest face
                // anyway, just past it, and the tile there pushes the particle the rest of the way out.
                if (exit.depth == Float.MAX_VALUE) exit = nearestExit(solid, px, py, respectBuried = false).also { it.depth += 0.01f }
                depth = exit.depth
                nx = exit.nx
                ny = exit.ny
            }
            SolidShape.RISING -> {
                val u = px - solid.left
                val v = solid.bottom - py
                if (u <= v) return
                depth = (u - v) * DIAGONAL
                nx = -DIAGONAL
                ny = -DIAGONAL
            }
            SolidShape.FALLING -> {
                val u = px - solid.left
                val v = solid.bottom - py
                val side = solid.right - solid.left
                if (u + v >= side) return
                depth = (side - u - v) * DIAGONAL
                nx = DIAGONAL
                ny = -DIAGONAL
            }
        }
        var vx = px - body.prevX[i]
        var vy = py - body.prevY[i]
        body.x[i] = px + nx * depth
        body.y[i] = py + ny * depth
        val into = vx * nx + vy * ny
        if (into < 0f) {
            vx -= into * nx
            vy -= into * ny
        }
        body.prevX[i] = body.x[i] - vx
        body.prevY[i] = body.y[i] - vy
        if (ny < -0.5f) {
            contacts.recordFloor(i, solid.isBouncy)
        }
    }

    /** The way out of a box that costs the least movement, reused between calls so nothing allocates. */
    private class Exit {
        var depth = Float.MAX_VALUE
        var nx = 0f
        var ny = 0f
    }

    private val exitScratch = Exit()

    private fun nearestExit(solid: Solid, px: Float, py: Float, respectBuried: Boolean): Exit {
        val exit = exitScratch
        exit.depth = Float.MAX_VALUE
        exit.nx = 0f
        exit.ny = 0f
        val toLeft = px - solid.left
        if (toLeft < exit.depth && !(respectBuried && isBuried(solid, Side.LEFT))) { exit.depth = toLeft; exit.nx = -1f; exit.ny = 0f }
        val toRight = solid.right - px
        if (toRight < exit.depth && !(respectBuried && isBuried(solid, Side.RIGHT))) { exit.depth = toRight; exit.nx = 1f; exit.ny = 0f }
        val toTop = py - solid.top
        if (toTop < exit.depth && !(respectBuried && isBuried(solid, Side.TOP))) { exit.depth = toTop; exit.nx = 0f; exit.ny = -1f }
        val toBottom = solid.bottom - py
        if (toBottom < exit.depth && !(respectBuried && isBuried(solid, Side.BOTTOM))) { exit.depth = toBottom; exit.nx = 0f; exit.ny = 1f }
        return exit
    }

    private enum class Side { LEFT, RIGHT, TOP, BOTTOM }

    /** Whether the [side] face of a tile's [solid] lies against a neighbour that fills the whole face. */
    private fun isBuried(solid: Solid, side: Side): Boolean {
        if (solid.col < 0) return false
        val neighbour = when (side) {
            Side.LEFT -> at(solid.col - 1, solid.row)
            Side.RIGHT -> at(solid.col + 1, solid.row)
            Side.TOP -> at(solid.col, solid.row - 1)
            Side.BOTTOM -> at(solid.col, solid.row + 1)
        } ?: return false
        return when (side) {
            Side.LEFT -> neighbour.shape != SolidShape.FALLING && spansHeight(neighbour, solid) && neighbour.right == solid.left
            Side.RIGHT -> neighbour.shape != SolidShape.RISING && spansHeight(neighbour, solid) && neighbour.left == solid.right
            Side.TOP -> neighbour.bottom == solid.top && neighbour.left <= solid.left && neighbour.right >= solid.right
            Side.BOTTOM -> neighbour.shape == SolidShape.BOX && neighbour.top == solid.bottom &&
                neighbour.left <= solid.left && neighbour.right >= solid.right
        }
    }

    private fun spansHeight(neighbour: Solid, solid: Solid): Boolean = neighbour.top <= solid.top && neighbour.bottom >= solid.bottom
}
