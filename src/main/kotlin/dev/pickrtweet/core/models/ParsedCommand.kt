package dev.pickrtweet.core.models

import kotlinx.serialization.Serializable

@Serializable
data class ParsedCommand(
    val winners: Int = 1,
    val conditions: EntryConditions,
    val triggerMode: TriggerMode = TriggerMode.IMMEDIATE,
    val scheduledDelayMs: Long? = null,
)
