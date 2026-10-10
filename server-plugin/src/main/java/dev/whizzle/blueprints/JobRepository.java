package dev.whizzle.blueprints;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class JobRepository {
    private final JavaPlugin plugin;
    private final File file;

    public JobRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jobs.yml");
    }

    public List<BuildJob> load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection jobs = yml.getConfigurationSection("jobs");
        if (jobs == null) return new ArrayList<>();
        List<BuildJob> out = new ArrayList<>();
        for (String key : jobs.getKeys(false)) {
            ConfigurationSection s = jobs.getConfigurationSection(key);
            if (s == null) continue;
            try {
                out.add(new BuildJob(
                    UUID.fromString(key),
                    UUID.fromString(Objects.requireNonNull(s.getString("owner"))),
                    Objects.requireNonNull(s.getString("blueprint")),
                    Objects.requireNonNull(s.getString("world")),
                    s.getInt("origin.x"), s.getInt("origin.y"), s.getInt("origin.z"),
                    s.getInt("chest.x"), s.getInt("chest.y"), s.getInt("chest.z"),
                    s.getInt("next-block"),
                    BuildJob.State.valueOf(s.getString("state", "BUILDING")),
                    s.getString("pause-reason", "")
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping invalid job " + key + ": " + e.getMessage());
            }
        }
        return out;
    }

    public void save(Collection<BuildJob> jobs) {
        YamlConfiguration yml = new YamlConfiguration();
        for (BuildJob job : jobs) {
            String p = "jobs." + job.id();
            yml.set(p + ".owner", job.owner().toString());
            yml.set(p + ".blueprint", job.blueprintId());
            yml.set(p + ".world", job.world());
            yml.set(p + ".origin.x", job.originX());
            yml.set(p + ".origin.y", job.originY());
            yml.set(p + ".origin.z", job.originZ());
            yml.set(p + ".chest.x", job.chestX());
            yml.set(p + ".chest.y", job.chestY());
            yml.set(p + ".chest.z", job.chestZ());
            yml.set(p + ".next-block", job.nextBlock());
            yml.set(p + ".state", job.state().name());
            yml.set(p + ".pause-reason", job.pauseReason());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save jobs.yml: " + e.getMessage());
        }
    }
}
