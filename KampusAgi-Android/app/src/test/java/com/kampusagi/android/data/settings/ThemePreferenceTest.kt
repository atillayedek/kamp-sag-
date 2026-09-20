package com.kampusagi.android.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kampusagi.android.domain.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gerçek DataStore ile (geçici dosya): tercih yazılır, yeniden açılışta korunur, bozuk değerde sisteme düşülür.
 * Not: DataStore var olan dosyanın ÜSTÜNE yazarken Windows JVM'inde rename hatası verir (Android'de değil);
 * bu yüzden testler her dosyaya en fazla bir kez yazar.
 */
class ThemePreferenceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    /** Aynı dosyada aynı anda tek DataStore olabilir; "yeniden açılış" için eskisinin kapsamı iptal edilir. */
    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private fun newDataStore(file: File, scope: CoroutineScope = newScope()) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `varsayilan tercih SYSTEM dir`() = runBlocking {
        val repository = DataStoreThemePreferenceRepository(newDataStore(File(folder.root, "a.preferences_pb")))
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `yazilan tercih okunur ve dosyadan yeniden acilista korunur`() = runBlocking {
        val file = File(folder.root, "b.preferences_pb")
        val firstScope = newScope()
        val first = DataStoreThemePreferenceRepository(newDataStore(file, firstScope))
        first.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, first.themeMode.first())

        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val reopened = DataStoreThemePreferenceRepository(newDataStore(file))
        assertEquals(ThemeMode.DARK, reopened.themeMode.first())
    }

    @Test
    fun `taninmayan kayitli deger SYSTEM e duser`() = runBlocking {
        val dataStore = newDataStore(File(folder.root, "c.preferences_pb"))
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPYA" }
        assertEquals(ThemeMode.SYSTEM, DataStoreThemePreferenceRepository(dataStore).themeMode.first())
    }

    @Test
    fun `themeModeFrom bilinen adlari cozer null ve bilinmeyeni SYSTEM yapar`() {
        assertEquals(ThemeMode.LIGHT, themeModeFrom("LIGHT"))
        assertEquals(ThemeMode.DARK, themeModeFrom("DARK"))
        assertEquals(ThemeMode.SYSTEM, themeModeFrom(null))
        assertEquals(ThemeMode.SYSTEM, themeModeFrom("dark"))
    }

    @Test
    fun `isDark sistem durumunu yalnizca SYSTEM modunda izler`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.isDark(systemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemInDarkTheme = true))
    }
}
