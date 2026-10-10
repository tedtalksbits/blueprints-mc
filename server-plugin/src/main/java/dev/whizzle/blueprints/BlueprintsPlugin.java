package dev.whizzle.blueprints;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BlueprintsPlugin extends JavaPlugin {
    private BuildManager buildManager;
    private BukkitTask task;
    private BuildersChest buildersChest;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Add newly introduced settings to an existing config.yml without touching the operator's values.
        getConfig().options().copyDefaults(true);
        saveConfig();
        BlueprintRepository blueprints = new BlueprintRepository(this);
        blueprints.load();
        JobRepository jobs = new JobRepository(this);
        buildManager = new BuildManager(this, jobs);

        buildersChest = new BuildersChest(this);
        buildersChest.registerRecipe();
        getServer().getPluginManager().registerEvents(buildersChest, this);
        int radius = Math.clamp(getConfig().getInt("builders-chest.search-radius", 16), 1, 64);
        java.nio.file.Path imports = getDataFolder().toPath().resolve("import");
        try { java.nio.file.Files.createDirectories(imports); }
        catch (java.io.IOException e) { throw new IllegalStateException("Could not create " + imports, e); }
        BuildStarter starter = new BuildStarter(blueprints, buildManager, radius);
        BlueprintCommand command = new BlueprintCommand(blueprints, buildManager, starter, imports);
        ClientBridge bridge = new ClientBridge(blueprints, buildManager, starter,
            (player, channel, data) -> player.sendPluginMessage(this, channel, data));
        for (String channel : ClientBridge.INCOMING) getServer().getMessenger().registerIncomingPluginChannel(this, channel, bridge);
        for (String channel : ClientBridge.OUTGOING) getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        PluginCommand registered = getCommand("blueprint");
        if (registered == null) throw new IllegalStateException("blueprint command missing from plugin.yml");
        registered.setExecutor(command);
        registered.setTabCompleter(command);

        long ticks = Math.max(1L, getConfig().getLong("construction.ticks-between-cycles", 10L));
        task = getServer().getScheduler().runTaskTimer(this, buildManager::tick, ticks, ticks);
        getLogger().info("Blueprints enabled. Survival construction engine is running.");
    }

    public BuildManager builds() { return buildManager; }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (buildersChest != null) buildersChest.unregisterRecipe();
        if (buildManager != null) buildManager.shutdown();
    }
}
