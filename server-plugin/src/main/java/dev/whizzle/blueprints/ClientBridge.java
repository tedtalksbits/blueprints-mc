package dev.whizzle.blueprints;

import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

/**
 * Server half of the client-mod protocol (docs/PROTOCOL.md). Every request is validated here; the client only
 * proposes where and how to build, and the same BuildStarter checks as the command decide.
 */
public final class ClientBridge implements PluginMessageListener {
    public static final int PROTOCOL = 1;
    public static final String HELLO = "blueprints:hello", WELCOME = "blueprints:welcome",
        CATALOG_REQUEST = "blueprints:catalog_request", CATALOG = "blueprints:catalog",
        PLAN_REQUEST = "blueprints:plan_request", PLAN = "blueprints:plan",
        MATERIALS_REQUEST = "blueprints:materials_request", MATERIALS = "blueprints:materials",
        START = "blueprints:start", CANCEL = "blueprints:cancel", RESULT = "blueprints:result",
        JOBS_REQUEST = "blueprints:jobs_request", JOBS = "blueprints:jobs", CAPTURE = "blueprints:capture";
    public static final List<String> INCOMING = List.of(HELLO, CATALOG_REQUEST, PLAN_REQUEST, MATERIALS_REQUEST, START, CANCEL, JOBS_REQUEST, CAPTURE);
    public static final List<String> OUTGOING = List.of(WELCOME, CATALOG, PLAN, MATERIALS, RESULT, JOBS);
    private static final int MAX_ID = 64, REQUESTS_PER_SECOND = 20;

    /** Sends a message to one player; tests replace it. */
    public interface Outbox { void send(Player player, String channel, byte[] data); }

    private final BlueprintRepository repository;
    private final BuildManager builds;
    private final BuildStarter starter;
    private final Outbox outbox;
    private final Map<UUID, long[]> rate = new HashMap<>();

