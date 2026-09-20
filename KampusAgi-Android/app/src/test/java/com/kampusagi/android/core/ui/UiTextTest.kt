package com.kampusagi.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.domain.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Her tipli hata, strings.xml'den çözülen anlamlı bir Türkçe metne bağlıdır (koda gömülü metin yok). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UiTextTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val allErrors: List<AppError> = listOf(
        AppError.Network(), AppError.InvalidCredentials(), AppError.EmailNotConfirmed(), AppError.EmailAlreadyRegistered(),
        AppError.WeakPassword(), AppError.InvalidEmail(), AppError.RateLimited(), AppError.Unauthorized(), AppError.Forbidden(),
        AppError.NotFound(), AppError.UsernameTaken(), AppError.FileUnreadable(), AppError.FileTooLarge(10), AppError.NotPdf(),
        AppError.ProductUnavailable(), AppError.ProductAlreadyOwned(), AppError.Server("Sunucu mesajı"), AppError.Unknown(),
    )

    @Test
    fun `her hata turu bos olmayan bir mesaja cozulur`() {
        allErrors.forEach { error ->
            val text = error.toUiText().resolve(context)
            assertTrue("${error::class.simpleName} için mesaj boş", text.isNotBlank())
        }
    }

    @Test
    fun `sunucu mesaji aynen iletilir diger hatalar kaynak dosyasindan gelir`() {
        assertEquals("Sunucu mesajı", AppError.Server("Sunucu mesajı").toUiText().resolve(context))
        assertEquals("Bu plan şu an satın alınamıyor.", AppError.ProductUnavailable().toUiText().resolve(context))
        assertEquals("Bu plana zaten sahipsin. Planın birazdan etkinleşecek.", AppError.ProductAlreadyOwned().toUiText().resolve(context))
        assertEquals("Dosya çok büyük. En fazla 10 MB olabilir.", AppError.FileTooLarge(10).toUiText().resolve(context))
    }

    @Test
    fun `AppError olmayan istisna genel hata metnine duser`() {
        assertEquals(context.getString(com.kampusagi.android.R.string.error_unknown), IllegalStateException("x").toUiText().resolve(context))
    }
}
