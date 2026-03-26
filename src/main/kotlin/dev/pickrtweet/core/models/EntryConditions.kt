package dev.pickrtweet.core.models

import kotlinx.serialization.Serializable

@Serializable
data class EntryConditions(
    val reply: Boolean = true,
    val retweet: Boolean = false,
    val like: Boolean = false,
    val followHost: Boolean = false,
    val followAccounts: List<String> = emptyList(),
)
