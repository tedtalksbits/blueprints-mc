# Client ↔ server protocol (v1)

The Fabric client and the Paper plugin talk over plugin-message channels. The server is authoritative: the client
only proposes a blueprint, an origin, and a transform; the plugin applies exactly the same checks as
`/blueprint start`. Players without the mod keep using commands.

Encoding matches Minecraft's `FriendlyByteBuf`: **VarInt**, **Int** (4 bytes, big-endian), **Bool** (1 byte),
**String** (VarInt byte length + UTF-8). Server side: `Wire.java`. Client side: `Protocol.java`.

| Channel | Direction | Body |
|---|---|---|
| `blueprints:hello` | C→S | VarInt protocol |
| `blueprints:welcome` | S→C | VarInt protocol, Bool canBuild, VarInt chestSearchRadius, Bool canCapture, VarInt maxBlocks, VarInt maxSpan, Bool canUpload, VarInt maxUploadBytes |
| `blueprints:catalog_request` | C→S | (empty) |
| `blueprints:catalog` | S→C | VarInt n, n × (String id, String name, VarInt blocks, VarInt sizeX, sizeY, sizeZ) |
| `blueprints:plan_request` | C→S | String id |
| `blueprints:plan` | S→C | String id, String name, VarInt paletteSize, paletteSize × String blockState, VarInt n, n × (VarInt x, y, z, VarInt paletteIndex) |
| `blueprints:materials_request` | C→S | String id |
| `blueprints:materials` | S→C | String id, Bool building, [String jobId, VarInt done, VarInt total if building], Bool chestFound, [Int x, y, z if found], VarInt n, n × (String material, VarInt needed, VarInt inChest) |
| `blueprints:start` | C→S | String id, Int originX, originY, originZ, VarInt rotation (0/90/180/270), VarInt mirror (0 none, 1 x, 2 z) |
| `blueprints:cancel` | C→S | String jobId (full UUID or unique prefix) |
| `blueprints:result` | S→C | Bool ok, String message |
| `blueprints:capture` | C→S | String id, Int x1, y1, z1, Int x2, y2, z2 (inclusive corners, any order) |
| `blueprints:upload_begin` | C→S | String id, VarInt totalBytes |
| `blueprints:upload_ready` | S→C | Bool ok, String message |
| `blueprints:upload_chunk` | C→S | VarInt offset, VarInt length, length × byte (at most 30,000 bytes) |
| `blueprints:upload_end` | C→S | (empty) |
| `blueprints:jobs_request` | C→S | (empty) |
| `blueprints:jobs` | S→C | VarInt n, n × (String jobId, String blueprintId, String name, VarInt done, VarInt total, String state, String reason, Int originX, originY, originZ) |

## Semantics

- `materials`: if the player has an unfinished build of that blueprint, it describes that build (materials still to
  place, and the build's own reserved chest); otherwise the whole blueprint against the nearest free Builder's Chest.
- Plan coordinates are local and non-negative; the origin is the blueprint's minimum corner.
- Transforms match `/blueprint start`: mirror first (`x` negates local X, `z` negates local Z), then rotate
  clockwise seen from above. The origin stays fixed, so a transformed build can extend into negative offsets. The
  client keeps the visible box in place by sending `origin = desiredMinCorner − minCornerOfTransformedOffsets`.
- `capture` follows `/blueprint capture` (Creative, `blueprints.capture`, supported blocks, limits); it replies with `result` and pushes `catalog` on success.
- Uploads: `upload_begin` checks permission (`blueprints.upload`), name, and size, and answers `upload_ready`. Chunks
  must arrive in order (a gap aborts with a failed `result`); chunks are exempt from the request budget but bounded
  by the accepted size, and an upload expires after 2 minutes or on disconnect. `upload_end` imports the bytes
  exactly like `/blueprint import`, replies with `result`, and pushes `catalog`. Clients keep chunks under the
  game's 32 KB serverbound custom-payload limit.
- `start` replies with `result`, then pushes `jobs` on success. `cancel` replies with `result` and `jobs`.
- The server rate-limits each player to 20 requests per second and answers malformed requests with a failed
  `result`.
- Bumping the protocol: change `ClientBridge.PROTOCOL` and the client's `Protocol.VERSION` together; the client
  shows a clear "update the mod/plugin" message on a mismatch.
