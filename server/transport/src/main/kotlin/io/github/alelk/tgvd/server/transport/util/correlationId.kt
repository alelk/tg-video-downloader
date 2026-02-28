package io.github.alelk.tgvd.server.transport.util

import io.ktor.server.plugins.callid.callId
import io.ktor.server.routing.RoutingCall

/**
 * Extension property to get correlation ID from Ktor's CallId plugin.
 * Falls back to "unknown" if CallId plugin is not installed.
 */
val RoutingCall.correlationId: String
    get() = callId ?: "unknown"