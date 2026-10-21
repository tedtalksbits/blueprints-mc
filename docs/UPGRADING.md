# Upgrading to a new Minecraft version (26.3 and later)

This is the checklist for moving Blueprints to a new Minecraft release. It is written for whoever does the work, human or agent. Read it all before changing anything.

## What "upgrading" means here

Blueprints has two halves, and they upgrade very differently.

| Half | Talks to | Breaks on a new Minecraft version? | Typical work |
|---|---|---|---|
| Paper plugin (`server-plugin/`) | Paper's public Bukkit API, which is kept stable across releases | Rarely. `api-version: '26.2'` in `plugin.yml` is a *minimum*, so the 26.2 jar normally loads on 26.3 unchanged | Bump the Paper dependency, rebuild, run the tests and self-test, review new blocks |
| Fabric client mod (`fabric-client/`) | Minecraft's own internal classes (rendering, GUI, input, networking) | **Every minor release.** `fabric.mod.json` pins `"minecraft": "~26.2"` (26.2.x only), and Mojang renames and reshapes the internal classes between releases | Bump the version pins, fix compile errors in the Minecraft-facing files, run the game and look |

The two halves talk over our own protocol (`docs/PROTOCOL.md`), which is unrelated to the Minecraft version. So the server can move to 26.3 while players are still on a 26.2 client, or the other way round, as long as both sides speak the same `PROTOCOL` number. Players can only join a 26.3 server with a 26.3 game, though, so in practice the mod needs a 26.3 build when the server moves.

**Do the plugin first.** It decides whether the server can upgrade at all. The mod is optional for players: without it they still have `/blueprint` commands.

## Where the versions are pinned

| File | Setting |
|---|---|
| `server-plugin/build.gradle.kts` | `io.papermc.paper:paper-api:<version>` (compile and test), MockBukkit (artifact `mockbukkit-v26.2`; the name carries the Minecraft version), Java toolchain (25) |
| `server-plugin/src/main/resources/plugin.yml` | `api-version` (minimum; raise only if you use a new API) |
| `paper-selftest/build.gradle.kts` | its own `paper-api` dependency and Java toolchain |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle version for the plugin build |
| `fabric-client/gradle.properties` | `minecraft_version`, `loader_version`, `loom_version`, `fabric_api_version` |
| `fabric-client/src/main/resources/fabric.mod.json` | `"minecraft": "~26.2"`, `"fabricloader"`, `"java"` |
| `fabric-client/gradle/wrapper/gradle-wrapper.properties` | Gradle version that Loom needs |
| `run/local-paper/` | the test server's Paper jar (`paper-<mc>-<build>.jar`) |

Get exact versions from https://fabricmc.net/develop (Loader, Loom, Fabric API for the target version) and https://papermc.io/downloads/paper (Paper builds). Use only stable releases.

## Plugin upgrade steps

