package com.chenyehui.bible.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chenyehui.bible.data.AppPrefs
import com.chenyehui.bible.data.BookMeta
import com.chenyehui.bible.data.Manifest
import com.chenyehui.bible.data.ThemeMode
import com.chenyehui.bible.data.UiLanguageMode
import com.chenyehui.bible.data.TranslationInfo
import com.chenyehui.bible.data.chapterDisplayTitle
import java.util.Locale
import kotlin.math.roundToInt

enum class ReaderMenuTab { Directory, Settings }

@Composable
fun ReaderMenu(
    tab: ReaderMenuTab,
    onTabChange: (ReaderMenuTab) -> Unit,
    onDismiss: () -> Unit,
    manifest: Manifest,
    translation: TranslationInfo,
    book: BookMeta,
    chapter: Int,
    pageIndex: Int,
    pageCount: Int,
    onChapterSelected: (Int) -> Unit,
    onTranslationChange: (String) -> Unit,
    onBookSelected: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    letterSpacing: Float,
    onLetterSpacingChange: (Float) -> Unit,
    lineSpacingExtra: Float,
    onLineSpacingChange: (Float) -> Unit,
    paragraphSpacing: Float,
    onParagraphSpacingChange: (Float) -> Unit,
    languageMode: UiLanguageMode,
    onLanguageModeChange: (UiLanguageMode) -> Unit,
) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    var showBookPicker by remember { mutableStateOf(false) }
    val pageLabel = strings.pageLabel(
        (pageIndex + 1).coerceAtMost(pageCount.coerceAtLeast(1)),
        pageCount.coerceAtLeast(1),
    )

    BackHandler {
        if (showBookPicker) {
            showBookPicker = false
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.uiSurface),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.uiSurface,
            contentColor = palette.uiText,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .displayCutoutPadding(),
                ) {
                    MenuTopBar(
                        title = book.title,
                        onBack = onDismiss,
                        trailing = strings.translationShort(translation),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${chapterDisplayTitle(translation.language, chapter)} · " +
                                pageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.uiMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChoiceChip(
                                text = strings.directory,
                                selected = tab == ReaderMenuTab.Directory,
                                onClick = { onTabChange(ReaderMenuTab.Directory) },
                            )
                            Spacer(Modifier.width(8.dp))
                            ChoiceChip(
                                text = strings.settings,
                                selected = tab == ReaderMenuTab.Settings,
                                onClick = { onTabChange(ReaderMenuTab.Settings) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            when (tab) {
                                ReaderMenuTab.Directory -> DirectoryContent(
                                    translation = translation,
                                    book = book,
                                    chapter = chapter,
                                    onChapterSelected = onChapterSelected,
                                    onOpenBookPicker = { showBookPicker = true },
                                )

                                ReaderMenuTab.Settings -> SettingsContent(
                                    manifest = manifest,
                                    translation = translation,
                                    themeMode = themeMode,
                                    onThemeModeChange = onThemeModeChange,
                                    fontSize = fontSize,
                                    onFontSizeChange = onFontSizeChange,
                                    letterSpacing = letterSpacing,
                                    onLetterSpacingChange = onLetterSpacingChange,
                                    lineSpacingExtra = lineSpacingExtra,
                                    onLineSpacingChange = onLineSpacingChange,
                                    paragraphSpacing = paragraphSpacing,
                                    onParagraphSpacingChange = onParagraphSpacingChange,
                                    languageMode = languageMode,
                                    onLanguageModeChange = onLanguageModeChange,
                                    onTranslationChange = onTranslationChange,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showBookPicker) {
        BookPickerScreen(
            translation = translation,
            currentBookId = book.id,
            onDismiss = { showBookPicker = false },
            onSelect = { id ->
                showBookPicker = false
                onBookSelected(id)
            },
        )
    }
}

@Composable
private fun DirectoryContent(
    translation: TranslationInfo,
    book: BookMeta,
    chapter: Int,
    onChapterSelected: (Int) -> Unit,
    onOpenBookPicker: () -> Unit,
) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${strings.translationTitle(translation)} · " +
                    strings.chapterCount(book.chapters),
                style = MaterialTheme.typography.labelSmall,
                color = palette.uiMuted,
                modifier = Modifier.weight(1f),
            )
            ChoiceChip(
                text = strings.changeBook,
                selected = false,
                onClick = onOpenBookPicker,
                horizontalPadding = 10,
            )
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.runtime.key(translation.id, book.id) {
            val gridState = rememberLazyGridState(
                initialFirstVisibleItemIndex = (chapter - 1).coerceAtLeast(0),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items((1..book.chapters).toList(), key = { it }) { number ->
                    ChapterCell(
                        number = number,
                        selected = number == chapter,
                        onClick = { onChapterSelected(number) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterCell(number: Int, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalBiblePalette.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = if (selected) palette.uiSelected else Color.Transparent,
        contentColor = if (selected) palette.uiOnSelected else palette.uiText,
        border = if (selected) null else BorderStroke(1.dp, palette.uiBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SettingsContent(
    manifest: Manifest,
    translation: TranslationInfo,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    letterSpacing: Float,
    onLetterSpacingChange: (Float) -> Unit,
    lineSpacingExtra: Float,
    onLineSpacingChange: (Float) -> Unit,
    paragraphSpacing: Float,
    onParagraphSpacingChange: (Float) -> Unit,
    languageMode: UiLanguageMode,
    onLanguageModeChange: (UiLanguageMode) -> Unit,
    onTranslationChange: (String) -> Unit,
) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        SettingsLabel(
            strings.interfaceLanguage,
            strings.languageModeLabel(languageMode),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                text = strings.languageSystem,
                selected = languageMode == UiLanguageMode.SYSTEM,
                onClick = { onLanguageModeChange(UiLanguageMode.SYSTEM) },
            )
            ChoiceChip(
                text = strings.languageChinese,
                selected = languageMode == UiLanguageMode.CHINESE,
                onClick = { onLanguageModeChange(UiLanguageMode.CHINESE) },
            )
            ChoiceChip(
                text = strings.languageEnglish,
                selected = languageMode == UiLanguageMode.ENGLISH,
                onClick = { onLanguageModeChange(UiLanguageMode.ENGLISH) },
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsLabel(strings.themeLabel, when (themeMode) {
            ThemeMode.SYSTEM -> strings.themeSystem
            ThemeMode.LIGHT -> strings.themeLight
            ThemeMode.DARK -> strings.themeDark
        })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                text = strings.themeSystem,
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
            )
            ChoiceChip(
                text = strings.themeLight,
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
            )
            ChoiceChip(
                text = strings.themeDark,
                selected = themeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsLabel(strings.translationLabel, strings.translationTitle(translation))
        Spacer(Modifier.height(8.dp))
        TranslationChips(
            versions = manifest.versions,
            selectedId = translation.id,
            onSelect = onTranslationChange,
        )

        Spacer(Modifier.height(22.dp))
        SettingsLabel(strings.fontSize, fontSize.roundToInt().toString())
        Slider(
            value = fontSize,
            onValueChange = { onFontSizeChange(it.roundToInt().toFloat()) },
            valueRange = AppPrefs.MIN_FONT..AppPrefs.MAX_FONT,
            steps = (AppPrefs.MAX_FONT - AppPrefs.MIN_FONT).toInt() - 1,
        )

        Spacer(Modifier.height(10.dp))
        SettingsLabel(strings.letterSpacing, String.format(Locale.US, "%.1f", letterSpacing))
        Slider(
            value = letterSpacing,
            onValueChange = {
                onLetterSpacingChange((it * 10f).roundToInt() / 10f)
            },
            valueRange = AppPrefs.MIN_LETTER_SPACING..AppPrefs.MAX_LETTER_SPACING,
            steps = 19,
        )

        Spacer(Modifier.height(10.dp))
        SettingsLabel(strings.lineSpacing, String.format(Locale.US, "%.1f", lineSpacingExtra))
        Slider(
            value = lineSpacingExtra,
            onValueChange = {
                onLineSpacingChange((it * 10f).roundToInt() / 10f)
            },
            valueRange = AppPrefs.MIN_LINE_SPACING_EXTRA..AppPrefs.MAX_LINE_SPACING_EXTRA,
            steps = 9,
        )

        Spacer(Modifier.height(10.dp))
        SettingsLabel(strings.paragraphSpacing, String.format(Locale.US, "%.1f", paragraphSpacing))
        Slider(
            value = paragraphSpacing,
            onValueChange = {
                onParagraphSpacingChange((it * 10f).roundToInt() / 10f)
            },
            valueRange = AppPrefs.MIN_PARAGRAPH_SPACING..AppPrefs.MAX_PARAGRAPH_SPACING,
            steps = 19,
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsLabel(title: String, value: String) {
    val palette = LocalBiblePalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = palette.uiText,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = palette.uiMuted,
        )
    }
}

@Composable
private fun MenuTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: String,
) {
    val palette = LocalBiblePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextIconButton(label = "←", onClick = onBack, fontSize = 22)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = palette.uiText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelSmall,
            color = palette.uiMuted,
            maxLines = 1,
        )
    }
    Rule()
}

@Composable
private fun BookPickerScreen(
    translation: TranslationInfo,
    currentBookId: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    val oldTestament = translation.books.filter { it.testament == 0 }
    val newTestament = translation.books.filter { it.testament == 1 }
    val startIndex = if (currentBookId <= oldTestament.size) {
        currentBookId
    } else {
        oldTestament.size + 2 + (currentBookId - oldTestament.size - 1)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startIndex.coerceAtLeast(0),
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
        color = palette.uiBackground,
        contentColor = palette.uiText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding(),
        ) {
            MenuTopBar(
                title = strings.chooseBook,
                onBack = onDismiss,
                trailing = "${strings.translationShort(translation)} · " +
                    strings.bookCount(translation.books.size),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "ot") {
                    SectionHeading(
                        title = strings.oldTestament,
                        trailing = strings.bookCount(oldTestament.size),
                        modifier = Modifier.padding(
                            start = 22.dp,
                            end = 22.dp,
                            top = 18.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
                items(oldTestament, key = { "ot-${it.id}" }) { item ->
                    BookPickRow(
                        book = item,
                        selected = item.id == currentBookId,
                        onClick = { onSelect(item.id) },
                    )
                }
                item(key = "nt") {
                    SectionHeading(
                        title = strings.newTestament,
                        trailing = strings.bookCount(newTestament.size),
                        modifier = Modifier.padding(
                            start = 22.dp,
                            end = 22.dp,
                            top = 24.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
                items(newTestament, key = { "nt-${it.id}" }) { item ->
                    BookPickRow(
                        book = item,
                        selected = item.id == currentBookId,
                        onClick = { onSelect(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookPickRow(book: BookMeta, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) palette.uiSelected.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .height(50.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = book.id.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = palette.uiMuted,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.uiText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = strings.chapterCount(book.chapters),
            style = MaterialTheme.typography.labelSmall,
            color = palette.uiMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (selected) "✓" else book.short,
            style = MaterialTheme.typography.labelSmall,
            color = palette.uiMuted,
        )
    }
}
