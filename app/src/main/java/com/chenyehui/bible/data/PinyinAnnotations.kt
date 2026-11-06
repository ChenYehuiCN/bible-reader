package com.chenyehui.bible.data

/**
 * Removes phonetic annotations that the Chinese sources embed directly in the
 * text, for example "混沌（dùn）" or "生(zī‌)". Only parenthesized content that
 * is unambiguously pinyin is removed; section ranges and explanatory notes
 * such as "（1-11）", "（休止）" or "（尼尼微即是那大城）" are left untouched.
 */
fun stripPinyinAnnotations(text: String): String {
    if (text.indexOf('（') < 0 && text.indexOf('(') < 0) return text
    return PARENTHESIZED_ANNOTATION.replace(text) { match ->
        if (isPinyinAnnotation(match.groupValues[1])) "" else match.value
    }
}

private val PARENTHESIZED_ANNOTATION = Regex("[（(]([^（）()]*)[）)]")
private val ZERO_WIDTH = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]")

private val PINYIN_TONE_MARKS = setOf(
    'ā', 'á', 'ǎ', 'à',
    'ē', 'é', 'ě', 'è',
    'ī', 'í', 'ǐ', 'ì',
    'ō', 'ó', 'ǒ', 'ò',
    'ū', 'ú', 'ǔ', 'ù',
    'ǖ', 'ǘ', 'ǚ', 'ǜ',
    'Ā', 'Á', 'Ǎ', 'À',
    'Ē', 'É', 'Ě', 'È',
    'Ī', 'Í', 'Ǐ', 'Ì',
    'Ō', 'Ó', 'Ǒ', 'Ò',
    'Ū', 'Ú', 'Ǔ', 'Ù',
    'Ǖ', 'Ǘ', 'Ǚ', 'Ǜ',
)

private val PINYIN_EXTRA_LETTERS = setOf(
    'ü', 'Ü', 'ê', 'Ê', 'ń', 'ň', 'ǹ', 'Ń', 'Ň', 'Ǹ', 'ḿ', 'Ḿ',
)

private val PINYIN_SEPARATORS = setOf('\'', '’', '·', '-')

private fun isPinyinAnnotation(raw: String): Boolean {
    val value = ZERO_WIDTH.replace(raw, "").trim()
    if (value.isEmpty()) return false
    // A tone mark is the discriminator that keeps this matcher from eating
    // Latin words or numbered cross-references. The bundled Chinese sources
    // have no tone-numbered or neutral-tone-only annotations.
    if (value.none { it in PINYIN_TONE_MARKS }) return false
    return value.all { char ->
        char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in PINYIN_TONE_MARKS ||
            char in PINYIN_EXTRA_LETTERS ||
            char == ' ' ||
            char in PINYIN_SEPARATORS
    }
}
