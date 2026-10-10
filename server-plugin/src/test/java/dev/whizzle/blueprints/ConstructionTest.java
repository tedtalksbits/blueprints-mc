package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConstructionTest {
    @TempDir Path directory;
    ServerMock server;
    WorldMock world;
    PlayerMock owner;
    JavaPlugin plugin;
    JobRepository repository;
    BuildManager manager;
    Chest chest;
    @BeforeEach void setUp() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("build-test");
        owner = server.addPlayer(); owner.setOp(true); owner.setGameMode(GameMode.SURVIVAL);
        owner.teleport(new Location(world, 0, 65, 0));
        world.getBlockAt(0, 65, 0).setType(Material.CHEST);
        chest = (Chest) world.getBlockAt(0, 65, 0).getState();
        world.loadChunk(0, 0);
        plugin = MockBukkit.createMockPlugin();
        repository = new JobRepository(directory.resolve("jobs.yml"));
        manager = new BuildManager(plugin, repository);
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    ConstructionPlan plan(BlueprintBlock... blocks) { return PlanCompiler.compile(new BlueprintDefinition("test", "Test", List.of(blocks))); }
    BlueprintBlock stone(int x) { return new BlueprintBlock(x, 0, 0, "minecraft:stone"); }
    BuildJob start(ConstructionPlan plan) { return manager.start(owner, plan, new Location(world, 2, 65, 2), chest); }
    void put(Material material, int count) { chest.getBlockInventory().addItem(new ItemStack(material, count)); }

    @Test void missingMaterialsResumeAndCleanRestartDoesNotChargeTwice() {
        BuildJob job = start(plan(stone(0), stone(1)));
        manager.tick(); assertEquals(BuildJob.State.PAUSED_MATERIALS, job.state()); assertEquals(0, job.nextBlock());
        put(Material.STONE, 1); manager.tick(); assertEquals(1, job.nextBlock());
        assertEquals(Material.STONE, world.getBlockAt(2, 65, 2).getType());
        manager.shutdown();
        manager = new BuildManager(plugin, new JobRepository(directory.resolve("jobs.yml")));
        BuildJob restored = manager.jobs().iterator().next();
        manager.tick(); assertEquals(BuildJob.State.PAUSED_MATERIALS, restored.state());
        put(Material.STONE, 1); manager.tick(); assertEquals(BuildJob.State.COMPLETE, restored.state());
        assertTrue(chest.getBlockInventory().isEmpty()); assertEquals(Material.STONE, world.getBlockAt(3, 65, 2).getType());
    }
    @Test void stateTransitionsAreCheckpointedImmediately() {
        BuildJob job = start(plan(stone(0), stone(1)));
        manager.tick();
        assertEquals("PAUSED_MATERIALS", persisted(job).getString("state"));
        put(Material.STONE, 2); manager.tick();
        assertEquals("BUILDING", persisted(job).getString("state")); assertEquals(1, persisted(job).getInt("next-step"));
        manager.tick();
        assertEquals("COMPLETE", persisted(job).getString("state")); assertEquals(2, persisted(job).getInt("next-step"));
    }
    // Raw file read: JobRepository.load() would (correctly) quarantine jobs from a still-running session.
    org.bukkit.configuration.ConfigurationSection persisted(BuildJob job) {
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(directory.resolve("jobs.yml").toFile())
            .getConfigurationSection("jobs." + job.id());
    }
    @Test void exactExistingStateIsFreeButDifferentOrientationObstructs() {
        String stairs = "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]";
        world.getBlockAt(2, 65, 2).setBlockData(Bukkit.createBlockData(stairs));
        BuildJob job = start(plan(new BlueprintBlock(0, 0, 0, stairs), new BlueprintBlock(1, 0, 0, stairs)));
        put(Material.OAK_STAIRS, 1); manager.tick(); assertEquals(1, job.nextBlock());
        world.getBlockAt(3, 65, 2).setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[facing=east,half=top]"));
        manager.tick(); assertEquals(BuildJob.State.PAUSED_OBSTRUCTION, job.state());
        assertEquals(1, chest.getBlockInventory().getItem(0).getAmount());
        world.getBlockAt(3, 65, 2).setType(Material.AIR); manager.tick();
        assertEquals(stairs, world.getBlockAt(3, 65, 2).getBlockData().getAsString());
        assertEquals(BuildJob.State.COMPLETE, job.state());
    }
    @Test void doorHalvesPlacedTogetherForOneItemAndPartialDoorPauses() {
        var lower = new BlueprintBlock(0, 0, 0, "minecraft:oak_door[facing=west,half=lower,hinge=right,open=false,powered=false]");
        var upper = new BlueprintBlock(0, 1, 0, "minecraft:oak_door[facing=west,half=upper,hinge=right,open=false,powered=false]");
        BuildJob job = start(plan(lower, upper)); put(Material.OAK_DOOR, 1);
        world.getBlockAt(2, 65, 2).setBlockData(Bukkit.createBlockData(lower.blockData())); manager.tick();
        assertEquals(BuildJob.State.PAUSED_OBSTRUCTION, job.state()); assertFalse(chest.getBlockInventory().isEmpty());
        world.getBlockAt(2, 65, 2).setType(Material.AIR); manager.tick();
        assertEquals(BuildJob.State.COMPLETE, job.state()); assertTrue(chest.getBlockInventory().isEmpty());
        assertEquals(Bukkit.createBlockData(upper.blockData()).getAsString(), world.getBlockAt(2, 66, 2).getBlockData().getAsString());
    }
    @Test void uncleanRestartNeverReplaysEvenWhenCursorIsStale() {
        BuildJob job = start(plan(stone(0), stone(1))); put(Material.STONE, 2); manager.tick();
        // The on-disk dirty snapshot still has cursor zero; a crash cannot make this a free/repeated placement.
        manager = new BuildManager(plugin, new JobRepository(directory.resolve("jobs.yml")));
        BuildJob restored = manager.jobs().iterator().next(); manager.tick();
        assertEquals(BuildJob.State.RECOVERY_REQUIRED, restored.state()); assertEquals(0, restored.nextBlock());
        assertEquals(Material.AIR, world.getBlockAt(3, 65, 2).getType());
        assertEquals(1, chest.getBlockInventory().getItem(0).getAmount());
    }
    @Test void cancelRetainsPlacedBlocksAndNeverRefundsOrContinues() {
        BuildJob job = start(plan(stone(0), stone(1))); put(Material.STONE, 2); manager.tick();
        assertFalse(manager.cancel(job.id(), UUID.randomUUID(), false));
        assertTrue(manager.cancel(job.id(), owner.getUniqueId(), false)); manager.tick();
        assertEquals(Material.STONE, world.getBlockAt(2, 65, 2).getType());
        assertEquals(Material.AIR, world.getBlockAt(3, 65, 2).getType()); assertEquals(1, chest.getBlockInventory().getItem(0).getAmount());
    }
    @Test void ownerAndObstructionChecksNeverDebitMaterials() {
        BuildJob job = start(plan(stone(0))); put(Material.STONE, 1);
        owner.setGameMode(GameMode.CREATIVE); manager.tick(); assertEquals(BuildJob.State.PAUSED_OWNER, job.state());
        owner.setGameMode(GameMode.SURVIVAL); world.getBlockAt(2, 65, 2).setType(Material.DIRT); manager.tick();
        assertEquals(BuildJob.State.PAUSED_OBSTRUCTION, job.state()); assertEquals(1, chest.getBlockInventory().getItem(0).getAmount());
    }
    @Test void unloadedDestinationIsCheckedBeforeBlockAccess() {
        World spyWorld = spy(world);
        try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(spyWorld);
            BuildJob job = start(plan(stone(20))); put(Material.STONE, 1);
            when(spyWorld.isChunkLoaded(1, 0)).thenReturn(false);
            manager.tick(); assertEquals(BuildJob.State.PAUSED_CHUNK, job.state());
            verify(spyWorld, never()).getBlockAt(22, 65, 2);
            assertEquals(1, chest.getBlockInventory().getItem(0).getAmount());
        }
    }
    @Test void globalBudgetIsFairAndCannotMultiplyWithJobCount() {
        BuildJob first = start(plan(stone(0), stone(1))); put(Material.STONE, 2);
        PlayerMock second = server.addPlayer(); second.setOp(true); second.setGameMode(GameMode.SURVIVAL);
        second.teleport(new Location(world, 0, 65, 6));
        world.getBlockAt(0, 65, 6).setType(Material.CHEST);
        Chest otherChest = (Chest) world.getBlockAt(0, 65, 6).getState(); otherChest.getBlockInventory().addItem(new ItemStack(Material.STONE, 2));
        BuildJob other = manager.start(second, plan(stone(0), stone(1)), new Location(world, 2, 65, 6), otherChest);
        manager.tick(); assertEquals(1, first.nextBlock() + other.nextBlock());
        manager.tick(); assertEquals(1, first.nextBlock()); assertEquals(1, other.nextBlock());
    }
    @Test void limitsAndPermissionsRejectBeforeMutation() {
        owner.setOp(false);
        assertThrows(IllegalStateException.class, () -> start(plan(stone(0))));
        owner.setOp(true);
        assertThrows(IllegalStateException.class, () -> manager.start(owner, plan(stone(0)), new Location(world, 0, world.getMaxHeight(), 0), chest));
        assertThrows(IllegalStateException.class, () -> manager.start(owner, plan(stone(0)), chest.getLocation(), chest));
        start(plan(stone(0)));
        assertThrows(IllegalStateException.class, () -> start(plan(stone(1))));
    }
    @Test void failedCheckpointStopsAllFurtherDebitsAndKeepsDirtySession() throws Exception {
        BuildJob job = start(plan(stone(0), stone(1))); put(Material.STONE, 2);
        Files.createDirectory(directory.resolve("jobs.yml.tmp"));
        assertThrows(IllegalStateException.class, manager::save);
        manager.tick(); assertEquals(0, job.nextBlock()); assertEquals(2, chest.getBlockInventory().getItem(0).getAmount());
        assertEquals(BuildJob.State.RECOVERY_REQUIRED, new JobRepository(directory.resolve("jobs.yml")).load().getFirst().state());
    }
    @Test void bundledDemoCompilesAndHasExpectedMaterials() {
        BlueprintsPlugin loaded = MockBukkit.load(BlueprintsPlugin.class);
        BlueprintRepository blueprints = new BlueprintRepository(loaded); blueprints.load();
        ConstructionPlan demo = blueprints.get("orientation-demo").orElseThrow();
        assertEquals(38, demo.blockCount()); assertEquals(37, demo.steps().size());
        assertEquals(Map.of("STONE_BRICKS", 25, "OAK_STAIRS", 4, "OAK_LOG", 3, "OAK_SLAB", 4, "OAK_TRAPDOOR", 1, "OAK_DOOR", 1), demo.materials());
    }
}
