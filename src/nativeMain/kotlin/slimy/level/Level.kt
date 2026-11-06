package slimy.level

import slimy.physics.Solid
import slimy.physics.SolidGrid
import slimy.physics.SolidShape
import slimy.physics.Tuning
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
) {
    private var seconds = 0f

    val width: Float get() = cols * Tuning.TILE
    val height: Float get() = rows * Tuning.TILE

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
            var start: Spot? = null
            var finish: Spot? = null
            val t = Tuning.TILE

            fun solid(col: Int, row: Int, tile: Int, shape: SolidShape = SolidShape.BOX, bouncy: Boolean = false) {
                grid.add(Solid.tile(col, row, shape, bouncy))
                tiles.add(PlacedTile(col, row, tile))
            }

            for (row in rows.indices) for (col in 0 until cols) {
                val spot = Spot((col + 0.5f) * t, (row + 1) * t)
                when (val char = rows[row][col]) {
                    '.' -> {}
                    '#' -> solid(col, row, if (row > 0 && rows[row - 1][col] == '#') Tiles.DIRT else Tiles.GRASS)
                    'v' -> { grid.add(Solid.stub(col, row)); tiles.add(PlacedTile(col, row, Tiles.STUB)) }
                    '/' -> solid(col, row, Tiles.SLOPE_UP, SolidShape.RISING)
                    '\\' -> solid(col, row, Tiles.SLOPE_DOWN, SolidShape.FALLING)
                    'J' -> solid(col, row, Tiles.PAD, bouncy = true)
                    'S' -> start = spot
                    'F' -> finish = spot
                    in '1'..'4' -> {
                        val id = 'A' + (char - '1')
                        zones.add(Zone(id, ZONE_NAMES.getValue(id), spot.x, spot.y))
                    }
                    'M' -> crushers.add(crusherAt(rows, col, row, grid))
                    else -> throw IllegalArgumentException("Unknown tile '$char' at ($col, $row)")
                }
            }
            return Level(cols, rows.size, grid, tiles, start, finish, zones, crushers)
        }

        private fun crusherAt(rows: List<String>, col: Int, row: Int, grid: SolidGrid): Crusher {
            val t = Tuning.TILE
            var floorRow = row + 2
            while (floorRow < rows.size && rows[floorRow][col] in ".MSF1234") floorRow++
            require(floorRow < rows.size) { "The crusher at ($col, $row) has no floor below it" }
            val restTop = row * t
            val travel = floorRow * t - Crusher.GAP - (restTop + 2 * t)
            require(travel > 0f) { "The crusher at ($col, $row) is too close to its floor to swing" }
            val solid = Solid(SolidShape.BOX, col * t, restTop, (col + 2) * t, restTop + 2 * t)
            grid.dynamic.add(solid)
            return Crusher(solid, restTop, travel)
        }
    }
}
