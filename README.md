# pickr-core

The verifiable giveaway winner selection engine powering [Pickr](https://winwithpickr.com).
MIT licensed — audit it, fork it, verify any result picked by @winwithpickr.

## How verifiability works

1. A random seed is generated with `SecureRandom` before any entries are fetched
2. The seed is committed to the database immediately — it cannot be changed
3. The entry pool is fetched independently of the seed
4. The pool is shuffled using the committed seed via Fisher-Yates
5. Winners are the first N entries in the shuffled list

Given the seed and the entry pool, anyone can reproduce the exact result.
Every @winwithpickr result tweet includes the seed and pool size for this reason.

## Modules

| Module | Description |
|---|---|
| `SeededRandom` | Seed generation, deterministic Fisher-Yates shuffle, pool hash (SHA-256) |
| `PoolBuilder` | Entry pool construction from replies/retweets, condition intersection, follower filtering |
| `CommandParser` | Parses @winwithpickr mention text into structured `ParsedCommand` |
| `ReplyFormatter` | Formats result tweets, limit messages, signed upgrade URLs |
| `OAuth1Signer` | HMAC-SHA1 OAuth 1.0a request signing for X API v2 |

### Models

| Model | Description |
|---|---|
| `Tier` | `FREE`, `PRO`, `BUSINESS` |
| `TierConfig` | Entry/winner limits, feature flags, monthly pick limit, overage rate |
| `ParsedCommand` | Parsed command with winner count, conditions, trigger mode |
| `EntryConditions` | Reply, retweet, like, followHost, followAccounts flags |
| `XUser` | X user identity (id, username, public metrics) |
| `PoolResult` | Final entry pool with users and partial-follower flag |

## Commands

Reply to any giveaway tweet with:

| Command | What it does |
|---|---|
| `@winwithpickr pick` | Pick 1 winner from replies |
| `@winwithpickr pick 3` | Pick 3 winners |
| `@winwithpickr pick from replies+retweets` | Pick from both pools (intersection) |
| `@winwithpickr pick followers only` | Only pick from host's followers (Pro+) |
| `@winwithpickr pick in 2h` | Scheduled pick in 2 hours |
| `@winwithpickr watch` | Watch giveaway, pick when host says "picking a winner" |

### Watch trigger phrases

When a watched giveaway host replies or quote-RTs with any of these, the pick fires automatically:

> "pick a winner", "picking a winner", "draw a winner", "end giveaway",
> "giveaway over", "giveaway closed", "winner time", "time to pick"

## Usage

```kotlin
// Generate and commit seed before fetching pool
val seed = SeededRandom.generateSeed()

// ... fetch pool from X API ...

// Deterministic shuffle — same seed + pool = same result every time
val shuffled = SeededRandom.shuffle(pool, seed)
val winners  = shuffled.take(n)

// Verify independently
val hash = SeededRandom.poolHash(pool.map { it.id })
```

## Verification

To verify any @winwithpickr result:
1. Get the seed from the result tweet
2. Get the pool (reply/retweet list) from X at the time of the draw
3. Run `SeededRandom.shuffle(pool, seed)` — first N entries are the winners

## Dependencies

Zero service dependencies. Pure Kotlin with only:
- `kotlinx-serialization-json`
- `kotlinx-datetime`
- `kotlinx-coroutines-core`

No Ktor, no database, no Redis, no HTTP clients.

## License

MIT — see [LICENSE](LICENSE)
