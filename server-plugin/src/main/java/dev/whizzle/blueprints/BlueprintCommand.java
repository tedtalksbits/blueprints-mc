package dev.whizzle.blueprints;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class BlueprintCommand implements CommandExecutor, TabCompleter {
    private final BlueprintRepository repository;
    private final BuildManager builds;

    public BlueprintCommand(BlueprintRepository repository, BuildManager builds) {
        this.repository = repository;
        this.builds = builds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/blueprint list");
            sender.sendMessage("§e/blueprint start <id> <x> <y> <z> §7(look at Builder's Chest)");
            sender.sendMessage("§e/blueprint status");
            sender.sendMessage("§e/blueprint cancel <job-id>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage("§6Blueprints:");
                for (BlueprintDefinition def : repository.all()) {
                    sender.sendMessage("§e- " + def.id() + " §7(" + def.blocks().size() + " blocks) §f" + def.name());
                }
                return true;
            }
            case "status" -> {
                UUID who = sender instanceof Player p ? p.getUniqueId() : null;
                boolean admin = sender.hasPermission("blueprints.admin");
                sender.sendMessage("§6Construction jobs:");
                for (BuildJob job : builds.jobs()) {
                    if (!admin && (who == null || !job.owner().equals(who))) continue;
                    sender.sendMessage("§e" + job.id().toString().substring(0, 8) + " §f" + job.blueprintId()
                        + " §7#" + job.nextBlock() + " §f" + job.state()
                        + (job.pauseReason().isBlank() ? "" : " §7- " + job.pauseReason()));
                }
                return true;
            }
            case "cancel" -> {
                if (!(sender instanceof Player player) || args.length < 2) return false;
                UUID id = resolveJobId(args[1]);
                if (id == null) { sender.sendMessage("§cUnknown or ambiguous job id."); return true; }
                boolean ok = builds.cancel(id, player.getUniqueId(), sender.hasPermission("blueprints.admin"));
                sender.sendMessage(ok ? "§aConstruction cancelled." : "§cCould not cancel that job.");
                return true;
            }
            case "start" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length != 5) return false;
                BlueprintDefinition def = repository.get(args[1]).orElse(null);
                if (def == null) { sender.sendMessage("§cUnknown blueprint: " + args[1]); return true; }
                int x, y, z;
                try { x = Integer.parseInt(args[2]); y = Integer.parseInt(args[3]); z = Integer.parseInt(args[4]); }
                catch (NumberFormatException e) { sender.sendMessage("§cCoordinates must be integers."); return true; }

                Block target = player.getTargetBlockExact(6);
                if (target == null || !(target.getState() instanceof Chest chest)) {
                    sender.sendMessage("§cLook directly at the chest that should fund this build, then run the command again.");
                    return true;
                }
                try {
                    BuildJob job = builds.start(player, def, new Location(player.getWorld(), x, y, z), chest);
                    sender.sendMessage("§aStarted §f" + def.name() + "§a. Job: §f" + job.id().toString().substring(0, 8));
                    sender.sendMessage("§7Blocks will be consumed from the chest you're looking at and placed with exact block states.");
                } catch (IllegalStateException e) {
                    sender.sendMessage("§c" + e.getMessage());
                }
                return true;
            }
            default -> { return false; }
        }
    }

    private UUID resolveJobId(String token) {
        UUID exact = null;
        try { exact = UUID.fromString(token); } catch (IllegalArgumentException ignored) {}
        if (exact != null) return exact;
        List<UUID> matches = builds.jobs().stream().map(BuildJob::id)
            .filter(id -> id.toString().startsWith(token.toLowerCase(Locale.ROOT))).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "start", "status", "cancel");
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) return repository.all().stream().map(BlueprintDefinition::id).toList();
        return List.of();
    }
}
