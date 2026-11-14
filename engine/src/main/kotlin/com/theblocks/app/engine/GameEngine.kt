package com.theblocks.app.engine

import kotlin.random.Random

/** Immutable view of everything the UI needs to draw. */
data class GameSnapshot(
    val board: List<Int>,
    val tray: List<Piece?>,
    val score: Int,
    val best: Int,
    val combo: Int,
    val lines: Int,
    val gameOver: Boolean,
)

sealed interface PlaceResult {
    /** The piece did not fit (or was not there) - nothing changed. */
    data object Failure : PlaceResult

    data class Success(
        val points: Int,
        val linesCleared: Int,
        val clearedCells: Set<Int>,
        /** Board right after the piece landed, before lines were wiped. Used for the clear animation. */
        val boardAfterPlacement: List<Int>,
        val combo: Int,
        val trayRefilled: Boolean,
        val gameOver: Boolean,
    ) : PlaceResult
}

/** True when [piece] can be dropped with its top-left corner at (originX, originY). */
fun fits(board: List<Int>, piece: Piece, originX: Int, originY: Int): Boolean =
    fitsWhere(piece, originX, originY) { x, y -> board[y * BOARD_SIZE + x] }

fun fits(board: IntArray, piece: Piece, originX: Int, originY: Int): Boolean =
    fitsWhere(piece, originX, originY) { x, y -> board[y * BOARD_SIZE + x] }

private fun fitsWhere(piece: Piece, originX: Int, originY: Int, cellAt: (Int, Int) -> Int): Boolean {
    for (c in piece.cells) {
        val x = originX + c.x
        val y = originY + c.y
        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) return false
        if (cellAt(x, y) != EMPTY) return false
    }
    return true
}

/**
 * The whole ruleset, with no Android dependencies so it can be unit tested.
 *
 * Rules (same as the genre):
 *  - 8x8 board, three pieces in the tray at a time.
 *  - A piece may be dropped on empty cells only. No gravity: it stays where it lands.
 *  - Filling a full row and/or column clears it.
 *  - Clearing on consecutive placements builds a combo multiplier.
 *  - When the tray is empty it refills. Game over when no tray piece fits anywhere.
 */
