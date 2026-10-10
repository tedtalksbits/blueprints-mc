# Roadmap

## M0 — Construction engine (this bundle)

Prove exact block-state placement, real material consumption, slow construction, persistence, and pause/resume behavior on Paper 26.2.

## M1 — Client preview

Fabric 26.2 client mod with blueprint browser, hologram renderer, opacity, move/rotate/mirror, layer/focus view, and a clean placement panel.

## M2 — Litematic import

Read gzip-compressed NBT `.litematic` files, resolve region palettes and packed block-state arrays, normalize to relative coordinates + serialized Minecraft block data, and calculate material requirements.

Do not couple the server to Litematica's file format.

## M3 — Client/server protocol

Versioned custom payload handshake. Server advertises capabilities and limits. Client uploads blueprint metadata/hash and block payload in bounded chunks. Server validates size, allowed blocks, transform, region and permissions before creating a job.

## M4 — Survival UX

Builder's Chest selection, material checklist, missing-material status, estimated duration, start/cancel controls, construction particles/sounds, progress HUD, and pause reasons.

## M5 — Construction planner

Replace simple Y-order with structural phases and adjacency-aware ordering so buildings visibly grow foundation → frame/walls → floors → roof → detail. Keep placement rate configurable and bounded.

## M6 — Hardening

Block-to-item registry, multi-block blocks, waterlogging, redstone update policy, block entities, protection hooks, crash/restart tests, chunk unload behavior, malicious payload limits, and large-build profiling.

## Explicit non-goals for early releases

No instant paste button, no Creative inventory spawning, no imported chest contents, no entity cloning, no WorldEdit replacement, and no attempt to reproduce every Litematica feature.
