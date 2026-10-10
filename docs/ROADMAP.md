# Roadmap

## M0 — Server construction slice (implemented; core live acceptance passed 2026-10-10)

Pinned Paper 26.2/Java 25 build, Gradle wrapper, validated immutable plans, paired doors, correct slab costs, exact-state placement, material list, owner-bound Builder's Chest funding (one chest per job, 2 active jobs per player), global budget, pause/resume, immediate checkpoints on state changes, clean restart continuation, crash quarantine, and automated regression tests.

Passed live on Paper 26.2: empty/partial funding, all 38 demo states, clean restart with source edits, obstruction and free matching steps, Creative capture with a `90 x` transform, Builder's Chest crafting/binding/merge refusal/break return.

Not yet live-tested (covered by unit tests): chunk unloading, owner leaving/mode change, permission revocation, cancel, forced process kill. Next: verify Geyser commands. Add claim protection and CoreProtect hooks before granting construction permission broadly. Profile persistence limits.

Deferred idea: a floating text display above each Builder's Chest showing job progress (needs entity lifecycle handling).

## M1 — Capture and positioning (implemented); preview next

Creative selection capture and tested rotation/mirroring now support player-created builds without hand-editing YAML. Captures retain the same validation and material rules as the demo. Jobs persist transformed plans.

Next, after live Paper acceptance: a Fabric client with blueprint browsing, hologram opacity, simple movement controls, and material/progress displays. Research the current Fabric 26.2 APIs at implementation time.

## M2 — Litematic import and bounded protocol

Read schematic regions/palettes into the normalized model. Keep format parsing separate from construction. Add a versioned handshake, capability negotiation, bounded uploads, server validation, and owner/region checks. Do not import NBT or entities implicitly.

## M3 — Wider survival support

Expand the material/state registry with tests for each new family, add support/physics planning, other inventory sources, better history management, and clearer player pause controls. Redstone, fluids, beds, block entities, and moving mechanisms require deliberate semantics before support.

## M4 — Production hardening

Protection/audit integrations, persistent inventory/world transaction strategy, shutdown/crash fault injection on real Paper, multi-job performance profiling, large-blueprint storage, and recovery tooling. Keep the server authoritative throughout.

## Non-goals

No instant paste, free Survival materials, imported inventory contents, entity cloning, or broad recreation of Litematica's UI.
