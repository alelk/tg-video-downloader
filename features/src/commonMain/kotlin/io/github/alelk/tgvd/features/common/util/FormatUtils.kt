package io.github.alelk.tgvd.features.common.util
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "${hours}:${minutes.padZero()}:${seconds.padZero()}"
    else "${minutes}:${seconds.padZero()}"
}
private fun Int.padZero(): String = if (this < 10) "0$this" else "$this"
