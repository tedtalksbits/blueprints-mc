# Blueprints

**Design in Creative. Earn it in Survival.**

A Paper 26.2 plugin that builds a small, server-installed blueprint gradually from real materials in a Builder's Chest. This first slice focuses on exact block states and survival accounting; `.litematic` import and a Fabric client preview are supported.

## Build

Install **JDK 25**, set `JAVA_HOME`, then run the checked-in Gradle 9.1.0 wrapper:

```powershell
.\gradlew.bat build
```

```sh
./gradlew build
```

The first build downloads Gradle and Maven dependencies. Gradle's distribution is SHA-256 pinned, and the Paper API is pinned to `26.2.build.130-stable`. The plugin JAR is `server-plugin/build/libs/server-plugin-0.3.0-SNAPSHOT.jar`. Test reports are in `server-plugin/build/reports/tests/test/index.html`.

## Try the vertical slice

Use a separate Paper 26.2 test server running Java 25. Stop it, copy the JAR into `plugins/`, and start it normally. Blueprints installs `plugins/Blueprints/blueprints/orientation-demo.yml` on its first run. Existing files are preserved; remove an older demo file while stopped if you want the expanded example installed.

1. As an operator in **Survival**, craft a **Builder's Chest** (see below) or run `/blueprint givechest`, and place it next to an empty 5x5 area. The example occupies offsets `(0..4, 0..2, 0..4)` from your chosen origin.
2. Run `/blueprint materials orientation-demo`.
3. Fund the chest with **25 stone bricks, 4 oak stairs, 3 oak logs, 4 oak slabs, 1 oak trapdoor, and 1 oak door**. Use ordinary items; renamed or otherwise customized items are not consumed.
4. Within 16 blocks of the chest, run `/blueprint start orientation-demo <x> <y> <z>`. Your nearest free Builder's Chest funds the build. The origin is the bottom corner of the new platform, so choose a Y level whose destination cells are air.
5. Stay nearby in Survival. A construction step runs every half-second by default. Both door halves appear together for one door item; the double slab consumes two slab items.
6. Run `/blueprint status` to inspect progress and pause reasons. Missing materials, unloaded chunks, obstruction, a missing/double chest, and an unavailable owner pause work. These conditions resume automatically when resolved.
7. `/blueprint cancel <job-id-prefix>` stops a job. Placed blocks remain and spent items are not refunded.

The demo has **38 blocks in 37 paid steps**. It exercises four stair directions (including upside-down), X/Y/Z logs, bottom/top/double slabs, a top/open trapdoor, and a right-hinged door.

## Capture your own Creative build

Operators have `blueprints.capture` by default. In Creative, stand near the build and select its inclusive corners:

```text
/blueprint capture cabin 100 64 100 104 68 104
/blueprint materials cabin
```

Replace those sample coordinates with your build's coordinates. Either corner order works. The selection can be up to 65 blocks per side with up to 4,096 non-air blocks, entirely loaded and within 128 blocks of you. Air is skipped. If the selection contains blocks Blueprints can't build, capture lists every such type with its count and the first position, so you can remove them and place them by hand later. Selections cutting a door or tall plant in half are rejected. Capture only reads the design; it does not remove blocks or copy inventories/entities.

The origin is the selection's **minimum X, Y, Z corner**, including any selected air margin. The blueprint is immediately available and saved as `plugins/Blueprints/blueprints/blueprint-cabin.yml`. IDs use lowercase letters, digits, underscores, and hyphens, up to 64 characters. Existing IDs/files cannot be overwritten. To transfer to another server, copy that YAML into its Blueprints `blueprints/` directory while the destination server is stopped, then start it.

Switch to Survival, move to your destination and fund a Builder's Chest. Within 16 blocks of it, use:

```text
/blueprint start cabin 200 64 200 90 x
```

This mirrors the design's local X axis, then rotates it 90 degrees clockwise when viewed from above. Both arguments are optional: rotation defaults to `0`, mirror to `none`. Rotation choices are `0`, `90`, `180`, `270`; mirror choices are `none`, `x`, `z` (provide rotation before mirror).

**The origin stays fixed.** Rotation/mirroring can extend the build into negative local X/Z coordinates; the command does not shift the result back into a positive bounding box. For example, local `(2, 0, 3)` becomes `(-3, 0, -2)` with `90 x`. Facing, log axes, stair corner handedness, and door hinges transform with the geometry; top/bottom halves and open states are preserved. Materials do not change. Each job stores the resulting plan, so it resumes with the same placement after a normal restart.

## What is implemented

- Creative capture and origin-based rotation/mirroring for the supported building palette.
- Canonical Minecraft block states, validated before a blueprint can be used.
- Immutable construction snapshots: editing/deleting the source blueprint does not change an existing job.
- Bottom-up ordering, grouped doors, correct slab costs, and a material list command.
- Non-overwriting placement; already matching complete steps are accepted without payment.
- Global round-robin attempt budget, actual destination-chunk checks, world height/border checks, and bounded job/blueprint sizes.
- Main-thread inventory consumption and placement, with atomic job-file replacement.
- Normal `/stop` restart recovery, with automatic retries for ordinary pauses.
- Fail-closed crash recovery: unfinished jobs from an unclean session require inspection and cancellation, never automatic replay.
- 61 automated tests, plus a real-Paper self-test plugin (`paper-selftest`, development only), covering plan validation, materials, construction, persistence, scheduling, failure behavior, and Builder's Chest ownership.

