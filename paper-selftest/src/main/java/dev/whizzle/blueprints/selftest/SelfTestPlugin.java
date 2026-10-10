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
        try { palette(); transforms(); placement(); importFiles(); census(); }
        catch (Throwable t) { failures++; report.add("FAIL crashed: " + t); getLogger().log(java.util.logging.Level.SEVERE, "self-test crashed", t); }
        report.add(0, "SELFTEST " + (failures == 0 ? "PASSED" : "FAILED (" + failures + ")") + " on " + Bukkit.getVersion());
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.write(getDataFolder().toPath().resolve("report.txt"), report);
        } catch (IOException e) { getLogger().severe("Could not write report: " + e); }
        report.stream().filter(l -> !l.startsWith("PASS")).forEach(l -> getLogger().info(l));
        if (Boolean.getBoolean("blueprints.selftest.exit")) Bukkit.shutdown();
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
        "minecraft:anvil[facing=east]", "minecraft:flower_pot", "minecraft:respawn_anchor[charges=0]", "minecraft:composter[level=0]", "minecraft:spruce_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
    static final List<String> UNSUPPORTED = List.of("minecraft:chest", "minecraft:campfire", "minecraft:spawner",
        "minecraft:oak_sign", "minecraft:command_block", "minecraft:white_bed", "minecraft:furnace", "minecraft:bedrock",
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
