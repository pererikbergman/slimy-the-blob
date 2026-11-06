package slimy.level

/**
 * The test level: four zones in a row, each built to try one thing the slime must do. Everything
 * is in tiles of 16 units, 240 across and 24 down, with the ground's top at row 20 and a wall at
 * each end. Row numbers below are counted from the top.
 */
object TestLevel {
    const val COLS = 240
    const val ROWS = 24

    /** The row the ground's top face lies on. */
    private const val GROUND = 20

    val level: Level by lazy { Level.parse(map().rows()) }

    fun map(): LevelMap {
        val map = LevelMap(COLS, ROWS)
        map.fill(0, GROUND, COLS - 1, ROWS - 1, '#')
        map.fill(0, 0, 0, ROWS - 1, '#')
        map.fill(COLS - 1, 0, COLS - 1, ROWS - 1, '#')
        calibrationChamber(map)
        verticalAscent(map)
        squeezeCorridor(map)
        kineticGauntlet(map)
        return map
    }

    /** Zone A, columns 0 to 39: flat ground, a one-tile step and a two-tile block to hop over. */
    private fun calibrationChamber(map: LevelMap) {
        map.put(2, 19, '1')
        map.put(4, 19, 'S')
        map.fill(14, 19, 15, 19, '#')
        map.fill(24, 18, 25, 19, '#')
    }

    /** Zone B, columns 40 to 99: pits of two, three and four tiles, steps up and down, and a low wall. */
    private fun verticalAscent(map: LevelMap) {
        map.put(41, 19, '2')
        map.clear(44, GROUND, 45, ROWS - 1)
        map.clear(50, GROUND, 52, ROWS - 1)
        map.clear(57, GROUND, 60, ROWS - 1)
        map.fill(65, 18, 68, 19, '#')
        map.fill(69, 16, 72, 19, '#')
        map.fill(73, 14, 76, 19, '#')
        map.fill(77, 17, 80, 19, '#')
        map.fill(90, 18, 91, 19, '#')
    }

    /**
     * Zone C, columns 100 to 159: a cave whose ceiling comes down to three tunnels, 2 tiles, 1 tile
     * and half a tile high, then steps up to a deck with a one-tile shaft through it. The deck ends in
     * a wall, so the way on is down the shaft and along a two-tile corridor under the deck.
     */
    private fun squeezeCorridor(map: LevelMap) {
        map.put(101, 19, '3')
        map.fill(104, 0, 107, 14, '#')
        map.fill(108, 0, 111, 16, '#')
        map.fill(112, 0, 137, 17, '#')
        map.fill(118, 18, 125, 18, '#')
        map.fill(130, 18, 137, 18, '#')
        map.fill(130, 19, 137, 19, 'v')
        map.fill(138, 0, 149, 11, '#')
        map.fill(138, 18, 139, 19, '#')
        map.fill(140, 16, 141, 19, '#')
        map.fill(142, 16, 149, 17, '#')
        map.clear(146, 16, 146, 17)
        map.fill(150, 0, 159, 17, '#')
    }

    /**
     * Zone D, columns 160 to 239: a six-tile hill of 45 degree slopes, two bounce pads under a ledge
     * only a bounce reaches, and a crusher that swings down to eight units above the ground.
     */
    private fun kineticGauntlet(map: LevelMap) {
        map.put(161, 19, '4')
        for (k in 0..5) {
            map.put(166 + k, 19 - k, '/')
            if (k > 0) map.fill(166 + k, 20 - k, 166 + k, 19, '#')
        }
        map.fill(172, 14, 177, 19, '#')
        for (k in 0..5) {
            map.put(178 + k, 14 + k, '\\')
            if (k < 5) map.fill(178 + k, 15 + k, 178 + k, 19, '#')
        }
        map.put(191, GROUND, 'J')
        map.put(193, GROUND, 'J')
        map.fill(195, 13, 202, 14, '#')
        map.fill(206, 0, 215, 10, '#')
        map.put(210, 11, 'M')
        map.put(232, 19, 'F')
    }
}
