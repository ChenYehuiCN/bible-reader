package com.chenyehui.bible.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyehui.bible.R
import com.chenyehui.bible.data.TranslationInfo

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Int = 12,
) {
    val palette = LocalBiblePalette.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (selected) palette.uiSelected else Color.Transparent,
        contentColor = if (selected) palette.uiOnSelected else palette.uiText,
        border = if (selected) null else BorderStroke(1.dp, palette.uiBorder),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(horizontal = horizontalPadding.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TextIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Int = 19,
) {
    val palette = LocalBiblePalette.current
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = fontSize.sp,
            color = palette.uiText,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun SectionHeading(
    title: String,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalBiblePalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = palette.uiText,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelSmall,
            color = palette.uiMuted,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    val palette = LocalBiblePalette.current
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.uiBorder),
    )
}

@Composable
fun TranslationChips(
    versions: List<TranslationInfo>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        versions.forEach { version ->
            ChoiceChip(
                text = strings.translationShort(version),
                selected = version.id == selectedId,
                onClick = { onSelect(version.id) },
            )
        }
    }
}


/** Black launch screen with the white cross, used while local JSON and the
 * first page are being restored. */
@Composable
fun SplashLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cross),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(92.dp),
        )
    }
}
