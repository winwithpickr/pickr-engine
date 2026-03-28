package com.winwithpickr.twitter

import com.winwithpickr.core.models.TierConfig
import com.winwithpickr.core.pipeline.PipelineContext
import com.winwithpickr.twitter.models.EntryConditions
import com.winwithpickr.twitter.models.PublicMetrics
import com.winwithpickr.twitter.models.XUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.time.Duration.Companion.days

class XPoolBuilderTest {

    private lateinit var dataSource: XDataSource
    private lateinit var builder: XPoolBuilder

    private val freeTier = TierConfig(
        maxEntries = 500, maxWinners = 1,
        watermark = true, monthlyPickLimit = 3,
    )

    private val proTier = TierConfig(
        maxEntries = 10_000, maxWinners = 5,
        features = setOf(XFeatures.FOLLOWER_CHECK, XFeatures.FOLLOW_ACCOUNTS_CHECK),
        monthlyPickLimit = 5, overageRate = 50,
    )

    private val businessTier = TierConfig(
        maxEntries = 100_000, maxWinners = 20,
        features = setOf(XFeatures.FOLLOWER_CHECK, XFeatures.FOLLOW_ACCOUNTS_CHECK, XFeatures.FRAUD_FILTER),
        monthlyPickLimit = 15, overageRate = 25,
    )

    private val hostXId = "HOST_001"

    private fun users(vararg names: String) = names.map { XUser(id = "id_$it", displayName = it) }

    private fun context(giveawayId: String = "g1", maxEntries: Int = 500) = PipelineContext(
        giveawayId = giveawayId,
        hostId = hostXId,
        targetId = "t1",
        maxEntries = maxEntries,
    )

    @BeforeTest
    fun setup() {
        dataSource = mockk(relaxed = true)
        builder = XPoolBuilder(dataSource)
    }

    // ── Source intersection tests ─────────────────────────────────────────

    @Test
    fun replyPlusRetweetIntersection() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("bob", "carol", "dave")

        val conds = EntryConditions(reply = true, retweet = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(setOf("bob", "carol"), result.users.map { it.username }.toSet())
    }

    @Test
    fun emptyReplyPlusRetweetReturnsEmpty() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns emptyList()
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("bob")

        val conds = EntryConditions(reply = true, retweet = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertTrue(result.users.isEmpty())
    }

