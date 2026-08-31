package com.convx.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.convx.music.R
import com.convx.music.playback.PlayerConnection
import com.convx.music.extensions.metadata
import com.convx.music.ui.utils.additiveChrome
import com.convx.music.ui.utils.resize

@Composable
fun UpNextSong(
    playerConnection: PlayerConnection,
    titleColor: Color,
    subtitleColor: Color,
    modifier: Modifier = Modifier,
    // Applied to the text column only -- the thumbnail below is a real photo, not a white
    // glyph, so additively blending it would wash it out rather than tint it.
    additiveChrome: Boolean = false,
) {
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentIndex by playerConnection.currentMediaItemIndex.collectAsState()

    val nextItem = queueWindows.getOrNull(currentIndex + 1)?.mediaItem?.metadata
        ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = nextItem.thumbnailUrl?.resize(96, 96),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f)
                .additiveChrome(additiveChrome)
                .graphicsLayer { if (additiveChrome) alpha = 0.45f }
        ) {
            Text(
                text = stringResource(R.string.up_next),
                style = AppTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = subtitleColor.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Text(
                text = nextItem.title ?: "",
                style = AppTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = titleColor,
                maxLines = 1,
            )
            val artist = nextItem.artists?.firstOrNull()?.name
            if (!artist.isNullOrBlank()) {
                Text(
                    text = artist,
                    style = AppTheme.typography.labelSmall,
                    color = subtitleColor.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}
