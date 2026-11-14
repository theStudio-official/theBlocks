package com.theblocks.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameSaveTest {

    private fun piece(vararg cells: Pair<Int, Int>, color: Int = 0, id: Int = 1): Piece = Piece(
        cells = cells.map { CellOffset(it.first, it.second) },
        colorIndex = color,
        id = id,
    )

    private fun assertSameGame(expected: SavedGame, actual: SavedGame) {
        assertEquals("board", expected.board, actual.board)
        assertEquals("score", expected.score, actual.score)
        assertEquals("combo", expected.combo, actual.combo)
        assertEquals("lines", expected.lines, actual.lines)
        assertEquals("placements", expected.placements, actual.placements)
        assertEquals("gameOver", expected.gameOver, actual.gameOver)
        assertEquals("tray size", expected.tray.size, actual.tray.size)
        for (i in expected.tray.indices) {
            val a = expected.tray[i]
            val b = actual.tray[i]
            if (a == null || b == null) {
                assertEquals("tray[$i] should be empty in both", a, b)
            } else {
                assertEquals("tray[$i] id", a.id, b.id)
                assertEquals("tray[$i] color", a.colorIndex, b.colorIndex)
                assertEquals("tray[$i] cells", a.cells, b.cells)
                assertEquals("tray[$i] size", a.width to a.height, b.width to b.height)
            }
        }
    }

    @Test
    fun saveAndRestoreKeepsTheWholeGame() {
        val engine = GameEngine(Random(11))
        engine.setTrayForTest(
            listOf(
                piece(0 to 0, 1 to 0, 2 to 0, id = 5, color = 2),
                piece(0 to 0, 0 to 1, id = 6, color = 4),
                piece(0 to 0, id = 7, color = 1),
            ),
        )
        assertTrue(engine.place(0, 2, 3) is PlaceResult.Success)

        val saved = engine.save()
        val decoded = GameSaveCodec.decode(GameSaveCodec.encode(saved))
        assertNotNull("encoded save should decode", decoded)

        val restored = GameEngine(Random(12))
        restored.restore(decoded!!)

        assertSameGame(saved, restored.save())
        assertEquals(engine.score, restored.score)
        assertEquals(engine.cell(2, 3), restored.cell(2, 3))
        assertTrue(
            "restored ids must stay ahead of the tray",
            restored.save().nextPieceId > 7,
        )
        // The remaining tray must be playable, not just equal on paper.
        assertTrue(restored.place(1, 0, 0) is PlaceResult.Success)
    }

    @Test
    fun codecRoundTripIsLossless() {
        val engine = GameEngine(Random(21))
        engine.place(0, 0, 0)

        val saved = engine.save()
        val decoded = GameSaveCodec.decode(GameSaveCodec.encode(saved))
        assertNotNull(decoded)
        assertSameGame(saved, decoded!!)
        assertEquals("codec keeps the id counter", saved.nextPieceId, decoded.nextPieceId)
    }

    @Test
    fun restoredGameNeverReusesPieceIds() {
        val engine = GameEngine(Random(31))
        engine.setTrayForTest(
            listOf(piece(0 to 0, id = 40), piece(0 to 0, id = 41), piece(0 to 0, id = 42)),
        )
        val decoded = GameSaveCodec.decode(GameSaveCodec.encode(engine.save()))!!

        val restored = GameEngine(Random(32))
        restored.restore(decoded)
        assertEquals(43, restored.save().nextPieceId)

        // Emptying the tray forces a refill with brand-new ids.
        restored.place(0, 0, 0)
        restored.place(1, 0, 1)
        restored.place(2, 0, 2)
        val ids = (0 until TRAY_SIZE).mapNotNull { restored.pieceAt(it)?.id }
        assertEquals(TRAY_SIZE, ids.size)
        assertTrue("new pieces must have fresh ids: $ids", ids.all { it >= 43 })
    }

    @Test
    fun finishedGamesAreNotOfferedAsContinue() {
        val engine = GameEngine(Random(41))
        val big = piece(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2, id = 2)
        engine.setTrayForTest(listOf(piece(0 to 0, id = 1), big, big))
        val board = IntArray(BOARD_SIZE * BOARD_SIZE) { 0 }
        for ((x, y) in listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6, 7 to 7)) {
            board[y * BOARD_SIZE + x] = EMPTY
        }
        engine.loadBoardForTest(board)
        assertTrue((engine.place(0, 0, 0) as PlaceResult.Success).gameOver)

        assertTrue("a game over save is still valid data", engine.save().gameOver)
        // The ViewModel is the layer that refuses to keep finished games; the codec just
        // round-trips the flag so that decision survives a restore.
        val decoded = GameSaveCodec.decode(GameSaveCodec.encode(engine.save()))!!
        assertTrue(decoded.gameOver)
    }

    @Test
    fun codecRejectsMalformedData() {
        val valid = GameSaveCodec.encode(GameEngine(Random(51)).save())
        assertNotNull(GameSaveCodec.decode(valid))

        val cases = listOf(
            "",
            "garbage",
            "theBlocks-save-v2\n" + valid.substringAfter('\n'),
            // wrong board length
            valid.replaceFirst("-1,-1,-1,-1,-1,-1,-1,-1,", ""),
            // invalid color in the board
            valid.lines().toMutableList().also { it[1] = it[1].replaceFirst("-1", "6") }.joinToString("\n"),
            // invalid tray slot count
            valid.lines().toMutableList().also { it[2] = it[2].substringBefore(';') }.joinToString("\n"),
            // negative counter
            valid.lines().toMutableList().also { it[3] = "-5,0,0,0,0,0" }.joinToString("\n"),
            // invalid game over flag
            valid.lines().toMutableList().also { it[3] = "0,0,0,0,0,2" }.joinToString("\n"),
            // piece with duplicate cells
            valid.lines().toMutableList().also { it[2] = "1:2:0,0 0,0;-;-" }.joinToString("\n"),
            // piece that is not normalized
            valid.lines().toMutableList().also { it[2] = "1:2:1,1;-;-" }.joinToString("\n"),
            // piece with an out-of-range cell
            valid.lines().toMutableList().also { it[2] = "1:2:0,0 9,9;-;-" }.joinToString("\n"),
            // missing counters line
            valid.lines().take(3).joinToString("\n"),
        )
        for (bad in cases) {
            assertNull("should reject: $bad", GameSaveCodec.decode(bad))
        }
    }

    @Test
    fun restoreIsSafeOnAnEmptyTray() {
        val engine = GameEngine(Random(61))
        engine.setTrayForTest(listOf(piece(0 to 0, id = 3), null, null))
        val decoded = GameSaveCodec.decode(GameSaveCodec.encode(engine.save()))!!

        val restored = GameEngine(Random(62))
        restored.restore(decoded)
        assertNotNull(restored.pieceAt(0))
        assertNull(restored.pieceAt(1))
        assertNull(restored.pieceAt(2))
        assertFalse(restored.gameOver)
    }
}
