package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanTransformerTest {
    @TempDir Path directory;
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    ConstructionPlan plan(String data) {
        return PlanCompiler.compile(new BlueprintDefinition("test", "Test", List.of(new BlueprintBlock(2, 1, 3, "minecraft:" + data))));
    }
    BlueprintBlock only(ConstructionPlan plan) { return plan.steps().getFirst().blocks().getFirst(); }
    ConstructionPlan transform(ConstructionPlan plan, int rotation, BlueprintTransform.Mirror mirror) {
        return PlanTransformer.apply(plan, new BlueprintTransform(rotation, mirror));
    }
    @Test void clockwiseCoordinatesAndCompositionAreExplicit() {
        assertEquals(new BlueprintTransform.Position(-3, 1, 2), new BlueprintTransform(90, BlueprintTransform.Mirror.NONE).apply(2, 1, 3));
        assertEquals(new BlueprintTransform.Position(-3, 1, -2), new BlueprintTransform(90, BlueprintTransform.Mirror.X).apply(2, 1, 3));
        assertEquals(new BlueprintTransform.Position(3, 1, 2), new BlueprintTransform(90, BlueprintTransform.Mirror.Z).apply(2, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> BlueprintTransform.parse("45", "none"));
        assertThrows(IllegalArgumentException.class, () -> BlueprintTransform.parse("0", "y"));
        assertThrows(ArithmeticException.class, () -> new BlueprintTransform(0, BlueprintTransform.Mirror.X).apply(Integer.MIN_VALUE, 0, 0));
    }
    @Test void clockwiseRotationPreservesTopHalfAndCornerShape() {
        ConstructionPlan original = plan("oak_stairs[facing=north,half=top,shape=inner_left,waterlogged=false]");
        var result = only(transform(original, 90, BlueprintTransform.Mirror.NONE));
        assertEquals(-3, result.x()); assertEquals(2, result.z()); assertEquals(1, result.y());
        assertEquals("minecraft:oak_stairs[facing=east,half=top,shape=inner_left,waterlogged=false]", result.blockData());
        assertTrue(only(original).blockData().contains("facing=north"));
    }
    @Test void allStairCornersReflectTheirHandednessForEveryFacing() {
        for (String facing : List.of("north", "east", "south", "west")) for (String shape : List.of("inner_left", "inner_right", "outer_left", "outer_right", "straight")) {
            var original = plan("oak_stairs[facing=" + facing + ",half=top,shape=" + shape + "]");
            for (var mirror : List.of(BlueprintTransform.Mirror.X, BlueprintTransform.Mirror.Z)) {
                var result = transform(original, 0, mirror);
                Stairs stairs = (Stairs) Bukkit.createBlockData(only(result).blockData());
                String expected = shape.endsWith("left") ? shape.replace("left", "right") : shape.replace("right", "left");
                assertEquals(expected.toUpperCase(Locale.ROOT), stairs.getShape().name());
                assertEquals(original, transform(result, 0, mirror));
            }
        }
    }
    @Test void logAxisAndTrapdoorPropertiesArePreserved() {
        assertEquals("minecraft:oak_log[axis=z]", only(transform(plan("oak_log[axis=x]"), 90, BlueprintTransform.Mirror.NONE)).blockData());
        assertEquals("minecraft:oak_log[axis=y]", only(transform(plan("oak_log[axis=y]"), 90, BlueprintTransform.Mirror.X)).blockData());
        var trapdoor = transform(plan("oak_trapdoor[facing=east,half=top,open=true,powered=false,waterlogged=false]"), 0, BlueprintTransform.Mirror.X);
        assertEquals("minecraft:oak_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]", only(trapdoor).blockData());
    }
    @Test void fullRotationAndDoubleMirrorAreIdentityAcrossPalette() {
        for (String data : List.of("stone", "oak_log[axis=x]", "oak_stairs[facing=west,shape=outer_right]", "oak_slab[type=double]", "oak_trapdoor[facing=north,half=top,open=true]", "white_glazed_terracotta[facing=east]")) {
            var original = plan(data); var rotated = original;
            for (int i = 0; i < 4; i++) rotated = transform(rotated, 90, BlueprintTransform.Mirror.NONE);
            assertEquals(original, rotated);
            for (var mirror : BlueprintTransform.Mirror.values()) for (int rotation : List.of(0, 90, 180, 270)) {
                var result = transform(original, rotation, mirror);
                assertEquals(original.materials(), result.materials());
                assertEquals(original.blockCount(), result.blockCount());
            }
        }
    }
    @Test void mirroredDoorRemainsOnePaidStepAndSurvivesSnapshotReload() {
        var source = PlanCompiler.compile(new BlueprintDefinition("door", "Door", List.of(
            new BlueprintBlock(2, 0, 3, "minecraft:oak_door[facing=north,half=lower,hinge=right,open=true,powered=false]"),
            new BlueprintBlock(2, 1, 3, "minecraft:oak_door[facing=north,half=upper,hinge=right,open=true,powered=false]"))));
        var result = transform(source, 90, BlueprintTransform.Mirror.X);
        assertEquals(Map.of("OAK_DOOR", 1), result.materials()); assertEquals(1, result.steps().size());
        for (var block : result.steps().getFirst().blocks()) {
            var door = (Door) Bukkit.createBlockData(block.blockData());
            assertEquals(Door.Hinge.LEFT, door.getHinge()); assertEquals(org.bukkit.block.BlockFace.EAST, door.getFacing()); assertTrue(door.isOpen());
            assertEquals(-3, block.x()); assertEquals(-2, block.z());
        }
        var job = new BuildJob(UUID.randomUUID(), UUID.randomUUID(), result, UUID.randomUUID(), 0, 64, 0, 6, 64, 0, 0, BuildJob.State.BUILDING, "");
        var repository = new JobRepository(directory.resolve("jobs.yml")); repository.save(List.of(job), true);
        assertEquals(result, repository.load().getFirst().plan());
    }
}
