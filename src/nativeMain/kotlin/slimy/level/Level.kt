package slimy.level

import slimy.physics.Solid
import slimy.physics.SolidGrid
import slimy.physics.SolidShape
import kotlin.math.PI
import kotlin.math.cos

/** Indexes into `tiles.png`, one 16 x 16 cell each. The crusher has its own picture, `crusher.png`. */
object Tiles {
    const val GRASS = 0
    const val DIRT = 1
    const val STUB = 2
    const val PAD = 3
    const val SLOPE_UP = 4
    const val SLOPE_DOWN = 5
}

/** A tile to draw: its cell and which cell of the tile sheet to draw it with. */
class PlacedTile(val col: Int, val row: Int, val tile: Int)

/** A place where something stands: [x] is the middle of its cell and [y] its bottom edge. */
data class Spot(val x: Float, val y: Float)

/** A sign that names the part of the level to its right, up to the next sign. */
class Zone(val id: Char, val name: String, val x: Float, val y: Float)

/** The characters of a level map, in one place. See [Level] for what each one means. */
private object Legend {
    const val AIR = '.'
    const val GROUND = '#'
    const val STUB = 'v'
    const val RISING = '/'
    const val FALLING = '\\'
    const val PAD = 'J'
    const val CRUSHER = 'M'
    const val CURTAIN = 'r'
    const val START = 'S'
    const val FINISH = 'F'
    const val FIRST_ZONE = '1'
    const val LAST_ZONE = '4'

    /** Whether a cell with this character has no solid in it, so something that falls through the cell keeps falling. */
    fun isOpen(char: Char): Boolean =
        char == AIR || char == CRUSHER || char == CURTAIN || char == START || char == FINISH || char in FIRST_ZONE..LAST_ZONE
}

/** A curtain of hanging red slime from [left] to [right], hanging from height [top]. */
class CurtainSpec(val left: Float, val right: Float, val top: Float)

/**
 * A 2 x 2 tile block that swings down toward the floor and back up. It rests at the top of its
 * swing where the map has its `M` and comes down to leave [Crusher.GAP] above the floor.
 */
class Crusher(val solid: Solid, private val restTop: Float, private val travel: Float) {
    /** Puts the block where it is [seconds] into its swing. */
    fun moveTo(seconds: Float) {
        val swing = (1f - cos(2f * PI.toFloat() * seconds / PERIOD)) / 2f
        val height = solid.bottom - solid.top
        solid.top = restTop + travel * swing
        solid.bottom = solid.top + height
    }

    companion object {
        /** Seconds for a full swing down and back. */
        const val PERIOD = 3.2f

        /** Space left between the block at its lowest and the floor. */
        const val GAP = 8f
    }
}

/**
 * A level read from a [LevelMap]'s rows. The characters:
 *
 * | | |
 * |---|---|
 * | `.` | air |
 * | `#` | solid ground, grass on top |
 * | `v` | ceiling stub: the top half of the tile is solid |
 * | `/` `\` | 45 degree slope that rises or falls to the right |
 * | `J` | bounce pad |
 * | `M` | top-left tile of a crusher, which needs 2 x 2 free tiles and a floor below |
 * | `r` | air where a curtain of red slime hangs; a run of them in a row is one curtain, pinned under the tiles above |
 * | `S` `F` | where the slime starts and where the level ends |
 * | `1` to `4` | the sign that opens zone A to D |
 */
