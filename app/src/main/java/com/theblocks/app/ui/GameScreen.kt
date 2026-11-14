package com.theblocks.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.theblocks.app.BuildConfig
import com.theblocks.app.engine.BOARD_SIZE
import com.theblocks.app.engine.EMPTY
import com.theblocks.app.engine.Piece
import com.theblocks.app.engine.fits
import com.theblocks.app.ui.theme.BlockPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val CellGap = 5.dp
private val BoardPadding = 10.dp

/** How far above the finger the dragged piece floats, in cells. */
private const val DRAG_LIFT_CELLS = 0.85f

/** Must match GameViewModel's CLEAR_FLASH_MS so the shards finish as the cells vanish. */
private const val CLEAR_ANIM_MS = 320

private data class DragState(
    val pieceIndex: Int,
    val piece: Piece,
    val pointer: Offset,
)

private data class DropTarget(val col: Int, val row: Int)

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    onExitToMenu: () -> Unit = {},
    sound: SoundEffects? = null,
) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Placement feedback: sounds and haptics follow successful moves only.
    LaunchedEffect(state.lastMoveId) {
        if (state.lastMoveId == 0L) return@LaunchedEffect
        if (state.lastClearedLines > 0) {
            sound?.play(GameSound.Clear)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (state.lastCombo >= 2) {
                // Let the clear chime start before the combo flourish on top of it.
                delay(140)
                sound?.play(GameSound.Combo)
            }
        } else {
            sound?.play(GameSound.Drop)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (state.gameOver) {
            delay(220)
            sound?.play(GameSound.GameOver)
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }

    var drag by remember { mutableStateOf<DragState?>(null) }
    // Keeping the coordinates (not a snapshot rect) means the drop target is measured
    // after the entrance scale has finished, even though that scale is a draw-time transform.
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val gridRect = gridCoords?.boundsInRoot() ?: Rect.Zero
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val trayRects = remember { mutableStateMapOf<Int, Rect>() }

    val gapPx = with(density) { CellGap.toPx() }
    val cellPx = cellSizeFor(gridRect, gapPx)
    val liftPx = (cellPx + gapPx) * DRAG_LIFT_CELLS

    // Entrance: header drops in, board scales up, tray rises, all slightly staggered.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "headerAlpha",
    )
    val headerShift by animateFloatAsState(
        targetValue = if (entered) 0f else -16f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "headerShift",
    )
    val boardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 60),
        label = "boardAlpha",
    )
    val boardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing, delayMillis = 60),
        label = "boardScale",
    )
    val trayAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 280, delayMillis = 120),
        label = "trayAlpha",
    )
    val trayShift by animateFloatAsState(
        targetValue = if (entered) 0f else 26f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing, delayMillis = 120),
        label = "trayShift",
    )

    var invalidTick by remember { mutableIntStateOf(0) }

    val activeDrag = drag
    val target = activeDrag?.let { computeTarget(it, gridRect, cellPx, gapPx, liftPx) }
    val targetValid = activeDrag != null && target != null &&
        fits(state.board, activeDrag.piece, target.col, target.row)

    val previewCells: Map<Int, Boolean> = if (activeDrag != null && target != null) {
        val valid = fits(state.board, activeDrag.piece, target.col, target.row)
        buildMap {
            for (c in activeDrag.piece.cells) {
                val x = target.col + c.x
                val y = target.row + c.y
                if (x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE) {
                    put(y * BOARD_SIZE + x, valid)
                }
            }
        }
    } else {
        emptyMap()
    }
    val previewColor = activeDrag?.let { BlockPalette[it.piece.colorIndex % BlockPalette.size] }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { contentOrigin = it.positionInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            GameHeader(
                score = state.score,
                moveId = state.lastMoveId,
                soundEnabled = state.soundEnabled,
                onNewGame = viewModel::newGame,
                onToggleSound = viewModel::toggleSound,
                onExitToMenu = onExitToMenu,
                sound = sound,
                modifier = Modifier.graphicsLayer {
                    alpha = headerAlpha
                    translationY = headerShift.dp.toPx()
                },
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val boardSize = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(
                    modifier = Modifier
                        .size(boardSize)
                        .graphicsLayer {
                            alpha = boardAlpha
                            scaleX = boardScale
                            scaleY = boardScale
                        },
                ) {
                    BoardCard(
                        board = state.board,
                        clearing = state.clearing,
                        placedCells = state.lastPlacement,
                        moveId = state.lastMoveId,
                        invalidTick = invalidTick,
                        preview = previewCells,
                        previewColor = previewColor,
                        onGridCoords = { gridCoords = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    ComboBadge(
                        combo = state.combo,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-14).dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Tray(
                tray = state.tray,
                draggingIndex = drag?.pieceIndex,
                enabled = !state.gameOver,
                onSlotBounds = { index, rect -> trayRects[index] = rect },
                onDragStart = { index, piece, local ->
                    val rect = trayRects[index]
                    if (rect != null) {
                        drag = DragState(index, piece, rect.topLeft + local)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        sound?.play(GameSound.Pickup)
                    }
                },
                onDrag = { delta ->
                    drag = drag?.let { it.copy(pointer = it.pointer + delta) }
                },
                onDragEnd = {
                    val current = drag
                    drag = null
                    if (current != null) {
                        // Read the grid geometry again instead of using values captured by
                        // pointerInput: the first composition happens before layout, when the
                        // grid rect (and therefore the cell size) is still empty.
                        val grid = gridCoords?.boundsInRoot() ?: Rect.Zero
                        val cell = cellSizeFor(grid, gapPx)
                        val dropTarget = computeTarget(
                            current,
                            grid,
                            cell,
                            gapPx,
                            (cell + gapPx) * DRAG_LIFT_CELLS,
                        )
                        val board = viewModel.state.value.board
                        if (dropTarget != null && fits(board, current.piece, dropTarget.col, dropTarget.row)) {
                            viewModel.place(current.pieceIndex, dropTarget.col, dropTarget.row)
                        } else if (dropTarget != null &&
                            grid.inflate(liftPx * 2f).contains(current.pointer)
                        ) {
                            // Released over the board but the piece cannot go there.
                            sound?.play(GameSound.Invalid)
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            invalidTick += 1
                        }
                    }
                },
                modifier = Modifier.graphicsLayer {
                    alpha = trayAlpha
                    translationY = trayShift.dp.toPx()
                },
            )

            Spacer(Modifier.height(14.dp))
        }

        // The piece that follows the finger, floating just above it.
        if (activeDrag != null && cellPx > 0f) {
            val ghostScale = remember { Animatable(0.82f) }
            LaunchedEffect(Unit) {
                ghostScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
                )
            }
            val step = cellPx + gapPx
            val pieceWidth = activeDrag.piece.width * step - gapPx
            val pieceHeight = activeDrag.piece.height * step - gapPx
            val ghostX: Float
            val ghostY: Float
            val snapped = if (targetValid) target else null
            if (snapped != null) {
                ghostX = gridRect.left + snapped.col * step
                ghostY = gridRect.top + snapped.row * step
            } else {
                ghostX = activeDrag.pointer.x - pieceWidth / 2f
                ghostY = activeDrag.pointer.y - liftPx - pieceHeight / 2f
            }
            PieceView(
                piece = activeDrag.piece,
                cell = with(density) { cellPx.toDp() },
                gap = CellGap,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = ghostScale.value
                        scaleY = ghostScale.value
                    }
                    .offset {
                        IntOffset(
                            (ghostX - contentOrigin.x).roundToInt(),
                            (ghostY - contentOrigin.y).roundToInt(),
                        )
                    },
            )
        }

        // "+N" points that drift up from where the piece landed.
        if (state.lastMoveId > 0L && state.lastGain > 0) {
            ScorePopup(
                moveId = state.lastMoveId,
                gain = state.lastGain,
                placed = state.lastPlacement,
                grid = gridRect,
                gapPx = gapPx,
                contentOrigin = contentOrigin,
            )
        }
    }

    if (state.gameOver) {
        GameOverDialog(
            score = state.score,
            best = state.best,
            runStartBest = state.runStartBest,
            onPlayAgain = {
                sound?.play(GameSound.Tap)
                viewModel.newGame()
            },
            onExitToMenu = {
                sound?.play(GameSound.Tap)
                onExitToMenu()
            },
        )
    }
}

