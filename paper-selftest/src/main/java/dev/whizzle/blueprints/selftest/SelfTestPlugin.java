package dev.whizzle.blueprints.selftest;

import dev.whizzle.blueprints.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Runs once after startup, writes plugins/BlueprintsSelfTest/report.txt, and stops the server when started with
 * -Dblueprints.selftest.exit=true. Checks real Paper behavior that MockBukkit does not model.
 */
public final class SelfTestPlugin extends JavaPlugin {
    private final List<String> report = new ArrayList<>();
    private int failures;

    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, this::run, 20L); }

    private void check(boolean ok, String what) { if (!ok) failures++; report.add((ok ? "PASS " : "FAIL ") + what); }

    private void run() {
        try { channels(); palette(); transforms(); placement(); importFiles(); census(); waterSetup(); }
        catch (Throwable t) { failures++; report.add("FAIL crashed: " + t); getLogger().log(java.util.logging.Level.SEVERE, "self-test crashed", t); finish(); return; }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { waterCheck(); }
            catch (Throwable t) { failures++; report.add("FAIL crashed: " + t); getLogger().log(java.util.logging.Level.SEVERE, "self-test crashed", t); }
            finish();
        }, 60L);
    }

    private void finish() {
        String verdict = "SELFTEST " + (failures == 0 ? "PASSED" : "FAILED (" + failures + ")") + " on " + Bukkit.getVersion();
        report.add(0, verdict);   // first line for people reading the file
        report.add(verdict);      // last line for ci/run-selftest.sh
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.write(getDataFolder().toPath().resolve("report.txt"), report);
        } catch (IOException e) { getLogger().severe("Could not write report: " + e); }
        report.stream().filter(l -> !l.startsWith("PASS")).forEach(l -> getLogger().info(l));
        if (Boolean.getBoolean("blueprints.selftest.exit")) Bukkit.shutdown();
    }

    /** The client-mod channels must be registered on the real messenger (names are validated by Paper). */
    private void channels() {
        var blueprints = Bukkit.getPluginManager().getPlugin("Blueprints");
        var incoming = Bukkit.getMessenger().getIncomingChannels(blueprints);
        var outgoing = Bukkit.getMessenger().getOutgoingChannels(blueprints);
        for (String c : ClientBridge.INCOMING) check(incoming.contains(c), "incoming channel " + c);
        for (String c : ClientBridge.OUTGOING) check(outgoing.contains(c), "outgoing channel " + c);
    }

    static final List<String> SUPPORTED = List.of(
        "minecraft:sandstone_wall[east=low,north=tall,south=none,up=true,waterlogged=false,west=none]",
        "minecraft:end_stone_brick_wall[east=none,north=low,south=low,up=false,waterlogged=false,west=tall]",
        "minecraft:mud_brick_wall", "minecraft:sulfur_brick_wall",
        "minecraft:glass_pane[east=false,north=true,south=false,waterlogged=false,west=true]",
        "minecraft:red_stained_glass_pane[east=true,north=false,south=true,waterlogged=false,west=false]",
        "minecraft:iron_bars[east=true,north=true,south=false,waterlogged=false,west=false]",
        "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]",
        "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=true,powered=false]",
        "minecraft:lantern[hanging=true,waterlogged=false]", "minecraft:soul_lantern[hanging=false,waterlogged=false]",
        "minecraft:wall_torch[facing=west]", "minecraft:torch", "minecraft:iron_chain[axis=x,waterlogged=false]",
        "minecraft:cinnabar", "minecraft:cinnabar_bricks", "minecraft:cinnabar_brick_stairs[facing=north,half=top,shape=outer_left,waterlogged=false]",
        "minecraft:crimson_fungus", "minecraft:dead_bush", "minecraft:white_carpet", "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]",
        "minecraft:observer[facing=up,powered=false]", "minecraft:piston[extended=false,facing=north]", "minecraft:glowstone",
        "minecraft:brown_mushroom_block[down=false,east=true,north=true,south=false,up=true,west=false]",
        "minecraft:white_glazed_terracotta[facing=south]", "minecraft:sand", "minecraft:ladder[facing=east,waterlogged=false]",
        "minecraft:oak_button[face=wall,facing=north,powered=false]", "minecraft:lever[face=floor,facing=west,powered=false]",
        "minecraft:stone_pressure_plate[powered=false]", "minecraft:end_rod[facing=up]", "minecraft:copper_grate[waterlogged=false]",
        "minecraft:anvil[facing=east]", "minecraft:flower_pot", "minecraft:campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]",
        "minecraft:soul_campfire[facing=north,lit=false,signal_fire=true,waterlogged=false]", "minecraft:barrel[facing=up,open=false]",
        "minecraft:oak_sign[rotation=3,waterlogged=false]", "minecraft:oak_wall_sign[facing=east,waterlogged=false]",
        "minecraft:spruce_hanging_sign[attached=false,rotation=6,waterlogged=false]", "minecraft:spruce_wall_hanging_sign[facing=south,waterlogged=false]", "minecraft:respawn_anchor[charges=0]", "minecraft:composter[level=0]", "minecraft:spruce_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
    static final List<String> UNSUPPORTED = List.of("minecraft:chest", "minecraft:hopper", "minecraft:spawner",
        "minecraft:white_banner", "minecraft:command_block", "minecraft:white_bed", "minecraft:furnace", "minecraft:bedrock",
        "minecraft:redstone_wire", "minecraft:piston[extended=true]", "minecraft:respawn_anchor[charges=4]", "minecraft:composter[level=8]", "minecraft:oak_leaves[persistent=false]");

    private void palette() {
        for (String s : SUPPORTED) {
            BlockData d = Bukkit.createBlockData(s);
            check(MaterialCost.unsupportedReason(d) == null, "supported " + s + reason(d));
        }
        for (String s : UNSUPPORTED) check(MaterialCost.unsupportedReason(Bukkit.createBlockData(s)) != null, "refused " + s);
        check(MaterialCost.item(Bukkit.createBlockData("minecraft:wall_torch")) == Material.TORCH, "wall_torch is paid with TORCH");
        check(MaterialCost.item(Bukkit.createBlockData("minecraft:soul_wall_torch")) == Material.SOUL_TORCH, "soul_wall_torch is paid with SOUL_TORCH");
    }
    private static String reason(BlockData d) { String r = MaterialCost.unsupportedReason(d); return r == null ? "" : " -> " + r; }

    private static String one(ConstructionPlan p) { return p.steps().getFirst().blocks().getFirst().blockData(); }
    private static ConstructionPlan single(String state) {
        return PlanCompiler.compile(new BlueprintDefinition("t", "t", List.of(new BlueprintBlock(0, 0, 0, Bukkit.createBlockData(state).getAsString()))));
    }
    private static String apply(String state, String rotation, String mirror) {
        return one(PlanTransformer.apply(single(state), BlueprintTransform.parse(rotation, mirror)));
    }

    private void transforms() {
        for (String s : SUPPORTED) {
            String canonical = Bukkit.createBlockData(s).getAsString();
            String r = canonical;
            for (int i = 0; i < 4; i++) r = apply(r, "90", "none");
            check(r.equals(canonical), "4x90 returns to start: " + canonical + (r.equals(canonical) ? "" : " got " + r));
            check(apply(apply(canonical, "0", "x"), "0", "x").equals(canonical), "mirror x twice returns to start: " + canonical);
            check(apply(apply(canonical, "0", "z"), "0", "z").equals(canonical), "mirror z twice returns to start: " + canonical);
        }
        expect("minecraft:sandstone_wall[east=low,north=tall,south=none,up=true,waterlogged=false,west=none]", "90", "none",
            "minecraft:sandstone_wall[east=tall,north=none,south=low,up=true,waterlogged=false,west=none]");
        expect("minecraft:sandstone_wall[east=low,north=tall,south=none,up=true,waterlogged=false,west=none]", "0", "x",
            "minecraft:sandstone_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=low]");
        expect("minecraft:glass_pane[east=false,north=true,south=false,waterlogged=false,west=true]", "90", "none",
            "minecraft:glass_pane[east=true,north=true,south=false,waterlogged=false,west=false]");
        expect("minecraft:wall_torch[facing=west]", "90", "none", "minecraft:wall_torch[facing=north]");
        expect("minecraft:oak_sign[rotation=3,waterlogged=false]", "90", "none", "minecraft:oak_sign[rotation=7,waterlogged=false]");
        expect("minecraft:oak_sign[rotation=3,waterlogged=false]", "0", "x", "minecraft:oak_sign[rotation=13,waterlogged=false]");
        expect("minecraft:campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]", "90", "none", "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        check(MaterialCost.normalize(Bukkit.createBlockData("minecraft:barrel[open=true]")).getAsString().contains("open=false"), "barrels are placed closed");
        check(MaterialCost.item(Bukkit.createBlockData("minecraft:oak_wall_sign")) == Material.OAK_SIGN, "wall sign is paid with OAK_SIGN");
        expect("minecraft:iron_chain[axis=x,waterlogged=false]", "90", "none", "minecraft:iron_chain[axis=z,waterlogged=false]");
        expect("minecraft:brown_mushroom_block[down=false,east=true,north=true,south=false,up=true,west=false]", "180", "none",
            "minecraft:brown_mushroom_block[down=false,east=false,north=false,south=true,up=true,west=true]");
    }
    private void expect(String in, String rot, String mirror, String out) {
        String got = apply(in, rot, mirror), want = Bukkit.createBlockData(out).getAsString();
        check(got.equals(want), "transform " + rot + " " + mirror + ": " + in + " -> " + want + (got.equals(want) ? "" : " got " + got));
    }

    /** Exactly what BuildManager does: setBlockData(..., false) must keep the full state, connections included. */
    private void placement() {
        World world = Bukkit.getWorlds().getFirst();
        world.getChunkAt(0, 0).load(true);
        int y = world.getMaxHeight() - 20, x = 0;
        for (String s : SUPPORTED) {
            BlockData want = Bukkit.createBlockData(s);
            Block block = world.getBlockAt(x, y, 0);
            block.setBlockData(want, false);
            check(block.getBlockData().getAsString().equals(want.getAsString()), "placed exactly " + want.getAsString()
                + (block.getBlockData().getAsString().equals(want.getAsString()) ? "" : " got " + block.getBlockData().getAsString()));
            block.setType(Material.AIR, false);
            x += 2;
            if (x > 14) { x = 0; y--; }
        }
        // Tall plant: both halves placed together, as one paid step.
        Block lower = world.getBlockAt(0, y - 2, 2), upper = lower.getRelative(0, 1, 0);
        lower.setBlockData(Bukkit.createBlockData("minecraft:rose_bush[half=lower]"), false);
        upper.setBlockData(Bukkit.createBlockData("minecraft:rose_bush[half=upper]"), false);
        check(lower.getType() == Material.ROSE_BUSH && upper.getBlockData().getAsString().contains("half=upper"), "rose bush halves placed");
        lower.setType(Material.AIR, false); upper.setType(Material.AIR, false);
    }

    private void importFiles() throws IOException {
        Path dir = getDataFolder().toPath().resolve("litematics");
        if (!Files.isDirectory(dir)) { report.add("SKIP no litematics folder"); return; }
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".litematic")).toList()) {
                LitematicImport.Result r = LitematicImport.read(file, "selftest-" + file.getFileName().toString().replaceAll("[^a-z0-9]", ""));
                report.add("INFO import " + file.getFileName() + ": " + r.plan().blockCount() + " blocks, skipped " + r.skipped() + ", adjusted " + r.normalized());
                for (String rot : List.of("0", "90", "180", "270")) for (String m : List.of("none", "x", "z")) {
                    ConstructionPlan t = PlanTransformer.apply(r.plan(), BlueprintTransform.parse(rot, m));
                    check(t.blockCount() == r.plan().blockCount() && t.materials().equals(r.plan().materials()), "import " + file.getFileName() + " transform " + rot + " " + m + " keeps blocks and cost");
                }
            }
        }
    }

    // ---- Building in water ----

    private record WaterCase(BlockData data, Block wet, Block dry) {}
    private final List<WaterCase> waterCases = new ArrayList<>();

    /**
     * Every supported block (default state, as a build places it) goes into a cell surrounded by water and, as a
     * control, into the same spot in air, each on a stone floor. Then each is updated with physics so the water
     * flows. waterCheck() compares what survived with MaterialCost.canPlaceInWater, the rule builds use.
     */
    private void waterSetup() {
        World world = Bukkit.getWorlds().getFirst();
        int y0 = world.getMaxHeight() - 40, wetX = 2000, dryX = 2200, columns = 32;
        List<BlockData> datas = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock() || m.isAir()) continue;
            BlockData d;
            try { d = MaterialCost.normalize(m.createBlockData()); } catch (RuntimeException e) { continue; }
            if (MaterialCost.unsupportedReason(d) != null || d instanceof org.bukkit.block.data.Bisected) continue;
            datas.add(d);
        }
        int rows = (datas.size() + columns - 1) / columns, w = columns * 2 + 1, h = rows * 2 + 1;
        for (int cx = wetX >> 4; cx <= (dryX + w) >> 4; cx++) for (int cz = 0; cz <= h >> 4; cz++) world.setChunkForceLoaded(cx, cz, true);
        for (int base : new int[]{wetX, dryX}) {
            boolean wet = base == wetX;
            for (int x = -1; x <= w; x++) for (int z = -1; z <= h; z++) {
                boolean wall = x < 0 || z < 0 || x == w || z == h;
                world.getBlockAt(base + x, y0, z).setType(Material.STONE, false);
                for (int y = 1; y <= 4; y++)
                    world.getBlockAt(base + x, y0 + y, z).setType(wall ? (wet ? Material.GLASS : Material.AIR) : (wet && y <= 3 ? Material.WATER : Material.AIR), false);
            }
        }
        for (int i = 0; i < datas.size(); i++) {
            int x = 1 + (i % columns) * 2, z = 1 + (i / columns) * 2;
            Block wetCell = world.getBlockAt(wetX + x, y0 + 1, z), dryCell = world.getBlockAt(dryX + x, y0 + 1, z);
            wetCell.setBlockData(MaterialCost.inWater(datas.get(i)), false);   // what BuildManager places into water
            dryCell.setBlockData(datas.get(i), false);
            waterCases.add(new WaterCase(datas.get(i), wetCell, dryCell));
        }
        // Wake the neighbours: re-placing the water above with physics makes it flow (and tests survival) like in a real game.
        for (WaterCase c : waterCases) {
            Block above = c.wet().getRelative(0, 1, 0);
            above.setType(Material.AIR, false); above.setType(Material.WATER, true);
            Block dryAbove = c.dry().getRelative(0, 1, 0);
            dryAbove.setType(Material.STONE, false); dryAbove.setType(Material.AIR, true);
        }
        report.add("INFO water: " + waterCases.size() + " supported block types placed in water and in air (two-block types checked separately)");
        doorSetup();
    }

    private void waterCheck() {
        List<String> lost = new ArrayList<>(), stricter = new ArrayList<>(), support = new ArrayList<>();
        int dryOk = 0, washedAsExpected = 0;
        for (WaterCase c : waterCases) {
            String name = c.data().getMaterial().name().toLowerCase();
            boolean controlKept = c.dry().getType() == c.data().getMaterial();
            if (!controlKept) { support.add(name); continue; }
            boolean kept = c.wet().getType() == c.data().getMaterial();
            // Compared with the control in air, so neighbour-driven state (a note block's instrument) doesn't count.
            boolean same = kept && MaterialCost.sameIgnoringWater(c.wet().getBlockData(), c.dry().getBlockData());
            boolean rule = MaterialCost.canPlaceInWater(c.data());
            if (rule && same) dryOk++;
            else if (rule) lost.add(name + (kept ? " (changed to " + c.wet().getBlockData().getAsString() + ")" : " (washed away or turned into " + c.wet().getType().name().toLowerCase() + ")"));
            else if (!kept) washedAsExpected++;
            else stricter.add(name);
        }
        check(lost.isEmpty(), "every block a build places into water stays put (" + dryOk + " checked)" + (lost.isEmpty() ? "" : ": " + lost));
        report.add("INFO water: " + washedAsExpected + " block types refused in water really are washed away");
        if (!stricter.isEmpty()) report.add("INFO water: refused in water but would survive (could be allowed later): " + String.join(" ", stricter));
        if (!support.isEmpty()) report.add("INFO water: not checked, need other support than stone: " + String.join(" ", support));
        doorCheck();
    }

    private Block doorLower;

    private void doorSetup() {
        World world = Bukkit.getWorlds().getFirst();
        int y0 = world.getMaxHeight() - 40;
        Block lower = world.getBlockAt(2400, y0 + 1, 0), upper = lower.getRelative(0, 1, 0);
        world.getBlockAt(2400, y0, 0).setType(Material.STONE, false);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++)
            world.getBlockAt(2400 + dx, y0 + dy, dz).setType(Material.WATER, false);
        BlockData lowerData = Bukkit.createBlockData("minecraft:oak_door[half=lower]"), upperData = Bukkit.createBlockData("minecraft:oak_door[half=upper]");
        check(MaterialCost.canPlaceInWater(lowerData), "doors may be built in water");
        lower.setBlockData(lowerData, false); upper.setBlockData(upperData, false);
        Block side = lower.getRelative(1, 0, 0);
        side.setType(Material.AIR, false); side.setType(Material.WATER, true);
        doorLower = lower;
    }

    private void doorCheck() {
        Block upper = doorLower.getRelative(0, 1, 0);
        check(doorLower.getType() == Material.OAK_DOOR && upper.getType() == Material.OAK_DOOR, "a door placed in water stays (both halves)");
    }

    /** Lists every block id the palette refuses, grouped by reason, for review. */
    private void census() {
        Map<String, List<String>> refused = new TreeMap<>();
        int supported = 0;
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock() || m.isAir()) continue;
            String reason;
            try { reason = MaterialCost.unsupportedReason(m.createBlockData()); } catch (RuntimeException e) { reason = "error " + e.getClass().getSimpleName(); }
            if (reason == null) supported++; else refused.computeIfAbsent(reason, k -> new ArrayList<>()).add(m.name().toLowerCase());
        }
        report.add("INFO census: " + supported + " block types supported (default state)");
        refused.forEach((reason, names) -> report.add("INFO refused (" + reason + ", " + names.size() + "): " + String.join(" ", names)));
    }
}
