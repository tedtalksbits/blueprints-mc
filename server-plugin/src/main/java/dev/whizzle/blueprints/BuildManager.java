package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class BuildManager {
    private final JavaPlugin plugin;
    private final JobRepository repository;
    private final Map<UUID, BuildJob> jobs = new LinkedHashMap<>();
    private final int budget, maxActive, maxActivePerPlayer;
    private int rotation, saveCounter;
    private boolean failed;

    public BuildManager(JavaPlugin plugin, JobRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        budget = Math.clamp(plugin.getConfig().getInt("construction.steps-per-cycle", 1), 1, 16);
        maxActive = Math.clamp(plugin.getConfig().getInt("construction.max-active-jobs", 16), 1, 64);
        maxActivePerPlayer = Math.clamp(plugin.getConfig().getInt("construction.max-active-jobs-per-player", 2), 1, maxActive);
        for (BuildJob job : repository.load()) {
            // Revalidate persisted snapshots against this server's block registry, including cost and order.
            ConstructionPlan checked = PlanCompiler.compile(new BlueprintDefinition(job.plan().id(), job.plan().name(),
                job.plan().steps().stream().flatMap(s -> s.blocks().stream()).toList()));
            if (!checked.equals(job.plan())) throw new IllegalStateException("Persisted plan changed or is invalid: " + job.id());
            jobs.put(job.id(), job);
        }
        save(); // Write the dirty-session marker before any world/inventory mutation.
    }
    public Collection<BuildJob> jobs() { return Collections.unmodifiableCollection(jobs.values()); }

    public BuildJob start(Player owner, ConstructionPlan plan, Location origin, Chest chest) {
        if (failed) throw new IllegalStateException("Construction stopped after a storage/placement error; see server log");
        if (!owner.hasPermission("blueprints.build") || owner.getGameMode() != GameMode.SURVIVAL)
            throw new IllegalStateException("Construction requires Survival mode and blueprints.build permission");
        if (jobs.size() >= 64) throw new IllegalStateException("Job history limit reached (64); archive jobs.yml while server is stopped");
        if (jobs.values().stream().filter(j -> !j.terminal()).count() >= maxActive)
            throw new IllegalStateException("Server active job limit reached (" + maxActive + ")");
        if (jobs.values().stream().filter(j -> !j.terminal() && j.owner().equals(owner.getUniqueId())).count() >= maxActivePerPlayer)
            throw new IllegalStateException("You already have " + maxActivePerPlayer + " active builds; finish or cancel one first");
        World world = Objects.requireNonNull(origin.getWorld());
        if (!world.equals(chest.getWorld()) || !world.equals(owner.getWorld())) throw new IllegalStateException("Chest and build must share a world");
        requireSingleChest(chest);
        if (!BuildersChest.isOwnedBy(chest, owner)) throw new IllegalStateException("Builds must be funded from your own Builder's Chest");
        BuildJob job = new BuildJob(UUID.randomUUID(), owner.getUniqueId(), plan, world.getUID(),
            origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), chest.getX(), chest.getY(), chest.getZ(),
            0, BuildJob.State.BUILDING, "");
        for (ConstructionStep step : plan.steps()) for (BlueprintBlock spec : step.blocks()) {
            int x = add(job.originX(), spec.x()), y = add(job.originY(), spec.y()), z = add(job.originZ(), spec.z());
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) throw new IllegalStateException("Build exceeds world height");
            Location at = new Location(world, x + 0.5, y, z + 0.5);
            if (!world.getWorldBorder().isInside(at)) throw new IllegalStateException("Build exceeds world border");
            int chestReach = 128 + ConstructionPlan.maxSpan();
            if (at.distanceSquared(chest.getLocation()) > (double) chestReach * chestReach) throw new IllegalStateException("Entire build must be within " + chestReach + " blocks of its chest");
            if (x == chest.getX() && y == chest.getY() && z == chest.getZ()) throw new IllegalStateException("Build overlaps its funding chest");
            if (jobs.values().stream().anyMatch(j -> !j.terminal() && j.world().equals(world.getUID()) && occupies(j, x, y, z)))
                throw new IllegalStateException("Build overlaps another active job");
        }
        if (chestReserved(chest)) throw new IllegalStateException("Chest is already reserved by another job");
        jobs.put(job.id(), job);
        save();
        return job;
    }
    /** True while an active job draws from this chest or will build over it. */
    public boolean chestReserved(Chest chest) {
        UUID world = chest.getWorld().getUID();
        return jobs.values().stream().anyMatch(j -> !j.terminal() && j.world().equals(world)
            && occupies(j, chest.getX(), chest.getY(), chest.getZ()));
    }
    private static boolean occupies(BuildJob job, int x, int y, int z) {
        if (job.chestX() == x && job.chestY() == y && job.chestZ() == z) return true;
        return job.plan().steps().stream().flatMap(s -> s.blocks().stream()).anyMatch(b ->
            (long) job.originX() + b.x() == x && (long) job.originY() + b.y() == y && (long) job.originZ() + b.z() == z);
    }
    private static int add(int a, int b) {
        try { return Math.addExact(a, b); } catch (ArithmeticException e) { throw new IllegalStateException("Coordinate overflow"); }
    }
    private static void requireSingleChest(Chest chest) {
        if (!(chest.getBlockData() instanceof org.bukkit.block.data.type.Chest data)
            || data.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE)
            throw new IllegalStateException("V1 requires a single chest (not a double chest)");
    }
    public boolean cancel(UUID id, UUID requester, boolean admin) {
        if (failed) throw new IllegalStateException("Storage/placement error; restart and inspect server log first");
        BuildJob job = jobs.get(id);
        if (job == null || job.terminal() || (!admin && !job.owner().equals(requester))) return false;
        job.cancel(); save(); return true;
    }
    public void tick() {
        if (failed) return;
        List<BuildJob> active = jobs.values().stream().filter(BuildJob::runnable).toList();
        if (active.isEmpty()) return;
        try {
            // One attempt per job per round, bounded globally; paused jobs cannot starve later jobs.
            boolean stateChanged = false;
            for (int i = 0; i < Math.min(budget, active.size()); i++) {
                BuildJob job = active.get(Math.floorMod(rotation++, active.size()));
                BuildJob.State before = job.state();
                placeNext(job);
                stateChanged |= job.state() != before;
            }
            // Checkpoint immediately on completion/pause/resume; otherwise every 20 cycles.
            // Without this, a job finishing after the last periodic save stays BUILDING on disk until shutdown.
            if (stateChanged || ++saveCounter >= 20) { saveCounter = 0; save(); }
        } catch (Exception e) {
            failed = true;
            for (BuildJob job : jobs.values()) if (!job.terminal()) job.state(BuildJob.State.RECOVERY_REQUIRED, "Placement/storage error; inspect log and cancel after audit");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Construction stopped. Dirty-session marker retained; no automatic retry.", e);
        }
    }
    private void pause(BuildJob job, BuildJob.State state, String reason) { job.state(state, reason); }
    private void placeNext(BuildJob job) {
        World world = Bukkit.getWorld(job.world());
        if (world == null) { pause(job, BuildJob.State.PAUSED_CHUNK, "World unavailable"); return; }
        Player owner = Bukkit.getPlayer(job.owner());
        if (owner == null || !owner.isOnline() || owner.getGameMode() != GameMode.SURVIVAL
            || !owner.hasPermission("blueprints.build") || !owner.getWorld().getUID().equals(world.getUID())) {
            pause(job, BuildJob.State.PAUSED_OWNER, "Owner must be online in Survival with build permission in this world"); return;
        }
        ConstructionStep step = job.plan().steps().get(job.nextBlock());
        if (!world.isChunkLoaded(job.chestX() >> 4, job.chestZ() >> 4)) {
            pause(job, BuildJob.State.PAUSED_CHUNK, "Chest chunk unloaded"); return;
        }
        List<Block> targets = new ArrayList<>();
        List<BlockData> expected = new ArrayList<>();
        for (BlueprintBlock spec : step.blocks()) {
            int x = add(job.originX(), spec.x()), y = add(job.originY(), spec.y()), z = add(job.originZ(), spec.z());
            if (!world.isChunkLoaded(x >> 4, z >> 4)) { pause(job, BuildJob.State.PAUSED_CHUNK, "Destination chunk unloaded"); return; }
            Location at = new Location(world, x + 0.5, y, z + 0.5);
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.getWorldBorder().isInside(at)) {
                pause(job, BuildJob.State.PAUSED_OBSTRUCTION, "Outside world height/border"); return;
            }
            int ownerReach = 128 + ConstructionPlan.maxSpan();
            if (owner.getLocation().distanceSquared(at) > (double) ownerReach * ownerReach) { pause(job, BuildJob.State.PAUSED_OWNER, "Owner must remain within " + ownerReach + " blocks of construction"); return; }
            targets.add(world.getBlockAt(x, y, z));
            expected.add(Bukkit.createBlockData(spec.blockData()));
        }
        int matches = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getBlockData().getAsString().equals(expected.get(i).getAsString())) matches++;
        }
        if (matches == targets.size()) { job.advance(); return; }
        // A partly existing door is ambiguous: never create a free half or charge again for it.
        if (matches > 0 || targets.stream().anyMatch(b -> !b.getType().isAir())) {
            pause(job, BuildJob.State.PAUSED_OBSTRUCTION, "Destination occupied or door partially present at "
                + targets.getFirst().getX() + "," + targets.getFirst().getY() + "," + targets.getFirst().getZ()); return;
        }
        Block chestBlock = world.getBlockAt(job.chestX(), job.chestY(), job.chestZ());
        if (!(chestBlock.getState() instanceof Chest chest)) { pause(job, BuildJob.State.PAUSED_OBSTRUCTION, "Builder's chest missing"); return; }
        try { requireSingleChest(chest); } catch (IllegalStateException e) { pause(job, BuildJob.State.PAUSED_OBSTRUCTION, e.getMessage()); return; }
        if (!InventoryUtil.consume(chest.getBlockInventory(), Material.valueOf(step.material()), step.amount())) {
            pause(job, BuildJob.State.PAUSED_MATERIALS, "Missing " + step.amount() + " " + step.material() + " (plain items only)"); return;
        }
        // Same main-thread step for both door halves. No neighbor physics or imported block entities.
        for (int i = 0; i < targets.size(); i++) targets.get(i).setBlockData(expected.get(i), false);
        for (int i = 0; i < targets.size(); i++) {
            if (!targets.get(i).getBlockData().getAsString().equals(expected.get(i).getAsString()))
                throw new IllegalStateException("Placement did not retain the expected state; audit job " + job.id());
        }
        job.advance();
        if (job.state() == BuildJob.State.COMPLETE) owner.sendMessage("§aBlueprint complete: " + job.plan().name());
    }
    public void save() {
        try { repository.save(jobs.values(), false); }
        catch (RuntimeException e) { failed = true; throw e; }
    }
    public void shutdown() {
        // A failed write/placement must never mark this session safe to resume.
        repository.save(jobs.values(), !failed);
    }
}
