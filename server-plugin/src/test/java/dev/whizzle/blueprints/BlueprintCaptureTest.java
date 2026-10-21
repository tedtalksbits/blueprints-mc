package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlueprintCaptureTest {
    ServerMock server;
    WorldMock world;
    PlayerMock player;
    BlueprintsPlugin plugin;
    BlueprintRepository repository;
    @BeforeEach void setUp() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("creative");
        player = server.addPlayer(); player.setOp(true); player.setGameMode(GameMode.CREATIVE);
        player.teleport(new Location(world, 0, 65, 0)); world.loadChunk(0, 0);
        plugin = MockBukkit.load(BlueprintsPlugin.class);
        repository = new BlueprintRepository(plugin); repository.load();
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    ConstructionPlan capture(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        return BlueprintCapture.capture(player, id, x1, y1, z1, x2, y2, z2);
    }
    @Test void reversedSelectionSkipsAirAndPersistsCanonicalStates() throws Exception {
        world.getBlockAt(1, 65, 1).setBlockData(Bukkit.createBlockData("minecraft:oak_log[axis=z]"));
        world.getBlockAt(2, 65, 1).setBlockData(Bukkit.createBlockData("minecraft:oak_slab[type=double]"));
        var plan = capture("my_build", 3, 66, 2, 1, 65, 1); repository.saveNew(plan);
        assertEquals(2, plan.blockCount()); assertEquals(Map.of("OAK_LOG", 1, "OAK_SLAB", 2), plan.materials());
        assertEquals(new BlueprintBlock(0, 0, 0, "minecraft:oak_log[axis=z]"), plan.steps().getFirst().blocks().getFirst());
        var fresh = new BlueprintRepository(plugin); fresh.load(); assertEquals(plan, fresh.get("my_build").orElseThrow());
        Path file = plugin.getDataFolder().toPath().resolve("blueprints/blueprint-my_build.yml"); String saved = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> repository.saveNew(plan)); assertEquals(saved, Files.readString(file));
        assertEquals(Material.OAK_LOG, world.getBlockAt(1, 65, 1).getType());
    }
    @Test void emptyUnsupportedAndIncompleteDoorSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> capture("empty", 1, 65, 1, 1, 65, 1));
        world.getBlockAt(1, 65, 1).setType(Material.CHEST);
        assertThrows(IllegalArgumentException.class, () -> capture("container", 1, 65, 1, 1, 65, 1));
        world.getBlockAt(1, 65, 1).setBlockData(Bukkit.createBlockData("minecraft:oak_door[half=lower]"));
        assertThrows(IllegalArgumentException.class, () -> capture("partial", 1, 65, 1, 1, 65, 1));
        assertTrue(repository.get("partial").isEmpty());
    }
    @Test void captureListsEveryUnsupportedTypeAndNormalizesWater() {
        world.getBlockAt(1, 65, 1).setType(Material.CHEST);
        world.getBlockAt(2, 65, 1).setType(Material.CHEST);
        world.getBlockAt(3, 65, 1).setType(Material.FURNACE);
        var e = assertThrows(IllegalArgumentException.class, () -> capture("mixed", 1, 65, 1, 3, 65, 1));
        assertTrue(e.getMessage().contains("2 chest") && e.getMessage().contains("1 furnace") && e.getMessage().contains("first at 1 65 1"), e.getMessage());
        for (int x = 1; x <= 3; x++) world.getBlockAt(x, 65, 1).setType(Material.AIR);
        world.getBlockAt(1, 65, 1).setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[waterlogged=true]"));
        var plan = capture("wet", 1, 65, 1, 1, 65, 1);
        assertTrue(plan.steps().getFirst().blocks().getFirst().blockData().contains("waterlogged=false"));
    }
    @Test void largeSparseSelectionsAreAllowed() {
        world.getBlockAt(0, 65, 0).setType(Material.STONE);
        world.getBlockAt(40, 70, 40).setType(Material.STONE);
        world.loadChunk(1, 1); world.loadChunk(2, 2); world.loadChunk(0, 1); world.loadChunk(1, 0); world.loadChunk(0, 2); world.loadChunk(2, 0); world.loadChunk(1, 2); world.loadChunk(2, 1);
        assertEquals(2, capture("sparse", 0, 65, 0, 40, 70, 40).blockCount()); // 41x6x41 cells, far over the old 4096-cell cap
    }
    @Test void unauthorizedRemoteOversizedAndUnsafeIdsAreRejected() {
        world.getBlockAt(1, 65, 1).setType(Material.STONE);
        player.setGameMode(GameMode.SURVIVAL); assertThrows(IllegalArgumentException.class, () -> capture("build", 1, 65, 1, 1, 65, 1));
        player.setGameMode(GameMode.CREATIVE); player.setOp(false); assertThrows(IllegalArgumentException.class, () -> capture("build", 1, 65, 1, 1, 65, 1)); player.setOp(true);
        assertThrows(IllegalArgumentException.class, () -> capture("build", 200, 65, 0, 200, 65, 0));
        assertThrows(IllegalArgumentException.class, () -> capture("build", 0, 65, 0, 64, 129, 64));
        assertThrows(IllegalArgumentException.class, () -> capture("build", Integer.MIN_VALUE, 65, 0, Integer.MAX_VALUE, 65, 0));
        for (String id : List.of("../escape", "UPPER", "with.dot", "a".repeat(65)))
            assertThrows(IllegalArgumentException.class, () -> capture(id, 1, 65, 1, 1, 65, 1));
    }
    @Test void captureDoesNotLoadDestinationChunks() {
        var spyWorld = spy(world); var spyPlayer = spy(player); doReturn(spyWorld).when(spyPlayer).getWorld();
        doReturn(false).when(spyWorld).isChunkLoaded(1, 0);
        assertThrows(IllegalArgumentException.class, () -> BlueprintCapture.capture(spyPlayer, "unloaded", 16, 65, 0, 16, 65, 0));
        verify(spyWorld, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
    @Test void existingUnindexedFileIsNeverReplaced() throws Exception {
        world.getBlockAt(1, 65, 1).setType(Material.STONE); var plan = capture("collision", 1, 65, 1, 1, 65, 1);
        Path file = plugin.getDataFolder().toPath().resolve("blueprints/blueprint-collision.yml"); Files.writeString(file, "operator data");
        assertThrows(IllegalArgumentException.class, () -> repository.saveNew(plan)); assertEquals("operator data", Files.readString(file));
        assertTrue(repository.get("collision").isEmpty());
    }
    @Test void commandCaptureToTransformedSurvivalConstructionWorks() {
        world.getBlockAt(1, 65, 1).setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[facing=north,half=top,shape=inner_left]"));
        assertTrue(server.dispatchCommand(player, "blueprint capture custom 1 65 1 1 65 1"));
        assertTrue(player.nextMessage().contains("Saved custom")); player.nextMessage();
        world.getBlockAt(0, 65, 2).setType(Material.CHEST);
        BuildersChest.claim((Chest) world.getBlockAt(0, 65, 2).getState(), player);
        Chest chest = (Chest) world.getBlockAt(0, 65, 2).getState(); chest.getBlockInventory().addItem(new ItemStack(Material.OAK_STAIRS, 1));
        player.setGameMode(GameMode.SURVIVAL);
        assertTrue(server.dispatchCommand(player, "blueprint start custom 4 65 4 90 x"));
        server.getScheduler().performTicks(10);
        assertEquals("minecraft:oak_stairs[facing=east,half=top,shape=inner_right,waterlogged=false]", world.getBlockAt(4, 65, 4).getBlockData().getAsString());
        assertTrue(chest.getBlockInventory().isEmpty());
    }
}
