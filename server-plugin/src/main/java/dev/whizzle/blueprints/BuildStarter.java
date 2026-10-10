package dev.whizzle.blueprints;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Starting a build from a command or from the client mod: one path, same checks. */
public final class BuildStarter {
    public record Outcome(boolean ok, String message, BuildJob job) {}

    private final BlueprintRepository repository;
    private final BuildManager builds;
    private final int chestRadius;

    public BuildStarter(BlueprintRepository repository, BuildManager builds, int chestRadius) {
        this.repository = repository;
        this.builds = builds;
        this.chestRadius = chestRadius;
    }

    public int chestRadius() { return chestRadius; }

    /** The player's nearest Builder's Chest that is not already funding a job. */
    public Optional<Chest> freeChest(Player player) {
        return BuildersChest.nearestOwned(player, chestRadius, c -> !builds.chestReserved(c));
    }

    public Outcome start(Player player, String id, int x, int y, int z, BlueprintTransform transform) {
        ConstructionPlan definition = repository.get(id).orElse(null);
        if (definition == null) return new Outcome(false, "Unknown blueprint: " + id, null);
        Chest chest = freeChest(player).orElse(null);
        if (chest == null)
            return new Outcome(false, "No free Builder's Chest of yours within " + chestRadius + " blocks. Each active build needs its own.", null);
        try {
            ConstructionPlan positioned = PlanTransformer.apply(definition, transform);
            BuildJob job = builds.start(player, positioned, new Location(player.getWorld(), x, y, z), chest);
            return new Outcome(true, "Started " + definition.name() + " (job " + job.id().toString().substring(0, 8)
                + "), funded by your Builder's Chest at " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + ".", job);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new Outcome(false, e.getMessage(), null);
        }
    }
}
