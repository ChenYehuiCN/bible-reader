package com.chenyehui.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chenyehui.bible.data.AppPrefs
import com.chenyehui.bible.data.BibleRepository
import com.chenyehui.bible.data.Manifest
import com.chenyehui.bible.data.ReadingProgress
import com.chenyehui.bible.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

@Composable
fun BibleApp(volumeKeys: SharedFlow<Int>) {
    val context = LocalContext.current
    val repository = remember { BibleRepository(context) }
    val prefs = remember { AppPrefs(context) }

    var manifest by remember { mutableStateOf<Manifest?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    var languageMode by remember { mutableStateOf(prefs.uiLanguage) }
    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var fontSize by remember { mutableFloatStateOf(prefs.fontSize) }
    var letterSpacing by remember { mutableFloatStateOf(prefs.letterSpacing) }
    var lineSpacingExtra by remember { mutableFloatStateOf(prefs.lineSpacingExtra) }
    var paragraphSpacing by remember { mutableFloatStateOf(prefs.paragraphSpacing) }

    val initialProgress = remember {
        prefs.progress() ?: ReadingProgress(
            versionId = prefs.translationId,
            bookId = 1,
            chapter = 1,
            offset = 0,
        )
    }

    LaunchedEffect(retryKey) {
        loadError = null
        try {
            manifest = withContext(Dispatchers.IO) { repository.manifest }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: e.javaClass.simpleName
        }
    }

    val uiLanguage = resolveUiLanguage(languageMode)
    val strings = remember(uiLanguage) { uiStringsFor(uiLanguage) }

    CompositionLocalProvider(LocalUiStrings provides strings) {
    BibleTheme(themeMode) {
        val palette = LocalBiblePalette.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.readerBackground,
            contentColor = palette.readerText,
        ) {
            val loaded = manifest
            when {
                loaded == null && loadError != null -> AppMessage(
                    title = strings.loadError,
                    message = loadError ?: "",
                    action = strings.retry,
                    onAction = { retryKey++ },
                )

                loaded == null -> SplashLoading()

                else -> ReaderScreen(
                    repository = repository,
                    manifest = loaded,
                    initialProgress = initialProgress,
                    themeMode = themeMode,
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                    lineSpacingExtra = lineSpacingExtra,
                    paragraphSpacing = paragraphSpacing,
                    languageMode = languageMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        prefs.themeMode = mode
                    },
                    onFontSizeChange = { size ->
                        fontSize = size
                        prefs.fontSize = size
                    },
                    onLetterSpacingChange = { spacing ->
                        letterSpacing = spacing
                        prefs.letterSpacing = spacing
                    },
                    onLineSpacingChange = { spacing ->
                        lineSpacingExtra = spacing
                        prefs.lineSpacingExtra = spacing
                    },
                    onParagraphSpacingChange = { spacing ->
                        paragraphSpacing = spacing
                        prefs.paragraphSpacing = spacing
                    },
                    onLanguageModeChange = { mode ->
                        languageMode = mode
                        prefs.uiLanguage = mode
                    },
                    volumeKeys = volumeKeys,
                    onTranslationChanged = { id ->
                        prefs.translationId = id
                    },
                    onProgressChanged = { versionId, bookId, chapter, offset ->
                        prefs.saveProgress(versionId, bookId, chapter, offset)
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun AppMessage(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit,
) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.readerBackground)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.readerText,
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.readerMuted,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "[ $action ]",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.readerText,
                    modifier = Modifier
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