class GameEngine(
    private val random: Random = Random.Default,
    private val pieceFactory: (Random, Int) -> Piece = { r, id -> PieceLibrary.randomPiece(r, id) },
) {

    private val board = IntArray(BOARD_SIZE * BOARD_SIZE) { EMPTY }
    private val tray = arrayOfNulls<Piece>(TRAY_SIZE)
    private var nextPieceId = 0

    var score = 0
        private set
    var combo = 0
        private set
    var lines = 0
        private set
    var placements = 0
        private set
    var gameOver = false
        private set

    init {
        newGame()
    }

    fun newGame() {
        board.fill(EMPTY)
        tray.fill(null)
        score = 0
        combo = 0
        lines = 0
        placements = 0
        gameOver = false
        refillTray(ensurePlayable = false)
    }

    fun cell(x: Int, y: Int): Int = board[y * BOARD_SIZE + x]

    fun pieceAt(index: Int): Piece? = tray.getOrNull(index)

    fun canPlace(piece: Piece, originX: Int, originY: Int): Boolean = fits(board, piece, originX, originY)

    /** Any placement at all available with the pieces currently in the tray? */
    fun hasAnyMove(): Boolean {
        for (piece in tray) {
            if (piece == null) continue
            for (y in 0..(BOARD_SIZE - piece.height)) {
                for (x in 0..(BOARD_SIZE - piece.width)) {
                    if (fits(board, piece, x, y)) return true
                }
            }
        }
        return false
    }

    fun place(pieceIndex: Int, originX: Int, originY: Int): PlaceResult {
        if (gameOver) return PlaceResult.Failure
        val piece = tray.getOrNull(pieceIndex) ?: return PlaceResult.Failure
        if (!canPlace(piece, originX, originY)) return PlaceResult.Failure

        for (c in piece.cells) {
            board[(originY + c.y) * BOARD_SIZE + (originX + c.x)] = piece.colorIndex
        }
        placements++
        val boardAfterPlacement = board.toList()

        val fullRows = (0 until BOARD_SIZE).filter { y ->
            (0 until BOARD_SIZE).all { x -> board[y * BOARD_SIZE + x] != EMPTY }
        }
        val fullCols = (0 until BOARD_SIZE).filter { x ->
            (0 until BOARD_SIZE).all { y -> board[y * BOARD_SIZE + x] != EMPTY }
        }

        val cleared = LinkedHashSet<Int>()
        for (y in fullRows) for (x in 0 until BOARD_SIZE) cleared.add(y * BOARD_SIZE + x)
        for (x in fullCols) for (y in 0 until BOARD_SIZE) cleared.add(y * BOARD_SIZE + x)

        val lineCount = fullRows.size + fullCols.size
        var points = piece.size
        if (lineCount > 0) {
            combo += 1
            lines += lineCount
            // 10 per line, multiplied by the current combo streak, plus a bonus for multi-line clears.
            points += lineCount * 10 * combo.coerceAtMost(8) + (lineCount - 1) * 10
        } else {
            combo = 0
        }
        score += points

        for (index in cleared) board[index] = EMPTY

        tray[pieceIndex] = null
        var refilled = false
        if (tray.all { it == null }) {
            refillTray(ensurePlayable = true)
            refilled = true
        }

        if (!hasAnyMove()) gameOver = true

        return PlaceResult.Success(
            points = points,
            linesCleared = lineCount,
            clearedCells = cleared,
            boardAfterPlacement = boardAfterPlacement,
            combo = combo,
            trayRefilled = refilled,
            gameOver = gameOver,
        )
    }

    fun snapshot(best: Int = 0): GameSnapshot = GameSnapshot(
        board = board.toList(),
        tray = tray.toList(),
        score = score,
        best = best,
        combo = combo,
        lines = lines,
        gameOver = gameOver,
    )

    /** A resumable copy of the current game, for persistence. */
    fun save(): SavedGame = SavedGame(
        board = board.toList(),
        tray = tray.toList(),
        score = score,
        combo = combo,
        lines = lines,
        placements = placements,
        nextPieceId = nextPieceId,
        gameOver = gameOver,
    )

    /** Restores a [SavedGame] produced by [save]. */
    fun restore(saved: SavedGame) {
        require(saved.board.size == board.size) { "board must be ${board.size} cells" }
        saved.board.forEachIndexed { index, value -> board[index] = value }
        for (index in tray.indices) tray[index] = saved.tray.getOrNull(index)
        score = saved.score
        combo = saved.combo
        lines = saved.lines
        placements = saved.placements
        // Never hand out an id that is already on the board tray.
        nextPieceId = maxOf(saved.nextPieceId, (tray.filterNotNull().maxOfOrNull { it.id } ?: -1) + 1)
        gameOver = saved.gameOver || !hasAnyMove()
    }

    /**
     * Refills all three slots. When [ensurePlayable] is set we retry a few times so the
     * player does not lose to a tray that was never placeable in the first place.
     */
    private fun refillTray(ensurePlayable: Boolean) {
        repeat(TRAY_SIZE) { i -> tray[i] = pieceFactory(random, nextPieceId++) }
        if (!ensurePlayable) return
        var attempts = 0
        while (!hasAnyMove() && attempts < 80) {
            repeat(TRAY_SIZE) { i -> tray[i] = pieceFactory(random, nextPieceId++) }
            attempts++
        }
    }

    /** Test-only hook so board states that are hard to reach through play can be exercised. */
    internal fun loadBoardForTest(cells: IntArray) {
        require(cells.size == board.size) { "board must be ${board.size} cells" }
        cells.copyInto(board)
        gameOver = !hasAnyMove()
    }

    /** Test-only hook to force the tray contents. */
    internal fun setTrayForTest(pieces: List<Piece?>) {
        for (i in tray.indices) tray[i] = pieces.getOrNull(i)
    }
}
