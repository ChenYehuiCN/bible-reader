package com.chenyehui.bible.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.chenyehui.bible.data.AppPrefs
import com.chenyehui.bible.data.Block
import com.chenyehui.bible.data.Chapter

/** One drawable block segment on a page. */
data class PageChunk(
    val blockIndex: Int,
    val firstLine: Int,
    val lastLine: Int,
    val top: Float,
)

/** A page window; [start]/[end] are character offsets in the chapter blocks. */
data class PageSlice(
    val start: Int,
    val end: Int,
    val chunks: List<PageChunk>,
)

enum class ChapterBlockKind { Title, Heading3, Heading4, Body }

/** A measured chapter block before pagination. */
data class ChapterBlockSpec(
    val kind: ChapterBlockKind,
    val text: AnnotatedString,
    val style: TextStyle,
    val gapBeforePx: Float,
)

private data class RawChunk(
    val kind: ChapterBlockKind,
    val text: String = "",
    val verses: List<Block.Verse> = emptyList(),
)

/**
 * Split a chapter into layout blocks: title, h3, h4 and body paragraphs.
 * Paragraph breaks now come from the source <br/> markers, so the printer's
 * paragraphing is preserved instead of every verse being its own paragraph.
 */
fun buildChapterBlockSpecs(
    chapter: Chapter,
    chapterTitle: String,
    language: String,
    baseFontSize: Float,
    lineSpacingExtra: Float,
    paragraphSpacing: Float,
    textColor: Color,
    headingColor: Color,
    mutedColor: Color,
    baseStyle: TextStyle,
    density: Density,
): List<ChapterBlockSpec> {
    val factor = AppPrefs.BASE_LINE_HEIGHT + lineSpacingExtra
    val bodyLineHeight = baseFontSize * factor
    val bodyLineHeightPx = with(density) { bodyLineHeight.sp.toPx() }
    val paragraphGapPx = with(density) { (paragraphSpacing * baseFontSize).sp.toPx() }

    val raws = ArrayList<RawChunk>()
    if (chapterTitle.isNotBlank()) {
        raws += RawChunk(ChapterBlockKind.Title, text = chapterTitle)
    }
    val body = ArrayList<Block.Verse>()

    fun flushBody() {
        if (body.isNotEmpty()) {
            raws += RawChunk(ChapterBlockKind.Body, verses = body.toList())
            body.clear()
        }
    }

    chapter.blocks.forEach { block ->
        when (block) {
            is Block.Heading -> {
                flushBody()
                raws += RawChunk(
                    kind = if (block.level == 3) {
                        ChapterBlockKind.Heading3
                    } else {
                        ChapterBlockKind.Heading4
                    },
                    text = block.text,
                )
            }

            Block.ParagraphBreak -> flushBody()
            is Block.Verse -> body += block
        }
    }
    flushBody()

    var previous: ChapterBlockKind? = null
    return raws.map { raw ->
        val gap = gapBefore(
            previous = previous,
            next = raw.kind,
            lineHeightPx = bodyLineHeightPx,
            paragraphGapPx = paragraphGapPx,
        )
        previous = raw.kind
        val (text, style) = when (raw.kind) {
            ChapterBlockKind.Title -> {
                val size = baseFontSize * 1.45f
                AnnotatedString(raw.text) to baseStyle.copy(
                    color = headingColor,
                    fontSize = size.sp,
                    lineHeight = (size * factor).sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            ChapterBlockKind.Heading3, ChapterBlockKind.Heading4 -> {
                val intro = raw.text.length > 40
                val size = when {
                    intro -> baseFontSize
                    raw.kind == ChapterBlockKind.Heading3 -> baseFontSize * 1.16f
                    else -> baseFontSize * 1.02f
                }
                AnnotatedString(raw.text) to baseStyle.copy(
                    color = if (intro) mutedColor else headingColor,
                    fontSize = size.sp,
                    lineHeight = if (intro) bodyLineHeight.sp else (size * factor).sp,
                    fontWeight = if (intro) FontWeight.Normal else FontWeight.Bold,
                )
            }

            ChapterBlockKind.Body -> buildBodyText(
                verses = raw.verses,
                language = language,
                textColor = textColor,
                indentSp = baseFontSize * if (language == "en") 1.5f else 2f,
            ) to baseStyle.copy(color = textColor)
        }
        ChapterBlockSpec(
            kind = raw.kind,
            text = text,
            style = style,
            gapBeforePx = gap,
        )
    }
}

private fun buildBodyText(
    verses: List<Block.Verse>,
    language: String,
    textColor: Color,
    indentSp: Float,
): AnnotatedString = buildAnnotatedString {
    pushStyle(
        ParagraphStyle(
            textIndent = TextIndent(firstLine = indentSp.sp),
        ),
    )
    verses.forEachIndexed { index, verse ->
        if (index > 0 && language != "zh") append(' ')
        pushStyle(SpanStyle(color = textColor))
        append(verse.text)
        pop()
    }
    pop()
}

/**
 * Heading gaps win over paragraph spacing; a paragraph that ends before an h4
 * therefore gets the h4 top margin rather than paragraph gap + heading gap.
 */
private fun gapBefore(
    previous: ChapterBlockKind?,
    next: ChapterBlockKind,
    lineHeightPx: Float,
    paragraphGapPx: Float,
): Float = when {
    previous == null -> 0f
    previous == ChapterBlockKind.Title && next == ChapterBlockKind.Body ->
        lineHeightPx * 0.9f

    previous == ChapterBlockKind.Title -> lineHeightPx * 1.0f
    next == ChapterBlockKind.Heading3 -> lineHeightPx * 1.15f
    next == ChapterBlockKind.Heading4 -> lineHeightPx * 0.85f
    previous == ChapterBlockKind.Heading3 -> lineHeightPx * 0.55f
    previous == ChapterBlockKind.Heading4 -> lineHeightPx * 0.45f
    else -> paragraphGapPx
}

private fun lineHeight(block: ChapterBlock, line: Int): Float =
    block.layout.getLineBottom(line) - block.layout.getLineTop(line)

private fun blockHeight(block: ChapterBlock): Float {
    if (block.layout.lineCount == 0) return 0f
    return block.layout.getLineBottom(block.layout.lineCount - 1) -
        block.layout.getLineTop(0)
}

/**
 * Paginate measured blocks into page windows. Gaps are inserted between blocks
 * but never at the top of a page, and a heading is moved to the next page when
 * it would otherwise be stranded at the bottom.
 */
fun paginate(blocks: List<ChapterBlock>, pageHeightPx: Int): List<PageSlice> {
    if (blocks.isEmpty() || pageHeightPx <= 0) return emptyList()
    val pages = ArrayList<PageSlice>()
    var pageChunks = ArrayList<PageChunk>()
    var pageY = 0f
    var pageStart = -1
    var pageEnd = -1

    var chunkBlock = -1
    var chunkFirst = 0
    var chunkLast = 0
    var chunkTop = 0f

    fun flushChunk() {
        if (chunkBlock >= 0) {
            pageChunks += PageChunk(chunkBlock, chunkFirst, chunkLast, chunkTop)
            chunkBlock = -1
        }
    }

    fun pageHasContent(): Boolean = pageChunks.isNotEmpty() || chunkBlock >= 0

    fun finishPage() {
        flushChunk()
        if (pageChunks.isNotEmpty()) {
            pages += PageSlice(
                start = pageStart.coerceAtLeast(0),
                end = pageEnd.coerceAtLeast(0),
                chunks = pageChunks.toList(),
            )
        }
        pageChunks = ArrayList()
        pageY = 0f
        pageStart = -1
        pageEnd = -1
    }

    blocks.forEachIndexed { index, block ->
        val lineCount = block.layout.lineCount
        if (lineCount == 0) return@forEachIndexed

        val isHeading = block.kind == ChapterBlockKind.Heading3 ||
            block.kind == ChapterBlockKind.Heading4
        if (isHeading && pageHasContent()) {
            val next = blocks.getOrNull(index + 1)
            if (next != null && next.layout.lineCount > 0) {
                val needed = block.gapBeforePx + blockHeight(block) +
                    next.gapBeforePx + lineHeight(next, 0)
                if (pageY + needed > pageHeightPx) {
                    finishPage()
                }
            }
        }

        // Do not leave a body block with only a single line at the bottom.
        val minLines = if (block.kind == ChapterBlockKind.Body) minOf(2, lineCount) else 1
        var needed = block.gapBeforePx
        for (i in 0 until minLines) needed += lineHeight(block, i)
        if (pageHasContent() && pageY + needed > pageHeightPx) {
            finishPage()
        }

        val firstLineHeight = lineHeight(block, 0)
        if (pageHasContent()) {
            if (pageY + block.gapBeforePx + firstLineHeight > pageHeightPx) {
                finishPage()
            } else {
                pageY += block.gapBeforePx
            }
        }

        var line = 0
        while (line < lineCount) {
            val height = lineHeight(block, line)
            if (pageHasContent() && pageY + height > pageHeightPx) {
                flushChunk()
                finishPage()
                continue
            }
            if (!pageHasContent() && pageY != 0f) {
                pageY = 0f
            }
            if (chunkBlock != index) {
                flushChunk()
                chunkBlock = index
                chunkFirst = line
                chunkTop = pageY
            }
            chunkLast = line
            if (pageStart < 0) {
                pageStart = block.startOffset + block.layout.getLineStart(line)
            }
            pageY += height
            pageEnd = block.startOffset + block.layout.getLineEnd(line, visibleEnd = true)
            line++
        }
        flushChunk()
    }

    finishPage()
    return pages
}
