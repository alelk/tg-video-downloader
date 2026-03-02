package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Video quality setting for downloads. Mirrors domain `DownloadPolicy.VideoQuality`. */
@Serializable
enum class VideoQualityDto {
    @SerialName("best") BEST,
    @SerialName("hd_1080") HD_1080,
    @SerialName("hd_720") HD_720,
    @SerialName("sd_480") SD_480,
}
