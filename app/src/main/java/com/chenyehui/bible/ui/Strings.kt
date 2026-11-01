package com.chenyehui.bible.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.chenyehui.bible.data.TranslationInfo
import com.chenyehui.bible.data.UiLanguageMode
import java.util.Locale

enum class UiLanguage { CHINESE, ENGLISH }

/**
 * All strings that belong to the app's chrome. Scripture text and book names
 * stay in the language of the selected Bible translation.
 */
data class UiStrings(
    val language: UiLanguage,
    val loading: String,
    val loadError: String,
    val loadFailed: String,
    val retry: String,
    val noVerses: String,
    val directory: String,
    val settings: String,
    val changeBook: String,
    val chooseBook: String,
    val oldTestament: String,
    val newTestament: String,
    val translationLabel: String,
    val themeLabel: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val fontSize: String,
    val letterSpacing: String,
    val lineSpacing: String,
    val paragraphSpacing: String,
    val interfaceLanguage: String,
    val languageSystem: String,
    val languageChinese: String,
    val languageEnglish: String,
) {
    fun pageLabel(page: Int, total: Int): String =
        if (language == UiLanguage.CHINESE) "第 $page / $total 页" else "Page $page / $total"

    fun chapterCount(count: Int): String = when (language) {
        UiLanguage.CHINESE -> "$count 章"
        UiLanguage.ENGLISH -> if (count == 1) "1 chapter" else "$count chapters"
    }

    fun bookCount(count: Int): String = when (language) {
        UiLanguage.CHINESE -> "$count 卷"
        UiLanguage.ENGLISH -> if (count == 1) "1 book" else "$count books"
    }

    fun translationTitle(info: TranslationInfo): String = when (info.id) {
        "muling" -> if (language == UiLanguage.ENGLISH) "Muling Bible" else "牧灵圣经"
        "sigao" -> if (language == UiLanguage.ENGLISH) "Sigao Bible" else "思高圣经"
        "ccb" -> if (language == UiLanguage.ENGLISH) "CCB English" else "CCB 英文"
        else -> info.title
    }

    fun translationShort(info: TranslationInfo): String = when (info.id) {
        "muling" -> if (language == UiLanguage.ENGLISH) "Muling" else "牧灵"
        "sigao" -> if (language == UiLanguage.ENGLISH) "Sigao" else "思高"
        "ccb" -> "CCB"
        else -> info.short
    }

    fun languageModeLabel(mode: UiLanguageMode): String = when (mode) {
        UiLanguageMode.SYSTEM -> languageSystem
        UiLanguageMode.CHINESE -> languageChinese
        UiLanguageMode.ENGLISH -> languageEnglish
    }
}

val ZhStrings = UiStrings(
    language = UiLanguage.CHINESE,
    loading = "载入中…",
    loadError = "无法读取圣经数据",
    loadFailed = "载入失败",
    retry = "重试",
    noVerses = "本章暂无经文",
    directory = "目录",
    settings = "设置",
    changeBook = "切换书卷",
    chooseBook = "选择书卷",
    oldTestament = "旧约",
    newTestament = "新约",
    translationLabel = "译本",
    themeLabel = "主题",
    themeSystem = "跟随系统",
    themeLight = "浅色",
    themeDark = "深色",
    fontSize = "字号",
    letterSpacing = "字距",
    lineSpacing = "行距",
    paragraphSpacing = "段距",
    interfaceLanguage = "界面语言",
    languageSystem = "跟随系统",
    languageChinese = "中文",
    languageEnglish = "English",
)

val EnStrings = UiStrings(
    language = UiLanguage.ENGLISH,
    loading = "Loading…",
    loadError = "Unable to read Bible data",
    loadFailed = "Loading failed",
    retry = "Retry",
    noVerses = "No verses in this chapter",
    directory = "Contents",
    settings = "Settings",
    changeBook = "Change book",
    chooseBook = "Choose book",
    oldTestament = "Old Testament",
    newTestament = "New Testament",
    translationLabel = "Translation",
    themeLabel = "Theme",
    themeSystem = "System",
    themeLight = "Light",
    themeDark = "Dark",
    fontSize = "Font size",
    letterSpacing = "Letter spacing",
    lineSpacing = "Line spacing",
    paragraphSpacing = "Paragraph spacing",
    interfaceLanguage = "Language",
    languageSystem = "System",
    languageChinese = "中文",
    languageEnglish = "English",
)

val LocalUiStrings = staticCompositionLocalOf { ZhStrings }

fun uiStringsFor(language: UiLanguage): UiStrings = when (language) {
    UiLanguage.CHINESE -> ZhStrings
    UiLanguage.ENGLISH -> EnStrings
}

fun resolveUiLanguage(mode: UiLanguageMode): UiLanguage = when (mode) {
    UiLanguageMode.CHINESE -> UiLanguage.CHINESE
    UiLanguageMode.ENGLISH -> UiLanguage.ENGLISH
    UiLanguageMode.SYSTEM ->
        if (Locale.getDefault().language.startsWith("zh")) {
            UiLanguage.CHINESE
        } else {
            UiLanguage.ENGLISH
        }
}
