package dev.whizzle.blueprints;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class BlueprintCommand implements CommandExecutor, TabCompleter {
    private final BlueprintRepository repository;
    private final BuildManager builds;
    private final BuildStarter starter;
    private final Path importDirectory;
    private final BlueprintPolicy policy;

    public BlueprintCommand(BlueprintRepository repository, BuildManager builds, BuildStarter starter, Path importDirectory) {
        this(repository, builds, starter, importDirectory, BlueprintPolicy.DEFAULT);
    }

    public BlueprintCommand(BlueprintRepository repository, BuildManager builds, BuildStarter starter, Path importDirectory, BlueprintPolicy policy) {
        this.policy = policy;
        this.repository = repository;
        this.builds = builds;
        this.starter = starter;
        this.importDirectory = importDirectory.toAbsolutePath().normalize();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/blueprint list");
            sender.sendMessage("§e/blueprint start <id> <x> <y> <z> [0|90|180|270] [none|x|z] §7(uses your nearest free Builder's Chest)");
            sender.sendMessage("§e/blueprint capture <id> <x1> <y1> <z1> <x2> <y2> <z2> §7(Creative)");
            sender.sendMessage("§e/blueprint materials <id>");
            if (sender.hasPermission("blueprints.import")) sender.sendMessage("§e/blueprint import <file> [id] §7(.litematic in plugins/Blueprints/import)");
            sender.sendMessage("§e/blueprint delete <id> §7(your own blueprints; operators: any)");
            sender.sendMessage("§e/blueprint status");
            sender.sendMessage("§e/blueprint cancel <job-id>");
            if (sender.hasPermission("blueprints.admin")) sender.sendMessage("§e/blueprint givechest [player]");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "import" -> {
                if (!sender.hasPermission("blueprints.import")) { sender.sendMessage("§cYou don't have permission."); return true; }
                if (args.length < 2 || args.length > 3) { sender.sendMessage("§e/blueprint import <file> [id]"); return true; }
                String lower = args[1].toLowerCase(Locale.ROOT);
                String fileName = lower.endsWith(".litematic") || lower.endsWith(".schem") ? args[1]
                    : Files.isRegularFile(importDirectory.resolve(args[1] + ".schem")) && !Files.isRegularFile(importDirectory.resolve(args[1] + ".litematic"))
                        ? args[1] + ".schem" : args[1] + ".litematic";
                Path file = importDirectory.resolve(fileName).normalize();
                if (!fileName.matches("[A-Za-z0-9_.()+-]{1,128}") || !importDirectory.equals(file.getParent())) {
                    sender.sendMessage("§cUse a plain file name from plugins/Blueprints/import."); return true;
                }
                if (!Files.isRegularFile(file)) { sender.sendMessage("§cNo such file in plugins/Blueprints/import: " + fileName); return true; }
                String id = args.length == 3 ? args[2] : idFromFile(fileName);
                try {
                    BlueprintRepository.validateId(id);
                    if (repository.get(id).isPresent()) { sender.sendMessage("§cBlueprint already exists: " + id + ". Pass a different id."); return true; }
                    LitematicImport.Result result = LitematicImport.read(file, id);
                    repository.saveNew(result.plan(), sender instanceof Player p ? BlueprintPolicy.ownerOf(p) : null);
                    ConstructionPlan plan = result.plan();
                    sender.sendMessage("§aImported " + plan.id() + " §7(" + plan.name() + ")§a: " + plan.blockCount() + " blocks.");
                    String notes = LitematicImport.notes(result);
                    if (!notes.isEmpty()) sender.sendMessage("§6" + notes);
                    sender.sendMessage("§7Use /blueprint materials " + plan.id() + " to see its Survival cost.");
                } catch (IOException e) { sender.sendMessage("§cCould not read " + fileName + ": " + e.getMessage()); }
                catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) { sender.sendMessage("§cImport failed: " + e.getMessage()); }
                return true;
            }
            case "givechest" -> {
                if (!sender.hasPermission("blueprints.admin")) { sender.sendMessage("§cYou don't have permission."); return true; }
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                if (target == null) { sender.sendMessage("§cUnknown player."); return true; }
                target.getInventory().addItem(BuildersChest.item()).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                sender.sendMessage("§aGave a Builder's Chest to " + target.getName() + ".");
                return true;
            }
            case "capture" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length != 8) { sender.sendMessage("§e/blueprint capture <id> <x1> <y1> <z1> <x2> <y2> <z2>"); return true; }
                String cap = policy.capProblem(player, repository);
                if (cap != null) { sender.sendMessage("§c" + cap); return true; }
                try {
                    ConstructionPlan plan = BlueprintCapture.capture(player, args[1],
                        Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                        Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]));
                    repository.saveNew(plan, BlueprintPolicy.ownerOf(player));
                    sender.sendMessage("§aSaved " + plan.id() + ": " + plan.blockCount() + " blocks. Origin is the selection's minimum X/Y/Z corner.");
                    sender.sendMessage("§7Use /blueprint materials " + plan.id() + " to see its Survival cost.");
                } catch (NumberFormatException e) { sender.sendMessage("§cCoordinates must be integers."); }
                catch (IllegalArgumentException | IllegalStateException e) { sender.sendMessage("§c" + e.getMessage()); }
                return true;
            }
            case "delete" -> {
                if (args.length != 2) { sender.sendMessage("§e/blueprint delete <id>"); return true; }
                if (repository.get(args[1]).isEmpty()) { sender.sendMessage("§cUnknown blueprint: " + args[1]); return true; }
                boolean allowed = sender instanceof Player p ? policy.canDelete(p, args[1], repository) : true; // console
                if (!allowed) { sender.sendMessage("§cOnly the blueprint's owner or an operator can delete it."); return true; }
                try {
                    repository.delete(args[1]);
                    sender.sendMessage("§aDeleted " + args[1] + ". §7The file is in plugins/Blueprints/blueprints/trash; running builds continue.");
                } catch (IllegalArgumentException | IllegalStateException e) { sender.sendMessage("§c" + e.getMessage()); }
                return true;
            }
            case "list" -> {
                sender.sendMessage("§6Blueprints:");
                for (ConstructionPlan def : repository.all()) {
                    sender.sendMessage("§e- " + def.id() + " §7(" + def.blockCount() + " blocks) §f" + def.name()
                        + repository.owner(def.id()).map(o -> " §7by " + o.name()).orElse(""));
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
                int x, y, z;
                try { x = Integer.parseInt(args[2]); y = Integer.parseInt(args[3]); z = Integer.parseInt(args[4]); }
                catch (NumberFormatException e) { sender.sendMessage("§cCoordinates must be integers."); return true; }

                BlueprintTransform transform;
                try { transform = BlueprintTransform.parse(args.length > 5 ? args[5] : "0", args.length > 6 ? args[6] : "none"); }
                catch (IllegalArgumentException e) { sender.sendMessage("§c" + e.getMessage()); return true; }

                BuildStarter.Outcome outcome = starter.start(player, args[1], x, y, z, transform);
                sender.sendMessage((outcome.ok() ? "§a" : "§c") + outcome.message());
                return true;
            }
            default -> { return false; }
        }
    }

    /** File name to a valid blueprint id: lowercase, invalid characters to '-', at most 64 characters. */
    static String idFromFile(String fileName) {
        String base = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]+", "-").replaceAll("^[^a-z0-9]+", "");
        return base.length() > 64 ? base.substring(0, 64) : base;
    }

    private List<String> importFiles() {
        try (Stream<Path> files = Files.list(importDirectory)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".litematic") || n.toLowerCase(Locale.ROOT).endsWith(".schem")).sorted().toList();
        } catch (IOException e) { return List.of(); }
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
        if (args.length == 1) return sender.hasPermission("blueprints.admin")
            ? List.of("list", "materials", "capture", "import", "start", "status", "cancel", "delete", "givechest")
            : List.of("list", "materials", "capture", "start", "status", "cancel", "delete");
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("materials"))) return repository.all().stream().map(ConstructionPlan::id).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) return repository.all().stream().map(ConstructionPlan::id)
            .filter(id -> !(sender instanceof Player p) || policy.canDelete(p, id, repository)).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("import") && sender.hasPermission("blueprints.import")) return importFiles();
        if (args[0].equalsIgnoreCase("start") && args.length == 6) return List.of("0", "90", "180", "270");
        if (args[0].equalsIgnoreCase("start") && args.length == 7) return List.of("none", "x", "z");
        return List.of();
    }
}
