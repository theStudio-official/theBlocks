package com.theblocks.app.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.theblocks.app.engine.EMPTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Continue-game persistence. A fresh ViewModel stands in for a cold app start: it reads the
 * same SharedPreferences the previous instance wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameViewModelTest {

    @Before
    fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("theblocks", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun newViewModel(): GameViewModel =
        GameViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun aFreshInstallHasNothingToContinue() {
        assertFalse(newViewModel().state.value.canContinue)
    }

    @Test
    fun startingAGameSavesItSoAColdStartCanContinue() {
        val first = newViewModel()
        first.newGame()
        // Placing the first tray piece at the top-left always fits on an empty board.
        first.place(0, 0, 0)
        assertTrue(first.state.value.score > 0)

        val coldStart = newViewModel()
        assertTrue("a saved run should be offered", coldStart.state.value.canContinue)
    }

    @Test
    fun continuingRestoresBoardScoreAndTray() {
        val first = newViewModel()
        first.newGame()
        first.place(0, 2, 2)
        val saved = first.state.value

        val coldStart = newViewModel()
        assertTrue(coldStart.continueGame())

        val resumed = coldStart.state.value
        assertEquals(saved.score, resumed.score)
        assertEquals(saved.board, resumed.board)
        assertEquals(saved.tray.map { it?.id }, resumed.tray.map { it?.id })
        assertTrue("the resumed board should hold the piece", resumed.board.count { it != EMPTY } > 0)
        // Continue is consumed by this session; only a new cold start may offer it again.
        assertFalse(coldStart.state.value.canContinue)
    }

    @Test
    fun newGameDropsTheOldSave() {
        val first = newViewModel()
        first.newGame()
        first.place(0, 0, 0)

        val coldStart = newViewModel()
        assertTrue(coldStart.state.value.canContinue)
        coldStart.newGame()
        assertFalse("starting over must hide Continue", coldStart.state.value.canContinue)
        assertEquals(0, coldStart.state.value.score)
    }

    @Test
    fun corruptSaveIsIgnoredAndCleared() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("theblocks", Application.MODE_PRIVATE)
            .edit()
            .putString("saved_game", "not a real save")
            .commit()

        val viewModel = newViewModel()
        assertFalse(viewModel.state.value.canContinue)
        assertFalse(viewModel.continueGame())
        assertNull(
            app.getSharedPreferences("theblocks", Application.MODE_PRIVATE)
                .getString("saved_game", null),
        )
    }
}
