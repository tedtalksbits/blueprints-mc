package dev.whizzle.blueprints;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BlueprintRepository {
    private final JavaPlugin plugin;
    private final File directory;
    private final Map<String, ConstructionPlan> blueprints = new TreeMap<>();

    public BlueprintRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "blueprints");
    }

    public void load() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create blueprint directory: " + directory);
        }
        installExampleIfMissing();
        blueprints.clear();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                ConstructionPlan def = PlanCompiler.compile(parse(file));
                if (blueprints.putIfAbsent(def.id().toLowerCase(Locale.ROOT), def) != null) throw new IllegalArgumentException("Duplicate blueprint id: " + def.id());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + blueprints.size() + " blueprint(s).");
    }

    public Optional<ConstructionPlan> get(String id) {
        return Optional.ofNullable(blueprints.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<ConstructionPlan> all() {
        return Collections.unmodifiableCollection(blueprints.values());
    }

    public static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Blueprint id must use 1..64 lowercase letters, digits, underscores, or hyphens");
    }

    /** Publish a fully validated capture. Existing source files and plans are never overwritten. */
    public void saveNew(ConstructionPlan plan) {
        validateId(plan.id());
        if (blueprints.containsKey(plan.id())) throw new IllegalArgumentException("Blueprint already exists: " + plan.id());
        ConstructionPlan verified = PlanCompiler.compile(new BlueprintDefinition(plan.id(), plan.name(),
            plan.steps().stream().flatMap(s -> s.blocks().stream()).toList()));
        if (!verified.equals(plan)) throw new IllegalArgumentException("Capture plan must be canonical and have validated costs");
        Path temporary = null;
        try {
            Files.createDirectories(directory.toPath());
            // Prefix also avoids Windows reserved basenames such as CON and NUL.
            Path target = directory.toPath().resolve("blueprint-" + plan.id() + ".yml");
            if (Files.exists(target)) throw new IllegalArgumentException("Blueprint file already exists: " + plan.id());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("id", plan.id());
            yaml.set("name", plan.name());
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (ConstructionStep step : plan.steps()) for (BlueprintBlock b : step.blocks())
                blocks.add(Map.of("x", b.x(), "y", b.y(), "z", b.z(), "block-data", b.blockData()));
            yaml.set("blocks", blocks);
            temporary = Files.createTempFile(directory.toPath(), ".capture-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, target); // No REPLACE_EXISTING: a concurrent/external file must survive.
            blueprints.put(plan.id(), plan);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save blueprint: " + plan.id(), e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException e) { plugin.getLogger().warning("Could not remove capture temporary file: " + temporary); }
        }
    }

    private BlueprintDefinition parse(File file) {
        YamlConfiguration yml = new YamlConfiguration();
        try { yml.load(file); } catch (Exception e) { throw new IllegalArgumentException("Invalid YAML", e); }
        String id = Objects.requireNonNullElse(yml.getString("id"), stripExtension(file.getName()));
        String name = Objects.requireNonNullElse(yml.getString("name"), id);
        List<?> raw = Objects.requireNonNull(yml.getList("blocks"), "Missing blocks list");
        List<BlueprintBlock> blocks = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> row)) throw new IllegalArgumentException("Block entry must be a mapping");
            int x = intValue(row.get("x"));
            int y = intValue(row.get("y"));
            int z = intValue(row.get("z"));
            Object data = row.get("block-data");
            if (data == null) throw new IllegalArgumentException("Block entry missing block-data");
            blocks.add(new BlueprintBlock(x, y, z, data.toString()));
        }
        return new BlueprintDefinition(id, name, List.copyOf(blocks));
    }

    private static int intValue(Object value) {
        if (value instanceof Integer n) return n;
        return Integer.parseInt(String.valueOf(value));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void installExampleIfMissing() {
        File target = new File(directory, "orientation-demo.yml");
        if (target.exists()) return;
        try (InputStream in = plugin.getResource("orientation-demo.yml")) {
            if (in != null) Files.copy(in, target.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Unable to install example blueprint", e);
        }
    }
}
