package com.chenyehui.bible.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyehui.bible.data.AppPrefs
import com.chenyehui.bible.data.BibleRepository
import com.chenyehui.bible.data.Book
import com.chenyehui.bible.data.BookMeta
import com.chenyehui.bible.data.Chapter
import com.chenyehui.bible.data.Manifest
import com.chenyehui.bible.data.ReadingProgress
import com.chenyehui.bible.data.ThemeMode
import com.chenyehui.bible.data.UiLanguageMode
import com.chenyehui.bible.data.chapterDisplayTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class TurnDirection { Forward, Backward }

private data class TurnTarget(
    val direction: TurnDirection,
    val chapterIndex: Int,
    val pageIndex: Int,
    val layout: ChapterLayout,
)

private data class VisualTurn(
    val direction: TurnDirection,
    val baseLayout: ChapterLayout,
    val basePage: Int,
    val movingLayout: ChapterLayout,
    val movingPage: Int,
)

@Composable
fun ReaderScreen(
    repository: BibleRepository,
    manifest: Manifest,
    initialProgress: ReadingProgress,
    themeMode: ThemeMode,
    fontSize: Float,
    letterSpacing: Float,
    lineSpacingExtra: Float,
    paragraphSpacing: Float,
    languageMode: UiLanguageMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onLanguageModeChange: (UiLanguageMode) -> Unit,
    volumeKeys: SharedFlow<Int>,
    onTranslationChanged: (String) -> Unit,
    onProgressChanged: (String, Int, Int, Int) -> Unit,
) {
    val palette = LocalBiblePalette.current
    val strings = LocalUiStrings.current
    val initialVersion = remember {
        manifest.versionOrFirst(initialProgress.versionId)
    }
    val initialMeta = remember {
        initialVersion.books.firstOrNull { it.id == initialProgress.bookId }
            ?: initialVersion.books.first()
    }

    var translation by remember { mutableStateOf(initialVersion) }
    var bookMeta by remember { mutableStateOf(initialMeta) }
    var chapterNumber by remember {
        mutableIntStateOf(initialProgress.chapter.coerceIn(1, initialMeta.chapters))
    }
    var anchorOffset by remember { mutableIntStateOf(initialProgress.offset.coerceAtLeast(0)) }
    var pageIndex by remember { mutableIntStateOf(-1) }
    var pageRestored by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(0) }
    var seekLastPage by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf<ReaderMenuTab?>(null) }

    var book by remember(translation.id, bookMeta.id) { mutableStateOf<Book?>(null) }
    var loadError by remember(translation.id, bookMeta.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(translation.id, bookMeta.id) { mutableIntStateOf(0) }

    LaunchedEffect(translation.id, bookMeta.id, reloadKey) {
        book = null
        loadError = null
        try {
            book = withContext(Dispatchers.IO) {
                repository.book(translation.id, bookMeta.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: e.javaClass.simpleName
        }
    }

    LaunchedEffect(book) {
        val loaded = book ?: return@LaunchedEffect
        if (loaded.chapters.none { it.number == chapterNumber }) {
            chapterNumber = loaded.chapters.first().number
            anchorOffset = 0
            pageIndex = 0
        }
    }

    val chapters = book?.chapters.orEmpty()
    val chapterIndex = chapters.indexOfFirst { it.number == chapterNumber }.coerceAtLeast(0)
    val currentChapter = chapters.getOrNull(chapterIndex)
    val previousChapter = chapters.getOrNull(chapterIndex - 1)
    val nextChapter = chapters.getOrNull(chapterIndex + 1)


    val bookPosition = translation.books.indexOfFirst { it.id == bookMeta.id }.coerceAtLeast(0)
    val previousBookMeta = translation.books.getOrNull(bookPosition - 1)
    val nextBookMeta = translation.books.getOrNull(bookPosition + 1)

    fun switchBook(meta: BookMeta, lastChapter: Boolean) {
        bookMeta = meta
        chapterNumber = if (lastChapter) meta.chapters else 1
        anchorOffset = 0
        pageIndex = 0
        pageCount = 0
        seekLastPage = lastChapter
        sheetTab = null
    }

    fun switchTranslation(newId: String) {
        val next = manifest.versionOrFirst(newId)
        val nextMeta = next.books.firstOrNull { it.id == bookMeta.id } ?: next.books.first()
        translation = next
        bookMeta = nextMeta
        chapterNumber = chapterNumber.coerceIn(1, nextMeta.chapters)
        anchorOffset = 0
        pageIndex = 0
        pageCount = 0
        seekLastPage = false
        onTranslationChanged(next.id)
        sheetTab = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.readerBackground),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight

            when {
                loadError != null -> ReaderMessage(
                    title = strings.loadFailed,
                    message = loadError ?: "",
                    action = strings.retry,
                    onAction = { reloadKey++ },
                )

                book == null -> ReaderMessage(
                    title = strings.loading,
                    message = "",
                    action = null,
                    onAction = {},
                )

                currentChapter == null -> ReaderMessage(
                    title = strings.noVerses,
                    message = "",
                    action = null,
                    onAction = {},
                )

                widthPx <= 0 || heightPx <= 0 -> Unit

                else -> {
                    val textMeasurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val style = remember(fontSize, letterSpacing, lineSpacingExtra, palette) {
                        TextStyle(
                            fontFamily = MonoFamily,
                            fontSize = fontSize.sp,
                            lineHeight = (
                                fontSize * (
                                    AppPrefs.BASE_LINE_HEIGHT + lineSpacingExtra
                                )
                            ).sp,
                            letterSpacing = letterSpacing.sp,
                            color = palette.readerText,
                        )
                    }

                    fun layoutFor(chapter: Chapter?): ChapterLayout? = chapter?.let {
                        buildChapterLayout(
                            chapter = it,
                            title = chapterDisplayTitle(translation.language, it.number),
                            language = translation.language,
                            fontSize = fontSize,
                            textColor = palette.readerText,
                            headingColor = palette.readerHeading,
                            mutedColor = palette.readerMuted,
                            style = style,
                            lineSpacingExtra = lineSpacingExtra,
                            paragraphSpacing = paragraphSpacing,
                            density = density,
                            textMeasurer = textMeasurer,
                            widthPx = widthPx,
                            heightPx = heightPx,
                        )
                    }

                    val currentLayout = remember(
                        currentChapter,
                        style,
                        paragraphSpacing,
                        density,
                        widthPx,
                        heightPx,
                    ) {
                        layoutFor(currentChapter)
                    }
                    val previousLayout = remember(
                        previousChapter,
                        style,
                        paragraphSpacing,
                        density,
                        widthPx,
                        heightPx,
                    ) {
                        layoutFor(previousChapter)
                    }
                    val nextLayout = remember(
                        nextChapter,
                        style,
                        paragraphSpacing,
                        density,
                        widthPx,
                        heightPx,
                    ) {
                        layoutFor(nextChapter)
                    }

                    if (currentLayout == null) {
                        LaunchedEffect(Unit) {
                            pageIndex = 0
                            pageRestored = true
                        }
                        ReaderMessage(
                            title = strings.noVerses,
                            message = "",
                            action = null,
                            onAction = {},
                        )
                    } else {
                        val currentPages = currentLayout.pages
                        val scope = rememberCoroutineScope()
                        var visualTurn by remember { mutableStateOf<VisualTurn?>(null) }
                        var progress by remember { mutableFloatStateOf(0f) }
                        var animationJob by remember { mutableStateOf<Job?>(null) }

                        LaunchedEffect(currentLayout) {
                            pageCount = currentPages.size
                            if (currentPages.isEmpty()) {
                                pageIndex = 0
                                pageRestored = true
                                return@LaunchedEffect
                            }
                            val targetIndex = if (seekLastPage) {
                                currentPages.lastIndex
                            } else {
                                currentPages.indexOfLast { it.start <= anchorOffset }
                                    .coerceAtLeast(0)
                            }
                            pageIndex = targetIndex
                            pageRestored = true
                            // Keep the original character anchor across pure layout
                            // changes (IME/insets/rotation). Only a user page turn,
                            // chapter/book change or the "last page" seek moves it.
                            if (seekLastPage) {
                                anchorOffset = currentPages[targetIndex].start
                            }
                            seekLastPage = false
                            onProgressChanged(
                                translation.id,
                                bookMeta.id,
                                chapterNumber,
                                anchorOffset,
                            )
                        }

                        fun targetFor(direction: TurnDirection): TurnTarget? = when (direction) {
                            TurnDirection.Forward -> when {
                                pageIndex < currentPages.lastIndex -> TurnTarget(
                                    direction = TurnDirection.Forward,
                                    chapterIndex = chapterIndex,
                                    pageIndex = pageIndex + 1,
                                    layout = currentLayout,
                                )

                                nextLayout != null && nextLayout.pages.isNotEmpty() -> TurnTarget(
                                    direction = TurnDirection.Forward,
                                    chapterIndex = chapterIndex + 1,
                                    pageIndex = 0,
                                    layout = nextLayout,
                                )

                                else -> null
                            }

                            TurnDirection.Backward -> when {
                                pageIndex > 0 -> TurnTarget(
                                    direction = TurnDirection.Backward,
                                    chapterIndex = chapterIndex,
                                    pageIndex = pageIndex - 1,
                                    layout = currentLayout,
                                )

                                previousLayout != null && previousLayout.pages.isNotEmpty() ->
                                    TurnTarget(
                                        direction = TurnDirection.Backward,
                                        chapterIndex = chapterIndex - 1,
                                        pageIndex = previousLayout.pages.lastIndex,
                                        layout = previousLayout,
                                    )

                                else -> null
                            }
                        }

                        fun commitLogical(target: TurnTarget) {
                            val targetPage = target.layout.pages.getOrNull(target.pageIndex)
                                ?: return
                            val targetChapterNumber = chapters[target.chapterIndex].number
                            anchorOffset = targetPage.start
                            if (target.chapterIndex != chapterIndex) {
                                chapterNumber = targetChapterNumber
                            }
                            pageIndex = target.pageIndex
                            onProgressChanged(
                                translation.id,
                                bookMeta.id,
                                targetChapterNumber,
                                targetPage.start,
                            )
                        }

                        fun finishVisualNow() {
                            animationJob?.cancel()
                            animationJob = null
                            visualTurn = null
                            progress = 0f
                        }

                        fun animateVisualTo(
                            visual: VisualTurn,
                            target: Float,
                            duration: Int,
                        ) {
                            animationJob?.cancel()
                            animationJob = scope.launch {
                                animate(
                                    initialValue = progress,
                                    targetValue = target,
                                    animationSpec = tween(durationMillis = duration),
                                ) { value, _ ->
                                    progress = value
                                }
                                if (visualTurn === visual) {
                                    visualTurn = null
                                    progress = 0f
                                }
                            }
                        }

                        fun visualFor(
                            direction: TurnDirection,
                            oldLayout: ChapterLayout,
                            oldPage: Int,
                            target: TurnTarget,
                        ): VisualTurn = if (direction == TurnDirection.Forward) {
                            VisualTurn(
                                direction = TurnDirection.Forward,
                                baseLayout = target.layout,
                                basePage = target.pageIndex,
                                movingLayout = oldLayout,
                                movingPage = oldPage,
                            )
                        } else {
                            VisualTurn(
                                direction = TurnDirection.Backward,
                                baseLayout = oldLayout,
                                basePage = oldPage,
                                movingLayout = target.layout,
                                movingPage = target.pageIndex,
                            )
                        }

                        fun startTapTurn(direction: TurnDirection) {
                            if (pageIndex < 0) return
                            finishVisualNow()
                            val oldLayout = currentLayout
                            val oldPage = pageIndex
                            val target = targetFor(direction)
                            if (target == null) {
                                if (direction == TurnDirection.Forward) {
                                    nextBookMeta?.let { switchBook(it, lastChapter = false) }
                                } else {
                                    previousBookMeta?.let { switchBook(it, lastChapter = true) }
                                }
                                return
                            }
                            commitLogical(target)
                            val visual = visualFor(direction, oldLayout, oldPage, target)
                            visualTurn = visual
                            progress = 0f
                            animateVisualTo(visual, 1f, 230)
                        }

                        val latestPageTurn = rememberUpdatedState<(TurnDirection) -> Unit> {
                            startTapTurn(it)
                        }
                        LaunchedEffect(volumeKeys) {
                            volumeKeys.collect { direction ->
                                if (sheetTab == null) {
                                    latestPageTurn.value(
                                        if (direction < 0) {
                                            TurnDirection.Backward
                                        } else {
                                            TurnDirection.Forward
                                        },
                                    )
                                }
                            }
                        }

                        val visual = visualTurn
                        val movingOffset = when {
                            visual == null -> 0f
                            visual.direction == TurnDirection.Forward ->
                                -widthPx.toFloat() * progress

                            else -> -widthPx.toFloat() + widthPx.toFloat() * progress
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pageIndex, chapterNumber, currentLayout) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val pageWidth = size.width.toFloat()
                                        val pageHeight = size.height.toFloat()
                                        val downInCenter =
                                            abs(down.position.x - pageWidth / 2f) <=
                                                pageWidth * 0.11f &&
                                            abs(down.position.y - pageHeight / 2f) <=
                                                pageHeight * 0.05f
                                        val centerTapSlop = 8f
                                        var totalX = 0f
                                        var totalY = 0f
                                        var dragStarted = false
                                        var dragDirection: TurnDirection? = null
                                        var dragTarget: TurnTarget? = null
                                        var dragVisual: VisualTurn? = null
                                        var boundarySwipe = false

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            totalX = change.position.x - down.position.x
                                            totalY = change.position.y - down.position.y

                                            if (
                                                !dragStarted &&
                                                abs(totalX) > 2f &&
                                                abs(totalX) > abs(totalY)
                                            ) {
                                                dragStarted = true
                                                finishVisualNow()
                                                val direction = if (totalX < 0) {
                                                    TurnDirection.Forward
                                                } else {
                                                    TurnDirection.Backward
                                                }
                                                dragDirection = direction
                                                val oldLayout = currentLayout
                                                val oldPage = pageIndex
                                                val target = targetFor(direction)
                                                if (target != null) {
                                                    val visual = visualFor(
                                                        direction,
                                                        oldLayout,
                                                        oldPage,
                                                        target,
                                                    )
                                                    visualTurn = visual
                                                    dragVisual = visual
                                                    dragTarget = target
                                                    progress = 0f
                                                } else {
                                                    boundarySwipe = true
                                                }
                                            }

                                            if (dragStarted) {
                                                change.consume()
                                                val direction = dragDirection
                                                if (dragVisual != null && direction != null) {
                                                    val width = size.width.toFloat()
                                                    val fraction = if (
                                                        direction == TurnDirection.Forward
                                                    ) {
                                                        (-totalX / width).coerceIn(0f, 1f)
                                                    } else {
                                                        (totalX / width).coerceIn(0f, 1f)
                                                    }
                                                    progress = fraction
                                                }
                                            }
                                        }

                                        if (!dragStarted) {
                                            if (
                                                abs(totalX) <= viewConfiguration.touchSlop &&
                                                abs(totalY) <= viewConfiguration.touchSlop &&
                                                downInCenter
                                            ) {
                                                finishVisualNow()
                                                sheetTab = ReaderMenuTab.Directory
                                            }
                                        } else if (
                                            downInCenter &&
                                            abs(totalX) < centerTapSlop &&
                                            abs(totalY) < centerTapSlop
                                        ) {
                                            // A centre tap with a little finger jitter is
                                            // still a tap: cancel the tiny page drag.
                                            finishVisualNow()
                                            sheetTab = ReaderMenuTab.Directory
                                        } else if (dragTarget != null && dragVisual != null) {
                                            commitLogical(dragTarget)
                                            animateVisualTo(
                                                visual = dragVisual,
                                                target = 1f,
                                                duration = 180,
                                            )
                                        } else if (boundarySwipe && dragDirection != null) {
                                            if (dragDirection == TurnDirection.Forward) {
                                                nextBookMeta?.let {
                                                    switchBook(it, lastChapter = false)
                                                }
                                            } else {
                                                previousBookMeta?.let {
                                                    switchBook(it, lastChapter = true)
                                                }
                                            }
                                        }
                                    }
                                },
                        ) {
                            if (visual == null) {
                                PageCanvas(
                                    layout = currentLayout,
                                    pageIndex = pageIndex,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                PageCanvas(
                                    layout = visual.baseLayout,
                                    pageIndex = visual.basePage,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = movingOffset }
                                        .background(palette.readerBackground),
                                ) {
                                    PageCanvas(
                                        layout = visual.movingLayout,
                                        pageIndex = visual.movingPage,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val showStartupSplash = !pageRestored && loadError == null &&
            (book == null || currentChapter != null)
        if (showStartupSplash) {
            SplashLoading()
        }

        sheetTab?.let { tab ->
            ReaderMenu(
                tab = tab,
                onTabChange = { sheetTab = it },
                onDismiss = { sheetTab = null },
                manifest = manifest,
                translation = translation,
                book = bookMeta,
                chapter = chapterNumber,
                pageIndex = pageIndex,
                pageCount = pageCount,
                onChapterSelected = { selected ->
                    if (selected != chapterNumber) {
                        chapterNumber = selected
                        anchorOffset = 0
                        pageIndex = 0
                    }
                    sheetTab = null
                },
                onTranslationChange = ::switchTranslation,
                onBookSelected = { id ->
                    translation.books.firstOrNull { it.id == id }?.let {
                        switchBook(it, lastChapter = false)
                    }
                },
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
            )
        }
    }
}

@Composable
private fun ReaderMessage(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit,
) {
    val palette = LocalBiblePalette.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.readerText,
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.readerMuted,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "[ $action ]",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.readerText,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var up = false
                                while (!up) {
                                    val event = awaitPointerEvent()
                                    up = event.changes.none { it.pressed }
                                }
                                onAction()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
