package dev.whizzle.blueprints;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Strict, atomic snapshots. Corruption must never silently discard funded jobs. */
public final class JobRepository {
    private final Path file;
    private boolean previousClean = true;
    public JobRepository(JavaPlugin plugin) { this(plugin.getDataFolder().toPath().resolve("jobs.yml")); }
    public JobRepository(Path file) { this.file = file; }
    public boolean previousClean() { return previousClean; }

    public List<BuildJob> load() {
        if (!Files.exists(file)) return List.of();
        try {
            YamlConfiguration yml = new YamlConfiguration();
            yml.load(file.toFile());
            if (yml.getInt("schema", -1) != 1) throw new IllegalArgumentException("Unsupported jobs schema; archive legacy jobs.yml manually");
            previousClean = yml.getBoolean("clean-shutdown", false);
            ConfigurationSection jobs = Objects.requireNonNull(yml.getConfigurationSection("jobs"), "Missing jobs section");
            List<BuildJob> result = new ArrayList<>();
            for (String key : jobs.getKeys(false)) {
                ConfigurationSection s = Objects.requireNonNull(jobs.getConfigurationSection(key));
                List<ConstructionStep> steps = new ArrayList<>();
                for (Object entry : Objects.requireNonNull(s.getList("plan.steps"), "Missing steps list")) {
                    if (!(entry instanceof Map<?, ?> row)) throw new IllegalArgumentException("Step must be a mapping");
                    if (!(row.get("blocks") instanceof List<?> rawBlocks)) throw new IllegalArgumentException("Missing step blocks");
                    List<BlueprintBlock> blocks = new ArrayList<>();
                    for (Object raw : rawBlocks) {
                        Map<?, ?> b = (Map<?, ?>) raw;
                        blocks.add(new BlueprintBlock(integer(b.get("x")), integer(b.get("y")), integer(b.get("z")), (String) b.get("data")));
                    }
                    steps.add(new ConstructionStep(blocks, (String) row.get("material"), integer(row.get("amount"))));
                }
                ConstructionPlan plan = new ConstructionPlan(required(s, "plan.id"), required(s, "plan.name"), steps);
                BuildJob job = new BuildJob(UUID.fromString(key), UUID.fromString(required(s, "owner")), plan,
                    UUID.fromString(required(s, "world")), coordinate(s, "origin.x"), coordinate(s, "origin.y"), coordinate(s, "origin.z"),
                    coordinate(s, "chest.x"), coordinate(s, "chest.y"), coordinate(s, "chest.z"), coordinate(s, "next-step"),
                    BuildJob.State.valueOf(required(s, "state")), s.getString("reason", ""));
                if (!previousClean && !job.terminal()) job.state(BuildJob.State.RECOVERY_REQUIRED,
                    "Unclean shutdown: inspect world and chest, then cancel; no automatic replay");
                result.add(job);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot safely load " + file + "; original file preserved", e);
        }
    }
    private static String required(ConfigurationSection s, String path) { return Objects.requireNonNull(s.getString(path), path); }
    private static int integer(Object value) {
        if (!(value instanceof Integer n)) throw new IllegalArgumentException("Expected integer, got " + value);
        return n;
    }
    private static int coordinate(ConfigurationSection s, String path) { return integer(s.get(path)); }

    public void save(Collection<BuildJob> jobs, boolean cleanShutdown) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("schema", 1);
        yml.set("clean-shutdown", cleanShutdown);
        yml.createSection("jobs");
        for (BuildJob job : jobs) {
            String p = "jobs." + job.id();
            yml.set(p + ".owner", job.owner().toString());
            yml.set(p + ".world", job.world().toString());
            yml.set(p + ".origin.x", job.originX()); yml.set(p + ".origin.y", job.originY()); yml.set(p + ".origin.z", job.originZ());
            yml.set(p + ".chest.x", job.chestX()); yml.set(p + ".chest.y", job.chestY()); yml.set(p + ".chest.z", job.chestZ());
            yml.set(p + ".next-step", job.nextBlock()); yml.set(p + ".state", job.state().name()); yml.set(p + ".reason", job.pauseReason());
            yml.set(p + ".plan.id", job.plan().id()); yml.set(p + ".plan.name", job.plan().name());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (ConstructionStep step : job.plan().steps()) {
                List<Map<String, Object>> blocks = new ArrayList<>();
                for (BlueprintBlock b : step.blocks()) blocks.add(Map.of("x", b.x(), "y", b.y(), "z", b.z(), "data", b.blockData()));
                steps.add(Map.of("material", step.material(), "amount", step.amount(), "blocks", blocks));
            }
            yml.set(p + ".plan.steps", steps);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            byte[] bytes = yml.saveToString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Fail closed on filesystems without atomic replacement; never silently use a torn-write-prone fallback.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new IllegalStateException("Cannot persist jobs; construction must stop", e); }
    }
}