/** Floating "+points" label, one instance restarted per successful placement. */
@Composable
private fun ScorePopup(
    moveId: Long,
    gain: Int,
    placed: Set<Int>,
    grid: Rect,
    gapPx: Float,
    contentOrigin: Offset,
) {
    if (placed.isEmpty() || grid.width <= 0f) return

    val pitch = (grid.width + gapPx) / BOARD_SIZE
    var sumX = 0f
    var sumY = 0f
    for (index in placed) {
        sumX += index % BOARD_SIZE
        sumY += index / BOARD_SIZE
    }
    val centerX = grid.left + (sumX / placed.size + 0.5f) * pitch - gapPx / 2f
    val centerY = grid.top + (sumY / placed.size + 0.5f) * pitch - gapPx / 2f

    val progress = remember { Animatable(0f) }
    LaunchedEffect(moveId) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        )
    }

    Text(
        text = "+$gain",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .graphicsLayer {
                // Read the animation inside the layer block so only drawing is invalidated.
                val p = progress.value
                translationX = centerX - contentOrigin.x - size.width / 2f
                translationY = centerY - contentOrigin.y - size.height / 2f - 54.dp.toPx() * p
                alpha = 1f - ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val s = 1.15f - 0.15f * p
                scaleX = s
                scaleY = s
            },
    )
}

