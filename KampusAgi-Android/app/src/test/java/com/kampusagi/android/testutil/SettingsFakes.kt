package com.kampusagi.android.testutil

import com.kampusagi.android.domain.account.AccountRepository
import com.kampusagi.android.domain.settings.NotificationPreferencesRepository
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.domain.settings.ThemePreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeThemePreferenceRepository(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferenceRepository {
    private val state = MutableStateFlow(initial)
    override val themeMode: Flow<ThemeMode> = state
    val saved = mutableListOf<ThemeMode>()
    var setBlock: suspend (ThemeMode) -> Unit = {}

    override suspend fun setThemeMode(mode: ThemeMode) {
        setBlock(mode)
        saved += mode
        state.value = mode
    }
}

class FakeAccountRepository : AccountRepository {
    var deleteBlock: suspend () -> Unit = {}
    var deleteCalls = 0

    override suspend fun deleteAccount() {
        deleteCalls++
        deleteBlock()
    }
}

class FakeNotificationPreferencesRepository : NotificationPreferencesRepository {
    var getBlock: suspend () -> Boolean = { true }
    var setBlock: suspend (Boolean) -> Unit = {}
    val saved = mutableListOf<Boolean>()

    override suspend fun isNewMessageEnabled() = getBlock()

    override suspend fun setNewMessageEnabled(enabled: Boolean) {
        setBlock(enabled)
        saved += enabled
    }
}
