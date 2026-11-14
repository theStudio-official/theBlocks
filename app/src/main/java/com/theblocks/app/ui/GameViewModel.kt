package com.theblocks.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.theblocks.app.engine.BOARD_SIZE
import com.theblocks.app.engine.GameEngine
import com.theblocks.app.engine.GameSaveCodec
import com.theblocks.app.engine.Piece
import com.theblocks.app.engine.PlaceResult
import com.theblocks.app.engine.SavedGame
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameUiState(
    val board: List<Int>,
    val tray: List<Piece?>,
    val score: Int,
    val best: Int,
    /** Best score when the current run started, so the game over screen can celebrate properly. */
    val runStartBest: Int,
    val combo: Int,
    val lines: Int,
    val gameOver: Boolean,
    /** Cells currently flashing before they disappear. */
    val clearing: Set<Int> = emptySet(),
    /** Increments once per successful placement. Drives sounds and pop animations. */
    val lastMoveId: Long = 0L,
    /** Cells filled by the last placement, for the pop-in animation. */
    val lastPlacement: Set<Int> = emptySet(),
    /** Points earned by the last placement, for the floating "+N" label. */
    val lastGain: Int = 0,
    val lastClearedLines: Int = 0,
    val lastCombo: Int = 0,
    val soundEnabled: Boolean = true,
    /** True when a game from a previous app session can be resumed. */
    val canContinue: Boolean = false,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = GameEngine()
    private val prefs = app.getSharedPreferences(PREFS, Application.MODE_PRIVATE)

    private var best: Int = prefs.getInt(KEY_BEST, 0)
    private var runStartBest: Int = best
    private var soundEnabled: Boolean = prefs.getBoolean(KEY_SOUND, true)
    private var clearJob: Job? = null

    /**
     * A game left behind by a previous process. Only a cold start can produce this, which is
     * what makes the title screen's Continue button appear exactly when the app was closed
     * during a run.
     */
    private var pendingResume: SavedGame? =
        prefs.getString(KEY_SAVE, null)?.let { GameSaveCodec.decode(it) }

    init {
        // Never keep a save we cannot read.
        if (pendingResume == null && prefs.contains(KEY_SAVE)) {
            prefs.edit { remove(KEY_SAVE) }
        }
    }

    private var lastMoveId = 0L
    private var lastPlacement: Set<Int> = emptySet()
    private var lastGain = 0
    private var lastClearedLines = 0
    private var lastCombo = 0

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private fun buildState(
        boardOverride: List<Int>? = null,
        clearing: Set<Int> = emptySet(),
    ): GameUiState {
        val snap = engine.snapshot(best)
        return GameUiState(
            board = boardOverride ?: snap.board,
            tray = snap.tray,
            score = snap.score,
            best = snap.best,
            runStartBest = runStartBest,
            combo = snap.combo,
            lines = snap.lines,
            gameOver = snap.gameOver,
            clearing = clearing,
            lastMoveId = lastMoveId,
            lastPlacement = lastPlacement,
            lastGain = lastGain,
            lastClearedLines = lastClearedLines,
            lastCombo = lastCombo,
            soundEnabled = soundEnabled,
            canContinue = pendingResume != null,
        )
    }

    fun newGame() {
        clearJob?.cancel()
        pendingResume = null
        engine.newGame()
        runStartBest = best
        lastMoveId = 0L
        lastPlacement = emptySet()
        lastGain = 0
        lastClearedLines = 0
        lastCombo = 0
        persistSave()
        _state.value = buildState()
    }

    /**
     * Resumes the game saved by a previous process. Returns false when there is nothing to
     * resume (for example after a corrupt save was discarded).
     */
    fun continueGame(): Boolean {
        val saved = pendingResume ?: return false
        clearJob?.cancel()
        engine.restore(saved)
        pendingResume = null
        runStartBest = best
        lastMoveId = 0L
        lastPlacement = emptySet()
        lastGain = 0
        lastClearedLines = 0
        lastCombo = 0
        persistSave()
        _state.value = buildState()
        return true
    }

    fun toggleSound() {
        soundEnabled = !soundEnabled
        prefs.edit { putBoolean(KEY_SOUND, soundEnabled) }
        _state.value = buildState()
    }

    /** Drops [pieceIndex] with its top-left corner at (originX, originY). */
    fun place(pieceIndex: Int, originX: Int, originY: Int) {
        clearJob?.cancel()
        val piece = engine.pieceAt(pieceIndex)
        val scoreBefore = engine.score
        when (val result = engine.place(pieceIndex, originX, originY)) {
            is PlaceResult.Failure -> Unit
            is PlaceResult.Success -> {
                if (engine.score > best) {
                    best = engine.score
                    prefs.edit { putInt(KEY_BEST, best) }
                }
                lastMoveId += 1
                lastGain = engine.score - scoreBefore
                lastClearedLines = result.linesCleared
                lastCombo = result.combo
                lastPlacement = piece?.let { placed ->
                    buildSet {
                        for (c in placed.cells) {
                            val x = originX + c.x
                            val y = originY + c.y
                            if (x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE) {
                                add(y * BOARD_SIZE + x)
                            }
                        }
                    }
                } ?: emptySet()

                // Persist before emitting the state so a cold start can always resume.
                persistSave()

                if (result.clearedCells.isEmpty()) {
                    _state.value = buildState()
                } else {
                    // Show the board with the piece still on it while the cleared lines flash.
                    _state.value = buildState(
                        boardOverride = result.boardAfterPlacement,
                        clearing = result.clearedCells,
                    )
                    clearJob = viewModelScope.launch {
                        delay(CLEAR_FLASH_MS)
                        _state.value = buildState()
                    }
                }
            }
        }
    }

    private fun persistSave() {
        if (engine.gameOver) {
            // A finished run is not something to continue.
            prefs.edit { remove(KEY_SAVE) }
            return
        }
        prefs.edit { putString(KEY_SAVE, GameSaveCodec.encode(engine.save())) }
    }

    private companion object {
        const val PREFS = "theblocks"
        const val KEY_BEST = "best_score"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_SAVE = "saved_game"
        const val CLEAR_FLASH_MS = 320L
    }
}
