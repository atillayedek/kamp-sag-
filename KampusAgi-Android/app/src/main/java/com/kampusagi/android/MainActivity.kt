package com.kampusagi.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import com.kampusagi.android.feature.settings.AppThemeViewModel
import com.kampusagi.android.navigation.KampusAgiNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Edge-to-edge açık, sistem çubuğu insets'leri her ekranda
 * `Scaffold`/`WindowInsets` ile doğru uygulanmalıdır (bkz. her ekranın kendi
 * Scaffold kullanımı).
 *
 * Push bildirim (FCM) kullanıcı tarafından İPTAL EDİLDİ (2026-09-16) — bkz.
 * memory-bank/Memory_Bank.md §11, AI_Guidelines.md §45.7. Bildirim izni
 * isteme kodu ve POST_NOTIFICATIONS manifest izni bilerek YOK; bu proje
 * turunda hiçbir push bildirim altyapısı kurulmuyor.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appThemeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by appThemeViewModel.themeMode.collectAsStateWithLifecycle()
            // Tercih okunana kadar (birkaç ms) çizim yapılmaz: yanlış temayla bir kare görünmesin.
            themeMode?.let { mode ->
                val darkTheme = mode.isDark(isSystemInDarkTheme())
                // Sistem çubuğu ikon renkleri uygulama temasına uysun (sistem temasından bağımsız açık/koyu seçimi için şart).
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                    )
                    onDispose { }
                }
                KampusAgiTheme(darkTheme = darkTheme) {
                    Surface {
                        KampusAgiNavHost()
                    }
                }
            }
        }
    }
}

/** Gezinme çubuğu scrim renkleri (androidx.activity varsayılanlarıyla aynı). */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
