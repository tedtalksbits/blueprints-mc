package dev.whizzle.blueprints;

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
            sender.sendMessage("§e/blueprint start <id> <x> <y> <z> [0|90|180|270] [none|x|z] §7(look at Builder's Chest)");
            sender.sendMessage("§e/blueprint capture <id> <x1> <y1> <z1> <x2> <y2> <z2> §7(Creative)");
            sender.sendMessage("§e/blueprint materials <id>");
            sender.sendMessage("§e/blueprint status");
            sender.sendMessage("§e/blueprint cancel <job-id>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "capture" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length != 8) { sender.sendMessage("§e/blueprint capture <id> <x1> <y1> <z1> <x2> <y2> <z2>"); return true; }
                try {
                    ConstructionPlan plan = BlueprintCapture.capture(player, args[1],
                        Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                        Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]));
                    repository.saveNew(plan);
                    sender.sendMessage("§aSaved " + plan.id() + ": " + plan.blockCount() + " blocks. Origin is the selection's minimum X/Y/Z corner.");
                    sender.sendMessage("§7Use /blueprint materials " + plan.id() + " to see its Survival cost.");
                } catch (NumberFormatException e) { sender.sendMessage("§cCoordinates must be integers."); }
                catch (IllegalArgumentException | IllegalStateException e) { sender.sendMessage("§c" + e.getMessage()); }
                return true;
            }
            case "list" -> {
                sender.sendMessage("§6Blueprints:");
                for (ConstructionPlan def : repository.all()) {
                    sender.sendMessage("§e- " + def.id() + " §7(" + def.blockCount() + " blocks) §f" + def.name());
                }
                return true;
            }
            case "materials" -> {
                if (args.length != 2) return false;
                ConstructionPlan plan = repository.get(args[1]).orElse(null);
                if (plan == null) { sender.sendMessage("§cUnknown blueprint."); return true; }
                sender.sendMessage("§6Full blueprint materials (existing matching blocks cost nothing):");
                plan.materials().forEach((item, count) -> sender.sendMessage("§e" + count + " §f" + item));
                return true;
            }
            case "status" -> {
                UUID who = sender instanceof Player p ? p.getUniqueId() : null;
                boolean admin = sender.hasPermission("blueprints.admin");
                sender.sendMessage("§6Construction jobs:");
                for (BuildJob job : builds.jobs()) {
                    if (!admin && (who == null || !job.owner().equals(who))) continue;
                    sender.sendMessage("§e" + job.id().toString().substring(0, 8) + " §f" + job.blueprintId()
                        + " §7" + job.nextBlock() + "/" + job.plan().steps().size() + " steps" + " §f" + job.state()
                        + (job.pauseReason().isBlank() ? "" : " §7- " + job.pauseReason()));
                }
                return true;
            }
            case "cancel" -> {
                if (!(sender instanceof Player player) || args.length < 2) return false;
                UUID id = resolveJobId(args[1]);
                if (id == null) { sender.sendMessage("§cUnknown or ambiguous job id."); return true; }
                boolean ok;
                try { ok = builds.cancel(id, player.getUniqueId(), sender.hasPermission("blueprints.admin")); }
                catch (IllegalStateException e) { sender.sendMessage("§c" + e.getMessage()); return true; }
                sender.sendMessage(ok ? "§aConstruction cancelled." : "§cCould not cancel that job.");
                return true;
            }
            case "start" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 5 || args.length > 7) return false;
                ConstructionPlan def = repository.get(args[1]).orElse(null);
                if (def == null) { sender.sendMessage("§cUnknown blueprint: " + args[1]); return true; }
                int x, y, z;
                try { x = Integer.parseInt(args[2]); y = Integer.parseInt(args[3]); z = Integer.parseInt(args[4]); }
                catch (NumberFormatException e) { sender.sendMessage("§cCoordinates must be integers."); return true; }

                BlueprintTransform transform;
                try { transform = BlueprintTransform.parse(args.length > 5 ? args[5] : "0", args.length > 6 ? args[6] : "none"); }
                catch (IllegalArgumentException e) { sender.sendMessage("§c" + e.getMessage()); return true; }

                Block target = player.getTargetBlockExact(6);
                if (target == null || !(target.getState() instanceof Chest chest)) {
                    sender.sendMessage("§cLook directly at the chest that should fund this build, then run the command again.");
                    return true;
                }
                try {
                    ConstructionPlan positioned = PlanTransformer.apply(def, transform);
                    BuildJob job = builds.start(player, positioned, new Location(player.getWorld(), x, y, z), chest);
                    sender.sendMessage("§aStarted §f" + def.name() + "§a. Job: §f" + job.id().toString().substring(0, 8));
                    sender.sendMessage("§7Blocks will be consumed from the chest you're looking at and placed with exact block states.");
                } catch (IllegalArgumentException | IllegalStateException e) {
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
        if (args.length == 1) return List.of("list", "materials", "capture", "start", "status", "cancel");
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("materials"))) return repository.all().stream().map(ConstructionPlan::id).toList();
        if (args[0].equalsIgnoreCase("start") && args.length == 6) return List.of("0", "90", "180", "270");
        if (args[0].equalsIgnoreCase("start") && args.length == 7) return List.of("none", "x", "z");
        return List.of();
    }
}
