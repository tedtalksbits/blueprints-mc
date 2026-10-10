package dev.whizzle.blueprints;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JobRepositoryTest {
    @TempDir Path directory;
    private ConstructionPlan plan() {
        return new ConstructionPlan("test", "Test", List.of(
            new ConstructionStep(List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")), "STONE", 1),
            new ConstructionStep(List.of(new BlueprintBlock(1, 0, 0, "minecraft:oak_slab[type=double,waterlogged=false]")), "OAK_SLAB", 2)));
    }
    private BuildJob job() { return new BuildJob(UUID.randomUUID(), UUID.randomUUID(), plan(), UUID.randomUUID(), -32, 64, 8, -34, 64, 8, 0, BuildJob.State.BUILDING, ""); }
    @Test void cleanRestartPreservesSnapshotAndCursor() {
        JobRepository repo = new JobRepository(directory.resolve("jobs.yml"));
        BuildJob original = job(); original.advance(); original.state(BuildJob.State.PAUSED_MATERIALS, "Missing slabs");
        repo.save(List.of(original), true);
        BuildJob restored = repo.load().getFirst();
        assertEquals(original.id(), restored.id()); assertEquals(original.owner(), restored.owner());
        assertEquals(original.world(), restored.world()); assertEquals(original.plan(), restored.plan());
        assertEquals(-32, restored.originX()); assertEquals(-34, restored.chestX());
        assertEquals(1, restored.nextBlock()); assertEquals(BuildJob.State.PAUSED_MATERIALS, restored.state());
        assertEquals("Missing slabs", restored.pauseReason()); assertTrue(repo.previousClean());
    }
    @Test void uncleanRestartQuarantinesUnfinishedJobsButPreservesTerminalJobs() {
        JobRepository repo = new JobRepository(directory.resolve("jobs.yml"));
        BuildJob pending = job(), complete = job(), cancelled = job();
        complete.advance(); complete.advance(); cancelled.cancel();
        repo.save(List.of(pending, complete, cancelled), false);
        var jobs = repo.load();
        assertEquals(BuildJob.State.RECOVERY_REQUIRED, jobs.get(0).state());
        assertFalse(jobs.get(0).runnable()); assertThrows(IllegalStateException.class, jobs.get(0)::advance);
        assertEquals(BuildJob.State.COMPLETE, jobs.get(1).state());
        assertEquals(BuildJob.State.CANCELLED, jobs.get(2).state());
        repo.save(jobs, true);
        assertEquals(BuildJob.State.RECOVERY_REQUIRED, repo.load().getFirst().state());
    }
    @Test void corruptFileIsNeverSilentlyIgnoredOrOverwritten() throws Exception {
        Path file = directory.resolve("jobs.yml"); String broken = "schema: 1\njobs: [broken\n";
        Files.writeString(file, broken);
        assertThrows(IllegalStateException.class, () -> new JobRepository(file).load());
        assertEquals(broken, Files.readString(file));
    }
    @Test void legacySchemaFailsClosed() throws Exception {
        Path file = directory.resolve("jobs.yml"); Files.writeString(file, "jobs: {}\n");
        assertThrows(IllegalStateException.class, () -> new JobRepository(file).load());
    }
    @Test void atomicReplacementAndEmptySnapshotRoundTrip() {
        JobRepository repo = new JobRepository(directory.resolve("jobs.yml"));
        repo.save(List.of(job()), false); repo.save(List.of(), true);
        assertTrue(repo.load().isEmpty()); assertTrue(repo.previousClean());
    }
    @Test void invalidCursorCannotBecomeAJob() {
        BuildJob original = job();
        assertThrows(IllegalArgumentException.class, () -> new BuildJob(original.id(), original.owner(), plan(), original.world(), 0, 0, 0, 0, 0, 0, 3, BuildJob.State.BUILDING, ""));
    }
    @Test void failedReplacementLeavesOriginalSnapshotIntact() throws Exception {
        Path file = directory.resolve("jobs.yml"); JobRepository repo = new JobRepository(file);
        repo.save(List.of(job()), true); String original = Files.readString(file);
        Files.createDirectory(directory.resolve("jobs.yml.tmp"));
        assertThrows(IllegalStateException.class, () -> repo.save(List.of(), false));
        assertEquals(original, Files.readString(file));
    }
    @Test void modelCopiesInputsAndTotalsCosts() {
        List<ConstructionStep> steps = new ArrayList<>(plan().steps());
        ConstructionPlan plan = new ConstructionPlan("test", "Test", steps); steps.clear();
        assertEquals(Map.of("STONE", 1, "OAK_SLAB", 2), plan.materials());
        assertThrows(UnsupportedOperationException.class, () -> plan.steps().clear());
    }
}
