package com.qmurzik.animetv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sized for a "10-foot" TV viewing distance: noticeably larger than the phone-oriented
 * Material3 defaults, per item 2's "читаемый текст с расстояния" requirement. [textScale]
 * comes straight from Settings -> Appearance so users can bump it further.
 */
fun tvTypography(textScale: Float = 1f): Typography {
    fun sp(base: Int) = (base * textScale).sp

    return Typography(
        displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = sp(57), lineHeight = sp(64)),
        displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = sp(45), lineHeight = sp(52)),
        headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = sp(36), lineHeight = sp(44)),
        headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = sp(30), lineHeight = sp(38)),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = sp(26), lineHeight = sp(32)),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = sp(22), lineHeight = sp(28)),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = sp(20), lineHeight = sp(28)),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = sp(18), lineHeight = sp(26)),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = sp(18), lineHeight = sp(24)),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = sp(16), lineHeight = sp(22)),
    )
}
