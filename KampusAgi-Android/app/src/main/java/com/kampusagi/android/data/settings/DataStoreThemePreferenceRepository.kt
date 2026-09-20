package com.kampusagi.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.domain.settings.ThemePreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

@Singleton
class DataStoreThemePreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemePreferenceRepository {

    override val themeMode: Flow<ThemeMode> = dataStore.data
        // Okunamayan tercih dosyası uygulamayı bozmamalı; sistem temasına düşülür.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> themeModeFrom(preferences[THEME_MODE_KEY]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }
}

/** Bilinmeyen/eksik değer (ör. sonradan kaldırılmış bir mod) güvenle [ThemeMode.SYSTEM]'e düşer. */
internal fun themeModeFrom(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
