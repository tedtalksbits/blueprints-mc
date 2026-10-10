# Architecture

## Product boundary

Blueprints is not intended to be a Litematica clone. Litematica is an import source and compatibility format. The product is a survival construction system with a focused UX.

## Components

### Fabric client
Responsible for creative-side capture/import, hologram rendering, placement transforms, material/progress UI, and player interaction. It is never authoritative for inventories or world mutation.

### Paper plugin
Authoritative for blueprint validation, job ownership, material consumption, exact block-state placement, persistence, throttling, and anti-abuse rules.

### Shared protocol/model
A versioned normalized blueprint representation. The server should never need to understand every historical `.litematic` encoding; the client converts an imported schematic into our stable model.

## Normalized block model

Each block is represented as a relative coordinate plus Minecraft's serialized block-data string, e.g.:

`minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]`

This preserves the exact state that matters to the user.

## Construction semantics

A job has:

- owner UUID
- blueprint identity/hash
- world + transformed origin
- funding inventory location
- ordered construction plan
- cursor/progress
- paused/completed/cancelled state

The server processes a bounded amount of work per cycle. V0 sorts by Y then X/Z. Later versions should compute build phases (foundation, structure, roof, detail) so construction looks intentional.

## Material accounting

The Builder's Chest is the source of truth. Before a block is placed, the corresponding item is consumed. If the target already matches, the job advances without consuming an item. This is critical for safe restart/retry semantics.

Block-to-item mapping needs a dedicated registry before broad schematic support. Many blocks map directly; others do not (wall variants, water, crops, upper/lower halves, multi-block structures).

## Safety defaults

- no arbitrary overwrite
- no imported container contents
- no entities
- no command blocks
- no spawners by default
- no item-bearing block entity duplication
- no force-loaded chunks by default
- bounded placements per tick
- persistent job cursor
- server validates every request

## Multiplayer direction

A later version can make the Builder's Chest communal and track material contributions per UUID. That should be gameplay/UI data, not a prerequisite for the first usable release.
