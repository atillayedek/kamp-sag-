package com.kampusagi.android.core

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.initialsOf
import com.kampusagi.android.core.file.PickedDocumentReader
import com.kampusagi.android.core.file.hasPdfSignature
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.feature.auth.register.RegisterValidators
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UtilitiesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // --- Avatar baş harfleri (Türkçe büyük harf kuralı) ---------------------------------------

    @Test
    fun `bas harfler Turkce kuralla buyutulur`() {
        assertEquals("İŞ", initialsOf("ilker şahin"))
        assertEquals("AY", initialsOf("ayşe nur yılmaz"))
        assertEquals("Ç", initialsOf("çağlar"))
        assertEquals("?", initialsOf("   "))
        assertEquals("?", initialsOf(""))
    }

    // --- Doğrulayıcılar ------------------------------------------------------------------------

    @Test
    fun `e-posta dogrulama`() {
        assertTrue(RegisterValidators.isValidEmail("ali@kampus.edu.tr"))
        assertTrue(RegisterValidators.isValidEmail("  ali@kampus.edu.tr "))
        assertFalse(RegisterValidators.isValidEmail("ali@kampus"))
        assertFalse(RegisterValidators.isValidEmail("ali kampus@x.com"))
        assertFalse(RegisterValidators.isValidEmail(""))
    }

    @Test
    fun `sifre en az 6 karakter olmali`() {
        assertFalse(RegisterValidators.isValidPassword("12345"))
        assertTrue(RegisterValidators.isValidPassword("123456"))
    }

    @Test
    fun `kullanici adi veritabani kisitlariyla ayni kurali izler`() {
        assertTrue(RegisterValidators.isValidUsername("abc"))
        assertTrue(RegisterValidators.isValidUsername("a_b_1"))
        assertTrue(RegisterValidators.isValidUsername("a".repeat(30)))
        assertFalse(RegisterValidators.isValidUsername("ab"))
        assertFalse(RegisterValidators.isValidUsername("a".repeat(31)))
        assertFalse(RegisterValidators.isValidUsername("Abc"))
        assertFalse(RegisterValidators.isValidUsername("ab-c"))
        assertFalse(RegisterValidators.isValidUsername("ışık"))
    }

    // --- PDF imzası ve belge okuyucu -----------------------------------------------------------

    @Test
    fun `PDF imzasi yalnizca gercek PDF basliginda gecer`() {
        assertTrue("%PDF-1.7 icerik".toByteArray().hasPdfSignature())
        assertFalse("PK".toByteArray().hasPdfSignature())
        assertFalse(byteArrayOf().hasPdfSignature())
        assertFalse("%PDF".toByteArray().hasPdfSignature())
    }

    @Test
    fun `belge okuyucu gecerli PDF'i okur`() = runTest {
        val file = tempFolder.newFile("a.pdf").apply { writeBytes("%PDF-1.4 govde".toByteArray()) }
        val document = PickedDocumentReader(context, UnconfinedTestDispatcher()).read(Uri.fromFile(file))
        assertEquals("%PDF-1.4 govde", String(document.bytes))
    }

    @Test
    fun `belge okuyucu PDF olmayan icerigi reddeder`() = runTest {
        val file = tempFolder.newFile("resim.pdf").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)) }
        try {
            PickedDocumentReader(context, UnconfinedTestDispatcher()).read(Uri.fromFile(file))
            fail("NotPdf bekleniyordu")
        } catch (e: AppError) {
            assertTrue(e is AppError.NotPdf)
        }
    }

    @Test
    fun `belge okuyucu boyut sinirini okuma sirasinda uygular`() = runTest {
        val file = tempFolder.newFile("buyuk.pdf")
        file.outputStream().use { out ->
            out.write("%PDF-1.4".toByteArray())
            out.write(ByteArray(PickedDocumentReader.MAX_DOCUMENT_BYTES.toInt()))
        }
        try {
            PickedDocumentReader(context, UnconfinedTestDispatcher()).read(Uri.fromFile(file))
            fail("FileTooLarge bekleniyordu")
        } catch (e: AppError.FileTooLarge) {
            assertEquals(PickedDocumentReader.MAX_DOCUMENT_MEGABYTES, e.maxMegabytes)
        }
    }

    @Test
    fun `okunamayan dosya FileUnreadable olur`() = runTest {
        val missing = Uri.fromFile(tempFolder.root.resolve("yok.pdf"))
        try {
            PickedDocumentReader(context, UnconfinedTestDispatcher()).read(missing)
            fail("FileUnreadable bekleniyordu")
        } catch (e: AppError) {
            assertTrue(e is AppError.FileUnreadable)
        }
    }

    // --- Hata -> kullanıcı metni ---------------------------------------------------------------

    @Test
    fun `her hata tipi bir Turkce mesaja eslenir`() {
        assertEquals(uiText(R.string.error_network), AppError.Network().toUiText())
        assertEquals(uiText(R.string.error_invalid_credentials), AppError.InvalidCredentials().toUiText())
        assertEquals(uiText(R.string.error_email_not_confirmed), AppError.EmailNotConfirmed().toUiText())
        assertEquals(uiText(R.string.error_weak_password), AppError.WeakPassword().toUiText())
        assertEquals(uiText(R.string.error_rate_limited), AppError.RateLimited().toUiText())
        assertEquals(uiText(R.string.error_unauthorized), AppError.Unauthorized().toUiText())
        assertEquals(uiText(R.string.error_forbidden), AppError.Forbidden().toUiText())
        assertEquals(uiText(R.string.error_not_found), AppError.NotFound().toUiText())
        assertEquals(uiText(R.string.error_file_too_large, 10), AppError.FileTooLarge(10).toUiText())
        assertEquals(UiText.Plain("Sunucu mesajı"), AppError.Server("Sunucu mesajı").toUiText())
        assertEquals(uiText(R.string.error_unknown), IllegalStateException("x").toUiText())
    }

    @Test
    fun `UiText kaynak metni bicimlendirerek cozer`() {
        assertEquals("Dosya çok büyük. En fazla 10 MB olabilir.", uiText(R.string.error_file_too_large, 10).resolve(context))
        assertEquals("düz", UiText.Plain("düz").resolve(context))
        assertNull(null as UiText?)
    }
}
