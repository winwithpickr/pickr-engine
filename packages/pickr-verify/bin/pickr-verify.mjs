#!/usr/bin/env node

import { createRequire } from "module";
import { fileURLToPath } from "url";
import { dirname, join } from "path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

// Load the Kotlin/JS bundle (UMD — exports as "pickr")
const pickr = require(join(__dirname, "..", "lib", "pickr-parser.js"));
const { verifyPick } = pickr;

function usage() {
  console.log(`
pickr-verify — Independently verify winwithpickr giveaway results

Usage:
  pickr-verify --seed <hex> --pool <id,id,...> --winners <n> [--expected-hash <hash>]

Options:
  --seed            64-character hex seed from the giveaway result page
  --pool            Comma-separated sorted X user IDs from the entry pool
  --winners         Number of winners to pick
  --expected-hash   (optional) Expected pool hash to verify against

Example:
  npx @winwithpickr/verify \\
    --seed abc123...def \\
    --pool 111,222,333,444,555 \\
    --winners 2
`);
}

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i++) {
    if (argv[i].startsWith("--") && i + 1 < argv.length) {
      args[argv[i].slice(2)] = argv[++i];
    }
  }
  return args;
}

const args = parseArgs(process.argv);

if (args.help || (!args.seed && !args.pool)) {
  usage();
  process.exit(0);
}

if (!args.seed) {
  console.error("Error: --seed is required");
  process.exit(1);
}
if (!args.pool) {
  console.error("Error: --pool is required");
  process.exit(1);
}

const seed = args.seed;
const poolIds = args.pool.split(",").map((s) => s.trim()).filter(Boolean);
const winnerCount = parseInt(args.winners || "1", 10);

if (seed.length !== 64 || !/^[0-9a-f]+$/.test(seed)) {
  console.error("Error: seed must be a 64-character hex string");
  process.exit(1);
}
if (poolIds.length === 0) {
  console.error("Error: pool must contain at least one ID");
  process.exit(1);
}
if (isNaN(winnerCount) || winnerCount < 1 || winnerCount > poolIds.length) {
  console.error(`Error: winners must be between 1 and ${poolIds.length}`);
  process.exit(1);
}

const result = verifyPick(seed, poolIds, winnerCount);

console.log("── Pickr Verification ──");
console.log();
console.log(`Seed:        ${seed}`);
console.log(`Pool size:   ${poolIds.length}`);
console.log(`Pool hash:   ${result.poolHash}`);
console.log(`Winners:     ${result.winners.join(", ")}`);

if (args["expected-hash"]) {
  const match = args["expected-hash"] === result.poolHash;
  console.log();
  console.log(`Expected:    ${args["expected-hash"]}`);
  console.log(`Match:       ${match ? "YES" : "NO — MISMATCH"}`);
  process.exit(match ? 0 : 1);
}
