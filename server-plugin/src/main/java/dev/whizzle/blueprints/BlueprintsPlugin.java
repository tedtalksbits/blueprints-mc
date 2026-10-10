package dev.whizzle.blueprints;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class BlueprintsPlugin extends JavaPlugin {
    private BuildManager buildManager;
    private BukkitTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BlueprintRepository blueprints = new BlueprintRepository(this);
        blueprints.load();
        JobRepository jobs = new JobRepository(this);
        buildManager = new BuildManager(this, blueprints, jobs);

        BlueprintCommand command = new BlueprintCommand(blueprints, buildManager);
        PluginCommand registered = getCommand("blueprint");
        if (registered == null) throw new IllegalStateException("blueprint command missing from plugin.yml");
        registered.setExecutor(command);
        registered.setTabCompleter(command);

        long ticks = Math.max(1L, getConfig().getLong("construction.ticks-between-cycles", 10L));
        task = getServer().getScheduler().runTaskTimer(this, buildManager::tick, ticks, ticks);
        getLogger().info("Blueprints enabled. Survival construction engine is running.");
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (buildManager != null) buildManager.save();
    }
}
