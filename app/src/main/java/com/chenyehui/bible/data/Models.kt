package com.chenyehui.bible.data

/** A translation plus its 73-book table of contents. */
data class TranslationInfo(
    val id: String,
    val title: String,
    val short: String,
    val language: String,
    val books: List<BookMeta>,
)

data class BookMeta(
    val id: Int,
    val title: String,
    val short: String,
    val testament: Int, // 0 = Old Testament, 1 = New Testament
    val chapters: Int,
)

data class Manifest(val versions: List<TranslationInfo>) {
    fun version(id: String): TranslationInfo? = versions.firstOrNull { it.id == id }

    fun versionOrFirst(id: String): TranslationInfo =
        version(id) ?: versions.firstOrNull()
        ?: error("The bundled Bible manifest contains no translations")

    fun book(versionId: String, bookId: Int): BookMeta? =
        version(versionId)?.books?.firstOrNull { it.id == bookId }
}

data class ReadingProgress(
    val versionId: String,
    val bookId: Int,
    val chapter: Int,
    val offset: Int = 0,
)

data class Book(
    val id: Int,
    val title: String,
    val chapters: List<Chapter>,
)

data class Chapter(
    val number: Int,
    val blocks: List<Block>,
) {
    val verses: List<Block.Verse> get() = blocks.filterIsInstance<Block.Verse>()
}

sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Verse(val number: Int, val text: String) : Block

    /** A paragraph boundary from a <br/> in the print source. */
    data object ParagraphBreak : Block
}

enum class UiLanguageMode(val key: String) {
    SYSTEM("system"),
    CHINESE("zh"),
    ENGLISH("en");

    companion object {
        fun fromKey(key: String?): UiLanguageMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private val CHINESE_DIGITS = "零一二三四五六七八九"

fun chineseNumber(number: Int): String {
    if (number <= 0) return number.toString()
    if (number < 10) return CHINESE_DIGITS[number].toString()
    if (number < 20) {
        return "十" + if (number % 10 == 0) "" else CHINESE_DIGITS[number % 10].toString()
    }
    if (number < 100) {
        val tens = number / 10
        val ones = number % 10
        return CHINESE_DIGITS[tens].toString() + "十" +
            if (ones == 0) "" else CHINESE_DIGITS[ones].toString()
    }
    if (number < 1000) {
        val hundreds = number / 100
        val rest = number % 100
        val prefix = CHINESE_DIGITS[hundreds].toString() + "百"
        if (rest == 0) return prefix
        if (rest < 10) return prefix + "零" + CHINESE_DIGITS[rest]
        if (rest < 20) {
            val ones = rest % 10
            return prefix + "一十" + if (ones == 0) "" else CHINESE_DIGITS[ones].toString()
        }
        val tens = rest / 10
        val ones = rest % 10
        return prefix + CHINESE_DIGITS[tens] + "十" +
            if (ones == 0) "" else CHINESE_DIGITS[ones].toString()
    }
    return number.toString()
}

fun chapterDisplayTitle(language: String, number: Int): String =
    if (language == "en") "Chapter $number" else "第" + chineseNumber(number) + "章"

fun chapterLabel(language: String, number: Int): String = chapterDisplayTitle(language, number)
