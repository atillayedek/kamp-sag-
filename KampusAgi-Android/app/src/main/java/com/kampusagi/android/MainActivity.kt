package com.kampusagi.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.kampusagi.android.core.designsystem.KampusAgiTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KampusAgiTheme {
                Surface {
                    KampusAgiNavHost()
                }
            }
        }
    }
}
