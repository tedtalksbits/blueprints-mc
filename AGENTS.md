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
- CI: `.github/workflows/build.yml`; releases: `.github/workflows/release.yml`.
