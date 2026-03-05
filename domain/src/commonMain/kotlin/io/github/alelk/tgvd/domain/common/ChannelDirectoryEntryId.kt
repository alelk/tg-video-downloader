package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@JvmInline
value class ChannelDirectoryEntryId(val value: Uuid)

