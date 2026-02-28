package io.github.alelk.tgvd.server.infra.config

data class PostProcessConfig(
    val taggingTool: TaggingTool = TaggingTool.FFMPEG,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
) {
    enum class TaggingTool { FFMPEG, ATOMICPARSLEY, MP4BOX }
}

