package com.theblocks.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    private fun piece(vararg cells: Pair<Int, Int>, color: Int = 0): Piece = Piece(
        cells = cells.map { CellOffset(it.first, it.second) },
        colorIndex = color,
        id = 1,
    )

    private fun emptyBoard(): IntArray = IntArray(BOARD_SIZE * BOARD_SIZE) { EMPTY }

    private fun filledBoard(): IntArray = IntArray(BOARD_SIZE * BOARD_SIZE) { 0 }

    private fun boardWithHoles(vararg holes: Pair<Int, Int>): IntArray {
        val board = filledBoard()
        for ((x, y) in holes) board[y * BOARD_SIZE + x] = EMPTY
        return board
    }

    @Test
    fun newGameStartsWithEmptyBoardAndThreePieces() {
        val engine = GameEngine(Random(42))
        assertEquals(0, engine.score)
        assertEquals(0, engine.combo)
        assertFalse(engine.gameOver)
        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) {
            assertEquals(EMPTY, engine.cell(x, y))
        }
        val snapshot = engine.snapshot()
        assertEquals(TRAY_SIZE, snapshot.tray.size)
        assertTrue(snapshot.tray.all { it != null })
    }

    @Test
    fun placementMustBeInsideTheBoard() {
        val engine = GameEngine(Random(1))
        engine.setTrayForTest(listOf(piece(0 to 0, 1 to 0), null, null))

        assertTrue(engine.canPlace(piece(0 to 0, 1 to 0), 0, 0))
        assertFalse(engine.canPlace(piece(0 to 0, 1 to 0), 7, 0))
        assertFalse(engine.canPlace(piece(0 to 0, 1 to 0), 0, -1))
        assertTrue(engine.place(0, 7, 0) is PlaceResult.Failure)
    }

    @Test
    fun placementCannotOverlapExistingBlocks() {
        val engine = GameEngine(Random(2))
        engine.setTrayForTest(listOf(piece(0 to 0), piece(0 to 0), null))
        assertTrue(engine.place(0, 3, 3) is PlaceResult.Success)
        assertTrue(engine.place(1, 3, 3) is PlaceResult.Failure)
        assertEquals(0, engine.cell(3, 3))
        assertEquals(EMPTY, engine.cell(4, 3))
    }

    @Test
    fun fillingARowClearsIt() {
        val engine = GameEngine(Random(3))
        engine.setTrayForTest(listOf(piece(0 to 0), null, null))
        val board = emptyBoard()
        for (x in 1 until BOARD_SIZE) board[0 * BOARD_SIZE + x] = 2
        engine.loadBoardForTest(board)

        val result = engine.place(0, 0, 0)
        assertTrue(result is PlaceResult.Success)
        result as PlaceResult.Success
        assertEquals(1, result.linesCleared)
        assertEquals(1, engine.combo)
        for (x in 0 until BOARD_SIZE) assertEquals(EMPTY, engine.cell(x, 0))
        assertTrue(result.clearedCells.size == BOARD_SIZE)
        assertTrue(result.points > 1)
    }

    @Test
    fun fillingAColumnClearsIt() {
        val engine = GameEngine(Random(4))
        engine.setTrayForTest(listOf(piece(0 to 0), null, null))
        val board = emptyBoard()
        for (y in 1 until BOARD_SIZE) board[y * BOARD_SIZE + 5] = 1
        engine.loadBoardForTest(board)

        val result = engine.place(0, 5, 0) as PlaceResult.Success
        assertEquals(1, result.linesCleared)
        for (y in 0 until BOARD_SIZE) assertEquals(EMPTY, engine.cell(5, y))
    }

    @Test
    fun rowAndColumnClearTogether() {
        val engine = GameEngine(Random(5))
        engine.setTrayForTest(listOf(piece(0 to 0), null, null))
        val board = emptyBoard()
        for (x in 1 until BOARD_SIZE) board[x] = 3
        for (y in 1 until BOARD_SIZE) board[y * BOARD_SIZE] = 3
        engine.loadBoardForTest(board)

        val result = engine.place(0, 0, 0) as PlaceResult.Success
        assertEquals(2, result.linesCleared)
        assertEquals(2 * BOARD_SIZE - 1, result.clearedCells.size)
        for (x in 0 until BOARD_SIZE) assertEquals(EMPTY, engine.cell(x, 0))
        for (y in 0 until BOARD_SIZE) assertEquals(EMPTY, engine.cell(0, y))
    }

    @Test
    fun comboGrowsOnConsecutiveClearsAndResetsOtherwise() {
        val engine = GameEngine(Random(6))
        engine.setTrayForTest(listOf(piece(0 to 0), piece(0 to 0), piece(0 to 0)))
        val board = emptyBoard()
        for (x in 1 until BOARD_SIZE) {
            board[0 * BOARD_SIZE + x] = 1
            board[1 * BOARD_SIZE + x] = 1
        }
        engine.loadBoardForTest(board)

        val first = engine.place(0, 0, 0) as PlaceResult.Success
        assertEquals(1, first.combo)
        val second = engine.place(1, 0, 1) as PlaceResult.Success
        assertEquals(2, second.combo)
        assertTrue("combo should increase the reward", second.points > first.points)

        val quiet = engine.place(2, 5, 5) as PlaceResult.Success
        assertEquals(0, quiet.combo)
        assertEquals(1, quiet.points)
    }

    @Test
    fun trayRefillsAfterAllThreePiecesAreUsed() {
        val engine = GameEngine(Random(7))
        engine.setTrayForTest(listOf(piece(0 to 0), piece(0 to 0), piece(0 to 0)))

        assertTrue(engine.place(0, 0, 0) is PlaceResult.Success)
        assertNull(engine.pieceAt(0))
        assertNotNull(engine.pieceAt(1))
        assertTrue(engine.place(1, 2, 0) is PlaceResult.Success)
        assertTrue(engine.place(2, 4, 0) is PlaceResult.Success)

        assertTrue("tray should be full again", (0 until TRAY_SIZE).all { engine.pieceAt(it) != null })
        assertFalse(engine.gameOver)
    }

    @Test
    fun gameIsOverWhenNothingFits() {
        val engine = GameEngine(Random(8))
        val big = piece(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2)
        engine.setTrayForTest(listOf(piece(0 to 0), big, big))
        // Every row and column keeps at least one hole, so filling (0,0) completes no line,
        // and the scattered holes leave no room for a 3x3 piece.
        engine.loadBoardForTest(
            boardWithHoles(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6, 7 to 7),
        )

        val result = engine.place(0, 0, 0) as PlaceResult.Success
        assertEquals(0, result.linesCleared)
        assertTrue(result.gameOver)
        assertTrue(engine.gameOver)
        assertTrue(engine.place(1, 0, 0) is PlaceResult.Failure)
    }

    @Test
    fun restartWipesTheBoardAndScore() {
        val engine = GameEngine(Random(9))
        engine.setTrayForTest(listOf(piece(0 to 0), null, null))
        engine.place(0, 4, 4)
        assertTrue(engine.score > 0)

        engine.newGame()
        assertEquals(0, engine.score)
        assertEquals(0, engine.combo)
        assertFalse(engine.gameOver)
        assertEquals(EMPTY, engine.cell(4, 4))
        assertTrue((0 until TRAY_SIZE).all { engine.pieceAt(it) != null })
    }

    @Test
    fun pieceLibraryOnlyProducesSaneShapes() {
        val random = Random(1234)
        repeat(500) {
            val piece = PieceLibrary.randomPiece(random, it)
            assertTrue("piece should have cells", piece.cells.isNotEmpty())
            assertTrue("piece should fit the board", piece.width <= BOARD_SIZE && piece.height <= BOARD_SIZE)
            assertEquals("cells should be normalized", 0, piece.cells.minOf { c -> c.x })
            assertEquals("cells should be normalized", 0, piece.cells.minOf { c -> c.y })
            assertEquals("cells should be unique", piece.cells.size, piece.cells.toSet().size)
            assertTrue(piece.colorIndex in 0 until COLOR_COUNT)
        }
    }

    @Test
    fun hasAnyMoveIsFalseOnAFullBoard() {
        val engine = GameEngine(Random(10))
        engine.setTrayForTest(listOf(piece(0 to 0), piece(0 to 0), piece(0 to 0)))
        engine.loadBoardForTest(filledBoard())
        assertFalse(engine.hasAnyMove())
    }
}
