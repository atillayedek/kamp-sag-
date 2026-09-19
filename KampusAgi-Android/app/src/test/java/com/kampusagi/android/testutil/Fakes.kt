package com.kampusagi.android.testutil

import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.auth.AuthUser
import com.kampusagi.android.domain.auth.SignUpResult
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.university.University
import com.kampusagi.android.domain.university.UniversityRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import com.kampusagi.android.domain.verification.VerificationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

val testUser = AuthUser(uid = "user-1", email = "ogrenci@kampus.test", isEmailVerified = true)

class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {
    val state = MutableStateFlow(initialUser)
    override val currentUser: AuthUser? get() = state.value
    override fun observeAuthState(): Flow<AuthUser?> = state

    var signInBlock: suspend (String, String) -> AuthUser = { _, _ -> testUser.also { state.value = it } }
    var signUpBlock: suspend (String, String) -> SignUpResult = { _, _ -> SignUpResult.SignedIn(testUser.also { state.value = it }) }
    var resendCalls = mutableListOf<String>()
    var refreshCalls = 0
    var signOutCalls = 0
    var refreshBlock: suspend () -> Unit = {}

    override suspend fun signIn(email: String, password: String): AuthUser = signInBlock(email, password)
    override suspend fun signUp(email: String, password: String): SignUpResult = signUpBlock(email, password)
    override suspend fun resendSignUpConfirmation(email: String) { resendCalls += email }
    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser = testUser.also { state.value = it }
    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun refreshSession() { refreshCalls++; refreshBlock() }
    override suspend fun signOut() { signOutCalls++; state.value = null }
}

class FakeProfileRepository(var profile: Profile = incompleteProfile) : ProfileRepository {
    var getBlock: suspend () -> Profile = { profile }
    var updateBlock: suspend (String, String, String, String) -> Unit = { _, _, _, _ -> }
    val updates = mutableListOf<List<String>>()

    override suspend fun getMyProfile(): Profile = getBlock()
    override suspend fun updateMyProfile(fullName: String, username: String, universityId: String, department: String) {
        updateBlock(fullName, username, universityId, department)
        updates += listOf(fullName, username, universityId, department)
    }

    companion object {
        val incompleteProfile = Profile("user-1", "user_abc12345", "", null, null, null)
        val completeProfile = Profile("user-1", "ogrenci", "Test Öğrenci", null, "uni-1", "Bilgisayar Mühendisliği")
    }
}

class FakeUniversityRepository(var universities: List<University> = defaultUniversities) : UniversityRepository {
    var block: suspend () -> List<University> = { universities }
    var calls = 0
    override suspend fun getActiveUniversities(): List<University> { calls++; return block() }

    companion object {
        val defaultUniversities = listOf(
            University("uni-1", "Test Üniversitesi", "TÜ", "Ankara"),
            University("uni-2", "Deneme Teknik Üniversitesi", "DTÜ", "İstanbul"),
        )
    }
}

class FakeVerificationRepository : StudentVerificationRepository {
    val statuses = MutableSharedFlow<VerificationStatus>(replay = 1)
    var fetchBlock: suspend () -> VerificationStatus = { error("fetchBlock ayarlanmadı") }
    var observeBlock: (() -> Flow<VerificationStatus>)? = null
    val submitted = mutableListOf<ByteArray>()
    var submitBlock: suspend (ByteArray) -> Unit = {}
    var observeCalls = 0

    override suspend fun submitDocument(fileBytes: ByteArray) {
        submitBlock(fileBytes)
        submitted += fileBytes
    }

    override suspend fun fetchStatus(): VerificationStatus = fetchBlock()

    override fun observeStatus(): Flow<VerificationStatus> {
        observeCalls++
        return observeBlock?.invoke() ?: flow { statuses.collect { emit(it) } }
    }
}