1. Update the `paper-api` versions in both `build.gradle.kts` files to the new stable build, and MockBukkit to a release for that Paper version (https://github.com/MockBukkit/MockBukkit/releases). If MockBukkit has not caught up yet, its unimplemented calls show up as *skipped* tests, not failures, so count the skips.
2. `gradlew build` from the repo root. Compile errors here are rare. If one appears, check Paper's changelog for the renamed API.
3. Run the real-server self-test. Copy `server-plugin/build/libs/server-plugin-*.jar` and `paper-selftest/build/libs/paper-selftest-*.jar` into a throwaway Paper server's `plugins/` (fresh world), optionally put `.litematic` files in `plugins/BlueprintsSelfTest/litematics/`, and start it with `java -Dblueprints.selftest.exit=true -jar paper-<version>.jar --nogui`. It stops itself and writes `plugins/BlueprintsSelfTest/report.txt`; the log says `SELFTEST PASSED`. It checks the plugin channels, transforms, exact placement, and litematic import on a real Paper server, and writes a **block census**: every block type, sorted into supported and refused, with reasons.
4. **Diff the census against the previous version.** New Minecraft blocks land in one of three places:
   - Supported automatically, because they are plain full blocks, stairs, slabs, and so on. Check that they rotate correctly if they have a facing.
   - Refused as "not in the building palette yet". Decide whether to add them to `MaterialCost` (and `PlanTransformer` if they have a direction or shape).
   - Refused as "stores contents or data". Leave them refused unless they can be placed empty, like campfires, barrels, and signs (`MaterialCost.placedEmpty`).
5. Bump the plugin `version` and update the version line in `README.md`.

New block families must extend `MaterialCost` (cost and normalization) and `PlanTransformer` (rotation and mirroring) together, with tests in `PaletteTest` / `PlanTransformerTest`.

## Client mod upgrade steps

Only these files touch Minecraft or Fabric, so they are the only ones expected to break:

| File | Uses |
|---|---|
| `BlueprintsClient.java` | entrypoint, tick events, HUD and world-render registration, networking registration |
| `Keys.java` | key mappings and categories |
| `Actions.java` | player position and facing, crosshair raycast, chat messages |
| `Compat.java` | Iris detection (by reflection) |
| `net/Net.java`, `net/RawPayload.java` | custom payload types and send/receive |
| `render/PreviewRenderer.java` | world rendering of the preview (the most version-sensitive file) |
| `ui/*.java` | screens, buttons, text fields, HUD drawing |

These are pure Java and should not change: `model/Transform.java`, `model/Placement.java`, `net/Wire.java`, `net/Messages.java`, `ClientState.java`, `Uploader.java`. Their tests (`PlacementTest`, `MessagesTest`) must keep passing unchanged. If one needs a change, the protocol or geometry changed, which is a separate decision.

Steps:

1. Update `fabric-client/gradle.properties`, `fabric.mod.json` (`~26.3`), and the Gradle wrapper if the new Loom requires it.
2. `cd fabric-client && gradlew build`. Fix each compile error by finding the new name in the Minecraft sources. Loom attaches them: `gradlew genSources`, or browse in the IDE. 26.x is unobfuscated, so names are Mojang's own. Areas that changed during 26.x and are likely to move again:
   - GUI drawing: `GuiGraphicsExtractor`, `Screen.extractRenderState`. Never call `extractBackground` yourself; the game crashes with "blur once per frame".
   - Current screen: `minecraft.gui.screen()` / `setScreen()`.
   - World rendering: `LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES`, `submitNodeCollector().submitCustomGeometry(...)`, `RenderTypes.debugFilledBox()`.
   - HUD: `HudElementRegistry`. Keys: `KeyMappingHelper`, `KeyMapping.Category`.
   - Networking: `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`, `ClientPlayNetworking`.
3. Prefer Fabric API events over mixins. The mod currently has **no mixins**, which is the main reason upgrades stay small. Keep it that way unless there is no alternative.
4. Run the game against the upgraded test server and walk `docs/TEST_PLAN.md` tests 1–9 at minimum: menu opens, preview draws with correct shapes and colors, move/rotate/mirror, red obstruction, HUD panel, start, progress, stop. The preview renderer can compile and still draw nothing, so you have to look.
5. Bump the mod `version` in `gradle.properties` and name the jar with the Minecraft version when publishing, for example `blueprints-client-0.2.0+26.3.jar`.

## Protocol changes

Change `ClientBridge.PROTOCOL` (plugin) and `Messages.PROTOCOL` (client) together, and only when a message's bytes change. A Minecraft upgrade alone is not a reason to bump it. On a mismatch the client shows "needs a different version" instead of misbehaving.

## Done when

- Plugin tests and client tests pass, and the self-test passes on the new Paper.
- The census diff is reviewed, and new blocks are either supported or deliberately refused.
- The test plan has been walked in a real client on the new version.
- `README.md` names the new versions, and the jars are rebuilt.
