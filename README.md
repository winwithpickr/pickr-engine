# pickr-engine

The verifiable giveaway engine behind [@winwithpickr](https://x.com/winwithpickr).
Open-source, MIT-licensed — audit the algorithm, verify any result, or build on top of it.

Kotlin Multiplatform (JVM + JS). Zero service dependencies.

## What this library does

pickr-engine is a **pure verification engine**. Given a seed and a list of user IDs, it produces a deterministic winner selection that anyone can reproduce. It has no knowledge of databases, APIs, or any platform — it just does math.

```
seed + pool → shuffle → winners
pool        → sort + SHA-256 → pool hash
```

Same inputs, same outputs, every time. That's the entire contract.

## How winwithpickr uses it

The [@winwithpickr](https://x.com/winwithpickr) service wraps this library with platform APIs (X, Telegram), a database, and billing — none of which can influence the pick outcome. The service enforces a strict ordering that makes the result independently verifiable:

1. **Seed committed first** — `SeedGenerator` produces a random 64-char hex seed. The service stores it and anchors the seed hash to Solana _before_ touching any platform API — proving the seed existed before the pool was known.
2. **Pool fetched second** — the service calls the platform API to collect entries (replies, retweets, button presses, comments). This happens entirely _after_ the seed is locked.
3. **Deterministic pick** — `SeededRandom.shuffle(pool, seed)` runs the committed seed against the fetched pool. Winners are the first N entries. This is the only step that lives in pickr-engine.
4. **Everything published** — the seed, full entry pool, pool hash, and winners are stored and exposed on the result's verification page.

Because step 3 is deterministic and open-source, anyone with the seed and pool can rerun it and confirm the winners match — without trusting the service.

## Verify a result

```kotlin
val shuffled = SeededRandom.shuffle(pool, seed)
val winners  = shuffled.take(n)
val hash     = SeededRandom.poolHash(pool.map { it.id })
```

## Architecture

pickr-engine is the **open core** — the math that determines who wins. Platform-specific SDKs ([pickr-twitter](https://github.com/winwithpickr/pickr-twitter), [pickr-telegram](https://github.com/winwithpickr/pickr-telegram)) handle platform integration. The closed-source server handles everything else.

```
              pickr-engine (open, MIT)
              ─────────────────────────
              SeededRandom.shuffle()    ← deterministic pick
              SeededRandom.poolHash()   ← verifiable fingerprint
              SeedGenerator             ← cryptographic seed (JVM)
              PoolPipeline              ← source → filter → cap
              Candidate, TierConfig     ← shared models

    ┌──────────────┼──────────────┐
    │              │              │
pickr-twitter  pickr-telegram  verify CLI
(X SDK)        (Telegram SDK)  (npm / JAR)
```

## Modules

| Module | Target | Description |
|---|---|---|
| `SeededRandom` | common | Deterministic Fisher-Yates shuffle, SHA-256 pool hash |
| `SeedGenerator` | JVM | Cryptographic seed generation (`SecureRandom`) |
| `PoolPipeline` | common | Source → filter → cap pipeline for entry pools |
| `PoolSource` | common | Interface for entry pool data sources |
| `PoolFilter` | common | Interface for entry pool filters |
| `PipelineContext` | common | Shared state across pipeline execution |

### Models (common)

| Model | Description |
|---|---|
| `Tier` | `FREE`, `PRO`, `BUSINESS` |
| `TierConfig` | Entry/winner limits, feature flags, monthly pick limit, overage rate |
| `Candidate` | Platform-agnostic entry (id, username) |
| `TriggerMode` | `IMMEDIATE`, `WATCH`, `SCHEDULED` |

## Building

```bash
# Run all tests
./gradlew jvmTest

# Build standalone JAR
./gradlew verifyJar
# → build/libs/pickr-verify.jar

# Build JS bundle
./gradlew jsBrowserProductionWebpack

# Run verification locally via Gradle
./gradlew verifyCli \
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

Blockchain anchoring is **not** part of pickr-engine — it lives entirely in the service layer. Every verification page links to both transactions on Solscan.

## Related repos

| Repo | Description |
|---|---|
| [pickr-engine](https://github.com/winwithpickr/pickr-engine) | This repo — platform-agnostic core |
| [pickr-twitter](https://github.com/winwithpickr/pickr-twitter) | X/Twitter SDK — command parser, pool builder, reply formatter |
| [pickr-telegram](https://github.com/winwithpickr/pickr-telegram) | Telegram SDK — command parser, pool builder, Mini App support |

## License

MIT — see [LICENSE](LICENSE)