private fun cellSizeFor(grid: Rect, gap: Float): Float =
    if (grid.width > 0f) (grid.width - gap * (BOARD_SIZE - 1)) / BOARD_SIZE else 0f

private fun computeTarget(
    drag: DragState,
    grid: Rect,
    cell: Float,
    gap: Float,
    lift: Float,
): DropTarget? {
    if (cell <= 0f || grid.width <= 0f) return null
    val step = cell + gap
    val width = drag.piece.width * step - gap
    val height = drag.piece.height * step - gap
    val left = drag.pointer.x - width / 2f
    val top = drag.pointer.y - lift - height / 2f
    val col = ((left - grid.left) / step).roundToInt()
    val row = ((top - grid.top) / step).roundToInt()
    return DropTarget(col, row)
}

@Composable
private fun GameHeader(
    score: Int,
    moveId: Long,
    soundEnabled: Boolean,
    onNewGame: () -> Unit,
    onToggleSound: () -> Unit,
    onExitToMenu: () -> Unit,
    sound: SoundEffects?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "score",
    )
    val scoreScale = remember { Animatable(1f) }
    LaunchedEffect(moveId) {
        if (moveId > 0L) {
            scoreScale.snapTo(1.18f)
            scoreScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "theBlocks!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = animatedScore.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = scoreScale.value
                    scaleY = scoreScale.value
                },
            )
        }

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .testTag("menu")
                    .semantics { contentDescription = "More options" },
            ) {
                OverflowDots()
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("New game") },
                    onClick = {
                        menuOpen = false
                        sound?.play(GameSound.Tap)
                        onNewGame()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Main menu") },
                    onClick = {
                        menuOpen = false
                        sound?.play(GameSound.Tap)
                        onExitToMenu()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (soundEnabled) "Sound: on" else "Sound: off") },
                    onClick = {
                        menuOpen = false
                        sound?.play(GameSound.Tap)
                        haptics.performHapticFeedback(
                            if (soundEnabled) HapticFeedbackType.ToggleOff
                            else HapticFeedbackType.ToggleOn,
                        )
                        onToggleSound()
                    },
                )
                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = {
                        menuOpen = false
                        sound?.play(GameSound.Tap)
                        showAbout = true
                    },
                )
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            sound = sound,
            onDismiss = { showAbout = false },
        )
    }
}

@Composable
private fun AboutDialog(sound: SoundEffects?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(text = "theBlocks!", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "theBlocks! is a clean and open-source alternative to Block Blast " +
                        "with no ads, no accounts and no tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "drop pieces onto the 8×8 board, clear full rows and columns, and " +
                        "chain combos for bigger scores. you can play offline or online, " +
                        "doesn't matter since the game doesn't have trackers that spy on you " +
                        "or intrusive ads and doesn't even connect to the internet, unlike " +
                        "some other games (cough cough Block Blast)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "made with ❤️ by theStudio (and deepseek)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    sound?.play(GameSound.Tap)
                    onDismiss()
                },
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun OverflowDots() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(20.dp)) {
        val radius = size.width * 0.095f
        val x = size.width / 2f
        for (fraction in listOf(0.2f, 0.5f, 0.8f)) {
            drawCircle(color = color, radius = radius, center = Offset(x, size.height * fraction))
        }
    }
}

