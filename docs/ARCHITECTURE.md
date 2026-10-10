# Architecture and V1 decisions

## Boundaries

`BlueprintDefinition`/`BlueprintBlock` represent the source format. `PlanCompiler` is the Paper-specific boundary: it parses canonical `BlockData`, rejects unsupported states, groups matching door halves, computes costs, and orders placements by Y/X/Z. `MaterialCost` deliberately uses a narrow palette rather than treating every item-bearing block as safe.

`ConstructionPlan`, `ConstructionStep`, and `BuildJob` contain no Bukkit types. A step is one paid unit with one or two destination blocks. A plan is immutable, validates duplicate/bounded coordinates, and holds material totals. A job stores its full plan, owner/world UUIDs, origin/chest coordinates, step cursor, and state. The cursor counts steps, not blocks.

`BuildManager` runs on Paper's main thread. `InventoryUtil` counts eligible items before changing any slot, so a missing two-slab cost cannot partially consume inventory. Eligibility compares with the material's ordinary item stack; custom metadata is excluded. `JobRepository` serializes schema-1 snapshots, forces the temporary file, and atomically replaces `jobs.yml`. It refuses unsupported schemas or malformed jobs rather than skipping funded work.

## One construction cycle

The global budget is `construction.steps-per-cycle` (1..16). Jobs receive round-robin attempts, at most one per job per cycle. A paused job uses an attempt, preventing a large population of blocked jobs from causing unbounded scanning. The interval defaults to 10 ticks.

Each attempt verifies the owner, world, chest chunk, every destination chunk, height/border, and distance before accessing destination blocks. It never force-loads destination chunks. If every block in a step exactly matches the canonical plan, the cursor advances without consuming materials. Otherwise all destinations must be air. A partially existing door pauses; it is never completed for free or charged twice ambiguously. A single chest must exist, with sufficient plain materials. The debit and both door placements occur together on the main thread, then the resulting states are checked before advancing.

No refund/rollback is attempted after a placement exception: a partly successful world change makes that unsafe. Instead the engine stops and requires recovery review. Cancellation also never removes blocks or refunds spent items.

## Persistence and recovery

Before construction begins, the plugin saves `clean-shutdown: false`. Checkpoints are written every 20 active cycles, and on job start/cancellation. On ordinary plugin shutdown it saves the current cursor and `clean-shutdown: true`, unless a storage/placement failure occurred. Paper's normal `/stop` completes world saving; this is the supported restart path.

On a subsequent startup with a dirty marker, every unfinished job becomes `RECOVERY_REQUIRED` and stays there across later clean restarts. Completed/cancelled jobs remain terminal. There is deliberately no force-resume command in V1. An operator should inspect both chest and placed blocks, reconcile any loss/duplication, then cancel the quarantined job. A fresh job can accept intact matching blocks, but only after that manual audit. Partial doors must be resolved manually.

**This is not a cross-file transaction.** Paper saves world blocks, chest contents, and this plugin's file independently. A killed process, disk failure, external rollback, or crash during server shutdown can save these at different moments. Quarantine prevents automatic replay of uncertain steps; it cannot undo Minecraft's own rollback or guarantee zero item loss/duplication across a crash. Do not infer crash atomicity from an atomic YAML write. True durable cross-world/inventory transactions remain future work.

Persisted plans are recompiled on startup and checked against the snapshot, including costs/order. A changed Minecraft registry or invalid snapshot fails startup for operator investigation. Schema-less scaffold `jobs.yml` files are not migrated because they lack stable plans and reliable accounting. Keep a backup, audit existing work, and archive the old file while stopped before installing this version. Never delete/replace an active job file while the server is running.

## Limits and multiplayer policy

- At most 4,096 source blocks, offsets within -64..64, 16 unfinished jobs, 64 total job records, and one unfinished job per owner.
- A full build is within 128 blocks of its source. Height, border, coordinate overflow, overlap with other active plans/chests, and self-overlap with its chest are rejected.
- Only single chests; no double-chest adjacency loads or virtual inventory support.
- Permissions default construction to operators. Owners must remain online, nearby, permitted, and in Survival. Chest contents are shared physical resources; V1 does not establish chest ownership.
- No claim plugin or CoreProtect integration. Bukkit `setBlockData` is not a player placement event. Operators must use a dedicated test area/server.
- Physics is suppressed during placement. Paired door halves are set in one step, but later vanilla updates may change states. V1 does not promise arbitrary floating/unsupported constructions or redstone behavior.
- No imported NBT, entities, containers, command blocks, spawners, waterlogged states, or fluids. No labor fee/overwrite option; these were removed from the scaffold.
- Snapshot serialization is synchronous and bounded, intended for small builds. Larger workloads need profiling and a persistence redesign before raising limits. Completed history can be backed up and archived while the server is stopped, with unfinished jobs handled first.

## Research baseline

- [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/): Minecraft 26.1+ uses `VERSION.build.N-stable` artifact versions instead of `-R0.1-SNAPSHOT`.
- [Paper getting started](https://docs.papermc.io/paper/getting-started/): Java 25 for 26.1+.
- [Paper 26.2 Block API](https://jd.papermc.io/paper/26.2/org/bukkit/block/Block.html): exact block data and the limitations of suppressing physics.
- [Paper 26.2 release notes](https://papermc.io/news/26-2/): current version behavior and world storage context.
- [MockBukkit](https://github.com/MockBukkit/MockBukkit/tree/minecraft/v26.2): test doubles for the 26.2 API. These do not simulate real server physics, disk saves, Geyser, or protection plugins.

The future Fabric client should supply a normalized representation through a versioned, bounded protocol. It will not control accounting or placement. `.litematic` is an import format, not the persisted job model. Client implementation and Fabric-specific API research are intentionally deferred until the server acceptance test succeeds.
