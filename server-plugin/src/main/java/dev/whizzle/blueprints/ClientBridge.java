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
    public static final int PROTOCOL = 2;
    public static final String HELLO = "blueprints:hello", WELCOME = "blueprints:welcome",
        CATALOG_REQUEST = "blueprints:catalog_request", CATALOG = "blueprints:catalog",
        PLAN_REQUEST = "blueprints:plan_request", PLAN = "blueprints:plan",
        MATERIALS_REQUEST = "blueprints:materials_request", MATERIALS = "blueprints:materials",
        START = "blueprints:start", CANCEL = "blueprints:cancel", RESULT = "blueprints:result",
        JOBS_REQUEST = "blueprints:jobs_request", JOBS = "blueprints:jobs", CAPTURE = "blueprints:capture",
        UPLOAD_BEGIN = "blueprints:upload_begin", UPLOAD_CHUNK = "blueprints:upload_chunk", UPLOAD_END = "blueprints:upload_end",
        UPLOAD_READY = "blueprints:upload_ready", DELETE = "blueprints:delete";
    public static final List<String> INCOMING = List.of(HELLO, CATALOG_REQUEST, PLAN_REQUEST, MATERIALS_REQUEST, START, CANCEL, JOBS_REQUEST, CAPTURE, UPLOAD_BEGIN, UPLOAD_CHUNK, UPLOAD_END, DELETE);
    public static final List<String> OUTGOING = List.of(WELCOME, CATALOG, PLAN, MATERIALS, RESULT, JOBS, UPLOAD_READY);
    private static final int MAX_ID = 64, REQUESTS_PER_SECOND = 20, MAX_CHUNK = 30000;
    private static final long UPLOAD_TIMEOUT_MS = 120_000;

    /** An upload in progress: bytes arrive in order, then upload_end imports them. */
    private static final class Upload {
        final String id; final byte[] data; int received; final long started = System.currentTimeMillis();
        Upload(String id, int size) { this.id = id; this.data = new byte[size]; }
    }

    /** Sends a message to one player; tests replace it. */
    public interface Outbox { void send(Player player, String channel, byte[] data); }

    private final BlueprintRepository repository;
    private final BuildManager builds;
    private final BuildStarter starter;
    private final Outbox outbox;
    private final Map<UUID, long[]> rate = new HashMap<>();
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final int maxUploadBytes;
    private final BlueprintPolicy policy;

    public ClientBridge(BlueprintRepository repository, BuildManager builds, BuildStarter starter, Outbox outbox) {
        this(repository, builds, starter, outbox, 2 << 20, BlueprintPolicy.DEFAULT);
    }

    public ClientBridge(BlueprintRepository repository, BuildManager builds, BuildStarter starter, Outbox outbox, int maxUploadBytes,
                        BlueprintPolicy policy) {
        this.policy = policy;
        this.repository = repository;
        this.builds = builds;
        this.starter = starter;
        this.outbox = outbox;
        this.maxUploadBytes = Math.clamp(maxUploadBytes, 64 << 10, (int) LitematicImport.MAX_FILE_BYTES);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Upload chunks are bounded by the declared size of an accepted upload instead of the request budget.
        if (!channel.equals(UPLOAD_CHUNK) && !allow(player)) return;
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
                case UPLOAD_BEGIN -> { String id = in.utf(MAX_ID); int size = in.varInt(); in.end(); uploadBegin(player, id, size); }
                case UPLOAD_CHUNK -> uploadChunk(player, in);
                case UPLOAD_END -> { in.end(); uploadEnd(player); }
                case DELETE -> { String id = in.utf(MAX_ID); in.end(); delete(player, id); }
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
            .varInt(starter.chestRadius()).bool(player.hasPermission("blueprints.capture"))
            .varInt(ConstructionPlan.maxBlocks()).varInt(ConstructionPlan.maxSpan())
            .bool(policy.canUpload(player)).varInt(maxUploadBytes).bytes());
    }

    private void catalog(Player player) {
        Collection<ConstructionPlan> all = repository.all();
        Wire.Writer out = new Wire.Writer().varInt(policy.limitFor(player)).varInt(repository.ownedBy(player.getUniqueId())).varInt(all.size());
        for (ConstructionPlan plan : all) {
            int[] size = size(plan);
            out.utf(plan.id()).utf(plan.name()).varInt(plan.blockCount()).varInt(size[0]).varInt(size[1]).varInt(size[2])
                .utf(repository.owner(plan.id()).map(BlueprintRepository.Owner::name).orElse(""))
                .bool(policy.canDelete(player, plan.id(), repository));
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

    /**
     * Materials for a blueprint. If the player is already building it, this reports that build: what is still left
     * to place and what the build's own (reserved) chest holds. Otherwise it reports the whole blueprint against the
     * player's nearest free Builder's Chest.
     */
    private void materials(Player player, String id) {
        ConstructionPlan plan = repository.get(id).orElse(null);
        if (plan == null) { result(player, false, "Unknown blueprint: " + id); return; }
        BuildJob job = builds.jobs().stream().filter(j -> !j.terminal() && j.owner().equals(player.getUniqueId())
            && j.blueprintId().equalsIgnoreCase(plan.id())).findFirst().orElse(null);
        Map<String, Integer> needed;
        Chest chest;
        Wire.Writer out = new Wire.Writer().utf(plan.id()).bool(job != null);
        if (job != null) {
            out.utf(job.id().toString()).varInt(job.nextBlock()).varInt(job.plan().steps().size());
            needed = new TreeMap<>();
            for (ConstructionStep step : job.plan().steps().subList(job.nextBlock(), job.plan().steps().size()))
                needed.merge(step.material(), step.amount(), Integer::sum);
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(job.world());
            chest = world != null && world.isChunkLoaded(job.chestX() >> 4, job.chestZ() >> 4)
                && world.getBlockAt(job.chestX(), job.chestY(), job.chestZ()).getState() instanceof Chest c ? c : null;
        } else {
            needed = plan.materials();
            chest = starter.freeChest(player).orElse(null);
        }
        out.bool(chest != null);
        if (chest != null) out.int32(chest.getX()).int32(chest.getY()).int32(chest.getZ());
        out.varInt(needed.size());
        Chest source = chest;
        needed.forEach((material, count) -> {
            int have = source == null ? 0 : InventoryUtil.count(source.getBlockInventory(), org.bukkit.Material.valueOf(material));
            out.utf(material).varInt(count).varInt(have);
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
        String cap = policy.capProblem(player, repository);
        if (cap != null) { result(player, false, cap); return; }
        try {
            ConstructionPlan plan = BlueprintCapture.capture(player, id, x1, y1, z1, x2, y2, z2);
            repository.saveNew(plan, BlueprintPolicy.ownerOf(player));
            result(player, true, "Saved blueprint " + plan.id() + " (" + plan.blockCount() + " blocks).");
            catalog(player);
        } catch (IllegalArgumentException | IllegalStateException e) {
            result(player, false, e.getMessage());
        }
    }

    private void uploadBegin(Player player, String id, int size) {
        uploads.remove(player.getUniqueId());
        String problem = null;
        if (!policy.canUpload(player)) problem = "You don't have permission to upload blueprints.";
        else if (policy.capProblem(player, repository) != null) problem = policy.capProblem(player, repository);
        else if (size <= 0 || size > maxUploadBytes) problem = "File is " + (size >> 10) + " KB; this server accepts up to " + (maxUploadBytes >> 10) + " KB.";
        else {
            try { BlueprintRepository.validateId(id); } catch (IllegalArgumentException e) { problem = e.getMessage(); }
            if (problem == null && repository.get(id).isPresent()) problem = "A blueprint named " + id + " already exists. Choose another name.";
        }
        if (problem == null) uploads.put(player.getUniqueId(), new Upload(id, size));
        outbox.send(player, UPLOAD_READY, new Wire.Writer().bool(problem == null).utf(problem == null ? "Uploading " + id : problem).bytes());
    }

    private void uploadChunk(Player player, Wire.Reader in) {
        Upload upload = uploads.get(player.getUniqueId());
        if (upload == null) return;                                   // stale chunks after a failure: ignore cheaply
        if (System.currentTimeMillis() - upload.started > UPLOAD_TIMEOUT_MS) { abort(player, "Upload timed out."); return; }
        int offset = in.varInt();
        byte[] bytes = in.bytes(MAX_CHUNK);
        in.end();
        if (offset != upload.received || offset + bytes.length > upload.data.length) { abort(player, "Upload data arrived out of order."); return; }
        System.arraycopy(bytes, 0, upload.data, offset, bytes.length);
        upload.received += bytes.length;
    }

    private void uploadEnd(Player player) {
        Upload upload = uploads.remove(player.getUniqueId());
        if (upload == null) { result(player, false, "No upload in progress."); return; }
        if (upload.received != upload.data.length) { result(player, false, "Upload incomplete (" + upload.received + " of " + upload.data.length + " bytes)."); return; }
        try {
            String cap = policy.capProblem(player, repository);
            if (cap != null) { result(player, false, cap); return; }
            if (!BlueprintRepository.isGzip(upload.data)) {
                ConstructionPlan plan = BlueprintRepository.fromYaml(upload.data, upload.id);
                repository.saveNew(plan, BlueprintPolicy.ownerOf(player));
                result(player, true, "Uploaded " + plan.id() + " (" + plan.blockCount() + " blocks).");
                catalog(player);
                return;
            }
            LitematicImport.Result imported = LitematicImport.read(upload.data, upload.id);
            repository.saveNew(imported.plan(), BlueprintPolicy.ownerOf(player));
            String notes = LitematicImport.notes(imported);
            result(player, true, "Uploaded " + imported.plan().id() + " (" + imported.plan().blockCount() + " blocks)." + (notes.isEmpty() ? "" : " " + notes));
            catalog(player);
        } catch (java.io.IOException e) {
            result(player, false, "That file isn't a readable .litematic, .schem or blueprint .yml (" + e.getMessage() + ").");
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            result(player, false, "Upload failed: " + e.getMessage());
        }
    }

    private void delete(Player player, String id) {
        if (repository.get(id).isEmpty()) { result(player, false, "Unknown blueprint: " + id); catalog(player); return; }
        if (!policy.canDelete(player, id, repository)) { result(player, false, "Only the blueprint's owner or an operator can delete " + id + "."); return; }
        try {
            repository.delete(id);
            result(player, true, "Deleted blueprint " + id + ". Builds already running from it continue.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            result(player, false, e.getMessage());
        }
        catalog(player);
    }

    private void abort(Player player, String message) {
        uploads.remove(player.getUniqueId());
        result(player, false, message);
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

    public void forget(Player player) { rate.remove(player.getUniqueId()); uploads.remove(player.getUniqueId()); }
}
