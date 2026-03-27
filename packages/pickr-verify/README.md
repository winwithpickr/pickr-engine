# @winwithpickr/verify

Independently verify any [@winwithpickr](https://x.com/winwithpickr) giveaway result from your terminal.

Same algorithm that runs on the server and in the browser verification page — single source of truth, zero trust required.

## Usage

```bash
npx @winwithpickr/verify \
  --seed <64-char-hex> \
  --pool <id,id,...> \
  --winners <n> \
  --expected-hash <hash>
```

The seed, pool IDs, and pool hash are all available on the giveaway's verification page (linked from every result tweet).

## Example

```bash
npx @winwithpickr/verify \
  --seed aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  --pool 111,222,333,444,555 \
  --winners 2 \
  --expected-hash aa6e12495e487da59aea17fa4be1654953ccfdc6d1c6cf9280a1c61411e3a01d
```

```
── Pickr Verification ──

Seed:        aaaaaaaaaa...
Pool size:   5
Pool hash:   aa6e12495e487da59aea17fa4be1654953ccfdc6d1c6cf9280a1c61411e3a01d
Winners:     111, 333

Expected:    aa6e12495e487da59aea17fa4be1654953ccfdc6d1c6cf9280a1c61411e3a01d
Match:       YES
```

Exits with code 0 on match, 1 on mismatch.

## Options

| Flag | Required | Description |
|---|---|---|
| `--seed` | Yes | 64-character hex seed from the result page |
| `--pool` | Yes | Comma-separated X user IDs (sorted, from the result page) |
| `--winners` | No | Number of winners (default: 1) |
| `--expected-hash` | No | Pool hash to verify against — exits 1 on mismatch |
| `--help` | No | Show usage |

## How it works

1. The pool IDs are sorted lexicographically and hashed with SHA-256 to produce the pool hash
2. The first 8 bytes of the hex seed initialize a java.util.Random-compatible LCG
3. A Fisher-Yates shuffle is run over the pool using that LCG
4. The first N entries of the shuffled pool are the winners

This is the exact same algorithm used by the winwithpickr service. The implementation lives in [pickr-core](https://github.com/bmcreations/winwithpickr) (Kotlin Multiplatform, MIT licensed) — the JVM server, browser verification page, and this CLI all compile from the same source.

## Also available as

- **Java JAR** — `java -jar pickr-verify.jar` — download from [GitHub Releases](https://github.com/bmcreations/winwithpickr/releases)
- **Browser** — every result tweet links to a verification page that runs this check client-side
- **Kotlin library** — `SeededRandom.shuffle(pool, seed)` via Maven/Gradle

## License

MIT
