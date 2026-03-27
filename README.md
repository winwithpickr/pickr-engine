# pickr-core

The verifiable giveaway engine behind [@winwithpickr](https://x.com/winwithpickr).
Open-source, MIT-licensed — audit the algorithm, verify any result, or build on top of it.

Kotlin Multiplatform (JVM + JS). Zero service dependencies.

## What this library does

pickr-core is a **pure verification engine**. Given a seed and a list of user IDs, it produces a deterministic winner selection that anyone can reproduce. It has no knowledge of databases, APIs, or the X platform — it just does math.

```
seed + pool → shuffle → winners
pool        → sort + SHA-256 → pool hash
```

Same inputs, same outputs, every time. That's the entire contract.

## How winwithpickr uses it

The [@winwithpickr](https://x.com/winwithpickr) service wraps this library with X API access, a database, and billing — none of which can influence the pick outcome. The service enforces a strict ordering that makes the result independently verifiable:

1. **Seed committed first** — `SeedGenerator` produces a random 64-char hex seed. The service stores it and anchors the seed hash to Solana _before_ touching the X API — proving the seed existed before the pool was known.
2. **Pool fetched second** — the service calls the X API to collect replies, retweets, and follower lists. This happens entirely _after_ the seed is locked. pickr-core's `PoolBuilder` handles deduplication and condition intersection, but the X data itself comes from the service layer.
3. **Deterministic pick** — `SeededRandom.shuffle(pool, seed)` runs the committed seed against the fetched pool. Winners are the first N entries. This is the only step that lives in pickr-core.
4. **Everything published** — the seed, full entry pool, pool hash, and winners are stored and exposed on the result's verification page.

Because step 3 is deterministic and open-source, anyone with the seed and pool can rerun it and confirm the winners match — without trusting the service.

## Verify a result

### CLI (npm)

```bash
npx @winwithpickr/verify \
  --seed <64-char-hex> \
  --pool <id,id,id,...> \
  --winners <n> \
  --expected-hash <hash-from-result-page>
```

### CLI (Java)

Download `pickr-verify.jar` from [Releases](https://github.com/bmcreations/pickr/releases):

```bash
java -jar pickr-verify.jar \
  --seed <64-char-hex> \
  --pool <id,id,id,...> \
  --winners <n> \
  --expected-hash <hash-from-result-page>
```

### Kotlin

```kotlin
// Deterministic shuffle — same seed + pool = same result, always
val shuffled = SeededRandom.shuffle(pool, seed)
val winners  = shuffled.take(n)

// Pool hash — SHA-256 of sorted, comma-joined user IDs
val hash = SeededRandom.poolHash(pool.map { it.id })
```

### JavaScript (browser)

The JS bundle is used on every [@winwithpickr](https://winwithpickr.com) verification page for zero-API client-side verification:

```javascript
const pickr = require("@winwithpickr/verify");
const result = pickr.dev.pickrtweet.core.verifyPick(seed, poolIds, winnerCount);
// result.winners — computed winner IDs
// result.poolHash — computed SHA-256 pool hash
```

## Architecture

pickr-core is the **open core** — the math that determines who wins. The closed-source service layer handles everything else: talking to X, storing results, billing, and queue orchestration. The boundary is intentional: the service feeds data _into_ pickr-core but cannot alter what comes _out_.

```
                    pickr-core (open, MIT)
                    ─────────────────────
                    SeededRandom.shuffle()    ← deterministic pick
                    SeededRandom.poolHash()   ← verifiable fingerprint
                    SeedGenerator             ← cryptographic seed (JVM)
                    CommandParser             ← parse @winwithpickr text
                    PoolBuilder               ← dedup, intersect, filter
                    ReplyFormatter            ← format result tweets
                    Models                    ← Tier, XUser, conditions

        ┌───────────────────┼───────────────────┐
        │                   │                   │
   pickr service        verify page          this CLI
   (closed)             (browser JS)         (npm / JAR)
   commits seed         loads pool+seed      takes pool+seed
   fetches pool         calls verifyPick()   calls verifyPick()
   calls shuffle()      shows match/mismatch shows match/mismatch
   posts result
```

## Modules

| Module | Target | Description |
|---|---|---|
| `SeededRandom` | common | Deterministic Fisher-Yates shuffle, SHA-256 pool hash |
| `SeedGenerator` | JVM | Cryptographic seed generation (`SecureRandom`) |
| `CommandParser` | common | Parses `@winwithpickr` mention text into `ParsedCommand` |
| `PoolBuilder` | common | Entry pool construction, condition intersection, follower filtering |
| `ReplyFormatter` | JVM | Result tweet formatting, signed upgrade URLs |
| `OAuth1Signer` | JVM | HMAC-SHA1 OAuth 1.0a signing for X API v2 |

### Models (common)

| Model | Description |
|---|---|
| `Tier` | `FREE`, `PRO`, `BUSINESS` |
| `TierConfig` | Entry/winner limits, feature flags, monthly pick limit, overage rate |
| `ParsedCommand` | Parsed command: winner count, conditions, trigger mode |
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
| `@winwithpickr watch` | Watch giveaway, pick when host triggers |

### Watch trigger phrases

When a watched giveaway host replies or quote-RTs with any of these, the pick fires automatically:

> "pick a winner", "picking a winner", "draw a winner", "end giveaway",
> "giveaway over", "giveaway closed", "winner time", "time to pick"

## Building

```bash
# Run all tests (JVM + JS)
./gradlew :pickr-core:allTests

# Build standalone JAR
./gradlew :pickr-core:verifyJar
# → pickr-core/build/libs/pickr-verify.jar

# Build npm package
./gradlew :pickr-core:assembleNpm
# → pickr-core/build/npm-package/

# Run verification locally via Gradle
./gradlew :pickr-core:verifyCli \
  --args="--seed <hex> --pool <ids> --winners <n>"
```

## The algorithm

The shuffle is a pure Kotlin implementation of java.util.Random's LCG (linear congruential generator) combined with Fisher-Yates. This is the single source of truth — the same code runs on JVM, in the browser, and in the npm CLI.

```
seed (64 hex chars)
  → parse first 8 bytes as big-endian Long
  → initialize LCG: state = (seedLong XOR 0x5DEECE66D) AND ((1<<48)-1)
  → Fisher-Yates shuffle using LCG-generated indices
  → winners = shuffled[0..n-1]

pool hash
  → sort user IDs lexicographically
  → join with ","
  → SHA-256 (pure Kotlin implementation, no platform deps)
  → hex-encode → 64-char string
```

## Blockchain proof (service-layer only)

Every winwithpickr pick anchors two hashes to Solana via `SolanaAnchor`:

1. **Seed commit** — the seed hash is written to Solana _before_ the pool is fetched, proving the seed existed before entries were known.
2. **Result anchor** — the pool hash and winners hash are written _after_ the pick, creating a tamper-proof record of the outcome.

The on-chain timestamps make it independently verifiable that the ordering was respected. Blockchain anchoring is **not** part of pickr-core — it lives entirely in the service layer. Every verification page links to both transactions on Solscan.

## Dependencies

Zero service dependencies. Pure Kotlin with only:
- `kotlinx-serialization-json`
- `kotlinx-datetime`
- `kotlinx-coroutines-core`

No Ktor, no database, no Redis, no HTTP clients.

## License

MIT — see [LICENSE](LICENSE)
