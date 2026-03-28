package dev.pickrtweet.twitter.filters

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolFilter
import dev.pickrtweet.twitter.models.XUser
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class FraudFilter(
    private val minAccountAgeDays: Int,
    private val minFollowers: Int,
) : PoolFilter<XUser> {
    override val name = "fraud_filter"

    override suspend fun apply(candidates: MutableMap<String, XUser>, context: PipelineContext) {
        if (minAccountAgeDays <= 0 && minFollowers <= 0) return

        val cutoff = Clock.System.now().minus(minAccountAgeDays.days)
        candidates.entries.removeAll { (_, user) ->
            val tooYoung = minAccountAgeDays > 0 && user.createdAt != null &&
                Instant.parse(user.createdAt!!) > cutoff
            val tooFewFollowers = minFollowers > 0 &&
                (user.publicMetrics?.followersCount ?: 0) < minFollowers
            tooYoung || tooFewFollowers
        }
    }
}