class Level private constructor(
    val cols: Int,
    val rows: Int,
    val grid: SolidGrid,
    val tiles: List<PlacedTile>,
    val start: Spot?,
    val finish: Spot?,
    val zones: List<Zone>,
    val crushers: List<Crusher>,
    val curtains: List<CurtainSpec>,
) {
    private var seconds = 0f

    val width: Float get() = cols * SolidGrid.TILE
    val height: Float get() = rows * SolidGrid.TILE

    /** Moves everything that moves by [dt] seconds. */
    fun advance(dt: Float) {
        seconds += dt
        for (crusher in crushers) crusher.moveTo(seconds)
    }

    /** The zone with this [id], `'A'` to `'D'`, or `null` if the level has none. */
    fun zone(id: Char): Zone? = zones.firstOrNull { it.id == id }

    /** The zone that holds [x]: the last sign at or before it. */
    fun zoneAt(x: Float): Zone? = zones.lastOrNull { it.x <= x } ?: zones.firstOrNull()

    companion object {
        private val ZONE_NAMES = mapOf(
            'A' to "The Calibration Chamber",
            'B' to "The Vertical Ascent",
            'C' to "The Squeeze Corridor",
            'D' to "The Kinetic Gauntlet",
        )

        /** Reads [rows], top row first. Every row must be as long as the first and use only the characters above. */
        fun parse(rows: List<String>): Level {
            val cols = rows.firstOrNull()?.length ?: 0
            for ((index, row) in rows.withIndex()) require(row.length == cols) { "Row $index has ${row.length} tiles, not $cols" }
            val grid = SolidGrid(cols, rows.size)
            val tiles = ArrayList<PlacedTile>()
            val zones = ArrayList<Zone>()
            val crushers = ArrayList<Crusher>()
            val curtains = ArrayList<CurtainSpec>()
            var start: Spot? = null
            var finish: Spot? = null
            val t = SolidGrid.TILE

            fun place(solid: Solid, tile: Int) {
                grid.add(solid)
                tiles.add(PlacedTile(solid.col, solid.row, tile))
            }
            fun solid(col: Int, row: Int, tile: Int, shape: SolidShape = SolidShape.BOX, bouncy: Boolean = false) = place(Solid.tile(col, row, shape, bouncy), tile)

            for (row in rows.indices) for (col in 0 until cols) {
                val spot = Spot((col + 0.5f) * t, (row + 1) * t)
                when (val char = rows[row][col]) {
                    Legend.AIR -> {}
                    Legend.GROUND -> solid(col, row, if (row > 0 && rows[row - 1][col] == Legend.GROUND) Tiles.DIRT else Tiles.GRASS)
                    Legend.STUB -> place(Solid.stub(col, row), Tiles.STUB)
                    Legend.RISING -> solid(col, row, Tiles.SLOPE_UP, SolidShape.RISING)
                    Legend.FALLING -> solid(col, row, Tiles.SLOPE_DOWN, SolidShape.FALLING)
                    Legend.PAD -> solid(col, row, Tiles.PAD, bouncy = true)
                    Legend.START -> start = spot
                    Legend.FINISH -> finish = spot
                    in Legend.FIRST_ZONE..Legend.LAST_ZONE -> {
                        val id = 'A' + (char - Legend.FIRST_ZONE)
                        zones.add(Zone(id, ZONE_NAMES.getValue(id), spot.x, spot.y))
                    }
                    Legend.CRUSHER -> crushers.add(crusherAt(rows, col, row, grid))
                    Legend.CURTAIN -> if (col == 0 || rows[row][col - 1] != Legend.CURTAIN) {
                        var end = col
                        while (end + 1 < cols && rows[row][end + 1] == Legend.CURTAIN) end++
                        curtains.add(CurtainSpec(col * t, (end + 1) * t, row * t))
                    }
                    else -> throw IllegalArgumentException("Unknown tile '$char' at ($col, $row)")
                }
            }
            return Level(cols, rows.size, grid, tiles, start, finish, zones, crushers, curtains)
        }

        private fun crusherAt(rows: List<String>, col: Int, row: Int, grid: SolidGrid): Crusher {
            val t = SolidGrid.TILE
            var floorRow = row + 2
            while (floorRow < rows.size && Legend.isOpen(rows[floorRow][col])) floorRow++
            require(floorRow < rows.size) { "The crusher at ($col, $row) has no floor below it" }
            val restTop = row * t
            val travel = floorRow * t - Crusher.GAP - (restTop + 2 * t)
            require(travel > 0f) { "The crusher at ($col, $row) is too close to its floor to swing" }
            val solid = Solid.moving(col * t, restTop, (col + 2) * t, restTop + 2 * t)
            grid.dynamic.add(solid)
            return Crusher(solid, restTop, travel)
        }
    }
}
