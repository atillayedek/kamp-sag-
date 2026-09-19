package com.kampusagi.android.data.match

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Anlamsal Eşleşme" etiketi yalnızca skor gerçekten embedding benzerliği içeriyorsa gösterilir (yanlış iddia yok). */
class SemanticScoreTest {

    private fun breakdown(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `semantic_similarity sayisal ise anlamsal skordur`() {
        assertTrue(isSemanticScore(breakdown("""{"semantic_similarity":0.82,"help_type":0.5}""")))
        assertTrue(isSemanticScore(breakdown("""{"semantic_similarity":0}""")))
    }

    @Test
    fun `NOT_IMPLEMENTED metni anlamsal sayilmaz`() {
        assertFalse(isSemanticScore(breakdown("""{"semantic_similarity":"NOT_IMPLEMENTED","help_type":0.5}""")))
    }

    @Test
    fun `alan yoksa veya kirilim yoksa anlamsal sayilmaz`() {
        assertFalse(isSemanticScore(breakdown("""{"help_type":0.5}""")))
        assertFalse(isSemanticScore(null))
    }

    @Test
    fun `null degeri anlamsal sayilmaz`() {
        assertFalse(isSemanticScore(breakdown("""{"semantic_similarity":null}""")))
    }
}
