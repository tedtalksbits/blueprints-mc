# Blueprints — Codex Kickoff

## Project

Repository: `https://github.com/tedtalksbits/blueprints-mc`

Blueprints is a Minecraft survival-building system inspired by the useful parts of Litematica, but designed around a different gameplay loop.

The core problem we want to solve is simple:

- The player already designed the build in Creative.
- The player already gathered the required materials in Survival.
- Repeating the entire build block-by-block in Survival is tedious.
- Litematica knows the exact block states/orientations, but its normal Easy Place workflow still leaves a lot of orientation-sensitive placement work to the player.
- We do **not** want instant WorldEdit-style copy/paste.

The goal is to make transferring a build from Creative into Survival feel like a legitimate construction process rather than either tedious manual labor or a cheat command.

## Product Idea

A player should be able to bring a blueprint into a Survival world, position it, fund it with real materials, and start construction.

A typical flow might look like:

1. Design a structure in Creative.
2. Export or select that structure as a blueprint.
3. In Survival, preview the blueprint as a hologram.
4. Position / rotate / mirror it.
5. Associate a chest or other material source with the project.
6. Supply the real blocks required by the blueprint.
7. Start construction.
8. The structure is built gradually over time using the exact block states from the blueprint.
9. Construction pauses naturally when required materials are missing and resumes when they are supplied.

The exact UX and implementation are open for improvement.

The important idea is:

> Creative is where the player designs. Survival is where the player earns and funds the build.

## Why This Exists

The biggest pain point is **orientation-sensitive blocks**.

The blueprint already knows that a block is, for example:

- an upside-down stair facing west
- a log on the X axis
- a trapdoor with a particular facing/half/open state
- a slab in the top half
- a door with a specific hinge and facing
- an observer or piston facing a particular direction

The construction system should preserve these exact block states instead of forcing the player to manually reproduce them through vanilla placement mechanics.

The second major pain point is repetition. If the player spent an hour designing a house in Creative and then gathered 2,000 legitimate materials in Survival, requiring another hour of repetitive block placement is not particularly interesting gameplay.

## Current Environment

The Minecraft server currently runs:

- Paper 26.2
- Dockerized server
- Survival / Hard difficulty
- Java + Bedrock crossplay through Geyser/Floodgate
- Chunky
- CoreProtect

This project should avoid destabilizing the existing server.

The repo already contains an early scaffold created during initial planning. Treat it as a starting point, not sacred architecture. Inspect it first and freely restructure or replace parts of it if there is a stronger implementation.

## Direction

The likely long-term shape is:

- a Paper server plugin for authoritative construction, material consumption, persistence, permissions, and exact block-state placement
- a Fabric client mod for blueprint loading, hologram rendering, positioning, materials/progress UI, and a pleasant player experience
- support for `.litematic` as an import format so players can use the existing schematic ecosystem

That architecture is not mandatory if there is a clearly better approach. Use engineering judgment.

The base experience should eventually feel good enough that a player does not need Litematica installed separately.

## Survival Construction

Construction should feel like a process, not a paste command.

A useful model would be a **Builder's Chest** or similar material source:

- the build consumes the actual required blocks
- materials are removed as they are used
- if materials run out, construction pauses rather than failing
- adding the missing materials allows construction to continue
- progress survives server restarts

An optional secondary labor cost such as emeralds may make sense later, but the real blocks should remain the primary cost.

Construction should happen gradually. The exact pacing and scheduling mechanism are yours to design.

It would be nice if construction visually makes some sense rather than simply iterating through arbitrary coordinates. For example, foundations and lower portions generally appearing before roofs would feel better, but do not over-engineer this before the basic system works.

## Safety / Survival Integrity

Use reasonable safeguards for a multiplayer Survival server.

The system should not become an item duplication mechanism or an unrestricted griefing tool.

Examples of things that should be considered carefully:

- overwriting existing player builds
- inventories inside schematic containers
- entities
- command blocks
- spawners
- fluids
- redstone / neighbor updates during partial construction
- interrupted or cancelled builds
- server restart recovery
- duplicate material consumption
- unloaded chunks

Choose sensible V1 behavior and document it. It is fine to leave advanced cases unsupported initially rather than building a huge rules engine.

## First Goal

Build a convincing end-to-end vertical slice.

The first milestone should prove that we can:

- represent a small blueprint
- preserve exact Minecraft block states
- position it in a world
- associate a Survival material source with it
- consume the correct materials
- build it progressively
- pause when materials are unavailable
- resume when materials appear
- survive a server restart
- correctly place orientation-sensitive blocks

A deliberately small orientation test structure containing stairs, logs, slabs, trapdoors, doors, etc. would be useful.

Do not spend the first milestone recreating all of Litematica.

Once the server-side construction mechanic is solid, move toward the player-facing client experience.

## Client Experience

When the client portion begins, prioritize clarity and ease of use.

Litematica's power is useful, but its UX is one of the reasons this project exists.

A player should eventually be able to do things like:

- open a blueprint
- see it clearly
- change hologram opacity directly
- rotate / move / mirror it without digging through menus
- see the material requirements
- understand what is missing
- start construction
- see construction progress
- understand why a project is paused

Avoid building an interface around dozens of obscure hotkeys unless a hotkey is genuinely the best interaction.

A simple, modern UI is more important than feature count.

## `.litematic` Compatibility

Supporting `.litematic` files is desirable because there is already a large ecosystem of schematics.

Treat `.litematic` as an import format, not necessarily the internal domain model.

Normalize imported schematics into a clean internal representation so the rest of the project is not tightly coupled to Litematica's file structure.

Other formats such as `.schem` could be added later.

## Engineering Expectations

This is intended to become a real project, not a throwaway prototype.

Please:

- inspect the existing repo before making assumptions
- keep the architecture understandable and extensible
- separate Minecraft-version-specific integration from core blueprint/construction logic where practical
- write tests around important pure logic and persistence behavior
- keep configuration understandable
- document how to build and run the project
- avoid premature abstraction and unnecessary infrastructure
- prefer working vertical slices over large amounts of speculative framework code

You have freedom to make implementation decisions.

If the existing scaffold is poor, change it.

If a different data model or project layout is better, use it.

If something in this brief would produce a bad implementation, make the better engineering choice and document why.

## Workflow

Work directly in the GitHub repository.

Use a feature branch for meaningful work, make useful commits, and push the branch so it can be reviewed and tested.

Do not wait for approval for every small implementation decision. Move the project forward, test what you build, and leave the repo in a runnable state.

At useful checkpoints, summarize:

- what now works
- what you changed architecturally
- how to test it
- known limitations
- what you recommend building next

## Definition of a Successful First Session

At the end of the first substantial implementation pass, we should be much closer to being able to install a Paper plugin on a test server and watch a small Survival-funded blueprint construct itself with correct block orientations.

Do the necessary research into current Paper/Fabric APIs and Minecraft 26.2 behavior rather than relying on outdated examples.

Start by inspecting the repository and establishing the current state, then proceed with the strongest implementation path.
