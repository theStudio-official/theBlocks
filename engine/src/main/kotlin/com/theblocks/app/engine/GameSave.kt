package com.theblocks.app.engine

/**
 * Everything needed to resume an in-progress game after the app was closed.
 *
 * [tray] keeps the actual [Piece] objects (shape, color and id), so a resumed tray looks
 * exactly like it did before, and new pieces keep getting fresh ids.
 */
data class SavedGame(
    val board: List<Int>,
    val tray: List<Piece?>,
    val score: Int,
    val combo: Int,
    val lines: Int,
    val placements: Int,
    val nextPieceId: Int,
    val gameOver: Boolean,
)

/**
 * Plain-text encoding for [SavedGame]. SharedPreferences only stores strings, and this keeps
 * the format readable for debugging without pulling in a serialization library.
 *
 * Layout (four lines):
 * ```
 * theBlocks-save-v1
 * -1,-1,...                       // 64 board cells
 * 3:2:0,0 1,0;-;7:4:0,0 0,1       // tray: "id:color:x,y x,y" or "-"
 * 120,2,5,14,21,0                 // score,combo,lines,placements,nextPieceId,gameOver
 * ```
 */
object GameSaveCodec {

    private const val VERSION = "theBlocks-save-v1"
    private const val EMPTY_SLOT = "-"

    fun encode(saved: SavedGame): String = buildString {
        append(VERSION).append('\n')
        append(saved.board.joinToString(",")).append('\n')
        append(
            saved.tray.joinToString(";") { piece ->
                if (piece == null) {
                    EMPTY_SLOT
                } else {
                    buildString {
                        append(piece.id).append(':').append(piece.colorIndex).append(':')
                        append(piece.cells.joinToString(" ") { "${it.x},${it.y}" })
                    }
                }
            },
        ).append('\n')
        append(
            listOf(
                saved.score,
                saved.combo,
                saved.lines,
                saved.placements,
                saved.nextPieceId,
                if (saved.gameOver) 1 else 0,
            ).joinToString(","),
        )
    }

    /** Returns null for anything malformed; callers should treat that as "no saved game". */
    fun decode(text: String): SavedGame? {
        val lines = text.trim().lines()
        if (lines.size < 4 || lines[0] != VERSION) return null

        val board = lines[1].split(",").map { it.toIntOrNull() ?: return null }
        if (board.size != BOARD_SIZE * BOARD_SIZE) return null
        if (board.any { it < EMPTY || it >= COLOR_COUNT }) return null

        val slots = lines[2].split(";")
        if (slots.size != TRAY_SIZE) return null
        val tray = slots.map { token ->
            if (token == EMPTY_SLOT) null else decodePiece(token) ?: return null
        }

        val numbers = lines[3].split(",").map { it.toIntOrNull() ?: return null }
        if (numbers.size != 6) return null
        val (score, combo, lineCount, placements, nextPieceId) = numbers
        if (score < 0 || combo < 0 || lineCount < 0 || placements < 0 || nextPieceId < 0) return null
        if (numbers[5] !in 0..1) return null

        return SavedGame(
            board = board,
            tray = tray,
            score = score,
            combo = combo,
            lines = lineCount,
            placements = placements,
            nextPieceId = nextPieceId,
            gameOver = numbers[5] == 1,
        )
    }

    private fun decodePiece(token: String): Piece? {
        val parts = token.split(":")
        if (parts.size != 3) return null
        val id = parts[0].toIntOrNull() ?: return null
        val color = parts[1].toIntOrNull() ?: return null
        if (id < 0 || color !in 0 until COLOR_COUNT) return null

        val rawCells = parts[2].split(" ")
        if (rawCells.isEmpty()) return null
        val cells = ArrayList<CellOffset>(rawCells.size)
        for (raw in rawCells) {
            val xy = raw.split(",")
            if (xy.size != 2) return null
            val x = xy[0].toIntOrNull() ?: return null
            val y = xy[1].toIntOrNull() ?: return null
            if (x !in 0..4 || y !in 0..4) return null
            cells.add(CellOffset(x, y))
        }
        if (cells.distinct().size != cells.size) return null
        // Pieces are stored normalised, so a shape must touch both axes at 0.
        if (cells.minOf { it.x } != 0 || cells.minOf { it.y } != 0) return null
        return Piece(
            cells = cells.sortedWith(compareBy({ it.y }, { it.x })),
            colorIndex = color,
            id = id,
        )
    }
}