## Fabric client (preview and place)

The optional client mod for **Minecraft Java 26.2 + Fabric** adds a see-through preview you can move, rotate, and mirror before starting a build. The server stays in charge: the mod only proposes a blueprint, a spot, and an orientation, and the plugin runs the same checks as `/blueprint start`. Players without the mod keep using commands.

Install: Fabric Loader for 26.2, plus `fabric-api-0.161.0+26.2.jar` and `blueprints-client-0.1.0.jar` in the instance's `mods` folder. `run/local-paper/build-and-install.cmd` builds the client too and copies both jars to `run/client-mods/`.

| Key (rebindable) | Action |
|---|---|
| `B` | Open Blueprints: choose a blueprint, see needed vs. in-chest materials, opacity, start, your builds, cancel |
| Arrow keys | Move the preview away / closer / left / right, relative to where you face (hold to repeat) |
| `Page Up` / `Page Down` | Move up / down |
| `G` | Move the preview to the block under your crosshair |
| `R` / `M` | Rotate 90° clockwise (turns in place) / mirror |
| `Enter` | Start the build here (uses your nearest free Builder's Chest) |
| `Backspace` | Close the preview |

Preview keys only act while a preview is shown, and the on-screen panel always lists the current bindings. The preview draws each block's real shape (stairs, slabs, doors, walls, fences show their orientation) in its map color; blocks already built correctly disappear, and anything in the way is drawn red. While builds run, the panel shows progress and why a build is paused.

Build the client separately (it needs a newer Gradle than the plugin): `cd fabric-client` then `gradlew build`. The wire protocol is in [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Import a Litematica schematic

Operators (`blueprints.import`) can turn a `.litematic` file into a blueprint:

1. Copy the file into `plugins/Blueprints/import/` (the server can stay running).
2. Run `/blueprint import <file> [id]`. The `.litematic` extension is optional, and tab completion lists the folder. Without an id, one is made from the file name.
3. Check `/blueprint materials <id>`, then build it like any other blueprint.

Only block states are imported; entities and container contents are ignored. Refused block types (see Supported blocks below) are **skipped and listed** in the import message so you can place them by hand. States are adjusted the same way as Creative capture. All regions of a multi-region schematic are combined, and the origin is the combined minimum corner. Current limits are 4,096 supported blocks and 65 blocks per side; larger schematics are rejected with their size.

## Builder's Chest

Builds draw only from the starter's own **Builder's Chest**, a crafted chest bound to whoever places it. Default recipe: a chest surrounded by 4 iron ingots (sides) and 4 paper (corners). Change it under `builders-chest.recipe` in `config.yml`, then restart.

- `/blueprint start` uses your nearest Builder's Chest within `builders-chest.search-radius` (default 16) that isn't already funding an active job. One chest funds one job; each player can run up to 2 jobs at once and the server 16 (`construction.max-active-jobs-per-player`, `construction.max-active-jobs`).
- Plain chests and other players' Builder's Chests are never used.
- Breaking it returns the Builder's Chest item; contents drop as normal. Explosions don't destroy it.
- It can't merge into a double chest. Leave a gap or sneak while placing.
- Operators can get one with `/blueprint givechest [player]`.

Jobs started before this feature keep using their original chest.

## Permissions and scope

`blueprints.use` defaults to everyone (list/materials/status and cancellation of one's own job). **`blueprints.build` defaults to operators** and is required both to start and to continue construction. `blueprints.admin` lets operators inspect/cancel others' jobs. Owners must stay online in Survival in the same world, within 128 blocks of each destination. Builds must stay within 128 blocks of their chest.

V1 is for trusted test builders. It does **not** integrate claim protection or CoreProtect, emit synthetic player-placement events, or establish ownership of arbitrary chests. Builds can only draw from the starter's own Builder's Chest, but granting build permission still allows placement in otherwise protected areas. Do not grant it broadly on the existing multiplayer server yet.

Supported blocks: about 785 block types on Paper 26.2, including all plain full blocks, stairs, slabs, walls, fences, gates, panes, bars, doors, trapdoors, logs and pillars, leaves, glass, carpets, torches, lanterns, chains, ladders, buttons, pressure plates, levers, flowers, saplings and tall plants, observers, and pistons. Rules are in `MaterialCost.java`. Refused: anything that stores contents or data (chests, furnaces, signs, banners, beds, campfires, heads), technical blocks (command blocks, spawners, barriers), fluids, rails and redstone wiring, crops and vines, and multi-count blocks (candles, sea pickles, petals, snow layers). Waterlogged and powered states are captured dry and unpowered, leaves are kept from decaying, and respawn anchors and composters are captured empty so a build never pays out stored materials.

See [the acceptance test](docs/TESTING.md), [architecture and recovery semantics](docs/ARCHITECTURE.md), and [roadmap](docs/ROADMAP.md).
