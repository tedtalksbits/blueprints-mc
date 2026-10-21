package dev.whizzle.blueprints;

import org.bukkit.entity.Player;

/**
 * Who may add and remove blueprints (config.yml uploads.*). Shared by the command and the client bridge so both
 * paths apply the same rules.
 *
 * <ul>
 *   <li>Upload: blueprints.upload, or everyone when uploads.allow-everyone is true.</li>
 *   <li>Cap: players without blueprints.admin may own at most uploads.max-per-player blueprints (0 = no cap).
 *       Captures and uploads both count.</li>
 *   <li>Delete: the blueprint's owner, or anyone with blueprints.admin.</li>
 * </ul>
 */
public record BlueprintPolicy(boolean allowEveryone, int maxPerPlayer) {
    public static final BlueprintPolicy DEFAULT = new BlueprintPolicy(false, 10);

    public BlueprintPolicy { maxPerPlayer = Math.max(0, maxPerPlayer); }

    public boolean canUpload(Player player) { return allowEveryone || player.hasPermission("blueprints.upload"); }

    /** The cap that applies to this player, or 0 when they have none. */
    public int limitFor(Player player) { return player.hasPermission("blueprints.admin") ? 0 : maxPerPlayer; }

    /** Null when the player may save one more blueprint, otherwise a message saying why not. */
    public String capProblem(Player player, BlueprintRepository repository) {
        int limit = limitFor(player);
        if (limit == 0) return null;
        int owned = repository.ownedBy(player.getUniqueId());
        if (owned < limit) return null;
        return "You already have " + owned + " blueprint" + (owned == 1 ? "" : "s") + " (the limit is " + limit
            + "). Delete one of yours to make room.";
    }

    public boolean canDelete(Player player, String id, BlueprintRepository repository) {
        return repository.canDelete(id, player.getUniqueId(), player.hasPermission("blueprints.admin"));
    }

    public static BlueprintRepository.Owner ownerOf(Player player) {
        return new BlueprintRepository.Owner(player.getUniqueId(), player.getName());
    }
}
