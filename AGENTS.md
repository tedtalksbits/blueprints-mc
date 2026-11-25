# Blueprints: notes for agents

Start here, then read `README.md` (what it does, commands, the client mod) and the `docs/` it points to:
`ARCHITECTURE.md` (boundaries and V1 decisions), `PROTOCOL.md` (client-server messages), `ROADMAP.md` (what's done
and next), `TESTING.md` and `TEST_PLAN.md` (how it's checked), `UPGRADING.md` (new Minecraft versions, the real-Paper
self-test). The original kickoff brief is in the owner's central folder:
`C:\Users\tedan\Documents\software\Claude outputs\blueprints-mc\BLUEPRINTS_CODEX_KICKOFF.md`.

## What this is

"Design in Creative. Earn it in Survival." A Paper plugin for the owner's (tedtalksbits) small survival server that
builds a blueprint gradually from real materials in a Builder's Chest, with Creative capture, `.litematic` and
Sponge `.schem` import, and an optional Fabric client mod that previews, moves, rotates and mirrors a blueprint.
Standalone: its own repo and versions; Logic Blocks, Shulker Styles and Glow-Fed Frogs copied its patterns, not the
other way round.

- `server-plugin/`: the Paper plugin (Java 25). Plugin 0.6.0 (`build.gradle.kts`).
- `fabric-client/`: the Fabric client mod, its own Gradle build. Client 0.4.0, Minecraft 26.3 only.
- `paper-selftest/`: development-only plugin that checks the plugin on a real Paper server (`docs/UPGRADING.md`).
- `run/local-paper/`: the owner's local Paper 26.3 test server (`build-and-install.cmd`, `start.cmd`). Other projects'
  dev loops install into it too.

## Status

See `docs/ROADMAP.md`: M0 (server construction) and M1 (capture, import, client preview) are implemented; the
roadmap lists what passed live and what is only unit-tested. Paper/Minecraft 26.3 (`api-version: 26.2`, so the plugin
also loads on 26.2).

## Working rules

- Commit locally; **the owner pushes** to GitHub. Agents commit as `Claude <noreply@anthropic.com>`, per commit with
  `git -c user.name=Claude -c user.email=noreply@anthropic.com commit ...`; don't change the repo's git config, the
  owner commits as himself, and never rewrite his commits.
- Reports: short and structured. Anything needing the owner's decision goes at the top, marked ⚠️. No
  over-explaining, no "•" separators.
- Verify on a real Paper server before claiming something works; MockBukkit is for unit tests only (it doesn't
  simulate physics, saves or protection plugins). Say plainly what was and wasn't seen in a real game.
- Kickoffs, mockups and draft art live in the owner's central folder
  `C:\Users\tedan\Documents\software\Claude outputs\<project>\`, never inside this (or any) project's repo. Copy
  only what the project needs into the repo.

## Building and testing

- Plugin: `gradlew build` at the root (JDK 25, Gradle 9.1.0 wrapper). Jar: `server-plugin/build/libs/server-plugin-<v>.jar`;
  test report: `server-plugin/build/reports/tests/test/index.html`.
- Client: `cd fabric-client && gradlew build` (it needs a newer Gradle than the plugin).
- Real-Paper self-test: `docs/UPGRADING.md` step 3 (`-Dblueprints.selftest.exit=true`, report in
  `plugins/BlueprintsSelfTest/report.txt`, log says `SELFTEST PASSED`).
- Dev loop: `run\local-paper\build-and-install.cmd` builds both and installs them; client jars go to `run/client-mods/`.
- Real-Paper self-test in one command (Linux, Git Bash, CI): `ci/run-selftest.sh <paper.jar> <new-folder>`; offline add
  `PAPER_SEED_DIR=<server folder>`. It must end `SELFTEST PASSED`.
- CI: `.github/workflows/ci.yml` (plugin, self-test on pinned Paper 26.3-211, client); releases:
  `.github/workflows/release.yml`, which reruns CI and refuses a tag that isn't `v<plugin version>`. Details in
  `docs/CI.md`. Before telling the owner a release is ready, check `build.gradle.kts` has the tag's version, and check
  `git tag` / `git status -sb` rather than your own notes about what has been released.

## Status (newest first; each says how it was checked)

- **Plugin 0.6.0, client 0.4.0 (2026-11-24), building into water.** Unit tests and the real-Paper self-test (every
  supported block in real water against a dry control: 663 stay put, 60 refused really wash away). **Not seen in a
  game yet**: `docs/TEST_PLAN.md` tests 8b-8c. Not released (latest tag `v0.5.0`).
- **CI runs the real-Paper self-test (2026-11-24).** The script was run offline against Paper 26.3-211; the workflow
  itself has not run on GitHub yet.
- **0.5.0, single-use Builder's Chest** (released `v0.5.0`): see the commit and `docs/TEST_PLAN.md`.
- **M0/M1** (construction, capture, imports, client preview, uploads, ownership): passed live on Paper 26.2 with a real
  client in October 2026 (`docs/ROADMAP.md`); 26.3 support was version pins plus two client API changes (`0381c66`).

## Decisions already made (don't reopen without asking)

| Decision | Why |
|---|---|
| Builds are funded from a crafted Builder's Chest; one chest per build, at most 2 builds per player, 16 active | Not free; running several builds costs more (owner) |
| The Builder's Chest becomes a normal chest when its build completes (`builders-chest.single-use`) | 0.5.0 |
| Preview draws block shapes in map colours, not textures | Owner: "fine for now" |
| Size limits are configurable (`limits.*`), with hard ceilings | Owner: fine "as long as I can easily configure" |
| Campfires, barrels and signs are built, always empty | Common building blocks; nothing can be duplicated |
| Uploads: an allow-everyone switch, per-player cap, owners and operators delete | Owner chose "B" |
| Water, seagrass and bubble columns don't block a build; blocks that can hold water go in waterlogged | Owner wants to build boats in water |
| Builds never remove water, so hulls come out flooded | Owner: draining would let players empty the ocean |
| No mixins in the client | Keeps Minecraft upgrades small (`docs/UPGRADING.md`) |

## Gotchas found the hard way

- Two Blueprints jars in a server's `plugins/` (say 0.4.0 and 0.5.0) make Paper refuse both ("Ambiguous plugin name").
  `run\local-paper\build-and-install.ps1` now installs a single `plugins\Blueprints.jar`.
- Paper's `Material.isSolid()` is the `minecraft:blocks_motion` tag; the client mirrors it with that tag.
- Slabs, stairs, fences and signs placed dry into water become waterlogged once the water updates, so the build
  places them waterlogged, and "already built" checks ignore the waterlogged flag (server and client).
- `getState().update(true, true)` on an unchanged block does nothing; to make water flow in a test, re-place a
  neighbouring water block with physics.
- MockBukkit's block data differs from Paper's in places (ladders and rails can hold water in Paper); check block
  behaviour in the real-Paper self-test, not only in unit tests.

## Open items

1. Owner to run `docs/TEST_PLAN.md` 8b-8c in a game (water), then release v0.6.0.
2. Agreed next (2026-11-24): Builder's Chest in a creative tab with a self-test step, then a custom item texture
  (drafts in `Claude outputs\blueprints-mc\`). Later, maybe: a different look for the placed chest (block entity
  renderer, likely a mixin; read Shulker Styles first).
3. Possible: move the dev-loop script into the repo (`run/` is gitignored today; other projects use
  `blueprints-mc\run\local-paper` and `.tools`, so keep that layout).
