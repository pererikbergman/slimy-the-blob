package slimy.physics

/** Scenery that several tests build the same way. */
object TestScenes {
    /**
     * A grid whose floor is [thickness] rows of tiles starting at row [firstRow], so the floor's top face
     * is at y = [firstRow] * TILE. Tiles in the columns of [bouncy] are bounce pads.
     */
    fun flatFloor(cols: Int, rows: Int, firstRow: Int, thickness: Int = 2, bouncy: IntRange = IntRange.EMPTY): SolidGrid =
        SolidGrid(cols, rows).apply {
            for (c in 0 until cols) for (r in firstRow until firstRow + thickness) add(Solid.tile(c, r, isBouncy = r == firstRow && c in bouncy))
        }

    /** A grid filled with solid tiles, to carve tunnels and shafts out of. */
    fun solidEverywhere(cols: Int, rows: Int): SolidGrid = SolidGrid(cols, rows).apply {
        for (c in 0 until cols) for (r in 0 until rows) add(Solid.tile(c, r))
    }
}

/** Empties the tiles in [cols] and [rows], both ranges included. */
fun SolidGrid.carve(cols: IntRange, rows: IntRange) {
    for (c in cols) for (r in rows) removeAt(c, r)
}
