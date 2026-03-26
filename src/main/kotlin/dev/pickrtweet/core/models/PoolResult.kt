package dev.pickrtweet.core.models

data class PoolResult(
    val users: List<XUser>,
    val followHostPartial: Boolean = false,
)
