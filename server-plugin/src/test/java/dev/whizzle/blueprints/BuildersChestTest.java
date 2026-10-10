package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildersChestTest {
    ServerMock server;
    WorldMock world;
    PlayerMock player, other;
    BlueprintsPlugin plugin;
    @BeforeEach void setUp() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("chests");
        player = server.addPlayer(); player.setOp(true); player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 0, 65, 0)); world.loadChunk(0, 0); world.loadChunk(-1, 0);
        other = server.addPlayer(); // added before load: joins after load would hit recipe discovery in the mock
        plugin = MockBukkit.load(BlueprintsPlugin.class);
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    Chest chestAt(int x, int y, int z) { world.getBlockAt(x, y, z).setType(Material.CHEST); return (Chest) world.getBlockAt(x, y, z).getState(); }
    Chest claimedAt(int x, int y, int z, PlayerMock owner) { BuildersChest.claim(chestAt(x, y, z), owner); return (Chest) world.getBlockAt(x, y, z).getState(); }

    @Test void recipeProducesTheTaggedItemAndPlainChestsDoNotCount() {
        Recipe recipe = Bukkit.getRecipe(BuildersChest.RECIPE_KEY);
        assertNotNull(recipe); assertTrue(BuildersChest.isItem(recipe.getResult()));
        assertFalse(BuildersChest.isItem(new ItemStack(Material.CHEST)));
        assertFalse(BuildersChest.isBuildersChest(chestAt(1, 65, 0).getBlock()));
    }
    @Test void placingTheItemBindsTheChestToThePlacer() {
        Block block = world.getBlockAt(1, 65, 0); BlockState replaced = block.getState();
        block.setType(Material.CHEST);
        server.getPluginManager().callEvent(new BlockPlaceEvent(block, replaced, world.getBlockAt(1, 64, 0),
            BuildersChest.item(), player, true, EquipmentSlot.HAND));
        assertTrue(BuildersChest.isOwnedBy((Chest) block.getState(), player));
    }
    @Test void placingAPlainChestDoesNotBindIt() {
        Block block = world.getBlockAt(1, 65, 0); BlockState replaced = block.getState();
        block.setType(Material.CHEST);
        server.getPluginManager().callEvent(new BlockPlaceEvent(block, replaced, world.getBlockAt(1, 64, 0),
            new ItemStack(Material.CHEST), player, true, EquipmentSlot.HAND));
        assertTrue(BuildersChest.owner((Chest) block.getState()).isEmpty());
    }
    @Test void breakingReturnsTheBuildersChestItem() {
        claimedAt(1, 65, 0, player);
        BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(1, 65, 0), player);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isDropItems());
        assertTrue(world.getEntities().stream().anyMatch(e -> e instanceof Item item && BuildersChest.isItem(item.getItemStack())));
    }
    @Test void startUsesTheNearestOwnBuildersChestAndIgnoresOthers() {
        chestAt(-1, 65, 0);                          // plain chest, closest
        claimedAt(-1, 65, 1, other);    // someone else's Builder's Chest
        assertTrue(server.dispatchCommand(player, "blueprint start orientation-demo 4 65 4"));
        assertTrue(player.nextMessage().contains("No free Builder's Chest"));
        assertTrue(plugin.builds().jobs().isEmpty());

        claimedAt(-6, 65, 0, player);
        claimedAt(-3, 65, 0, player);
        claimedAt(-40, 65, 0, player);               // outside the default 16-block radius
        assertTrue(server.dispatchCommand(player, "blueprint start orientation-demo 4 65 4"));
        assertTrue(player.nextMessage().contains("Started"));
        BuildJob job = plugin.builds().jobs().iterator().next();
        assertEquals(-3, job.chestX()); assertEquals(0, job.chestZ());
    }
    @Test void eachActiveBuildNeedsItsOwnChestAndPlayersAreCappedAtTwo() {
        claimedAt(-3, 65, 0, player);
        claimedAt(-5, 65, 0, player);
        claimedAt(-7, 65, 0, player);
        assertTrue(server.dispatchCommand(player, "blueprint start orientation-demo 4 65 4"));
        String first = player.nextMessage();
        assertTrue(server.dispatchCommand(player, "blueprint start orientation-demo 4 65 10"));
        String second = player.nextMessage();
        assertTrue(first.contains("Started") && second.contains("Started"), () -> first + " | " + second);
        List<Integer> chests = plugin.builds().jobs().stream().map(BuildJob::chestX).sorted().toList();
        assertEquals(List.of(-5, -3), chests, () -> "job chests " + chests);
        assertTrue(server.dispatchCommand(player, "blueprint start orientation-demo 10 65 4"));
        String third = player.nextMessage();
        assertTrue(third.contains("2 active builds"), third);
        assertEquals(2, plugin.builds().jobs().size());
    }
    @Test void giveChestRequiresAdmin() {
        assertTrue(server.dispatchCommand(other, "blueprint givechest"));
        assertFalse(other.getInventory().containsAtLeast(BuildersChest.item(), 1));
        assertTrue(server.dispatchCommand(player, "blueprint givechest"));
        assertTrue(player.getInventory().containsAtLeast(BuildersChest.item(), 1));
    }
}
