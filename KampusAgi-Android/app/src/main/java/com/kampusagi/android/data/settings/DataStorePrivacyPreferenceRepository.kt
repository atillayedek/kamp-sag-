package com.kampusagi.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.kampusagi.android.domain.settings.PrivacyPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val ANALYTICS_ENABLED_KEY = booleanPreferencesKey("analytics_enabled")

@Singleton
class DataStorePrivacyPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PrivacyPreferenceRepository {

    /** Kayıt yoksa (ve dosya okunamıyorsa) varsayılan AÇIK'tır. */
    override val analyticsEnabled: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[ANALYTICS_ENABLED_KEY] ?: true }

    override suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { it[ANALYTICS_ENABLED_KEY] = enabled }
    }
}
