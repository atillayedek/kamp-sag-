package com.kampusagi.android.data.common

import com.kampusagi.android.domain.common.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ErrorMappingTest {

    @Test
    fun `IOException ve alt siniflari Network olur`() {
        assertTrue(IOException("kesildi").toAppError() is AppError.Network)
        assertTrue(UnknownHostException("dns").toAppError() is AppError.Network)
    }

    @Test
    fun `AppError aynen korunur`() {
        val error = AppError.Forbidden()
        assertSame(error, error.toAppError())
    }

    @Test
    fun `bilinmeyen hata Unknown icinde nedeniyle saklanir`() {
        val cause = IllegalStateException("beklenmedik")
        val mapped = cause.toAppError()
        assertTrue(mapped is AppError.Unknown)
        assertSame(cause, mapped.cause)
    }

    @Test
    fun `sunucu hata govdesinden Turkce mesaj okunur`() {
        assertEquals("Zaten incelenmekte olan bir başvurunuz var.", parseServerError("""{"error":"Zaten incelenmekte olan bir başvurunuz var."}"""))
        assertEquals("Mesaj", parseServerError("""{"error":"Mesaj","extra":1}"""))
    }

    @Test
    fun `gecersiz veya bos govde mesaj vermez`() {
        assertNull(parseServerError(null))
        assertNull(parseServerError(""))
        assertNull(parseServerError("bu json degil"))
        assertNull(parseServerError("""{"baska":"alan"}"""))
        assertNull(parseServerError("""{"error":"  "}"""))
    }

    @Test
    fun `mapErrors iptali yutmaz`() = runTest {
        try {
            mapErrors<Unit> { throw CancellationException("iptal") }
            fail("CancellationException yayılmalıydı")
        } catch (e: CancellationException) {
            assertEquals("iptal", e.message)
        }
    }

    @Test
    fun `mapErrors diger hatalari AppError a cevirir`() = runTest {
        try {
            mapErrors<Unit> { throw IOException("ag yok") }
            fail("AppError fırlatılmalıydı")
        } catch (e: AppError) {
            assertTrue(e is AppError.Network)
        }
    }
}
