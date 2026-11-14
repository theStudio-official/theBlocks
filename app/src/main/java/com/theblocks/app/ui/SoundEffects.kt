package com.theblocks.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.theblocks.app.R

/** Every short sound the game can make. */
enum class GameSound { Pickup, Drop, Clear, Combo, Invalid, GameOver, Tap }

/**
 * Tiny SoundPool wrapper for the synthesised placeholder effects in res/raw.
 *
 * One instance is created at the app root and shared by the title and game screens.
 * Sounds are skipped until their sample has finished loading, and the whole thing is a
 * no-op when [enabled] is false (the in-game Sound toggle).
 */
class SoundEffects(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loaded = HashSet<Int>()
    private val samples: Map<GameSound, Int>

    var enabled: Boolean = true

    init {
        // Register the listener before loading so no completion can be missed.
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        samples = mapOf(
            GameSound.Pickup to pool.load(context, R.raw.sfx_pickup, 1),
            GameSound.Drop to pool.load(context, R.raw.sfx_drop, 1),
            GameSound.Clear to pool.load(context, R.raw.sfx_clear, 1),
            GameSound.Combo to pool.load(context, R.raw.sfx_combo, 1),
            GameSound.Invalid to pool.load(context, R.raw.sfx_invalid, 1),
            GameSound.GameOver to pool.load(context, R.raw.sfx_gameover, 1),
            GameSound.Tap to pool.load(context, R.raw.sfx_tap, 1),
        )
    }

    fun play(sound: GameSound) {
        if (!enabled) return
        val id = samples[sound] ?: return
        if (id !in loaded) return
        val volume = when (sound) {
            GameSound.Clear, GameSound.Combo -> 0.85f
            GameSound.Pickup, GameSound.Tap -> 0.55f
            else -> 0.75f
        }
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}

/** Creates (and disposes) a [SoundEffects] tied to the composition. */
@Composable
fun rememberSoundEffects(): SoundEffects {
    val context = LocalContext.current.applicationContext
    val sound = remember { SoundEffects(context) }
    DisposableEffect(sound) {
        onDispose { sound.release() }
    }
    return sound
}
