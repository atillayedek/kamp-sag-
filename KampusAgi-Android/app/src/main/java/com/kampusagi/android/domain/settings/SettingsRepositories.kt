package com.kampusagi.android.domain.settings

import kotlinx.coroutines.flow.Flow

/** Görünüm tercihi: sistem temasını izle (varsayılan) veya açık/koyu sabitle. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** Sistem koyu mu bilgisiyle bu tercihin gerçekte koyu olup olmadığını çözer. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}

/** Cihazda kalıcı saklanan görünüm tercihi. */
interface ThemePreferenceRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

/**
 * Sunucuda saklanan bildirim tercihleri. Yeni mesaj bildirimi sunucu tarafında tercihe göre üretilir
 * (bkz. supabase/migrations/202609190009). Tüm metotlar hata durumunda
 * [com.kampusagi.android.domain.common.AppError] fırlatır.
 */
interface NotificationPreferencesRepository {
    /** Hiç ayar yapılmamışsa `true` (varsayılan açık). */
    suspend fun isNewMessageEnabled(): Boolean

    suspend fun setNewMessageEnabled(enabled: Boolean)
}

/**
 * Cihazda saklanan gizlilik tercihi: kullanım istatistikleri (yalnızca olay adı, bkz. AnalyticsEvent) paylaşılsın mı?
 * Varsayılan AÇIK; kullanıcı Profil > Gizlilik ve Konum'dan kapatabilir (docs/DECISIONS.md D36).
 */
interface PrivacyPreferenceRepository {
    val analyticsEnabled: Flow<Boolean>

    suspend fun setAnalyticsEnabled(enabled: Boolean)
}
