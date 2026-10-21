package dev.whizzle.blueprints;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The widened building palette: families, item costs, two-block plants, and rotate/mirror of connecting blocks. */
class PaletteTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    static ConstructionPlan plan(BlueprintBlock... blocks) { return PlanCompiler.compile(new BlueprintDefinition("p", "P", List.of(blocks))); }
    static BlueprintBlock at(int x, int y, int z, String data) { return new BlueprintBlock(x, y, z, data); }
    static Map<String, String> byPosition(ConstructionPlan plan) {
        Map<String, String> m = new HashMap<>();
        plan.steps().forEach(s -> s.blocks().forEach(b -> m.put(b.x() + "," + b.y() + "," + b.z(), b.blockData())));
        return m;
    }

    @Test void buildingFamiliesAreSupported() {
        for (String data : List.of("sandstone_wall", "glass_pane", "white_stained_glass_pane", "iron_bars", "oak_fence",
                "oak_fence_gate", "lantern", "torch", "wall_torch[facing=north]", "soul_torch", "white_carpet",
                "moss_carpet", "dandelion", "oak_sapling", "dead_bush", "crimson_fungus", "oak_leaves[persistent=true]",
                "iron_chain", "hay_block", "bone_block", "sand", "gravel", "white_concrete_powder", "emerald_block", "observer",
                "piston[extended=false,facing=up]", "glowstone", "bookshelf"))
            assertNull(MaterialCost.unsupportedReason(Bukkit.createBlockData("minecraft:" + data)), data);
    }
    @Test void blockEntitiesAndTechnicalBlocksAreRefusedWithAReason() {
        for (String data : List.of("chest", "furnace", "hopper", "white_bed", "white_banner", "spawner", "command_block", "bedrock", "barrier"))
            assertNotNull(MaterialCost.unsupportedReason(Bukkit.createBlockData("minecraft:" + data)), data);
    }
    @Test void campfiresBarrelsAndSignsArePlacedEmpty() {
        for (String data : List.of("campfire", "soul_campfire", "barrel[facing=up]", "oak_sign", "oak_wall_sign", "spruce_hanging_sign", "spruce_wall_hanging_sign"))
            assertNull(MaterialCost.unsupportedReason(Bukkit.createBlockData("minecraft:" + data)), data);
        assertEquals(Map.of("OAK_SIGN", 1), plan(at(0, 0, 0, "minecraft:oak_wall_sign")).materials());   // paid with the sign item
    }
    @Test void signDirectionsRotateAndMirror() {
        BlueprintTransform r90 = new BlueprintTransform(90, BlueprintTransform.Mirror.NONE), mx = new BlueprintTransform(0, BlueprintTransform.Mirror.X);
        assertEquals(org.bukkit.block.BlockFace.WEST, PlanTransformer.turn16(org.bukkit.block.BlockFace.SOUTH, r90));
        assertEquals(org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST, PlanTransformer.turn16(org.bukkit.block.BlockFace.EAST_NORTH_EAST, r90)); // east->south, north->east
        assertEquals(org.bukkit.block.BlockFace.WEST_NORTH_WEST, PlanTransformer.turn16(org.bukkit.block.BlockFace.EAST_NORTH_EAST, mx));
        assertEquals(org.bukkit.block.BlockFace.SOUTH, PlanTransformer.turn16(org.bukkit.block.BlockFace.SOUTH, mx));
        for (var face : new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.SOUTH_WEST, org.bukkit.block.BlockFace.NORTH_NORTH_EAST}) {
            var f = face;
            for (int i = 0; i < 4; i++) f = PlanTransformer.turn16(f, r90);
            assertEquals(face, f);
        }
    }
    @Test void wallTorchIsPaidWithATorch() {
        ConstructionPlan p = plan(at(0, 0, 0, "minecraft:wall_torch[facing=east]"));
        assertEquals(Map.of("TORCH", 1), p.materials());
    }
    @Test void tallPlantsCostOneItemForBothHalves() {
        ConstructionPlan p = plan(at(0, 0, 0, "minecraft:rose_bush[half=lower]"), at(0, 1, 0, "minecraft:rose_bush[half=upper]"));
        assertEquals(1, p.steps().size()); assertEquals(2, p.blockCount()); assertEquals(Map.of("ROSE_BUSH", 1), p.materials());
        assertThrows(IllegalArgumentException.class, () -> plan(at(0, 0, 0, "minecraft:rose_bush[half=lower]")));
        assertThrows(IllegalArgumentException.class, () -> plan(at(0, 1, 0, "minecraft:rose_bush[half=upper]")));
    }
    @Test void normalizeTurnsOffWaterAndPowerAndKeepsLeaves() {
        assertEquals("minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]",
            MaterialCost.normalize(Bukkit.createBlockData("minecraft:oak_leaves[persistent=false,waterlogged=true]")).getAsString());
        assertNull(MaterialCost.unsupportedReason(MaterialCost.normalize(Bukkit.createBlockData("minecraft:oak_trapdoor[powered=true]"))));
    }
    // Walls, panes, and lantern states are missing from MockBukkit's block data; real Paper checks live in the in-server self-test.
    @Test void fenceConnectionsRotateAndMirror() {
        ConstructionPlan p = plan(at(0, 0, 0, "minecraft:oak_fence[north=true,east=true]"));
        String r = byPosition(PlanTransformer.apply(p, BlueprintTransform.parse("90", "none"))).get("0,0,0");
        assertEquals(Bukkit.createBlockData("minecraft:oak_fence[east=true,south=true]").getAsString(), r); // north->east, east->south
        String m = byPosition(PlanTransformer.apply(p, BlueprintTransform.parse("0", "x"))).get("0,0,0");
        assertEquals(Bukkit.createBlockData("minecraft:oak_fence[north=true,west=true]").getAsString(), m);
    }
}
