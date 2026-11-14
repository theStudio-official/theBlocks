package com.theblocks.app.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.theblocks.app.engine.EMPTY
import com.theblocks.app.ui.theme.TheBlocksTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the real screens on the JVM and drives them with real touch gestures,
 * so the drag/drop pipeline is exercised end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class GameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newViewModel(): GameViewModel =
        GameViewModel(ApplicationProvider.getApplicationContext<Application>())

    /** Drags the piece in tray slot 0 to the middle of the board, like a finger would. */
    private fun dragFirstPieceToBoard() {
        val slot = compose.onNodeWithTag("tray-0").getUnclippedBoundsInRoot()
        val board = compose.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val slotCenterX = (slot.left + slot.right) / 2f
        val slotCenterY = (slot.top + slot.bottom) / 2f
        val boardCenterX = (board.left + board.right) / 2f
        val boardCenterY = (board.top + board.bottom) / 2f

        compose.onNodeWithTag("tray-0").performTouchInput {
            val dx = (boardCenterX - slotCenterX).toPx()
            val dy = (boardCenterY - slotCenterY).toPx()
            down(center)
            // Many small moves, so touch slop is consumed like it is by a real finger.
            val steps = 12
            repeat(steps) { moveBy(Offset(dx / steps, dy / steps)) }
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun rendersHeaderBoardAndTray() {
        val viewModel = newViewModel()
        compose.setContent { TheBlocksTheme { GameScreen(viewModel) } }

        compose.onNodeWithText("theBlocks!").assertExists()
        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("board").assertExists()
        compose.onNodeWithTag("tray-0").assertExists()
        compose.onNodeWithTag("tray-1").assertExists()
        compose.onNodeWithTag("tray-2").assertExists()
    }

    @Test
    fun draggingAPieceOntoTheBoardScores() {
        val viewModel = newViewModel()
        compose.setContent { TheBlocksTheme { GameScreen(viewModel) } }
        compose.waitForIdle()

        dragFirstPieceToBoard()

        compose.runOnIdle {
            assertTrue(
                "dropping a piece on an empty board should score",
                viewModel.state.value.score > 0,
            )
            assertTrue(
                "the piece should be on the board",
                viewModel.state.value.board.count { it != EMPTY } > 0,
            )
        }
    }

    @Test
    fun menuShowsNewGameMainMenuAndAbout() {
        val viewModel = newViewModel()
        compose.setContent { TheBlocksTheme { GameScreen(viewModel) } }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("New game").assertExists()
        compose.onNodeWithText("Main menu").assertExists()
        compose.onNodeWithText("Sound: on").assertExists()
        compose.onNodeWithText("About").assertExists()
    }

    @Test
    fun soundToggleFlipsThePreferenceAndTheMenuLabel() {
        val viewModel = newViewModel()
        compose.setContent { TheBlocksTheme { GameScreen(viewModel) } }
        compose.waitForIdle()

        val initial = viewModel.state.value.soundEnabled
        compose.onNodeWithTag("menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(if (initial) "Sound: on" else "Sound: off").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(
                "toggling should flip soundEnabled",
                viewModel.state.value.soundEnabled != initial,
            )
        }

        // The label follows the new state next time the menu opens.
        compose.onNodeWithTag("menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(if (initial) "Sound: off" else "Sound: on").assertExists()
    }

    @Test
    fun aboutDialogShowsVersionAndCredit() {
        val viewModel = newViewModel()
        compose.setContent { TheBlocksTheme { GameScreen(viewModel) } }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("About").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Version", substring = true).assertExists()
        compose.onNodeWithText("made with ❤️ by theStudio (and deepseek)").assertExists()
        compose.onNodeWithText("open-source alternative to Block Blast", substring = true).assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("made with ❤️ by theStudio (and deepseek)").assertDoesNotExist()
    }

    @Test
    fun menuNewGameResetsTheRun() {
        val viewModel = newViewModel()
        compose.setContent { TheBlocksTheme { GameScreen(viewModel) } }
        compose.waitForIdle()

        dragFirstPieceToBoard()
        compose.runOnIdle { assertTrue(viewModel.state.value.score > 0) }

        compose.onNodeWithTag("menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New game").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue("restart should zero the score", viewModel.state.value.score == 0)
            assertTrue(
                "restart should empty the board",
                viewModel.state.value.board.all { it == EMPTY },
            )
        }
    }

    @Test
    fun menuMainMenuInvokesExitCallback() {
        val viewModel = newViewModel()
        var exited = false
        compose.setContent {
            TheBlocksTheme {
                GameScreen(viewModel, onExitToMenu = { exited = true })
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Main menu").performClick()
        compose.waitForIdle()

        compose.runOnIdle { assertTrue("main menu should notify the caller", exited) }
    }

    @Test
    fun gameOverDialogOffersPlayAgainAndMainMenu() {
        var playedAgain = false
        var wentToMenu = false
        compose.setContent {
            TheBlocksTheme {
                GameOverDialog(
                    score = 120,
                    best = 120,
                    runStartBest = 0,
                    onPlayAgain = { playedAgain = true },
                    onExitToMenu = { wentToMenu = true },
                )
            }
        }

        compose.onNodeWithText("Play again").assertExists()
        compose.onNodeWithText("Main menu").assertExists()
        // The old duplicate "New game" entry is gone; the two actions now differ.
        compose.onNodeWithText("New game").assertDoesNotExist()

        compose.onNodeWithText("Main menu").performClick()
        compose.runOnIdle {
            assertTrue("main menu should notify the caller", wentToMenu)
            assertTrue("main menu must not start a new run", !playedAgain)
        }
    }

    @Test
    fun titleScreenShowsContinueOnlyWhenARunWasSaved() {
        var continued = false
        compose.setContent {
            TheBlocksTheme {
                TitleScreen(
                    best = 0,
                    canContinue = true,
                    onStart = {},
                    onContinue = { continued = true },
                )
            }
        }

        compose.onNodeWithText("Continue").assertExists()
        compose.onNodeWithText("New game").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertTrue("Continue should resume the saved run", continued) }
    }

    @Test
    fun titleScreenHidesContinueWithoutASavedRun() {
        compose.setContent {
            TheBlocksTheme { TitleScreen(best = 0, onStart = {}) }
        }

        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("New game").assertExists()
    }

    @Test
    fun titleScreenStartsANewGame() {
        var started = false
        compose.setContent {
            TheBlocksTheme { TitleScreen(best = 0, onStart = { started = true }) }
        }

        compose.onNodeWithText("theBlocks!").assertExists()
        compose.onNodeWithText("New game").performClick()

        compose.runOnIdle { assertTrue("the new game button should start a run", started) }
    }
}
