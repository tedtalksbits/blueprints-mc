# Test plan: Creative → preview → Survival build

What this covers: the full loop with the Fabric client. You design in Creative, save it from the menu, then in Survival you preview it, move, rotate and mirror it, fund it, and start it. Everything below was already exercised in a real 26.3 client against a real Paper server; this plan is for you to confirm it on your machine and judge the feel.

## Setup (once, about 10 minutes)

1. Stop the test server (type `stop` in its console).
2. Double-click `run\local-paper\build-and-install.cmd`. It builds and tests the plugin, installs it on the test server, builds the client mod, and copies two jars to `run\client-mods\`:
   - `blueprints-client-0.1.0.jar`
   - `fabric-api-0.164.0+26.3.jar`
3. Install Fabric for 26.3: download the installer from https://fabricmc.net/use/installer/, choose **Minecraft 26.3** and **Loader 0.19.5**, Install. The launcher gets a `fabric-loader-26.3` profile.
4. Copy both jars from `run\client-mods\` into `%APPDATA%\.minecraft\mods\` (create the folder if it is missing).
5. Start the server (`start.cmd`), launch the **fabric-loader-26.3** profile, join `127.0.0.1:25565`.

## Tests

| # | Do this | Expect |
|---|---|---|
| 1 | Press **B** | The Blueprints screen opens and lists the server's blueprints. |
| 2 | `/gamemode creative`. Build something small (stairs, a door, a log on its side make it interesting). **B → Capture new...** Look at one corner block, **Enter**. Look at the opposite corner, **Enter**. Type a name, **Save**. | A yellow box follows your crosshair after the first corner. "Saved blueprint <name> (N blocks)". It appears in the list. |
| 3 | `/gamemode survival`, walk to an open spot. **B →** select your build **→ Preview here** | A see-through copy appears in front of you. The top-left panel shows size, position, and the keys. |
| 4 | Arrow keys, **Page Up/Down**, **G**, **R**, **M** | Moves away/closer/left/right relative to where you face (hold to repeat), up/down, jumps to the crosshair, rotates in place, mirrors. Stairs, doors and slabs visibly face the right way. |
| 5 | `/blueprint givechest`, place it within 16 blocks. Open **B** | "Your Builder's Chest at ...". The materials table shows Needed / In chest / Missing. Put some items in; the numbers update within about a second. |
| 6 | Fill the chest, preview where you want it, press **Enter** | "Started ..." message. The panel shows progress; blocks appear bottom-up and vanish from the preview as they are placed correctly. The final build matches the preview exactly. |
| 7 | Start another build with too few materials, then open **B** and select that blueprint | It pauses; the panel says why ("paused: Missing 3 COBBLESTONE"). The menu switches to **Building now: N/M** and shows what's **left** and what *that build's* chest holds. Add items; the numbers update within a second and the build continues. |
| 8 | Preview over an existing block (place dirt where the preview has a block) | That spot is drawn red. |
| 9 | **B →** under "Your builds in progress", **Stop build** | A confirmation explains what happens. **Keep building** changes nothing; **Stop build** stops it, placed blocks stay, unused items remain in the chest. |
| 10 | Put a `.litematic` in `%APPDATA%\.minecraft\schematics\` (or any subfolder). **B → Upload .litematic...**, pick it, keep or change the name, **Upload** | "Uploaded <name> (N blocks)", plus a list of anything skipped. It appears in the menu; preview and build it. Your tower uploads complete: 895 of 895 blocks (the campfire is built, empty). |
| 10b | Build something with a **campfire, barrel, and sign** (put food in the campfire, items in the barrel, text on the sign), capture it, build it in Survival | The copies are built **empty**: no food, no items, blank sign. Rotating the blueprint turns standing signs correctly. |
| 11 | Stop the server, set `limits.max-span: 10` in `plugins\Blueprints\config.yml`, start, try uploading the tower again under a new name | Refused: "Schematic is 12x18x16; the current limit is 10 blocks per side". Set it back to 65 afterwards. |
| 12 | Stop the server. In `plugins\Blueprints\config.yml` set `uploads.allow-everyone: true` and `uploads.max-per-player: 2`. In the console, `deop TedTalks2`. Start, join, **B** | Top left shows **Yours: 0 / 2**. **Upload .litematic...** is there even though you're not an operator. |
| 13 | Upload the tower twice under two names (e.g. `mine-a`, `mine-b`) | Both upload. **Yours: 2 / 2** turns gold; **Upload** is greyed out and its tooltip says to delete one. Each shows "by TedTalks2". |
| 14 | Select `orientation-demo` (no owner) | No **Delete** button: you can only delete your own. |
| 15 | Select `mine-a` **→ Delete** | A confirmation explains it. **Keep it** changes nothing; **Delete** removes it from the list, Yours drops to 1 / 2, Upload works again. The file is in `plugins\Blueprints\blueprints\trash\`. |
| 16 | Start a build of `mine-b`, then delete `mine-b` while it builds | The build keeps going and finishes. Afterwards `op TedTalks2` and set the two settings back (or leave them). |

## Please report

Screenshots of anything that looks wrong, plus anything that felt clunky: key choices, panel size, opacity, colors, how placement in front of you feels.

## ⚠️ Known limits and decisions for you

0. **Shader packs (Iris) can hide the preview.** The panel warns when shaders are on; toggle them (Iris: `K`) while placing.

1. **The preview shows each block's shape in its map color, not real textures.** Orientation is clear (stairs, slabs, doors, fences, walls show their shape). Real textured, see-through models are possible and would be the next client step.
2. **Size limits are now in `config.yml`** (`limits.max-blocks`, `limits.max-span`, `uploads.max-kilobytes`), with hard ceilings of 32,768 blocks and 128 per side.
3. **Capture new...** is shown to anyone with capture permission; in Survival the server refuses with "Capture requires Creative mode".
4. **Uploads are operator-only by default.** Set `uploads.allow-everyone: true` to open them to everyone; `uploads.max-per-player` (default 10) caps how many each player keeps. Operators can delete anyone's blueprint; deleted files go to `blueprints\trash\`.
5. **The client needs Fabric for Minecraft 26.3.** Players without it keep using `/blueprint` commands.
