package io.github.alelk.tgvd.domain.storage

enum class MediaContainer(val extension: String) {
    MP4("mp4"),
    MKV("mkv"),
    WEBM("webm"),
    AVI("avi"),
    MOV("mov");

    companion object {
        fun fromExtension(ext: String): MediaContainer? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}

enum class AudioFormat(val extension: String) {
    M4A("m4a"),
    MP3("mp3"),
    OPUS("opus"),
    FLAC("flac"),
    WAV("wav");
}

enum class ImageFormat(val extension: String) {
    JPG("jpg"),
    PNG("png"),
    WEBP("webp");
}
