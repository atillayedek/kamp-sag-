package com.kampusagi.android.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Gerçek DataStore (geçici dosya): varsayılan AÇIK, kapatma yeniden açılışta korunur (dosya başına tek yazma: bkz. ThemePreferenceTest). */
class PrivacyPreferenceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private fun dataStore(file: File, scope: CoroutineScope = newScope()) = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `kayit yoksa istatistik paylasimi varsayilan olarak acik`() = runBlocking {
        val repository = DataStorePrivacyPreferenceRepository(dataStore(File(folder.root, "a.preferences_pb")))
        assertEquals(true, repository.analyticsEnabled.first())
    }

    @Test
    fun `kapatilan tercih yeniden acilista korunur`() = runBlocking {
        val file = File(folder.root, "b.preferences_pb")
        val firstScope = newScope()
        val first = DataStorePrivacyPreferenceRepository(dataStore(file, firstScope))
        first.setAnalyticsEnabled(false)
        assertEquals(false, first.analyticsEnabled.first())

        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        assertEquals(false, DataStorePrivacyPreferenceRepository(dataStore(file)).analyticsEnabled.first())
    }
}
