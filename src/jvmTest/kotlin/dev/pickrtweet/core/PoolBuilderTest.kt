package dev.pickrtweet.core

import dev.pickrtweet.core.models.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.time.Duration.Companion.days

class PoolBuilderTest {

    private lateinit var dataSource: PoolDataSource
    private lateinit var poolBuilder: PoolBuilder

    private val freeTier = TierConfig(
        maxEntries = 500, maxWinners = 1,
        allowedConditions = setOf("reply", "retweet"),
        followerCheck = false, followAccountsCheck = false,
        fraudFilter = false, scheduledPicks = false,
        permalink = false, watermark = true, monthlyPickLimit = 3, overageRate = null,
    )

    private val proTier = freeTier.copy(
        maxEntries = 10_000, maxWinners = 5,
        followerCheck = true, watermark = false, monthlyPickLimit = 5, overageRate = 50,
    )

    private val hostXId = "HOST_001"

    private val businessTier = freeTier.copy(
        maxEntries = 100_000, maxWinners = 20,
        followerCheck = true, fraudFilter = true, watermark = false,
        monthlyPickLimit = 15, overageRate = 25,
        minAccountAgeDays = 7, minFollowers = 5,
    )

    private fun users(vararg names: String) = names.map { XUser(id = "id_$it", username = it) }

    @BeforeTest
    fun setup() {
        dataSource = mockk(relaxed = true)
        poolBuilder = PoolBuilder(dataSource)
    }

