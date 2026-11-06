package com.chenyehui.bible.data

import android.content.Context
import java.util.Locale
import androidx.core.content.edit

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)

    /**
     * First run follows the system language: Chinese -> 牧灵, otherwise CCB.
     * Once the user picks a translation, the stored value always wins.
     */
    var translationId: String
        get() = prefs.getString(KEY_TRANSLATION, null) ?: defaultTranslationId()
        set(value) {
            prefs.edit { putString(KEY_TRANSLATION, value) }
        }

    var uiLanguage: UiLanguageMode
        get() = UiLanguageMode.fromKey(prefs.getString(KEY_UI_LANGUAGE, UiLanguageMode.SYSTEM.key))
        set(value) {
            prefs.edit { putString(KEY_UI_LANGUAGE, value.key) }
        }

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.key))
        set(value) {
            prefs.edit { putString(KEY_THEME, value.key) }
        }

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT, MAX_FONT)
        set(value) {
            prefs.edit { putFloat(KEY_FONT_SIZE, value.coerceIn(MIN_FONT, MAX_FONT)) }
        }

    /** Extra spacing in sp, on top of the font's natural advance. */
    var letterSpacing: Float
        get() = prefs.getFloat(KEY_LETTER_SPACING, DEFAULT_LETTER_SPACING)
            .coerceIn(MIN_LETTER_SPACING, MAX_LETTER_SPACING)

        set(value) {
            prefs.edit {
                putFloat(
                    KEY_LETTER_SPACING,
                    value.coerceIn(MIN_LETTER_SPACING, MAX_LETTER_SPACING),
                )
            }
        }

    /**
     * Extra line height in multiples of the font size. 0.0 uses the tight
     * 1.15x base line height.
     */
    var lineSpacingExtra: Float
        get() = prefs.getFloat(KEY_LINE_SPACING_EXTRA, DEFAULT_LINE_SPACING_EXTRA)
            .coerceIn(MIN_LINE_SPACING_EXTRA, MAX_LINE_SPACING_EXTRA)

        set(value) {
            prefs.edit {
                putFloat(
                    KEY_LINE_SPACING_EXTRA,
                    value.coerceIn(MIN_LINE_SPACING_EXTRA, MAX_LINE_SPACING_EXTRA),
                )
            }
        }

    /**
     * Space between body paragraphs, in multiples of the font size.
     * 0.6 is the default; it is independent from line spacing.
     */
    var paragraphSpacing: Float
        get() = prefs.getFloat(KEY_PARAGRAPH_SPACING, DEFAULT_PARAGRAPH_SPACING)
            .coerceIn(MIN_PARAGRAPH_SPACING, MAX_PARAGRAPH_SPACING)

        set(value) {
            prefs.edit {
                putFloat(
                    KEY_PARAGRAPH_SPACING,
                    value.coerceIn(MIN_PARAGRAPH_SPACING, MAX_PARAGRAPH_SPACING),
                )
            }
        }

    fun progress(): ReadingProgress? {
        val book = prefs.getInt(KEY_BOOK, 0)
        if (book <= 0) return null
        val chapter = prefs.getInt(KEY_CHAPTER, 1).coerceAtLeast(1)
        val version = prefs.getString(KEY_PROGRESS_VERSION, translationId) ?: translationId
        val offset = prefs.getInt(KEY_OFFSET, 0).coerceAtLeast(0)
        return ReadingProgress(version, book, chapter, offset)
    }

    fun saveProgress(
        versionId: String,
        bookId: Int,
        chapter: Int,
        offset: Int,
    ): ReadingProgress {
        val safeChapter = chapter.coerceAtLeast(1)
        val safeOffset = offset.coerceAtLeast(0)
        prefs.edit {
            putString(KEY_PROGRESS_VERSION, versionId)
            putInt(KEY_BOOK, bookId)
            putInt(KEY_CHAPTER, safeChapter)
            putInt(KEY_OFFSET, safeOffset)
        }
        return ReadingProgress(versionId, bookId, safeChapter, safeOffset)
    }

    companion object {
        const val DEFAULT_TRANSLATION_ZH = "muling"
        const val DEFAULT_TRANSLATION_EN = "ccb"

        fun defaultTranslationId(): String =
            if (Locale.getDefault().language.startsWith("zh")) {
                DEFAULT_TRANSLATION_ZH
            } else {
                DEFAULT_TRANSLATION_EN
            }
        const val DEFAULT_FONT_SIZE = 24f
        const val MIN_FONT = 16f
        const val MAX_FONT = 34f

        const val DEFAULT_LETTER_SPACING = 0f
        const val MIN_LETTER_SPACING = 0f
        const val MAX_LETTER_SPACING = 2f

        const val DEFAULT_LINE_SPACING_EXTRA = 0.0f
        const val MIN_LINE_SPACING_EXTRA = 0f
        const val MAX_LINE_SPACING_EXTRA = 1f

        const val DEFAULT_PARAGRAPH_SPACING = 0.6f
        const val MIN_PARAGRAPH_SPACING = 0f
        const val MAX_PARAGRAPH_SPACING = 2f

        /** Base line height multiplier when the extra spacing is 0.0. */
        const val BASE_LINE_HEIGHT = 1.15f

        private const val KEY_TRANSLATION = "translation"
        private const val KEY_UI_LANGUAGE = "ui_language"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_LETTER_SPACING = "letter_spacing_sp"
        private const val KEY_LINE_SPACING_EXTRA = "line_spacing_extra_v2"
        private const val KEY_PARAGRAPH_SPACING = "paragraph_spacing"
        private const val KEY_PROGRESS_VERSION = "progress_version"
        private const val KEY_BOOK = "last_book"
        private const val KEY_CHAPTER = "last_chapter"
        private const val KEY_OFFSET = "last_offset"
    }
}
