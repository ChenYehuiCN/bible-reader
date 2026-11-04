package com.chenyehui.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.chenyehui.bible.data.Chapter
import kotlin.math.roundToInt

/** A measured paragraph/heading block. */
data class ChapterBlock(
    val kind: ChapterBlockKind,
    val layout: TextLayoutResult,
    val startOffset: Int,
    val gapBeforePx: Float,
)

data class ChapterLayout(
    val chapter: Chapter,
    val title: String,
    val blocks: List<ChapterBlock>,
    val pages: List<PageSlice>,
    /** Left inset that centers the monospaced character grid in the page. */
    val horizontalOffsetPx: Float,
)

fun buildChapterLayout(
    chapter: Chapter,
    title: String,
    language: String,
    fontSize: Float,
    lineSpacingExtra: Float,
    paragraphSpacing: Float,
    textColor: Color,
    headingColor: Color,
    mutedColor: Color,
    style: TextStyle,
    density: Density,
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
): ChapterLayout {
    val safeWidth = widthPx.coerceAtLeast(1)
    val cellWidth = measureCellWidth(language, style, textMeasurer)
    val specs = buildChapterBlockSpecs(
        chapter = chapter,
        chapterTitle = title,
        language = language,
        baseFontSize = fontSize,
        lineSpacingExtra = lineSpacingExtra,
        paragraphSpacing = paragraphSpacing,
        textColor = textColor,
        headingColor = headingColor,
        mutedColor = mutedColor,
        baseStyle = style,
        density = density,
        characterCellWidthPx = cellWidth,
    )

    var offset = 0
    val columns = if (cellWidth > 0f) (safeWidth / cellWidth).toInt() else 0
    val gridWidth = if (columns > 0) {
        (columns * cellWidth).roundToInt().coerceIn(1, safeWidth)
    } else {
        safeWidth
    }
    val horizontalOffset = (safeWidth - gridWidth) / 2f

    val blocks = specs.map { spec ->
        val layout = textMeasurer.measure(
            text = spec.text,
            style = spec.style,
            softWrap = true,
            constraints = Constraints(maxWidth = gridWidth),
        )
        val block = ChapterBlock(
            kind = spec.kind,
            layout = layout,
            startOffset = offset,
            gapBeforePx = spec.gapBeforePx,
        )
        offset += spec.text.length
        block
    }

    return ChapterLayout(
        chapter = chapter,
        title = title,
        blocks = blocks,
        pages = paginate(blocks, heightPx),
        horizontalOffsetPx = horizontalOffset,
    )
}

/**
 * Advance of one body character in the monospaced grid. The grid is laid out
 * slightly narrower than the available width so it can be centered with equal
 * left/right space.
 */
private fun measureCellWidth(
    language: String,
    style: TextStyle,
    textMeasurer: TextMeasurer,
): Float {
    val sample = if (language == "zh") "中" else "0"
    val count = 64
    val layout = textMeasurer.measure(
        text = AnnotatedString(sample.repeat(count)),
        style = style,
        softWrap = false,
        constraints = Constraints(),
    )
    return layout.size.width.toFloat() / count
}

@Composable
fun PageCanvas(layout: ChapterLayout, pageIndex: Int, modifier: Modifier = Modifier) {
    val page = layout.pages.getOrNull(pageIndex) ?: return
    Canvas(modifier = modifier.fillMaxSize()) {
        page.chunks.forEach { chunk ->
            val block = layout.blocks.getOrNull(chunk.blockIndex) ?: return@forEach
            val textLayout = block.layout
            if (
                chunk.firstLine !in 0 until textLayout.lineCount ||
                chunk.lastLine !in 0 until textLayout.lineCount
            ) {
                return@forEach
            }
            val layoutTop = textLayout.getLineTop(chunk.firstLine)
            val layoutBottom = textLayout.getLineBottom(chunk.lastLine)
            val height = layoutBottom - layoutTop
            if (height <= 0f) return@forEach
            clipRect(
                top = chunk.top,
                bottom = chunk.top + height,
            ) {
                translate(
                    left = layout.horizontalOffsetPx,
                    top = chunk.top - layoutTop,
                ) {
                    drawText(textLayout)
                }
            }
        }
    }
}
