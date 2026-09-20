package com.kampusagi.android.snapshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.events.CampusEvent
import com.kampusagi.android.feature.events.EventsContent
import com.kampusagi.android.feature.events.EventsUiState
import com.kampusagi.android.testutil.testEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class EventsSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var previousTimeZone: TimeZone

    /** Etkinlik saatleri yerel saat diliminde yazılır; snapshot'lar makineden bağımsız olsun diye UTC sabitlenir. */
    @Before
    fun pinTimeZone() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(previousTimeZone)

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
    }

    @Composable
    private fun events(state: EventsUiState, onToggle: (CampusEvent) -> Unit = {}) = EventsContent(
        state = state, snackbarHostState = remember { SnackbarHostState() },
        onBack = {}, onRetry = {}, onRefresh = {}, onToggleAttendance = onToggle,
    )

    private val sample = listOf(
        testEvent("1", joined = true, count = 5, description = "Sıfırdan Python öğrenmek isteyenler için 3 saatlik atölye."),
        testEvent("2", joined = false, count = 0, university = false, description = ""),
    )

    @Test
    fun eventList() = snap("events_list") { events(EventsUiState(Loadable.Success(sample))) }

    @Test
    fun eventListWithPendingRequest() = snap("events_pending") {
        events(EventsUiState(Loadable.Success(sample), pendingEventIds = setOf("2")))
    }

    @Test
    fun eventEmpty() = snap("events_empty") { events(EventsUiState(Loadable.Success(emptyList()))) }

    @Test
    fun eventError() = snap("events_error") { events(EventsUiState(Loadable.Failure(AppError.Network()))) }

    @Test
    fun `katil dugmesi geri cagriyi calistirir katiliyorsan Katiliyorsun gorunur ve bekleyen istekte dugme kilitlenir`() {
        val toggled = mutableListOf<String>()
        composeRule.setContent {
            KampusAgiTheme(darkTheme = false) {
                events(EventsUiState(Loadable.Success(sample), pendingEventIds = emptySet())) { toggled += it.id }
            }
        }
        composeRule.onNodeWithText("5 kişi katılıyor").assertExists()
        composeRule.onNodeWithText("Katılıyorsun ✓").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Katıl").assertIsEnabled().performClick()
        assertEquals(listOf("1", "2"), toggled)
    }

    @Test
    fun `bekleyen istekte katil dugmesi kapali`() {
        composeRule.setContent {
            KampusAgiTheme(darkTheme = false) {
                events(EventsUiState(Loadable.Success(sample), pendingEventIds = setOf("2")))
            }
        }
        // Yüklenirken düğme metin yerine göstergeyi çizer; erişilebilirlik durumu "Yükleniyor" olur ve düğme kapalıdır.
        composeRule.onNode(hasStateDescription("Yükleniyor")).assertIsNotEnabled()
    }
}
