package com.theblocks.app.engine

import kotlin.random.Random

/** Board is always 8x8, like the game this is inspired by. */
const val BOARD_SIZE = 8
const val TRAY_SIZE = 3
const val EMPTY = -1
const val COLOR_COUNT = 6

/** A cell offset inside a piece, relative to the piece's top-left corner. */
data class CellOffset(val x: Int, val y: Int)

/** A polyomino sitting in the tray. */
class Piece(
    val cells: List<CellOffset>,
    val colorIndex: Int,
    val id: Int,
) {
    val width: Int = cells.maxOf { it.x } + 1
    val height: Int = cells.maxOf { it.y } + 1
    val size: Int get() = cells.size

    fun contains(x: Int, y: Int): Boolean = cells.any { it.x == x && it.y == y }

    override fun toString(): String = "Piece(id=$id, ${width}x$height, cells=$cells)"
}

/**
 * All the shapes that can show up. Each base shape is expanded into its unique
 * rotations, so L/J/S/Z/T pieces all exist in every orientation.
 */
object PieceLibrary {

    private data class ShapeDef(val cells: List<CellOffset>, val weight: Int)

    private fun cells(spec: String): List<CellOffset> =
        spec.trim().split(" ").map { part ->
            val (x, y) = part.split(",").map { it.toInt() }
            CellOffset(x, y)
        }

    private fun normalize(cells: List<CellOffset>): List<CellOffset> {
        val minX = cells.minOf { it.x }
        val minY = cells.minOf { it.y }
        return cells
            .map { CellOffset(it.x - minX, it.y - minY) }
            .sortedWith(compareBy({ it.y }, { it.x }))
    }

    private fun rotations(shape: List<CellOffset>): List<List<CellOffset>> {
        val out = mutableListOf<List<CellOffset>>()
        var current = normalize(shape)
        repeat(4) {
            if (out.none { it == current }) out.add(current)
            current = normalize(current.map { CellOffset(-it.y, it.x) })
        }
        return out
    }

    private val defs = listOf(
        ShapeDef(cells("0,0"), 3),                                          // single
        ShapeDef(cells("0,0 1,0"), 9),                                      // domino
        ShapeDef(cells("0,0 1,0 2,0"), 9),                                  // I3
        ShapeDef(cells("0,0 1,0 2,0 3,0"), 8),                              // I4
        ShapeDef(cells("0,0 1,0 2,0 3,0 4,0"), 4),                          // I5
        ShapeDef(cells("0,0 1,0 0,1 1,1"), 9),                              // O2
        ShapeDef(cells("0,0 1,0 2,0 0,1 1,1 2,1"), 4),                      // 3x2
        ShapeDef(cells("0,0 1,0 2,0 0,1 1,1 2,1 0,2 1,2 2,2"), 2),          // 3x3
        ShapeDef(cells("0,0 0,1 1,1"), 9),                                  // corner L3
        ShapeDef(cells("0,0 0,1 0,2 1,2"), 8),                              // L4
        ShapeDef(cells("0,0 0,1 0,2 0,3 1,3"), 5),                          // big L5
        ShapeDef(cells("0,0 1,0 2,0 1,1"), 8),                              // T4
        ShapeDef(cells("0,1 1,1 1,0 2,0"), 6),                              // S
        ShapeDef(cells("0,0 1,0 1,1 2,1"), 6),                              // Z
    )

    /** Flat, weighted list of every orientation of every shape. */
    private val pool: List<Pair<List<CellOffset>, Int>> = defs.flatMap { def ->
        rotations(def.cells).map { shape -> shape to def.weight }
    }

    private val totalWeight: Int = pool.sumOf { it.second }

    fun randomShape(random: Random): List<CellOffset> {
        var roll = random.nextInt(totalWeight)
        for ((shape, weight) in pool) {
            if (roll < weight) return shape
            roll -= weight
        }
        return pool.last().first
    }

    fun randomPiece(random: Random, id: Int): Piece = Piece(
        cells = randomShape(random),
        colorIndex = random.nextInt(COLOR_COUNT),
        id = id,
    )

    /** Total number of distinct orientations available (used by tests). */
    fun orientationCount(): Int = pool.size
}
