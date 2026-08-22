package io.github.alelk.tgvd.domain.system

data class YtDlpVersion(
    val version: String,
    val gitHead: String? = null,
)

/**
 * yt-dlp versions are date-based (e.g. "2024.08.06", occasionally "2024.08.06.123" for
 * patch/nightly releases), so a naive string equality check is not a reliable "is this newer"
 * test — this compares the dot-separated numeric components in order.
 */
fun YtDlpVersion.isNewerThan(other: YtDlpVersion): Boolean {
    val a = version.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val b = other.version.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val length = maxOf(a.size, b.size)
    for (i in 0 until length) {
        val ai = a.getOrElse(i) { 0 }
        val bi = b.getOrElse(i) { 0 }
        if (ai != bi) return ai > bi
    }
    return false
}