@Composable
private fun BoardCard(
    board: List<Int>,
    clearing: Set<Int>,
    placedCells: Set<Int>,
    moveId: Long,
    invalidTick: Int,
    preview: Map<Int, Boolean>,
    previewColor: Color?,
    onGridCoords: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A soft bounce whenever lines disappear.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(clearing) {
        if (clearing.isNotEmpty()) {
            bounce.animateTo(
                targetValue = 1.025f,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
            )
            bounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    // Sideways wobble when a piece is rejected.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(invalidTick) {
        if (invalidTick > 0) {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    0f at 0
                    -1f at 50
                    1f at 100
                    -0.6f at 160
                    0.4f at 220
                    0f at 300
                },
            )
        }
    }

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = bounce.value
            scaleY = bounce.value
            translationX = shake.value * 9.dp.toPx()
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(BoardPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("board")
                    .onGloballyPositioned { onGridCoords(it) },
                verticalArrangement = Arrangement.spacedBy(CellGap),
            ) {
                for (row in 0 until BOARD_SIZE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(CellGap),
                    ) {
                        for (col in 0 until BOARD_SIZE) {
                            val index = row * BOARD_SIZE + col
                            BoardCell(
                                value = board.getOrElse(index) { EMPTY },
                                isClearing = index in clearing,
                                justPlaced = index in placedCells,
                                moveId = moveId,
                                preview = preview[index],
                                previewColor = previewColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    value: Int,
    isClearing: Boolean,
    justPlaced: Boolean,
    moveId: Long,
    preview: Boolean?,
    previewColor: Color?,
    modifier: Modifier = Modifier,
) {
    val fill = when {
        value != EMPTY -> BlockPalette[value % BlockPalette.size]
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    }
    val shape = RoundedCornerShape(percent = 22)

    // Pop-in for freshly placed cells. Keyed on the move id as well, so placing a piece
    // back onto the same cell in a later move still animates.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(moveId, justPlaced) {
        // Cleared cells shatter instead of popping.
        if (justPlaced && moveId > 0L && !isClearing) {
            pop.snapTo(0.45f)
            pop.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    // Cleared lines shatter: the cell is replaced by four shards that fly apart and fade.
    val shatter = remember { Animatable(0f) }
    LaunchedEffect(isClearing) {
        if (isClearing) {
            shatter.snapTo(0f)
            shatter.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = CLEAR_ANIM_MS, easing = LinearOutSlowInEasing),
            )
        } else {
            shatter.snapTo(0f)
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    ) {
        if (isClearing) {
            ShatterOverlay(color = fill, progress = { shatter.value })
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fill, shape),
            )
        }
        if (preview != null) {
            // A quick pop each time the preview lands on a new cell, so sweeping the
            // piece across the board feels responsive rather than static. The remember
            // is scoped to this branch, so it replays whenever the preview re-appears.
            val previewScale = remember { Animatable(0.82f) }
            LaunchedEffect(Unit) {
                previewScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
                )
            }
            val overlay = if (preview) {
                (previewColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = previewScale.value
                        scaleY = previewScale.value
                    }
                    .background(overlay, shape),
            )
        }
    }
}

/**
 * Draws the four shards a cleared cell breaks into. Each quadrant starts where the intact
 * cell was, then drifts outwards, rotates a little and fades — a short, contained burst.
 */
