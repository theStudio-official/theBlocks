package com.theblocks.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.theblocks.app.R
import kotlinx.coroutines.delay

private enum class AppScreen { Splash, Title, Game }

private const val SPLASH_DURATION_MS = 1200L

/**
 * Splash -> title -> game. The game state lives in the ViewModel, so leaving to the
 * title screen and starting a new game always starts clean.
 */
@Composable
fun AppRoot(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Splash) }
    val sound = rememberSoundEffects()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.soundEnabled) {
        sound.enabled = state.soundEnabled
    }

    LaunchedEffect(screen) {
        if (screen == AppScreen.Splash) {
            delay(SPLASH_DURATION_MS)
            screen = AppScreen.Title
        }
    }

    BackHandler(enabled = screen == AppScreen.Game) {
        screen = AppScreen.Title
    }

    Crossfade(
        targetState = screen,
        modifier = modifier,
        label = "screen",
    ) { current ->
        when (current) {
            AppScreen.Splash -> SplashScreen()
            AppScreen.Title -> TitleScreen(
                best = state.best,
                canContinue = state.canContinue,
                sound = sound,
                onStart = {
                    viewModel.newGame()
                    screen = AppScreen.Game
                },
                onContinue = {
                    if (viewModel.continueGame()) screen = AppScreen.Game
                },
            )

            AppScreen.Game -> GameScreen(
                viewModel = viewModel,
                sound = sound,
                onExitToMenu = { screen = AppScreen.Title },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SplashScreen(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "splashAlpha",
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.studio_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer { this.alpha = alpha },
        )
    }
}

@Composable
fun TitleScreen(
    best: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    sound: SoundEffects? = null,
    canContinue: Boolean = false,
    onContinue: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "titleAlpha",
    )
    val rise by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "titleRise",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "titleLogoScale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                alpha = contentAlpha
                translationY = rise.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.menulogo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1.4734f)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                },
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "theBlocks!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "a simple, ad-free block blast alternative",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        if (canContinue) {
            FilledTonalButton(
                onClick = {
                    sound?.play(GameSound.Tap)
                    onContinue()
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(text = "Continue", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = {
                sound?.play(GameSound.Tap)
                onStart()
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
        ) {
            Text(text = "New game", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (best > 0) "best score: $best" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
