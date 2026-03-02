package io.github.alelk.tgvd.features.common.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alelk.tgvd.features.common.theme.*

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = statusColors(status)
    Surface(
        color = bgColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            text = statusDisplayName(status),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun statusColors(status: String): Pair<Color, Color> = when (status.lowercase()) {
    "pending" -> StatusPending to StatusPending
    "downloading" -> StatusDownloading to StatusDownloading
    "post_processing" -> StatusProcessing to StatusProcessing
    "completed", "done" -> StatusCompleted to StatusCompleted
    "failed" -> StatusFailed to StatusFailed
    "cancelled" -> StatusCancelled to StatusCancelled
    else -> StatusCancelled to StatusCancelled
}

private fun statusDisplayName(status: String): String = when (status.lowercase()) {
    "pending" -> "Pending"
    "downloading" -> "Downloading"
    "post_processing" -> "Processing"
    "completed", "done" -> "Done"
    "failed" -> "Failed"
    "cancelled" -> "Cancelled"
    else -> status
}