@Composable
private fun ShatterOverlay(color: Color, progress: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val corner = CornerRadius(w * 0.2f, w * 0.2f)

        // The intact cell fades out almost immediately, so no seams show on the first frame.
        val intact = (1f - p / 0.16f).coerceIn(0f, 1f)
        if (intact > 0f) {
            drawRoundRect(color = color.copy(alpha = intact), cornerRadius = corner)
        }

        val shardW = w * 0.44f
        val shardH = h * 0.44f
        val spread = w * 0.55f
        val fade = (1f - ((p - 0.25f) / 0.75f)).coerceIn(0f, 1f)
        val shrink = 1f - 0.35f * p
        val shards = listOf(
            Offset(0f, 0f) to Offset(-1f, -1f),
            Offset(w - shardW, 0f) to Offset(1f, -1f),
            Offset(0f, h - shardH) to Offset(-1f, 1f),
            Offset(w - shardW, h - shardH) to Offset(1f, 1f),
        )
        for ((topLeft, dir) in shards) {
            val cx = topLeft.x + shardW / 2f + dir.x * spread * p
            val cy = topLeft.y + shardH / 2f + dir.y * spread * p
            val sw = shardW * shrink
            val sh = shardH * shrink
            rotate(degrees = dir.x * dir.y * 26f * p, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = color.copy(alpha = fade),
                    topLeft = Offset(cx - sw / 2f, cy - sh / 2f),
                    size = Size(sw, sh),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@Composable
private fun ComboBadge(combo: Int, modifier: Modifier = Modifier) {
    // Small punch every time the combo count grows.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo >= 2) {
            pulse.snapTo(1.28f)
            pulse.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    AnimatedVisibility(
        visible = combo >= 2,
        enter = scaleIn(initialScale = 0.7f) + fadeIn(),
        exit = scaleOut(targetScale = 0.7f) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            },
        ) {
            Text(
                text = "COMBO ×$combo",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun Tray(
    tray: List<Piece?>,
    draggingIndex: Int?,
    enabled: Boolean,
    onSlotBounds: (Int, Rect) -> Unit,
    onDragStart: (Int, Piece, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (index in 0 until tray.size) {
            val piece = tray[index]
            val isBeingDragged = draggingIndex == index
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("tray-$index")
                    .onGloballyPositioned { onSlotBounds(index, it.boundsInRoot()) }
                    .pointerInput(piece?.id, enabled) {
                        if (piece == null || !enabled) return@pointerInput
                        // No touch slop: the piece starts following the finger on touch down,
                        // which is what makes dragging feel responsive.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            onDragStart(index, piece, down.position)
                            var last = down.position
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    val delta = change.position - last
                                    last = change.position
                                    if (delta != Offset.Zero) {
                                        change.consume()
                                        onDrag(delta)
                                    }
                                }
                            } finally {
                                // Also runs when the gesture is cancelled, so a drag can never
                                // get stuck on screen.
                                onDragEnd()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val slotWidth = maxWidth
                val slotHeight = maxHeight
                val slotColor by animateColorAsState(
                    targetValue = if (isBeingDragged) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(durationMillis = 140),
                    label = "slotColor",
                )
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(24.dp),
                    color = slotColor,
                ) {
                    if (piece != null) {
                        val cell = minOf(slotWidth / 5.4f, slotHeight / 5.4f)
                        // New pieces (and the initial tray) pop in instead of appearing abruptly,
                        // one slot after another so a refill reads as a wave.
                        val refill = remember { Animatable(1f) }
                        LaunchedEffect(piece.id) {
                            delay(index * 45L)
                            refill.snapTo(0.55f)
                            refill.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.5f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            PieceView(
                                piece = piece,
                                cell = cell,
                                gap = cell * 0.12f,
                                alpha = if (isBeingDragged) 0.25f else 1f,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = refill.value
                                    scaleY = refill.value
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PieceView(
    piece: Piece,
    cell: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val width = cell * piece.width + gap * (piece.width - 1)
    val height = cell * piece.height + gap * (piece.height - 1)
    val color = BlockPalette[piece.colorIndex % BlockPalette.size].copy(alpha = alpha)
    val shape = RoundedCornerShape(percent = 22)

    Box(modifier = modifier.size(width, height)) {
        piece.cells.forEach { offsetCell ->
            Box(
                modifier = Modifier
                    .offset(
                        x = cell * offsetCell.x.toFloat() + gap * offsetCell.x.toFloat(),
                        y = cell * offsetCell.y.toFloat() + gap * offsetCell.y.toFloat(),
                    )
                    .size(cell)
                    .background(color, shape),
            )
        }
    }
}

@Composable
internal fun GameOverDialog(
    score: Int,
    best: Int,
    runStartBest: Int,
    onPlayAgain: () -> Unit,
    onExitToMenu: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        shape = RoundedCornerShape(28.dp),
        title = { Text(text = "No moves left", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (score > runStartBest) "New best score!" else "Best: $best",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onPlayAgain) {
                Text("Play again")
            }
        },
        dismissButton = {
            TextButton(onClick = onExitToMenu) {
                Text("Main menu")
            }
        },
    )
}
