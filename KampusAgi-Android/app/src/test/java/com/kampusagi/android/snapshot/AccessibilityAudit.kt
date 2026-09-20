package com.kampusagi.android.snapshot

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue

/** Erişilebilirlik alt sınırı: dokunulabilir her öğe en az 44dp × 44dp (Material 48dp önerir; şartname 44pt/dp). */
const val MIN_TOUCH_TARGET_DP = 44f

private fun SemanticsNode.accessibleLabel(): String {
    val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    return listOf(text, description).filter { it.isNotBlank() }.joinToString(" / ")
}

/**
 * Ekrandaki TÜM dokunulabilir öğeleri (tıklama/anahtar/düğme) denetler; ihlal varsa hepsini listeleyerek başarısız olur:
 *  1. TalkBack etiketi: birleştirilmiş anlamsal düğümde metin ya da contentDescription bulunmalı (yalnızca ikon = etiketsiz).
 *  2. Dokunma alanı: genişlik ve yükseklik en az [MIN_TOUCH_TARGET_DP].
 * Snapshot testleri her ekran/durumu çizdikten sonra bunu çağırır; böylece tüm ekranlar otomatik denetlenir.
 */
fun ComposeContentTestRule.assertAccessibleInteractions(minTargetDp: Float = MIN_TOUCH_TARGET_DP) {
    val density = onRoot().fetchSemanticsNode().layoutInfo.density.density
    val problems = mutableListOf<String>()
    onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
        val label = node.accessibleLabel()
        // touchBoundsInRoot: Material'ın minimumInteractiveComponentSize ile genişlettiği GERÇEK dokunma alanını da hesaba katar.
        val bounds = node.touchBoundsInRoot
        val widthDp = bounds.width / density
        val heightDp = bounds.height / density
        val name = label.ifBlank { "(etiketsiz, id=${node.id})" }
        if (label.isBlank()) problems += "ETİKETSİZ dokunulabilir öğe: ${node.id} (${widthDp}x${heightDp}dp)"
        if (widthDp < minTargetDp || heightDp < minTargetDp) {
            problems += "KÜÇÜK dokunma alanı: \"$name\" ${"%.1f".format(widthDp)}x${"%.1f".format(heightDp)}dp (en az ${minTargetDp}dp)"
        }
    }
    assertTrue("Erişilebilirlik ihlalleri:\n" + problems.joinToString("\n"), problems.isEmpty())
}
