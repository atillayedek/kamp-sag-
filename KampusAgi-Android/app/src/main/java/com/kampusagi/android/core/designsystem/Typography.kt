package com.kampusagi.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Font: Manrope (Google Fonts), yoksa varsayılan Roboto (kullanıcının isteği).
 *
 * NOT — bilerek AKTİF EDİLMEDİ: Google Fonts downloadable font sağlayıcısı
 * (`GoogleFont.Provider`) çalışması için `res/values/font_certs.xml`'de
 * Google'ın imza sertifika hash'lerini içeren bir dizi gerektirir. Bu değerler
 * yalnızca Android Studio'nun "Downloadable Fonts" sihirbazı (Resource Manager
 * > Fonts > Manrope) tarafından doğru şekilde üretilebilir — buraya elle,
 * doğrulanamayan bir sertifika hash'i YAZILMADI (uydurmak, sessizce bozuk bir
 * font sağlayıcısına yol açardı). Aşağıdaki `manropeFontFamily` bu yüzden
 * `FontFamily.Default`'a eşittir (Android'de bu = Roboto) — kullanıcının
 * kendi "yoksa varsayılan Roboto" isteğiyle zaten tutarlı bir başlangıç
 * durumu. Manrope'u etkinleştirmek için:
 * 1. Android Studio > Resource Manager > + > Vector/Font Asset yerine
 *    "Downloadable Fonts" seçip Manrope'u ekleyin (font_certs.xml otomatik oluşur).
 * 2. Aşağıdaki TODO bloğunun yorumunu kaldırıp `manropeFontFamily`'yi buna göre değiştirin.
 */
val manropeFontFamily: FontFamily = FontFamily.Default

// TODO(Manrope): Android Studio'nun ürettiği font_certs.xml ile birlikte aktif edin.
// private val provider = GoogleFont.Provider(
//     providerAuthority = "com.google.android.gms.fonts",
//     providerPackage = "com.google.android.gms",
//     certificates = R.array.com_google_android_gms_fonts_certs
// )
// val manropeFontFamily = FontFamily(
//     Font(googleFont = GoogleFont("Manrope"), fontProvider = provider, weight = FontWeight.Normal),
//     Font(googleFont = GoogleFont("Manrope"), fontProvider = provider, weight = FontWeight.SemiBold),
//     Font(googleFont = GoogleFont("Manrope"), fontProvider = provider, weight = FontWeight.ExtraBold),
// )

/**
 * Kullanıcının tasarım spesifikasyonundan: headlineSmall (ExtraBold),
 * titleLarge (Bold), bodyMedium onSurfaceVariant (renk Theme.kt'de uygulanır),
 * buton yazısı 15sp SemiBold, alan etiketi labelMedium.
 */
val KampusAgiTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = manropeFontFamily, fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontFamily = manropeFontFamily, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = manropeFontFamily, fontWeight = FontWeight.Bold),
        bodyMedium = base.bodyMedium.copy(fontFamily = manropeFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = manropeFontFamily, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontFamily = manropeFontFamily, fontWeight = FontWeight.SemiBold),
    )
}

/** Buton yazısı — 15sp SemiBold (spesifikasyonda birebir istendi). */
val KampusAgiButtonTextStyle = TextStyle(
    fontFamily = manropeFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
)
