package com.kampusagi.android.snapshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.university.University
import com.kampusagi.android.feature.auth.login.LoginScreenContent
import com.kampusagi.android.feature.auth.login.LoginUiState
import com.kampusagi.android.feature.auth.register.RegisterActions
import com.kampusagi.android.feature.auth.register.RegisterScreenContent
import com.kampusagi.android.feature.auth.register.RegisterStep
import com.kampusagi.android.feature.auth.register.RegisterUiState
import com.kampusagi.android.feature.auth.welcome.WelcomeScreen
import com.kampusagi.android.feature.verification.PendingReviewScreenContent
import com.kampusagi.android.feature.verification.RejectedScreenContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Kimlik doğrulama / kayıt / doğrulama ekranlarının light + dark ekran görüntüleri (test verisi yalnızca burada). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class ScreenSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val universities = listOf(
        University("uni-1", "Test Üniversitesi", "TÜ", "Ankara"),
        University("uni-2", "Deneme Teknik Üniversitesi", "DTÜ", "İstanbul"),
        University("uni-3", "Örnek Kampüs Üniversitesi", "ÖKÜ", "İzmir"),
    )

    private val noActions = RegisterActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
    }

    @Composable
    private fun register(state: RegisterUiState) =
        RegisterScreenContent(state = state, snackbarHostState = remember { SnackbarHostState() }, actions = noActions)

    @Test
    fun welcome() = snap("screen_welcome") { WelcomeScreen(onLoginClick = {}, onRegisterClick = {}) }

    @Test
    fun login() = snap("screen_login") {
        LoginScreenContent(
            uiState = LoginUiState(email = "ogrenci@kampus.test", password = "gizli"),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {}, onEmailChange = {}, onPasswordChange = {},
            onForgotPassword = {}, onSubmit = {}, onGoogleClick = {},
        )
    }

    @Test
    fun registerAccount() = snap("screen_register_1_account") {
        register(RegisterUiState(email = "ogrenci@kampus.test", password = "123456"))
    }

    @Test
    fun registerEmailConfirmation() = snap("screen_register_1b_confirm") {
        register(RegisterUiState(email = "ogrenci@kampus.test", password = "123456", awaitingEmailConfirmation = true))
    }

    @Test
    fun registerPersonal() = snap("screen_register_2_personal") {
        register(
            RegisterUiState(
                step = RegisterStep.PERSONAL_INFO, firstStep = RegisterStep.PERSONAL_INFO,
                fullName = "Test Öğrenci", username = "Ab",
            ),
        )
    }

    @Test
    fun registerUniversity() = snap("screen_register_3_university") {
        register(
            RegisterUiState(
                step = RegisterStep.UNIVERSITY, firstStep = RegisterStep.PERSONAL_INFO,
                universities = Loadable.Success(universities), selectedUniversity = universities[1],
            ),
        )
    }

    @Test
    fun registerUniversityLoading() = snap("screen_register_3b_university_loading") {
        register(RegisterUiState(step = RegisterStep.UNIVERSITY, firstStep = RegisterStep.PERSONAL_INFO, universities = Loadable.Loading))
    }

    @Test
    fun registerDepartment() = snap("screen_register_4_department") {
        register(
            RegisterUiState(
                step = RegisterStep.DEPARTMENT, firstStep = RegisterStep.PERSONAL_INFO,
                department = "Bilgisayar Mühendisliği",
            ),
        )
    }

    @Test
    fun registerSummary() = snap("screen_register_5_summary") {
        register(
            RegisterUiState(
                step = RegisterStep.SUMMARY, firstStep = RegisterStep.PERSONAL_INFO,
                email = "ogrenci@kampus.test", fullName = "Test Öğrenci", username = "test_ogrenci",
                selectedUniversity = universities[0], department = "Bilgisayar Mühendisliği",
            ),
        )
    }

    @Test
    fun registerDocument() = snap("screen_register_6_document") {
        register(
            RegisterUiState(
                step = RegisterStep.STUDENT_DOCUMENT, firstStep = RegisterStep.PERSONAL_INFO,
                documentName = "ogrenci_belgesi.pdf",
            ),
        )
    }

    @Test
    fun registerDocumentEmpty() = snap("screen_register_6b_document_empty") {
        register(RegisterUiState(step = RegisterStep.STUDENT_DOCUMENT, firstStep = RegisterStep.PERSONAL_INFO))
    }

    @Test
    fun pendingReview() = snap("screen_pending") {
        PendingReviewScreenContent(isRefreshing = false, snackbarHostState = remember { SnackbarHostState() }, onRefresh = {}, onLogout = {})
    }

    @Test
    fun rejected() = snap("screen_rejected") {
        RejectedScreenContent(
            reason = "Belge okunamıyor.", isResubmitting = false,
            snackbarHostState = remember { SnackbarHostState() }, onPickDocument = {}, onLogout = {},
        )
    }
}
