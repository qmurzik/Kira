package com.qmurzik.animetv.domain.source

/**
 * Turns a title into a stable dedup/merge key so "Attack on Titan" from one source and
 * "Attack on Titan" (with different punctuation/casing) from another collapse into the
 * same [com.qmurzik.animetv.domain.model.AnimeId] instead of showing up as two cards.
 * Deliberately simple (lowercase, strip punctuation/whitespace) rather than fuzzy-matching -
 * a false merge (two different shows collapsed into one) is worse than an occasional
 * duplicate card, which the user can still tell apart by poster/year.
 */
object TitleNormalizer {

    fun normalize(title: String): String = title
        .lowercase()
        .replace(Regex("[^a-z0-9\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsCyrillic}]+"), "")
        .trim()

    /** Stable id used as [com.qmurzik.animetv.domain.model.AnimeId]. */
    fun idFor(title: String): String = "anime_${normalize(title).hashCode().toUInt()}"
}
