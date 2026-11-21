package dev.w0rd3r.morphbar.ui.island

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A truly touchable 112x48dp control, explicitly positioned BELOW Android's
 * status bar by the overlay service. The physical camera pill stays above.
 * Only a small 48x22dp indicator is painted, keeping the Pixel design intact.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactActionHandle(
    isNotification: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 48.dp)
            .combinedClickable(
                onClickLabel = if (isNotification) "Открыть уведомление" else "Раскрыть плеер",
                onLongClickLabel = "Показать подробности MorphBar",
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(width = 48.dp, height = 22.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xEB17191D)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    if (isNotification) Icons.Rounded.OpenInNew else Icons.Rounded.ExpandMore,
                contentDescription =
                    if (isNotification) "Открыть уведомление" else "Развернуть MorphBar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
