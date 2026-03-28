package dev.pickrtweet.twitter.models

import kotlinx.serialization.Serializable

@Serializable
data class EntryConditions(
    val reply: Boolean = true,
    val retweet: Boolean = false,
    val like: Boolean = false,
    val quoteTweet: Boolean = false,
    val followHost: Boolean = false,
    val followAccounts: List<String> = emptyList(),
    val minAccountAgeDays: Int = 0,
    val minFollowers: Int = 0,
    val requiredHashtag: String? = null,
    val minTags: Int = 0,
)
