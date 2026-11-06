package slimy.level

/**
 * A level drawn as characters, one per tile, for [Level.parse] to read. `.` is air; the other
 * characters are listed on [Level]. Drawing outside the map throws, so a typo in a coordinate
 * does not quietly vanish.
 */
class LevelMap(val cols: Int, val rows: Int) {
    private val cells = Array(rows) { CharArray(cols) { '.' } }

    /** Puts [char] on the tile at ([col], [row]). */
    fun put(col: Int, row: Int, char: Char) {
        require(col in 0 until cols && row in 0 until rows) { "($col, $row) is outside a $cols x $rows map" }
        cells[row][col] = char
    }

    /** Puts [char] on every tile from ([col0], [row0]) to ([col1], [row1]), both included. */
    fun fill(col0: Int, row0: Int, col1: Int, row1: Int, char: Char) {
        for (row in row0..row1) for (col in col0..col1) put(col, row, char)
    }

    /** Empties every tile from ([col0], [row0]) to ([col1], [row1]), both included. */
    fun clear(col0: Int, row0: Int, col1: Int, row1: Int) = fill(col0, row0, col1, row1, '.')

    /** The map as one string per row, top first. */
    fun rows(): List<String> = cells.map { it.concatToString() }
}
