package dev.pickrtweet.twitter.models

import dev.pickrtweet.core.models.TriggerMode
import kotlinx.serialization.Serializable

@Serializable
data class ParsedCommand(
    val winners: Int = 1,
    val conditions: EntryConditions,
    val triggerMode: TriggerMode = TriggerMode.IMMEDIATE,
    val scheduledDelayMs: Long? = null,
)
