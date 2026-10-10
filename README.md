# Blueprints

A survival-first construction system for Minecraft Paper.

**Design in Creative. Earn it in Survival.**

This repository starts with the server-side construction engine because that is the core gameplay mechanic: a blueprint has exact block states, a Builder's Chest pays the material cost, and the structure is constructed gradually rather than pasted instantly.

## Current vertical slice (0.1)

- Paper 26.2 target.
- Blueprint definitions contain exact Minecraft block-data strings, including orientation.
- Builder's Chest supplies one real item per placed block.
- Construction runs gradually on the main server tick loop.
- Jobs persist to `plugins/Blueprints/jobs.yml` and resume after restart.
- Jobs pause if materials are missing, the chest disappears, chunks unload, or the destination is obstructed.
- Existing blocks that already match the blueprint are accepted without charging twice.
- By default the plugin refuses to overwrite existing non-air blocks.
- Optional emerald labor fee is supported but disabled by default.

The included `orientation-demo` exists specifically to verify stairs are placed north/east/south/west using their exact block states.

## Why server-first?

A client-only mod cannot force arbitrary block states on an ordinary Paper server. The server plugin can. That means stairs, trapdoors, logs, slabs, pistons, doors, etc. can be reconstructed exactly as designed rather than relying on click orientation.

## Development command flow

After building and installing the JAR:

1. Put the required Stone Bricks and Oak Stairs into a chest.
2. Stand where you want the blueprint origin.
3. Look directly at the funding chest (within six blocks).
4. Run `/blueprint start orientation-demo <x> <y> <z>`.
5. Watch the structure build over time.
6. `/blueprint status` shows pause/completion state.

The coordinate command is intentionally developer-ish. The client mod will replace it with a ghost preview, move/rotate controls, a Start Construction button, and automatic anchor transmission.

## Build

Requires JDK 25 and Gradle. The project targets `io.papermc.paper:paper-api:26.2-R0.1-SNAPSHOT`.

```bash
./gradlew :server-plugin:build
```

Copy the resulting JAR from `server-plugin/build/libs/` to the Paper server's `plugins/` directory.

## Next milestone

The next milestone is the Fabric 26.2 client mod:

- import `.litematic`
- normalize regions/palettes into our internal Blueprint model
- render a clean ghost preview
- move/rotate/mirror
- choose a Builder's Chest
- show exact material requirements
- send the normalized blueprint + transform to the Paper plugin
- show live progress and missing-material state

See `docs/ARCHITECTURE.md` and `docs/ROADMAP.md`.