    @Test
    fun `reply plus retweet intersection returns only users in both lists`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("bob", "carol", "dave")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, retweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        val names = result.users.map { it.username }.toSet()
        assertEquals(setOf("bob", "carol"), names)
    }

    @Test
    fun `empty reply pool plus retweet pool returns empty`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns emptyList()
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("bob")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, retweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertTrue(result.users.isEmpty())
    }

    @Test
    fun `deduplication - duplicate author_id in replies counts once`() = runBlocking {
        val duped = listOf(
            XUser(id = "u1", username = "alice"),
            XUser(id = "u1", username = "alice"),
            XUser(id = "u2", username = "bob"),
        )
        coEvery { dataSource.fetchReplies(any(), any()) } returns duped

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(2, result.users.size)
        assertEquals(setOf("alice", "bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun `followerCheck false skips follower filtering regardless of followHost flag`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followHost = true),
            tierConfig = freeTier, // followerCheck = false
            giveawayId = "g1",
        )

        assertEquals(2, result.users.size)
        coVerify(exactly = 0) { dataSource.buildFollowerSet(any()) }
    }

    // ── Quote tweet pool tests ─────────────────────────────────────────────

    @Test
    fun `quote-only pool returns quoters`() = runBlocking {
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("alice", "bob")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = false, quoteTweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(setOf("alice", "bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun `reply plus quote intersection returns only users in both`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("bob", "dave")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, quoteTweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(setOf("bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun `empty replies plus quote requirement returns empty pool`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns emptyList()
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("alice", "bob")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, quoteTweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertTrue(result.users.isEmpty())
    }

    @Test
    fun `empty retweet intersection plus quote requirement returns empty pool`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice")
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("bob")  // no overlap with replies
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("bob")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, retweet = true, quoteTweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertTrue(result.users.isEmpty())
    }

    @Test
    fun `retweet plus quote intersection without reply`() = runBlocking {
        coEvery { dataSource.fetchRetweeters(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.fetchQuoteTweets(any(), any()) } returns users("bob", "dave")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = false, retweet = true, quoteTweet = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(setOf("bob"), result.users.map { it.username }.toSet())
    }

    @Test
    fun `quote tweet false does not fetch quotes`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice")

        poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, quoteTweet = false),
            tierConfig = freeTier, giveawayId = "g1",
        )

        coVerify(exactly = 0) { dataSource.fetchQuoteTweets(any(), any()) }
    }

    @Test
    fun `host is excluded from pool`() = runBlocking {
        val pool = users("alice") + XUser(id = hostXId, username = "host")
        coEvery { dataSource.fetchReplies(any(), any()) } returns pool

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
        assertTrue(result.users.none { it.id == hostXId })
    }

    @Test
    fun `follower check filters to followers only when enabled`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.buildFollowerSet(hostXId) } returns Pair(setOf("id_alice", "id_carol"), false)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followHost = true),
            tierConfig = proTier,
            giveawayId = "g1",
        )

        assertEquals(setOf("alice", "carol"), result.users.map { it.username }.toSet())
        assertFalse(result.followHostPartial)
    }

    // ── Fraud filter tests ─────────────────────────────────────────────────

    @Test
    fun `fraud filter removes young accounts`() = runBlocking {
        val oldAccount = XUser(
            id = "u1", username = "veteran",
            createdAt = Clock.System.now().minus(30.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 100),
        )
        val newAccount = XUser(
            id = "u2", username = "newbie",
            createdAt = Clock.System.now().minus(2.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 100),
        )
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(oldAccount, newAccount)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, minAccountAgeDays = 7),
            tierConfig = businessTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
        assertEquals("veteran", result.users[0].username)
    }

    @Test
    fun `fraud filter removes low-follower accounts`() = runBlocking {
        val popular = XUser(
            id = "u1", username = "popular",
            publicMetrics = PublicMetrics(followersCount = 50),
        )
        val bot = XUser(
            id = "u2", username = "bot",
            publicMetrics = PublicMetrics(followersCount = 2),
        )
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(popular, bot)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, minFollowers = 10),
            tierConfig = businessTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
        assertEquals("popular", result.users[0].username)
    }

    @Test
    fun `fraud filter uses tier defaults when conditions are zero`() = runBlocking {
        val ok = XUser(
            id = "u1", username = "legit",
            createdAt = Clock.System.now().minus(30.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 100),
        )
        val bot = XUser(
            id = "u2", username = "bot",
            createdAt = Clock.System.now().minus(1.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 1),
        )
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(ok, bot)

        // conditions have 0/0 but businessTier has defaults 7d/5 followers
        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true),
            tierConfig = businessTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
        assertEquals("legit", result.users[0].username)
    }

    // ── Hashtag filter tests ────────────────────────────────────────────────

    @Test
    fun `hashtag filter keeps only matching replies`() = runBlocking {
        val withTag = XUser(id = "u1", username = "alice", replyText = "Love this #giveaway!")
        val withoutTag = XUser(id = "u2", username = "bob", replyText = "I want to win!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(withTag, withoutTag)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, requiredHashtag = "giveaway"),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
        assertEquals("alice", result.users[0].username)
    }

    @Test
    fun `hashtag filter is case insensitive`() = runBlocking {
        val user = XUser(id = "u1", username = "alice", replyText = "#GIVEAWAY here!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(user)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, requiredHashtag = "giveaway"),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
    }

    @Test
    fun `hashtag filter excludes entries with null replyText`() = runBlocking {
        val noText = XUser(id = "u1", username = "alice")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(noText)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, requiredHashtag = "giveaway"),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertTrue(result.users.isEmpty())
    }

    // ── Min tags filter tests ────────────────────────────────────────────────

    @Test
    fun `min tags filter keeps replies with enough tags`() = runBlocking {
        val enough = XUser(id = "u1", username = "alice", replyText = "@friend1 @friend2 entering!")
        val notEnough = XUser(id = "u2", username = "bob", replyText = "@friend1 entering!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(enough, notEnough)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, minTags = 2),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
        assertEquals("alice", result.users[0].username)
    }

    @Test
    fun `min tags excludes host and bot handles from count`() = runBlocking {
        val user = XUser(id = "u1", username = "alice",
            replyText = "@hostuser @winwithpickr @friend1 entering!")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(user)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, minTags = 2),
            tierConfig = freeTier, giveawayId = "g1",
            excludeHandles = setOf("hostuser", "winwithpickr"),
        )

        // Only @friend1 counts → 1 < 2 → excluded
        assertTrue(result.users.isEmpty())
    }

    @Test
    fun `min tags excludes entries with null replyText`() = runBlocking {
        val noText = XUser(id = "u1", username = "alice")
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(noText)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, minTags = 1),
            tierConfig = freeTier, giveawayId = "g1",
        )

        assertTrue(result.users.isEmpty())
    }

    // ── Follow accounts filter tests ──────────────────────────────────

    @Test
    fun `followAccounts filters to users following all required accounts`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob", "carol")
        coEvery { dataSource.resolveHandle("sponsor") } returns "id_sponsor"
        // alice follows sponsor, bob doesn't, carol does
        coEvery { dataSource.fetchFollowing("id_alice") } returns setOf("id_sponsor", "id_other")
        coEvery { dataSource.fetchFollowing("id_bob") } returns setOf("id_other")
        coEvery { dataSource.fetchFollowing("id_carol") } returns setOf("id_sponsor")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followAccounts = listOf("sponsor")),
            tierConfig = proTier.copy(followAccountsCheck = true), giveawayId = "g1",
        )

        assertEquals(setOf("alice", "carol"), result.users.map { it.username }.toSet())
    }

    @Test
    fun `followAccounts requires ALL accounts to be followed`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")
        coEvery { dataSource.resolveHandle("sponsor1") } returns "id_s1"
        coEvery { dataSource.resolveHandle("sponsor2") } returns "id_s2"
        // alice follows both, bob follows only one
        coEvery { dataSource.fetchFollowing("id_alice") } returns setOf("id_s1", "id_s2")
        coEvery { dataSource.fetchFollowing("id_bob") } returns setOf("id_s1")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followAccounts = listOf("sponsor1", "sponsor2")),
            tierConfig = proTier.copy(followAccountsCheck = true), giveawayId = "g1",
        )

        assertEquals(listOf("alice"), result.users.map { it.username })
    }

    @Test
    fun `followAccounts skipped when tier followAccountsCheck is false`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice", "bob")

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followAccounts = listOf("sponsor")),
            tierConfig = freeTier, // followAccountsCheck = false
            giveawayId = "g1",
        )

        assertEquals(2, result.users.size)
        coVerify(exactly = 0) { dataSource.resolveHandle(any()) }
        coVerify(exactly = 0) { dataSource.fetchFollowing(any()) }
    }

    @Test
    fun `followAccounts skipped when unresolvable handle`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice")
        coEvery { dataSource.resolveHandle("deleted_account") } returns null

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followAccounts = listOf("deleted_account")),
            tierConfig = proTier.copy(followAccountsCheck = true), giveawayId = "g1",
        )

        // No filtering happened — unresolvable account is skipped gracefully
        assertEquals(1, result.users.size)
        coVerify(exactly = 0) { dataSource.fetchFollowing(any()) }
    }

    @Test
    fun `fraud filter skipped when tier fraudFilter is false`() = runBlocking {
        val newAccount = XUser(
            id = "u1", username = "newbie",
            createdAt = Clock.System.now().minus(1.days).toString(),
            publicMetrics = PublicMetrics(followersCount = 0),
        )
        coEvery { dataSource.fetchReplies(any(), any()) } returns listOf(newAccount)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true),
            tierConfig = freeTier, // fraudFilter = false
            giveawayId = "g1",
        )

        assertEquals(1, result.users.size)
    }

    @Test
    fun `partial follower set is flagged`() = runBlocking {
        coEvery { dataSource.fetchReplies(any(), any()) } returns users("alice")
        coEvery { dataSource.buildFollowerSet(hostXId) } returns Pair(setOf("id_alice"), true)

        val result = poolBuilder.build(
            parentTweetId = "t1", hostXId = hostXId,
            conditions = EntryConditions(reply = true, followHost = true),
            tierConfig = proTier, giveawayId = "g1",
        )

        assertTrue(result.followHostPartial)
    }
}
