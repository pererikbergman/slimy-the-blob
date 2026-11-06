package slimy.physics

import kotlin.math.floor

private const val DIAGONAL = 0.70710678f

/**
 * A surface whose normal points up by more than this (y grows down, so the normal's y is below it)
 * counts as a floor. A 45 degree slope's normal is at -0.707, so a slime standing on one is grounded.
 */
private const val MIN_FLOOR_NORMAL_Y = -0.5f

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
 * against a neighbour; a [moving] one has none. A bouncy one throws the slime back up.
 */
class Solid private constructor(
    val shape: SolidShape,
    val left: Float,
    var top: Float,
    val right: Float,
    var bottom: Float,
    val isBouncy: Boolean = false,
    val col: Int = -1,
    val row: Int = -1,
) {
    /** Whether this solid moves, such as a crusher, rather than filling a grid cell. It has no neighbours to bury its faces against. */
    val isMoving: Boolean get() = col < 0

    companion object {
        /** A solid that is not tied to a grid cell, filling the given edges; its [top] and [bottom] may change. */
        fun moving(left: Float, top: Float, right: Float, bottom: Float): Solid = Solid(SolidShape.BOX, left, top, right, bottom)

        /** The solid filling grid cell ([col], [row]) as [shape]. */
        fun tile(col: Int, row: Int, shape: SolidShape = SolidShape.BOX, isBouncy: Boolean = false): Solid {
            val t = SolidGrid.TILE
            return Solid(shape, col * t, row * t, (col + 1) * t, (row + 1) * t, isBouncy, col, row)
        }

        /** A ceiling stub: only the top half of cell ([col], [row]) is solid. */
        fun stub(col: Int, row: Int): Solid {
            val t = SolidGrid.TILE
            return Solid(SolidShape.BOX, col * t, row * t, (col + 1) * t, row * t + t / 2f, false, col, row)
        }
    }
}

/**
 * The level's solids: one per tile in a grid of cells [TILE] wide, plus [dynamic] ones that
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
        val tile = TILE
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
        val exit = exitScratch
        val isInside = when (solid.shape) {
            SolidShape.BOX -> exitFromBox(solid, px, py, exit)
            SolidShape.RISING -> exitFromRising(solid, px, py, exit)
            SolidShape.FALLING -> exitFromFalling(solid, px, py, exit)
        }
        if (!isInside) return
        moveOut(body, i, exit)
        if (exit.ny < MIN_FLOOR_NORMAL_Y) contacts.recordFloor(i, solid.isBouncy)
    }

    /** Moves particle [i] out along [exit] and removes the speed it had going into the surface, keeping the rest. */
    private fun moveOut(body: SoftBody, i: Int, exit: Exit) {
        var vx = body.x[i] - body.prevX[i]
        var vy = body.y[i] - body.prevY[i]
        body.x[i] += exit.nx * exit.depth
        body.y[i] += exit.ny * exit.depth
        val into = vx * exit.nx + vy * exit.ny
        if (into < 0f) {
            vx -= into * exit.nx
            vy -= into * exit.ny
        }
        body.prevX[i] = body.x[i] - vx
        body.prevY[i] = body.y[i] - vy
    }

    /** If the point is inside the box, writes the cheapest way out to [exit] and returns `true`. */
    private fun exitFromBox(solid: Solid, px: Float, py: Float, exit: Exit): Boolean {
        if (px <= solid.left || px >= solid.right || py <= solid.top || py >= solid.bottom) return false
        nearestExit(solid, px, py, respectBuried = true)
        // Buried on every side, such as under the foot of a wall: step toward the nearest face
        // anyway, just past it, and the tile there pushes the particle the rest of the way out.
        if (exit.depth == Float.MAX_VALUE) {
            nearestExit(solid, px, py, respectBuried = false)
            exit.depth += 0.01f
        }
        return true
    }

    /** If the point is under a surface that rises to the right, writes the way out, along the surface's normal, and returns `true`. */
    private fun exitFromRising(solid: Solid, px: Float, py: Float, exit: Exit): Boolean {
        val u = px - solid.left
        val v = solid.bottom - py
        if (u <= v) return false
        exit.depth = (u - v) * DIAGONAL
        exit.nx = -DIAGONAL
        exit.ny = -DIAGONAL
        return true
    }

    /** If the point is under a surface that falls to the right, writes the way out, along the surface's normal, and returns `true`. */
    private fun exitFromFalling(solid: Solid, px: Float, py: Float, exit: Exit): Boolean {
        val u = px - solid.left
        val v = solid.bottom - py
        val side = solid.right - solid.left
        if (u + v >= side) return false
        exit.depth = (side - u - v) * DIAGONAL
        exit.nx = DIAGONAL
        exit.ny = -DIAGONAL
        return true
    }

    /** The way out of a box that costs the least movement, reused between calls so nothing allocates. */
    private class Exit {
        var depth = Float.MAX_VALUE
        var nx = 0f
        var ny = 0f
    }

    private val exitScratch = Exit()

    /** Writes to the scratch [Exit] the face of a box that is cheapest to leave through, skipping buried faces when asked. */
    private fun nearestExit(solid: Solid, px: Float, py: Float, respectBuried: Boolean) {
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
    }

    private enum class Side { LEFT, RIGHT, TOP, BOTTOM }

    /** Whether the [side] face of a tile's [solid] lies against a neighbour that fills the whole face. */
    private fun isBuried(solid: Solid, side: Side): Boolean {
        if (solid.isMoving) return false
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

    companion object {
        /** Side of one level tile. */
        const val TILE = 16f
    }
}
