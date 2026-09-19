package com.kampusagi.android.core.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kampusagi.android.domain.common.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Seçilen belgenin arayüzde gösterilecek üst bilgisi (dosya içeriği okunmadan). */
class PickedDocumentInfo(val displayName: String?, val sizeBytes: Long?)

/** İçeriği doğrulanmış (PDF, boyut sınırı içinde) belge. */
class PickedDocument(val displayName: String?, val bytes: ByteArray)

/**
 * Storage Access Framework'ten seçilen PDF'i güvenle okur: boyut sınırı (bucket limitiyle aynı, 10 MB)
 * okuma SIRASINDA da uygulanır — büyük/yanlış etiketli dosya belleği doldurmaz; içerik `%PDF-`
 * imzasıyla doğrulanır (MIME beyanına güvenilmez).
 */
@Singleton
class PickedDocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun describe(uri: Uri): PickedDocumentInfo = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use PickedDocumentInfo(null, null)
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    PickedDocumentInfo(
                        displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null,
                        sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null,
                    )
                } ?: PickedDocumentInfo(null, null)
        } catch (e: SecurityException) {
            throw AppError.FileUnreadable(e)
        }
    }

    suspend fun read(uri: Uri): PickedDocument = withContext(Dispatchers.IO) {
        val info = describe(uri)
        if (info.sizeBytes != null && info.sizeBytes > MAX_DOCUMENT_BYTES) throw AppError.FileTooLarge(MAX_DOCUMENT_MEGABYTES)
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_DOCUMENT_BYTES) throw AppError.FileTooLarge(MAX_DOCUMENT_MEGABYTES)
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            } ?: throw AppError.FileUnreadable()
            if (!bytes.hasPdfSignature()) throw AppError.NotPdf()
            PickedDocument(info.displayName, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppError) {
            throw e
        } catch (e: IOException) {
            throw AppError.FileUnreadable(e)
        } catch (e: SecurityException) {
            throw AppError.FileUnreadable(e)
        }
    }

    companion object {
        const val MAX_DOCUMENT_MEGABYTES = 10
        const val MAX_DOCUMENT_BYTES = MAX_DOCUMENT_MEGABYTES * 1024L * 1024L
        private const val BUFFER_SIZE = 16 * 1024
    }
}

private val PDF_SIGNATURE = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(), '-'.code.toByte())

/** PDF dosyaları `%PDF-` ile başlar. */
internal fun ByteArray.hasPdfSignature(): Boolean =
    size >= PDF_SIGNATURE.size && PDF_SIGNATURE.indices.all { this[it] == PDF_SIGNATURE[it] }
