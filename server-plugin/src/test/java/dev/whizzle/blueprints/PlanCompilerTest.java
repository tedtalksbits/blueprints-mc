package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanCompilerTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    private ConstructionPlan compile(BlueprintBlock... blocks) { return PlanCompiler.compile(new BlueprintDefinition("test", "Test", List.of(blocks))); }
    private BlueprintBlock block(int x, int y, String data) { return new BlueprintBlock(x, y, 0, "minecraft:" + data); }
    @Test void doorIsOnePaidStepAndSlabsCostTwo() {
        var lower = block(0, 1, "oak_door[facing=west,half=lower,hinge=right,open=false,powered=false]");
        var upper = block(0, 2, "oak_door[facing=west,half=upper,hinge=right,open=false,powered=false]");
        var plan = compile(upper, lower, block(1, 0, "oak_slab[type=double,waterlogged=false]"));
        assertEquals(2, plan.steps().size()); assertEquals(3, plan.blockCount());
        assertEquals(Map.of("OAK_DOOR", 1, "OAK_SLAB", 2), plan.materials());
        assertEquals(2, plan.steps().get(1).blocks().size());
        assertTrue(plan.steps().get(1).blocks().getFirst().blockData().contains("facing=west"));
    }
    @Test void orphanAndMismatchedDoorsRejected() {
        var lower = block(0, 0, "oak_door[half=lower,facing=west]");
        var upper = block(0, 1, "oak_door[half=upper,facing=east]");
        assertThrows(IllegalArgumentException.class, () -> compile(lower));
        assertThrows(IllegalArgumentException.class, () -> compile(upper));
        assertThrows(IllegalArgumentException.class, () -> compile(lower, upper));
    }
    @Test void unsupportedAndUnsafeBlocksRejected() {
        for (String data : List.of("chest", "command_block", "spawner", "water", "redstone_wire", "piston[extended=true]", "oak_slab[waterlogged=true]", "oak_trapdoor[powered=true]", "air", "bedrock", "barrier", "furnace", "campfire", "oak_sign", "oak_leaves[persistent=false]"))
            assertThrows(IllegalArgumentException.class, () -> compile(block(0, 0, data)), data);
    }
    @Test void duplicatesAndOutOfBoundsRejected() {
        var stone = block(0, 0, "stone");
        assertThrows(IllegalArgumentException.class, () -> compile(stone, stone));
        assertThrows(IllegalArgumentException.class, () -> compile(block(Integer.MIN_VALUE, 0, "stone")));
        assertThrows(IllegalArgumentException.class, () -> compile());
    }
    @Test void canonicalStatesAndBottomUpOrdering() {
        var plan = compile(block(1, 2, "oak_log[axis=x]"), block(0, 0, "oak_stairs[facing=west,half=top]"));
        assertEquals(0, plan.steps().getFirst().blocks().getFirst().y());
        String data = plan.steps().getFirst().blocks().getFirst().blockData();
        assertTrue(data.contains("facing=west")); assertTrue(data.contains("half=top")); assertTrue(data.contains("waterlogged=false"));
    }
    @Test void inventoryDebitIsAllOrNothingAndSkipsNamedItems() {
        Inventory inventory = Bukkit.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(Material.OAK_SLAB, 1));
        assertFalse(InventoryUtil.consume(inventory, Material.OAK_SLAB, 2));
        assertEquals(1, inventory.getItem(0).getAmount());
        ItemStack named = new ItemStack(Material.OAK_SLAB, 8);
        var meta = named.getItemMeta(); meta.customName(net.kyori.adventure.text.Component.text("Keep me")); named.setItemMeta(meta);
        inventory.setItem(1, named);
        assertFalse(InventoryUtil.consume(inventory, Material.OAK_SLAB, 2));
        inventory.setItem(2, new ItemStack(Material.OAK_SLAB, 2));
        assertTrue(InventoryUtil.consume(inventory, Material.OAK_SLAB, 2));
        assertNull(inventory.getItem(0)); assertEquals(8, inventory.getItem(1).getAmount()); assertEquals(1, inventory.getItem(2).getAmount());
    }
}
