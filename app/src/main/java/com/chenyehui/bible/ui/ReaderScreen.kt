package com.chenyehui.bible.ui

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** A full page turn takes this long; partial turns keep the same page speed. */
private const val PAGE_TURN_DURATION_MS = 180

private const val LAYOUT_CACHE_SIZE = 8

private enum class TurnDirection { Forward, Backward }

/** A chapter together with the layout that was measured for it. */
private data class ChapterLocation(
    val bookMeta: BookMeta,
    val chapters: List<Chapter>,
    val chapterIndex: Int,
    val layout: ChapterLayout,
)

private data class TurnTarget(
    val direction: TurnDirection,
    val page: ChapterLocation,
    val pageIndex: Int,
)

private data class LayoutKey(val bookId: Int, val chapterNumber: Int)

/** Linear easing plus a duration proportional to the distance still to cover. */
private fun turnDurationMs(remainingFraction: Float): Int =
    (PAGE_TURN_DURATION_MS * remainingFraction.coerceIn(0f, 1f)).roundToInt()

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

    // Books are kept in a map rather than a single nullable slot so a page turn
    // can cross a book boundary without falling through the loading state and
    // losing the running animation. Neighbouring books are prefetched.
    val bookCache = remember(translation.id) { mutableStateMapOf<Int, Book>() }
    var loadError by remember(translation.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(translation.id) { mutableIntStateOf(0) }

    val bookPosition = translation.books.indexOfFirst { it.id == bookMeta.id }.coerceAtLeast(0)
    val previousBookMeta = translation.books.getOrNull(bookPosition - 1)
    val nextBookMeta = translation.books.getOrNull(bookPosition + 1)

    fun cacheBook(loaded: Book) {
        bookCache[loaded.id] = loaded
    }

    LaunchedEffect(translation.id, bookMeta.id, reloadKey) {
        loadError = null
        val currentId = bookMeta.id
        if (bookCache[currentId] == null) {
            try {
                cacheBook(
                    withContext(Dispatchers.IO) {
                        repository.book(translation.id, currentId)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.message ?: e.javaClass.simpleName
            }
        }
        // A turn across a chapter/book boundary needs the neighbour's first or
        // last chapter immediately, so keep both adjacent books loaded.
        if (bookCache[currentId] != null) {
            listOfNotNull(previousBookMeta, nextBookMeta).forEach { meta ->
                if (bookCache[meta.id] == null) {
                    launch {
                        try {
                            cacheBook(
                                withContext(Dispatchers.IO) {
                                    repository.book(translation.id, meta.id)
                                },
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // A failed prefetch only means the boundary turn
                            // falls back to the normal loading path.
                        }
                    }
                }
            }
        }
    }

    val book = bookCache[bookMeta.id]

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

    val previousBook = previousBookMeta?.let { bookCache[it.id] }
    val nextBook = nextBookMeta?.let { bookCache[it.id] }

    // Only the current book and its two neighbours are needed for page turns;
    // drop anything else so walking through the whole canon stays small.
    LaunchedEffect(bookMeta.id, previousBookMeta?.id, nextBookMeta?.id) {
        val keep = listOfNotNull(
            bookMeta.id,
            previousBookMeta?.id,
            nextBookMeta?.id,
        ).toSet()
        bookCache.keys.filter { it !in keep }.forEach { bookCache.remove(it) }
    }

    fun switchBook(meta: BookMeta, lastChapter: Boolean) {
        bookMeta = meta
        chapterNumber = if (lastChapter) meta.chapters else 1
        anchorOffset = 0
        pageIndex = 0
        pageCount = 0
        seekLastPage = lastChapter
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
    }

    // Keep the screen awake only while the text itself is on screen. The menu,
    // book picker, loading and error states leave it to the system to lock.
    val reading = sheetTab == null &&
        loadError == null &&
        book != null &&
        currentChapter != null &&
        pageCount > 0
    val view = LocalView.current
    DisposableEffect(view, reading) {
        view.keepScreenOn = reading
        onDispose { view.keepScreenOn = false }
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
                .displayCutoutPadding(),
        ) {
            val pageHorizontalPadding = 20.dp
            val pageVerticalPadding = 8.dp
            val density = LocalDensity.current
            val widthPx = (
                constraints.maxWidth -
                    with(density) { pageHorizontalPadding.toPx() } * 2f
                ).toInt()
            val heightPx = (
                constraints.maxHeight -
                    with(density) { pageVerticalPadding.toPx() } * 2f
                ).toInt()

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

                    val layoutCache = remember(
                        translation.id,
                        style,
                        paragraphSpacing,
                        density,
                        widthPx,
                        heightPx,
                    ) {
                        LinkedHashMap<LayoutKey, ChapterLayout>(16, 0.75f, true)
                    }

                    fun layoutFor(meta: BookMeta?, chapter: Chapter?): ChapterLayout? {
                        if (meta == null || chapter == null) return null
                        val key = LayoutKey(meta.id, chapter.number)
                        layoutCache[key]?.let { return it }
                        val built = buildChapterLayout(
                            chapter = chapter,
                            title = chapterDisplayTitle(translation.language, chapter.number),
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
                        layoutCache[key] = built
                        if (layoutCache.size > LAYOUT_CACHE_SIZE) {
                            layoutCache.keys.firstOrNull()?.let { layoutCache.remove(it) }
                        }
                        return built
                    }

                    val currentLayout = remember(
                        translation.id,
                        bookMeta.id,
                        currentChapter,
                        style,
                        paragraphSpacing,
                        density,
                        widthPx,
                        heightPx,
                    ) {
                        layoutFor(bookMeta, currentChapter)
                    }

                    // Neighbours already include the next/previous book at a
                    // book boundary, so a cross-book turn is just another turn.
                    // Their layouts are not measured during composition: that
                    // would stall the first frame of the turn into a new
                    // chapter. They are built while the reader is idle instead.
                    val previousChapterMeta = if (chapterIndex > 0) bookMeta else previousBookMeta
                    val previousChapter = if (chapterIndex > 0) {
                        chapters.getOrNull(chapterIndex - 1)
                    } else {
                        previousBook?.chapters?.lastOrNull()
                    }

                    val nextChapterMeta =
                        if (chapterIndex < chapters.lastIndex) bookMeta else nextBookMeta
                    val nextChapter = if (chapterIndex < chapters.lastIndex) {
                        chapters.getOrNull(chapterIndex + 1)
                    } else {
                        nextBook?.chapters?.firstOrNull()
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

                        // Measure neighbouring chapters while the reader is
                        // idle. Starting another turn cancels this effect, so
                        // the work never lands in the middle of an animation.
                        LaunchedEffect(
                            bookMeta.id,
                            chapterNumber,
                            previousChapterMeta?.id,
                            previousChapter?.number,
                            nextChapterMeta?.id,
                            nextChapter?.number,
                            visualTurn,
                            pageRestored,
                            currentLayout,
                        ) {
                            if (!pageRestored || visualTurn != null) {
                                return@LaunchedEffect
                            }
                            delay(120)
                            if (visualTurn != null) return@LaunchedEffect
                            previousChapter?.let { layoutFor(previousChapterMeta, it) }
                            delay(16)
                            if (visualTurn != null) return@LaunchedEffect
                            nextChapter?.let { layoutFor(nextChapterMeta, it) }
                        }

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

                        // Built on demand if the idle prefetch above has not
                        // reached the neighbour yet, so a turn never has to
                        // swap in a loading screen.
                        fun previousLocation(): ChapterLocation? {
                            val chapter = previousChapter ?: return null
                            val meta = previousChapterMeta ?: return null
                            val targetChapters = if (chapterIndex > 0) {
                                chapters
                            } else {
                                previousBook?.chapters ?: return null
                            }
                            val index = if (chapterIndex > 0) {
                                chapterIndex - 1
                            } else {
                                targetChapters.lastIndex
                            }
                            val layout = layoutFor(meta, chapter) ?: return null
                            return ChapterLocation(meta, targetChapters, index, layout)
                        }

                        fun nextLocation(): ChapterLocation? {
                            val chapter = nextChapter ?: return null
                            val meta = nextChapterMeta ?: return null
                            val targetChapters = if (chapterIndex < chapters.lastIndex) {
                                chapters
                            } else {
                                nextBook?.chapters ?: return null
                            }
                            val index = if (chapterIndex < chapters.lastIndex) {
                                chapterIndex + 1
                            } else {
                                0
                            }
                            val layout = layoutFor(meta, chapter) ?: return null
                            return ChapterLocation(meta, targetChapters, index, layout)
                        }

                        fun targetFor(direction: TurnDirection): TurnTarget? {
                            val currentLocation =
                                ChapterLocation(bookMeta, chapters, chapterIndex, currentLayout)
                            return when (direction) {
                                TurnDirection.Forward -> when {
                                    pageIndex < currentPages.lastIndex -> TurnTarget(
                                        direction = TurnDirection.Forward,
                                        page = currentLocation,
                                        pageIndex = pageIndex + 1,
                                    )

                                    else -> nextLocation()
                                        ?.takeIf { it.layout.pages.isNotEmpty() }
                                        ?.let { next ->
                                            TurnTarget(
                                                direction = TurnDirection.Forward,
                                                page = next,
                                                pageIndex = 0,
                                            )
                                        }
                                }

                                TurnDirection.Backward -> when {
                                    pageIndex > 0 -> TurnTarget(
                                        direction = TurnDirection.Backward,
                                        page = currentLocation,
                                        pageIndex = pageIndex - 1,
                                    )

                                    else -> previousLocation()
                                        ?.takeIf { it.layout.pages.isNotEmpty() }
                                        ?.let { previous ->
                                            TurnTarget(
                                                direction = TurnDirection.Backward,
                                                page = previous,
                                                pageIndex = previous.layout.pages.lastIndex,
                                            )
                                        }
                                }
                            }
                        }

                        fun commitLogical(target: TurnTarget) {
                            val targetPage = target.page.layout.pages.getOrNull(target.pageIndex)
                                ?: return
                            val targetBookMeta = target.page.bookMeta
                            val targetChapterNumber =
                                target.page.chapters[target.page.chapterIndex].number
                            anchorOffset = targetPage.start
                            if (targetBookMeta.id != bookMeta.id) {
                                bookMeta = targetBookMeta
                            }
                            if (targetChapterNumber != chapterNumber) {
                                chapterNumber = targetChapterNumber
                            }
                            pageIndex = target.pageIndex
                            onProgressChanged(
                                translation.id,
                                targetBookMeta.id,
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
                                    animationSpec = tween(
                                        durationMillis = duration,
                                        easing = LinearEasing,
                                    ),
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
                                baseLayout = target.page.layout,
                                basePage = target.pageIndex,
                                movingLayout = oldLayout,
                                movingPage = oldPage,
                            )
                        } else {
                            VisualTurn(
                                direction = TurnDirection.Backward,
                                baseLayout = oldLayout,
                                basePage = oldPage,
                                movingLayout = target.page.layout,
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
                            animateVisualTo(visual, 1f, PAGE_TURN_DURATION_MS)
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
                                        var previousX = down.position.x
                                        var lastHorizontalDelta = 0f
                                        var maxAbsTotalX = 0f
                                        var dragStarted = false
                                        var dragDirection: TurnDirection? = null
                                        var dragTarget: TurnTarget? = null
                                        var dragVisual: VisualTurn? = null
                                        var boundarySwipe = false

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            val isUp = !change.pressed
                                            totalX = change.position.x - down.position.x
                                            totalY = change.position.y - down.position.y
                                            val deltaX = change.position.x - previousX
                                            previousX = change.position.x
                                            if (!isUp && deltaX != 0f) {
                                                lastHorizontalDelta = deltaX
                                            }
                                            maxAbsTotalX = maxOf(maxAbsTotalX, abs(totalX))
                                            if (isUp) break

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

                                        val lastMovesOpposite = when (dragDirection) {
                                            TurnDirection.Forward -> lastHorizontalDelta > 0f
                                            TurnDirection.Backward -> lastHorizontalDelta < 0f
                                            null -> false
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
                                            maxAbsTotalX < centerTapSlop &&
                                            abs(totalY) < centerTapSlop
                                        ) {
                                            // A centre tap with a little finger jitter is
                                            // still a tap: cancel the tiny page drag.
                                            finishVisualNow()
                                            sheetTab = ReaderMenuTab.Directory
                                        } else if (dragTarget != null && dragVisual != null) {
                                            if (lastMovesOpposite) {
                                                // The finger's last movement is back
                                                // towards where it started: follow it
                                                // home instead of committing the turn.
                                                if (progress > 0f) {
                                                    animateVisualTo(
                                                        visual = dragVisual,
                                                        target = 0f,
                                                        duration = turnDurationMs(progress),
                                                    )
                                                } else {
                                                    finishVisualNow()
                                                }
                                            } else {
                                                commitLogical(dragTarget)
                                                animateVisualTo(
                                                    visual = dragVisual,
                                                    target = 1f,
                                                    duration = turnDurationMs(1f - progress),
                                                )
                                            }
                                        } else if (boundarySwipe && dragDirection != null) {
                                            if (!lastMovesOpposite) {
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
                                    }
                                },
                        ) {
                            if (visual == null) {
                                PageCanvas(
                                    layout = currentLayout,
                                    pageIndex = pageIndex,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(
                                            horizontal = pageHorizontalPadding,
                                            vertical = pageVerticalPadding,
                                        ),
                                )
                            } else {
                                PageCanvas(
                                    layout = visual.baseLayout,
                                    pageIndex = visual.basePage,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(
                                            horizontal = pageHorizontalPadding,
                                            vertical = pageVerticalPadding,
                                        ),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            // Read the animation value in the layer
                                            // block so a page turn only moves a render
                                            // node instead of recomposing the reader.
                                            translationX = when (visual.direction) {
                                                TurnDirection.Forward ->
                                                    -size.width * progress

                                                TurnDirection.Backward ->
                                                    -size.width * (1f - progress)
                                            }
                                        }
                                        .background(palette.readerBackground),
                                ) {
                                    PageCanvas(
                                        layout = visual.movingLayout,
                                        pageIndex = visual.movingPage,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                horizontal = pageHorizontalPadding,
                                                vertical = pageVerticalPadding,
                                            ),
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