    public ClientBridge(BlueprintRepository repository, BuildManager builds, BuildStarter starter, Outbox outbox) {
        this.repository = repository;
        this.builds = builds;
        this.starter = starter;
        this.outbox = outbox;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!allow(player)) return;
        try {
            Wire.Reader in = new Wire.Reader(message);
            switch (channel) {
                case HELLO -> { in.varInt(); in.end(); welcome(player); }
                case CATALOG_REQUEST -> { in.end(); catalog(player); }
                case PLAN_REQUEST -> { String id = in.utf(MAX_ID); in.end(); plan(player, id); }
                case MATERIALS_REQUEST -> { String id = in.utf(MAX_ID); in.end(); materials(player, id); }
                case START -> {
                    String id = in.utf(MAX_ID);
                    int x = in.int32(), y = in.int32(), z = in.int32(), rotation = in.varInt(), mirror = in.varInt();
                    in.end();
                    start(player, id, x, y, z, rotation, mirror);
                }
                case CANCEL -> { String job = in.utf(MAX_ID); in.end(); cancel(player, job); }
                case JOBS_REQUEST -> { in.end(); jobs(player); }
                case CAPTURE -> {
                    String id = in.utf(MAX_ID);
                    int x1 = in.int32(), y1 = in.int32(), z1 = in.int32(), x2 = in.int32(), y2 = in.int32(), z2 = in.int32();
                    in.end();
                    capture(player, id, x1, y1, z1, x2, y2, z2);
                }
                default -> { }
            }
        } catch (IllegalArgumentException e) {
            result(player, false, "Bad request from client: " + e.getMessage());
        }
    }

    private boolean allow(Player player) {
        long now = System.currentTimeMillis() / 1000;
        long[] window = rate.computeIfAbsent(player.getUniqueId(), k -> new long[]{now, 0});
        if (window[0] != now) { window[0] = now; window[1] = 0; }
        return ++window[1] <= REQUESTS_PER_SECOND;
    }

    private void welcome(Player player) {
        outbox.send(player, WELCOME, new Wire.Writer().varInt(PROTOCOL).bool(player.hasPermission("blueprints.build"))
            .varInt(starter.chestRadius()).bool(player.hasPermission("blueprints.capture")).bytes());
    }

    private void catalog(Player player) {
        Collection<ConstructionPlan> all = repository.all();
        Wire.Writer out = new Wire.Writer().varInt(all.size());
        for (ConstructionPlan plan : all) {
            int[] size = size(plan);
            out.utf(plan.id()).utf(plan.name()).varInt(plan.blockCount()).varInt(size[0]).varInt(size[1]).varInt(size[2]);
        }
        outbox.send(player, CATALOG, out.bytes());
    }

    /** Blocks are sent untransformed with a state palette; the client rotates for display. */
    private void plan(Player player, String id) {
        ConstructionPlan plan = repository.get(id).orElse(null);
        if (plan == null) { result(player, false, "Unknown blueprint: " + id); return; }
        Map<String, Integer> palette = new LinkedHashMap<>();
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (ConstructionStep step : plan.steps()) for (BlueprintBlock block : step.blocks()) {
            palette.putIfAbsent(block.blockData(), palette.size());
            blocks.add(block);
        }
        Wire.Writer out = new Wire.Writer().utf(plan.id()).utf(plan.name()).varInt(palette.size());
        palette.keySet().forEach(out::utf);
        out.varInt(blocks.size());
        for (BlueprintBlock b : blocks) out.varInt(b.x()).varInt(b.y()).varInt(b.z()).varInt(palette.get(b.blockData()));
        outbox.send(player, PLAN, out.bytes());
    }

    /** What the build needs, and what the player's nearest free Builder's Chest currently holds. */
    private void materials(Player player, String id) {
        ConstructionPlan plan = repository.get(id).orElse(null);
        if (plan == null) { result(player, false, "Unknown blueprint: " + id); return; }
        Chest chest = starter.freeChest(player).orElse(null);
        Wire.Writer out = new Wire.Writer().utf(plan.id()).bool(chest != null);
        if (chest != null) out.int32(chest.getX()).int32(chest.getY()).int32(chest.getZ());
        out.varInt(plan.materials().size());
        plan.materials().forEach((material, needed) -> {
            int have = chest == null ? 0 : InventoryUtil.count(chest.getBlockInventory(), org.bukkit.Material.valueOf(material));
            out.utf(material).varInt(needed).varInt(have);
        });
        outbox.send(player, MATERIALS, out.bytes());
    }

    private void start(Player player, String id, int x, int y, int z, int rotation, int mirror) {
        BlueprintTransform transform;
        try {
            BlueprintTransform.Mirror m = switch (mirror) { case 0 -> BlueprintTransform.Mirror.NONE; case 1 -> BlueprintTransform.Mirror.X;
                case 2 -> BlueprintTransform.Mirror.Z; default -> throw new IllegalArgumentException("Unknown mirror"); };
            transform = new BlueprintTransform(rotation, m);
        } catch (IllegalArgumentException e) { result(player, false, e.getMessage()); return; }
        BuildStarter.Outcome outcome = starter.start(player, id, x, y, z, transform);
        result(player, outcome.ok(), outcome.message());
        if (outcome.ok()) jobs(player);
    }

    /** Same rules as /blueprint capture: Creative mode, capture permission, supported blocks, size limits. */
    private void capture(Player player, String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        try {
            ConstructionPlan plan = BlueprintCapture.capture(player, id, x1, y1, z1, x2, y2, z2);
            repository.saveNew(plan);
            result(player, true, "Saved blueprint " + plan.id() + " (" + plan.blockCount() + " blocks).");
            catalog(player);
        } catch (IllegalArgumentException | IllegalStateException e) {
            result(player, false, e.getMessage());
        }
    }

    private void cancel(Player player, String token) {
        UUID id = builds.jobs().stream().map(BuildJob::id).filter(j -> j.toString().equals(token) || j.toString().startsWith(token))
            .reduce((a, b) -> null).orElse(null);
        boolean ok;
        try { ok = id != null && builds.cancel(id, player.getUniqueId(), player.hasPermission("blueprints.admin")); }
        catch (IllegalStateException e) { result(player, false, e.getMessage()); return; }
        result(player, ok, ok ? "Construction cancelled. Placed blocks stay; unused items remain in the chest." : "Could not cancel that job.");
        jobs(player);
    }

    /** The player's own jobs, newest last. */
    private void jobs(Player player) {
        List<BuildJob> mine = builds.jobs().stream().filter(j -> j.owner().equals(player.getUniqueId())).toList();
        Wire.Writer out = new Wire.Writer().varInt(mine.size());
        for (BuildJob job : mine) {
            out.utf(job.id().toString()).utf(job.blueprintId()).utf(job.plan().name()).varInt(job.nextBlock())
                .varInt(job.plan().steps().size()).utf(job.state().name()).utf(job.pauseReason())
                .int32(job.originX()).int32(job.originY()).int32(job.originZ());
        }
        outbox.send(player, JOBS, out.bytes());
    }

    private void result(Player player, boolean ok, String message) {
        outbox.send(player, RESULT, new Wire.Writer().bool(ok).utf(message).bytes());
    }

    private static int[] size(ConstructionPlan plan) {
        int maxX = 0, maxY = 0, maxZ = 0;
        for (ConstructionStep step : plan.steps()) for (BlueprintBlock b : step.blocks()) {
            maxX = Math.max(maxX, b.x()); maxY = Math.max(maxY, b.y()); maxZ = Math.max(maxZ, b.z());
        }
        return new int[]{maxX + 1, maxY + 1, maxZ + 1};
    }

    public void forget(Player player) { rate.remove(player.getUniqueId()); }
}
