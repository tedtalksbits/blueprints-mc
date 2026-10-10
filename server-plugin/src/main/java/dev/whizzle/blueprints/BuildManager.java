package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class BuildManager {
    private final JavaPlugin plugin;
    private final BlueprintRepository blueprints;
    private final JobRepository jobRepository;
    private final Map<UUID, BuildJob> jobs = new LinkedHashMap<>();
    private final boolean overwrite;
    private int saveCounter = 0;

    public BuildManager(JavaPlugin plugin, BlueprintRepository blueprints, JobRepository jobRepository) {
        this.plugin = plugin;
        this.blueprints = blueprints;
        this.jobRepository = jobRepository;
        this.overwrite = plugin.getConfig().getBoolean("safety.overwrite-existing-blocks", false);
        for (BuildJob job : jobRepository.load()) jobs.put(job.id(), job);
    }

    public Collection<BuildJob> jobs() { return Collections.unmodifiableCollection(jobs.values()); }

    public BuildJob start(Player owner, BlueprintDefinition blueprint, Location origin, Chest chest) {
        int fee = plugin.getConfig().getInt("labor-fee.emeralds", 0);
        if (fee > 0 && !InventoryUtil.consume(chest.getBlockInventory(), Material.EMERALD, fee)) {
            throw new IllegalStateException("Builder's chest needs " + fee + " emerald(s) for the labor fee.");
        }
        BuildJob job = new BuildJob(UUID.randomUUID(), owner.getUniqueId(), blueprint.id(),
            origin.getWorld().getName(), origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
            chest.getX(), chest.getY(), chest.getZ(), 0, BuildJob.State.BUILDING, "");
        jobs.put(job.id(), job);
        save();
        return job;
    }

    public boolean cancel(UUID id, UUID requester, boolean admin) {
        BuildJob job = jobs.get(id);
        if (job == null) return false;
        if (!admin && !job.owner().equals(requester)) return false;
        job.cancel();
        save();
        return true;
    }

    public void tick() {
        int max = plugin.getConfig().getInt("construction.blocks-per-cycle", 1);
        for (BuildJob job : jobs.values()) {
            if (job.state() == BuildJob.State.COMPLETE || job.state() == BuildJob.State.CANCELLED) continue;
            for (int i = 0; i < max; i++) {
                if (!placeNext(job)) break;
            }
        }
        if (++saveCounter >= 20) { // roughly every 10 seconds with a 10-tick cycle
            saveCounter = 0;
            save();
        }
    }

    private boolean placeNext(BuildJob job) {
        BlueprintDefinition blueprint = blueprints.get(job.blueprintId()).orElse(null);
        World world = Bukkit.getWorld(job.world());
        if (blueprint == null || world == null) {
            job.state(BuildJob.State.PAUSED_OBSTRUCTION, "Blueprint/world unavailable");
            return false;
        }
        if (job.nextBlock() >= blueprint.blocks().size()) {
            job.state(BuildJob.State.COMPLETE, "Complete");
            notifyOwner(job, "§aBlueprint complete: §f" + blueprint.name());
            return false;
        }

        int originChunkX = job.originX() >> 4;
        int originChunkZ = job.originZ() >> 4;
        int chestChunkX = job.chestX() >> 4;
        int chestChunkZ = job.chestZ() >> 4;
        if (!world.isChunkLoaded(originChunkX, originChunkZ) || !world.isChunkLoaded(chestChunkX, chestChunkZ)) {
            job.state(BuildJob.State.PAUSED_CHUNK, "Build/chest chunk unloaded");
            return false;
        }

        Block chestBlock = world.getBlockAt(job.chestX(), job.chestY(), job.chestZ());
        if (!(chestBlock.getState() instanceof Chest chest)) {
            job.state(BuildJob.State.PAUSED_OBSTRUCTION, "Builder's chest missing");
            return false;
        }

        BlueprintBlock spec = blueprint.blocks().get(job.nextBlock());
        Block target = world.getBlockAt(job.originX() + spec.x(), job.originY() + spec.y(), job.originZ() + spec.z());
        BlockData expected;
        try {
            expected = Bukkit.createBlockData(spec.blockData());
        } catch (IllegalArgumentException e) {
            job.state(BuildJob.State.PAUSED_OBSTRUCTION, "Invalid block data: " + spec.blockData());
            return false;
        }

        if (target.getBlockData().matches(expected)) {
            job.advance();
            return true;
        }
        if (!target.getType().isAir() && !overwrite) {
            job.state(BuildJob.State.PAUSED_OBSTRUCTION,
                "Obstructed at " + target.getX() + "," + target.getY() + "," + target.getZ() + " by " + target.getType());
            return false;
        }

        Material cost = MaterialCost.forBlockData(spec.blockData());
        if (cost == null) {
            job.state(BuildJob.State.PAUSED_OBSTRUCTION, "No safe item-cost mapping for " + expected.getMaterial());
            return false;
        }
        Inventory inv = chest.getBlockInventory();
        if (!InventoryUtil.consume(inv, cost, 1)) {
            job.state(BuildJob.State.PAUSED_MATERIALS, "Missing " + cost);
            return false;
        }

        target.setBlockData(expected, false); // exact state: facing/half/axis/etc; physics suppressed during construction
        job.advance();
        return true;
    }

    private void notifyOwner(BuildJob job, String message) {
        Player player = Bukkit.getPlayer(job.owner());
        if (player != null) player.sendMessage(message);
    }

    public void save() { jobRepository.save(jobs.values()); }
}