    @Test
    fun quoteOnlyPoolReturnsQuoters() = runBlocking {
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("alice", "bob")

        val conds = EntryConditions(reply = false, quoteTweet = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(setOf("alice", "bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun replyPlusQuoteIntersection() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("bob", "dave")

        val conds = EntryConditions(reply = true, quoteTweet = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(setOf("bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun emptyRepliesPlusQuoteReturnsEmpty() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns emptyList()
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("alice", "bob")

        val conds = EntryConditions(reply = true, quoteTweet = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertTrue(result.users.isEmpty())
    }

    @Test
    fun retweetPlusQuoteIntersectionWithoutReply() = runBlocking {
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("bob", "dave")

        val conds = EntryConditions(reply = false, retweet = true, quoteTweet = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(setOf("bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun hostIsExcludedFromPool() = runBlocking {
        val pool = users("alice") + XUser(id = hostXId, displayName = "host")
        coEvery { dataSource.fetchReplies(any(), any()) } returns pool

        val conds = EntryConditions(reply = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
        assertTrue(result.users.none { it.id == hostXId })
    }

    @Test
    fun deduplication() = runBlocking {
        val duped = listOf(
            XUser(id = "u1", displayName = "alice"),
            XUser(id = "u1", displayName = "alice"),
            XUser(id = "u2", displayName = "bob"),
        )
        coEvery { dataSource.fetchReplies(any(), any()) } returns duped

        val conds = EntryConditions(reply = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(2, result.users.size)
    }

    // ── Follower filter tests ────────────────────────────────────────────

    @Test
    fun followerCheckFiltersNonFollowers() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.checkFollowers(eq(hostXId), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice", "id_carol")), false)
        }

        val conds = EntryConditions(reply = true, followHost = true)
        val (pipeline, followFilter) = builder.buildPipeline(conds, proTier)
        val result = pipeline.build(context())

        assertEquals(setOf("alice", "carol"), result.users.map { it.username }.toSet())
        assertFalse(followFilter!!.isPartial)
    }

    @Test
    fun followerCheckSkippedOnFreeTier() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")

        val conds = EntryConditions(reply = true, followHost = true)
        val (pipeline, followFilter) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(2, result.users.size)
        assertNull(followFilter)
        coVerify(exactly = 0) { dataSource.checkFollowers(any(), any()) }
    }

    @Test
    fun partialFollowerSetIsFlagged() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice")
        coEvery { dataSource.checkFollowers(eq(hostXId), any()) } returns Pair(setOf("id_alice"), true)

        val conds = EntryConditions(reply = true, followHost = true)
        val (pipeline, followFilter) = builder.buildPipeline(conds, proTier)
        pipeline.build(context())

        assertTrue(followFilter!!.isPartial)
    }

    // ── Follow accounts filter tests ─────────────────────────────────────

    @Test
    fun followAccountsFiltersNonFollowers() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.resolveHandle("sponsor") } returns "id_sponsor"
        coEvery { dataSource.checkFollowers(eq("id_sponsor"), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice", "id_carol")), false)
        }

        val conds = EntryConditions(reply = true, followAccounts = listOf("sponsor"))
        val (pipeline, _) = builder.buildPipeline(conds, proTier)
        val result = pipeline.build(context())

        assertEquals(setOf("alice", "carol"), result.users.map { it.username }.toSet())
    }

    @Test
    fun followAccountsRequiresAll() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")
        coEvery { dataSource.resolveHandle("s1") } returns "id_s1"
        coEvery { dataSource.resolveHandle("s2") } returns "id_s2"
        coEvery { dataSource.checkFollowers(eq("id_s1"), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice", "id_bob")), false)
        }
        coEvery { dataSource.checkFollowers(eq("id_s2"), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice")), false)
        }

        val conds = EntryConditions(reply = true, followAccounts = listOf("s1", "s2"))
        val (pipeline, _) = builder.buildPipeline(conds, proTier)
        val result = pipeline.build(context())

        assertEquals(listOf("alice"), result.users.map { it.username })
    }

    @Test
    fun followAccountsSkippedOnFreeTier() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")

        val conds = EntryConditions(reply = true, followAccounts = listOf("sponsor"))
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(2, result.users.size)
        coVerify(exactly = 0) { dataSource.resolveHandle(any()) }
    }

    @Test
    fun followAccountsUsesCheckFollowersNotBuildFollowerSet() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")
        coEvery { dataSource.resolveHandle("sponsor") } returns "id_sponsor"
        coEvery { dataSource.checkFollowers(eq("id_sponsor"), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice")), false)
        }

        val conds = EntryConditions(reply = true, followAccounts = listOf("sponsor"))
        val (pipeline, _) = builder.buildPipeline(conds, proTier)
        pipeline.build(context())

        // checkFollowers called (with early exit), not buildFollowerSet
        coVerify(exactly = 1) { dataSource.checkFollowers(eq("id_sponsor"), any()) }
        coVerify(exactly = 0) { dataSource.buildFollowerSet(any()) }
    }

    @Test
    fun followAccountsPartialIsHandled() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice")
        coEvery { dataSource.resolveHandle("sponsor") } returns "id_sponsor"
        coEvery { dataSource.checkFollowers(eq("id_sponsor"), any()) } returns Pair(setOf("id_alice"), true)

        val conds = EntryConditions(reply = true, followAccounts = listOf("sponsor"))
        val (pipeline, _) = builder.buildPipeline(conds, proTier)
        pipeline.build(context())

        coVerify(exactly = 1) { dataSource.checkFollowers(eq("id_sponsor"), any()) }
    }

    @Test
    fun followAccountsUnresolvableHandleSkipsFilter() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")
        coEvery { dataSource.resolveHandle("nonexistent") } returns null

        val conds = EntryConditions(reply = true, followAccounts = listOf("nonexistent"))
        val (pipeline, _) = builder.buildPipeline(conds, proTier)
        val result = pipeline.build(context())

        assertEquals(2, result.users.size)
        coVerify(exactly = 0) { dataSource.checkFollowers(any(), any()) }
    }

    @Test
    fun followAccountsWithFollowHostBothUseCheckFollowers() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        // Host follower check
        coEvery { dataSource.checkFollowers(eq(hostXId), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice", "id_bob", "id_carol")), false)
        }
        // Sponsor account check
        coEvery { dataSource.resolveHandle("sponsor") } returns "id_sponsor"
        coEvery { dataSource.checkFollowers(eq("id_sponsor"), any()) } answers {
            val candidates = secondArg<Set<String>>()
            Pair(candidates.intersect(setOf("id_alice", "id_carol")), false)
        }

        val conds = EntryConditions(reply = true, followHost = true, followAccounts = listOf("sponsor"))
        val (pipeline, followFilter) = builder.buildPipeline(conds, proTier)
        val result = pipeline.build(context())

        assertEquals(setOf("alice", "carol"), result.users.map { it.username }.toSet())
        coVerify(exactly = 1) { dataSource.checkFollowers(eq(hostXId), any()) }
        coVerify(exactly = 1) { dataSource.checkFollowers(eq("id_sponsor"), any()) }
    }

    // ── Hashtag filter tests ─────────────────────────────────────────────

    @Test
    fun hashtagFilterKeepsMatches() = runBlocking {
        val withTag = XUser(id = "u1", displayName = "alice", replyText = "Love this #giveaway!")
        val withoutTag = XUser(id = "u2", displayName = "bob", replyText = "I want to win!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(withTag, withoutTag)

        val conds = EntryConditions(reply = true, requiredHashtag = "giveaway")
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
        assertEquals("alice", result.users[0].username)
    }

    @Test
    fun hashtagFilterIsCaseInsensitive() = runBlocking {
        val user = XUser(id = "u1", displayName = "alice", replyText = "#GIVEAWAY here!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(user)

        val conds = EntryConditions(reply = true, requiredHashtag = "giveaway")
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
    }

    // ── Min tags filter tests ────────────────────────────────────────────

    @Test
    fun minTagsFilterKeepsEnoughTags() = runBlocking {
        val enough = XUser(id = "u1", displayName = "alice", replyText = "@friend1 @friend2 entering!")
        val notEnough = XUser(id = "u2", displayName = "bob", replyText = "@friend1 entering!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(enough, notEnough)

        val conds = EntryConditions(reply = true, minTags = 2)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
        assertEquals("alice", result.users[0].username)
    }

    @Test
    fun minTagsExcludesHostAndBotHandles() = runBlocking {
        val user = XUser(id = "u1", displayName = "alice",
            replyText = "@hostuser @winwithpickr @friend1 entering!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(user)

        val conds = EntryConditions(reply = true, minTags = 2)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier,
            excludeHandles = setOf("hostuser", "winwithpickr"))
        val result = pipeline.build(context())

        assertTrue(result.users.isEmpty())
    }

    // ── Quote text filter tests ─────────────────────────────────────────

    @Test
    fun quoteTextFilterKeepsMatches() = runBlocking {
        val match = XUser(id = "u1", displayName = "alice", replyText = "I love this product!")
        val noMatch = XUser(id = "u2", displayName = "bob", replyText = "Random text here")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns listOf(match, noMatch)

        val conds = EntryConditions(reply = false, quoteTweet = true, requiredQuoteText = "I love this")
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
        assertEquals("alice", result.users[0].username)
    }

    @Test
    fun quoteTextFilterIsCaseInsensitive() = runBlocking {
        val user = XUser(id = "u1", displayName = "alice", replyText = "I LOVE THIS product!")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns listOf(user)

        val conds = EntryConditions(reply = false, quoteTweet = true, requiredQuoteText = "i love this")
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
    }

    @Test
    fun quoteTextFilterSkippedWhenNull() = runBlocking {
        val user = XUser(id = "u1", displayName = "alice", replyText = "anything")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns listOf(user)

        val conds = EntryConditions(reply = false, quoteTweet = true, requiredQuoteText = null)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
    }

    // ── Fraud filter tests ───────────────────────────────────────────────

    @Test
    fun fraudFilterRemovesYoungAccounts() = runBlocking {
        val old = XUser(id = "u1", displayName = "veteran",
            createdAt = Clock.System.now().minus(30.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 100))
        val young = XUser(id = "u2", displayName = "newbie",
            createdAt = Clock.System.now().minus(2.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 100))
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(old, young)

        val conds = EntryConditions(reply = true, minAccountAgeDays = 7)
        val (pipeline, _) = builder.buildPipeline(conds, businessTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
        assertEquals("veteran", result.users[0].username)
    }

    @Test
    fun fraudFilterSkippedOnFreeTier() = runBlocking {
        val young = XUser(id = "u1", displayName = "newbie",
            createdAt = Clock.System.now().minus(1.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 0))
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(young)

        val conds = EntryConditions(reply = true)
        val (pipeline, _) = builder.buildPipeline(conds, freeTier)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
    }

    @Test
    fun fraudFilterUsesTierDefaults() = runBlocking {
        val ok = XUser(id = "u1", displayName = "legit",
            createdAt = Clock.System.now().minus(30.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 100))
        val bot = XUser(id = "u2", displayName = "bot",
            createdAt = Clock.System.now().minus(1.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 1))
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(ok, bot)

        val conds = EntryConditions(reply = true) // no explicit thresholds
        val (pipeline, _) = builder.buildPipeline(conds, businessTier,
            defaultMinAgeDays = 7, defaultMinFollowers = 5)
        val result = pipeline.build(context())

        assertEquals(1, result.users.size)
        assertEquals("legit", result.users[0].username)
    }
}
