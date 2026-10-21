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
    private final Map<String, Owner> owners = new HashMap<>();
    private final Map<String, Path> sources = new HashMap<>();

    /** Who captured or uploaded a blueprint. Hand-written and pre-ownership files have none. */
    public record Owner(UUID id, String name) {
        public Owner { Objects.requireNonNull(id); name = name == null || name.isBlank() ? id.toString().substring(0, 8) : name; }
    }

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
        owners.clear();
        sources.clear();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration yml = readYaml(file);
                ConstructionPlan def = PlanCompiler.compile(parse(yml, file));
                String key = def.id().toLowerCase(Locale.ROOT);
                if (blueprints.putIfAbsent(key, def) != null) throw new IllegalArgumentException("Duplicate blueprint id: " + def.id());
                sources.put(key, file.toPath());
                Owner owner = owner(yml);
                if (owner != null) owners.put(key, owner);
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

    public Optional<Owner> owner(String id) { return Optional.ofNullable(owners.get(id.toLowerCase(Locale.ROOT))); }

    /** How many blueprints this player captured or uploaded. */
    public int ownedBy(UUID player) { return (int) owners.values().stream().filter(o -> o.id().equals(player)).count(); }

    /** Owners may delete their own blueprints; admins may delete any. */
    public boolean canDelete(String id, UUID player, boolean admin) {
        if (get(id).isEmpty()) return false;
        return admin || owner(id).map(o -> o.id().equals(player)).orElse(false);
    }

    /**
     * Removes a blueprint from the catalog and moves its file to blueprints/trash (operators can restore it by
     * moving it back and restarting). Running builds keep their own snapshot and are not affected.
     */
    public void delete(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (!blueprints.containsKey(key)) throw new IllegalArgumentException("Unknown blueprint: " + id);
        Path file = sources.get(key);
        try {
            if (file != null && Files.exists(file)) {
                Path trash = directory.toPath().resolve("trash");
                Files.createDirectories(trash);
                Path target = trash.resolve(file.getFileName());
                if (Files.exists(target)) target = trash.resolve(stripExtension(file.getFileName().toString()) + "-" + System.currentTimeMillis() + ".yml");
                Files.move(file, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not remove blueprint file for " + id + ": " + e.getMessage(), e);
        }
        blueprints.remove(key);
        owners.remove(key);
        sources.remove(key);
    }

    public static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Blueprint id must use 1..64 lowercase letters, digits, underscores, or hyphens");
    }

    /** Publish a fully validated capture. Existing source files and plans are never overwritten. */
    public void saveNew(ConstructionPlan plan) { saveNew(plan, null); }

    /** Publish a fully validated capture or upload, recording who made it (may be null). */
    public void saveNew(ConstructionPlan plan, Owner owner) {
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
            if (owner != null) { yaml.set("owner.uuid", owner.id().toString()); yaml.set("owner.name", owner.name()); }
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
            sources.put(plan.id(), target);
            if (owner != null) owners.put(plan.id(), owner);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save blueprint: " + plan.id(), e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException e) { plugin.getLogger().warning("Could not remove capture temporary file: " + temporary); }
        }
    }

    private static YamlConfiguration readYaml(File file) {
        YamlConfiguration yml = new YamlConfiguration();
        try { yml.load(file); } catch (Exception e) { throw new IllegalArgumentException("Invalid YAML", e); }
        return yml;
    }

    private static Owner owner(YamlConfiguration yml) {
        String uuid = yml.getString("owner.uuid");
        if (uuid == null) return null;
        try { return new Owner(UUID.fromString(uuid), yml.getString("owner.name")); }
        catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Reads an uploaded blueprint YAML (the format capture writes) under the given id. Any id/owner inside the
     * file is ignored; the result still goes through {@link PlanCompiler} before it can be saved.
     */
    public static ConstructionPlan fromYaml(byte[] data, String id) {
        YamlConfiguration yml = new YamlConfiguration();
        try { yml.loadFromString(new String(data, StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid blueprint YAML: " + e.getMessage(), e); }
        BlueprintDefinition parsed = parse(yml, id);
        return PlanCompiler.compile(parsed);
    }

    /** True when the bytes are a gzip stream (.litematic); anything else is treated as blueprint YAML. */
    public static boolean isGzip(byte[] data) {
        return data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
    }

    private static BlueprintDefinition parse(YamlConfiguration yml, File file) {
        return parse(yml, Objects.requireNonNullElse(yml.getString("id"), stripExtension(file.getName())));
    }

    private static BlueprintDefinition parse(YamlConfiguration yml, String id) {
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
