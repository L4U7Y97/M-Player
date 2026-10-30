package dev.localmusic.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.localmusic.domain.SortSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed settings (spec §7), file `settings.preferences_pb`. Conversions go through [SettingsMapper].
 * EQ band levels are stored unclamped-by-device (device clamping happens in AudioEffectsController, Phase 6).
 */
class SettingsRepository(context: Context, appScope: CoroutineScope) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toAppSettings() }
        .distinctUntilChanged()

    /** Hot copy for synchronous reads (theme on first frame, etc.). */
    val state: StateFlow<AppSettings> = settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    suspend fun setSort(spec: SortSpec) = store.edit {
        it[SORT_FIELD] = SettingsMapper.enumToString(spec.field)
        it[SORT_ASC] = spec.ascending
    }

    suspend fun setSkipSilence(enabled: Boolean) = store.edit { it[SKIP_SILENCE] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME_MODE] = SettingsMapper.enumToString(mode) }
    suspend fun setPureBlack(enabled: Boolean) = store.edit { it[PURE_BLACK] = enabled }
    suspend fun setAccent(accent: Accent) = store.edit { it[ACCENT] = SettingsMapper.enumToString(accent) }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun updateEq(transform: (EqSettings) -> EqSettings) = store.edit { prefs ->
        val eq = transform(prefs.toEq())
        prefs[EQ_ENABLED] = eq.enabled
        prefs[EQ_PRESET] = SettingsMapper.sanitizePresetIndex(eq.presetIndex)
        prefs[EQ_BANDS] = SettingsMapper.bandLevelsToCsv(eq.bandLevelsMb)
        prefs[BASS_ENABLED] = eq.bassBoostEnabled
        prefs[BASS_STRENGTH] = SettingsMapper.clampStrength(eq.bassBoostStrength)
    }

    private fun Preferences.toEq() = EqSettings(
        enabled = this[EQ_ENABLED] ?: false,
        presetIndex = SettingsMapper.sanitizePresetIndex(this[EQ_PRESET] ?: EqSettings.CUSTOM_PRESET),
        bandLevelsMb = SettingsMapper.bandLevelsFromCsv(this[EQ_BANDS], Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()),
        bassBoostEnabled = this[BASS_ENABLED] ?: false,
        bassBoostStrength = SettingsMapper.clampStrength(this[BASS_STRENGTH] ?: 0),
    )

    private fun Preferences.toAppSettings() = AppSettings(
        themeMode = SettingsMapper.themeModeFromString(this[THEME_MODE]),
        pureBlack = this[PURE_BLACK] ?: false,
        accent = SettingsMapper.accentFromString(this[ACCENT]),
        dynamicColor = this[DYNAMIC_COLOR] ?: true,
        skipSilence = this[SKIP_SILENCE] ?: false,
        sort = SortSpec(SettingsMapper.sortFieldFromString(this[SORT_FIELD]), this[SORT_ASC] ?: true),
        eq = toEq(),
    )

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val ACCENT = stringPreferencesKey("accent")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ASC = booleanPreferencesKey("sort_ascending")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = intPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val BASS_ENABLED = booleanPreferencesKey("bass_enabled")
        val BASS_STRENGTH = intPreferencesKey("bass_strength")
    }
}
