package dev.whizzle.blueprints;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public final class BlueprintRepository {
    private final JavaPlugin plugin;
    private final File directory;
    private final Map<String, BlueprintDefinition> blueprints = new TreeMap<>();

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
                BlueprintDefinition def = parse(file);
                blueprints.put(def.id().toLowerCase(Locale.ROOT), def);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + blueprints.size() + " blueprint(s).");
    }

    public Optional<BlueprintDefinition> get(String id) {
        return Optional.ofNullable(blueprints.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<BlueprintDefinition> all() {
        return Collections.unmodifiableCollection(blueprints.values());
    }

    private BlueprintDefinition parse(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String id = Objects.requireNonNullElse(yml.getString("id"), stripExtension(file.getName()));
        String name = Objects.requireNonNullElse(yml.getString("name"), id);
        List<Map<?, ?>> raw = yml.getMapList("blocks");
        List<BlueprintBlock> blocks = new ArrayList<>(raw.size());
        for (Map<?, ?> row : raw) {
            int x = intValue(row.get("x"));
            int y = intValue(row.get("y"));
            int z = intValue(row.get("z"));
            Object data = row.get("block-data");
            if (data == null) throw new IllegalArgumentException("Block entry missing block-data");
            blocks.add(new BlueprintBlock(x, y, z, data.toString()));
        }
        blocks.sort(Comparator.comparingInt(BlueprintBlock::y)
            .thenComparingInt(BlueprintBlock::x)
            .thenComparingInt(BlueprintBlock::z));
        return new BlueprintDefinition(id, name, List.copyOf(blocks));
    }

    private static int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
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
