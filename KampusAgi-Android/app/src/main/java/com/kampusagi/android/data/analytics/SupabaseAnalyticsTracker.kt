package com.kampusagi.android.data.analytics

import android.util.Log
import com.kampusagi.android.di.IoDispatcher
import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.domain.analytics.AnalyticsTracker
import com.kampusagi.android.domain.settings.PrivacyPreferenceRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Analytics"

@Singleton
class SupabaseAnalyticsTracker @Inject constructor(
    private val client: SupabaseClient,
    private val privacy: PrivacyPreferenceRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) : AnalyticsTracker {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun track(event: AnalyticsEvent) {
        scope.launch {
            try {
                if (!privacy.analyticsEnabled.first()) return@launch
                if (client.auth.currentUserOrNull() == null) return@launch
                client.postgrest.rpc("track_event", buildJsonObject { put("p_name", event.name) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Analitik en iyi gayretle çalışır: başarısızlık yalnızca loglanır, kullanıcıya yansımaz ve yeniden denenmez.
                Log.w(TAG, "Analitik olayı gönderilemedi: ${event.name}", e)
            }
        }
    }
}
