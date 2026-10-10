# Blueprints

**Design in Creative. Earn it in Survival.**

A Paper 26.2 plugin that builds a small, server-installed blueprint gradually from real materials in a Builder's Chest. This first slice focuses on exact block states and survival accounting; the Fabric client and `.litematic` importer come later.

## Build

Install **JDK 25**, set `JAVA_HOME`, then run the checked-in Gradle 9.1.0 wrapper:

```powershell
.\gradlew.bat build
```

```sh
./gradlew build
```

The first build downloads Gradle and Maven dependencies. Gradle's distribution is SHA-256 pinned, and the Paper API is pinned to `26.2.build.130-stable`. The plugin JAR is `server-plugin/build/libs/server-plugin-0.2.0-SNAPSHOT.jar`. Test reports are in `server-plugin/build/reports/tests/test/index.html`.

## Try the vertical slice

Use a separate Paper 26.2 test server running Java 25. Stop it, copy the JAR into `plugins/`, and start it normally. Blueprints installs `plugins/Blueprints/blueprints/orientation-demo.yml` on its first run. Existing files are preserved; remove an older demo file while stopped if you want the expanded example installed.

1. As an operator in **Survival**, place a **single chest** next to an empty 5x5 area. The example occupies offsets `(0..4, 0..2, 0..4)` from your chosen origin.
2. Run `/blueprint materials orientation-demo`.
3. Fund the chest with **25 stone bricks, 4 oak stairs, 3 oak logs, 4 oak slabs, 1 oak trapdoor, and 1 oak door**. Use ordinary items; renamed or otherwise customized items are not consumed.
4. Look at the chest from within six blocks and run `/blueprint start orientation-demo <x> <y> <z>`. The origin is the bottom corner of the new platform, so choose a Y level whose destination cells are air.
5. Stay nearby in Survival. A construction step runs every half-second by default. Both door halves appear together for one door item; the double slab consumes two slab items.
6. Run `/blueprint status` to inspect progress and pause reasons. Missing materials, unloaded chunks, obstruction, a missing/double chest, and an unavailable owner pause work. These conditions resume automatically when resolved.
7. `/blueprint cancel <job-id-prefix>` stops a job. Placed blocks remain and spent items are not refunded.

The demo has **38 blocks in 37 paid steps**. It exercises four stair directions (including upside-down), X/Y/Z logs, bottom/top/double slabs, a top/open trapdoor, and a right-hinged door.

## What is implemented

- Canonical Minecraft block states, validated before a blueprint can be used.
- Immutable construction snapshots: editing/deleting the source blueprint does not change an existing job.
- Bottom-up ordering, grouped doors, correct slab costs, and a material list command.
- Non-overwriting placement; already matching complete steps are accepted without payment.
- Global round-robin attempt budget, actual destination-chunk checks, world height/border checks, and bounded job/blueprint sizes.
- Main-thread inventory consumption and placement, with atomic job-file replacement.
- Normal `/stop` restart recovery, with automatic retries for ordinary pauses.
- Fail-closed crash recovery: unfinished jobs from an unclean session require inspection and cancellation, never automatic replay.
- 25 automated tests covering plan validation, materials, construction, persistence, scheduling, and failure behavior.

## Permissions and scope

`blueprints.use` defaults to everyone (list/materials/status and cancellation of one's own job). **`blueprints.build` defaults to operators** and is required both to start and to continue construction. `blueprints.admin` lets operators inspect/cancel others' jobs. Owners must stay online in Survival in the same world, within 128 blocks of each destination. Builds must stay within 128 blocks of their chest.

V1 is for trusted test builders. It does **not** integrate claim protection or CoreProtect, emit synthetic player-placement events, or establish ownership of arbitrary chests. Granting build permission therefore allows use of reachable chests and placement in otherwise protected areas. Do not grant it broadly on the existing multiplayer server yet.

Supported blocks are a conservative palette of common masonry, planks/logs/wood, wool/concrete/glass/terracotta, stairs, slabs, trapdoors, and doors. Exact rules are in `MaterialCost.java`. Waterlogged/powered states, containers, entities, fluids, command blocks, spawners, falling blocks, pistons/observers, and other special mechanisms are unsupported. No NBT/container contents are imported. No rotation, mirroring, hologram, upload, or client mod is included yet.

See [the acceptance test](docs/TESTING.md), [architecture and recovery semantics](docs/ARCHITECTURE.md), and [roadmap](docs/ROADMAP.md).
