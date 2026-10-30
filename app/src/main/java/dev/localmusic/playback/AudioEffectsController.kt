package dev.localmusic.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import dev.localmusic.settings.EqSettings
import dev.localmusic.settings.SettingsMapper
import dev.localmusic.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the device equalizer supports (spec §7). Center frequencies in Hz, levels in millibels. */
data class EqCapabilities(
    val bandCount: Int,
    val centerFreqsHz: List<Int>,
    val minMb: Int,
    val maxMb: Int,
    val presetNames: List<String>,
)

data class EffectsState(
    /** False until the first creation attempt finished. */
    val checked: Boolean = false,
    /** Equalizer usable on this device. False -> the EQ screen shows "Equalizer not available on this device". */
    val equalizerAvailable: Boolean = false,
    val bassBoostAvailable: Boolean = false,
    val bassStrengthSupported: Boolean = false,
    val caps: EqCapabilities? = null,
)

/**
 * Owns the android.media.audiofx Equalizer + BassBoost bound to the app-generated audio session id that the
 * service's ExoPlayer uses (spec §7), so effects exist independently of the service. Created lazily in
 * try/catch: failure -> not available, never a crash. Collects `settings.eq` and applies it; re-applies after
 * re-creation. All effect calls happen on the main thread.
 */
class AudioEffectsController(
    private val audioSessionId: Int,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var collectJob: Job? = null

    private val _state = MutableStateFlow(EffectsState())
    val state: StateFlow<EffectsState> = _state.asStateFlow()

    /** Idempotent: creates the effects (if not yet) and starts applying the persisted settings. Main thread. */
    fun start() {
        ensureEffects()
        if (collectJob == null) {
            collectJob = scope.launch {
                settings.settings.map { it.eq }.distinctUntilChanged().collect { apply(it) }
            }
        }
    }

    /** Live preview while a band slider is dragged; persisted separately on drag end. */
    fun previewBand(band: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val caps = _state.value.caps ?: return
        if (band !in 0 until caps.bandCount) return
        safely("preview band") {
            eq.setBandLevel(band.toShort(), SettingsMapper.clampBandLevel(levelMb, caps.minMb, caps.maxMb).toShort())
        }
    }

    /** Live preview of the bass boost strength while dragging. */
    fun previewBassStrength(strength: Int) {
        val bb = bassBoost ?: return
        if (!_state.value.bassStrengthSupported) return
        safely("preview bass") { bb.setStrength(SettingsMapper.clampStrength(strength).toShort()) }
    }

    /** Releases the effects (they are re-created and re-applied by the next [start]). */
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    private fun ensureEffects() {
        if (equalizer != null || bassBoost != null) return
        var eqCaps: EqCapabilities? = null
        var created: Equalizer? = null
        val eq = try {
            Equalizer(0, audioSessionId).also { e ->
                created = e
                val bands = e.numberOfBands.toInt()
                check(bands > 0) { "no bands" }
                val range = e.bandLevelRange
                eqCaps = EqCapabilities(
                    bandCount = bands,
                    centerFreqsHz = (0 until bands).map { e.getCenterFreq(it.toShort()) / 1000 },
                    minMb = range[0].toInt(),
                    maxMb = range[1].toInt(),
                    presetNames = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) },
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer not available: $t")
            runCatching { created?.release() }
            eqCaps = null
            null
        }
        val bb = try {
            BassBoost(0, audioSessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "BassBoost not available: $t")
            null
        }
        equalizer = eq
        bassBoost = bb
        _state.value = EffectsState(
            checked = true,
            equalizerAvailable = eq != null,
            bassBoostAvailable = bb != null,
            bassStrengthSupported = bb?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: false,
            caps = eqCaps,
        )
        Log.d(TAG, "effects created on session $audioSessionId: eq=${eq != null} bass=${bb != null} caps=$eqCaps")
        // Re-apply whatever is persisted (e.g. after a release/re-create).
        if (collectJob != null) apply(settings.state.value.eq)
    }

    private fun apply(eq: EqSettings) {
        val e = equalizer
        val caps = _state.value.caps
        if (e != null && caps != null) {
            safely("apply eq") {
                if (eq.presetIndex >= 0 && eq.presetIndex < caps.presetNames.size) {
                    e.usePreset(eq.presetIndex.toShort())
                    val levels = (0 until caps.bandCount).map { e.getBandLevel(it.toShort()).toInt() }
                    if (levels != eq.bandLevelsMb) {
                        // Save the preset's levels so the sliders move and a later band edit starts from them.
                        scope.launch {
                            settings.updateEq { cur ->
                                if (cur.presetIndex == eq.presetIndex) cur.copy(bandLevelsMb = levels) else cur
                            }
                        }
                    }
                } else {
                    for (band in 0 until caps.bandCount) {
                        val level = SettingsMapper.clampBandLevel(eq.bandLevelsMb.getOrElse(band) { 0 }, caps.minMb, caps.maxMb)
                        e.setBandLevel(band.toShort(), level.toShort())
                    }
                }
                e.enabled = eq.enabled
            }
        }
        val bb = bassBoost
        if (bb != null) {
            safely("apply bass") {
                if (_state.value.bassStrengthSupported) {
                    bb.setStrength(SettingsMapper.clampStrength(eq.bassBoostStrength).toShort())
                }
                bb.enabled = eq.bassBoostEnabled
            }
        }
        Log.d(TAG, "applied $eq")
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // E.g. the audio server restarted and the effect handle died: drop the effects so the next start()
            // (service creation / Equalizer screen) re-creates and re-applies them.
            Log.w(TAG, "$what failed: $t")
            release()
        }
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
