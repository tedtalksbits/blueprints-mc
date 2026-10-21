package dev.whizzle.blueprints;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The client-mod protocol, decoded exactly as the Fabric client reads it. */
class ClientBridgeTest {
    record Sent(String channel, byte[] data) {}
    ServerMock server; WorldMock world; PlayerMock player; BlueprintsPlugin plugin;
    BlueprintRepository repository; ClientBridge bridge; final List<Sent> sent = new ArrayList<>();

    @BeforeEach void setUp() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("bridge");
        player = server.addPlayer(); player.setOp(true); player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 0, 65, 0)); world.loadChunk(0, 0); world.loadChunk(-1, 0);
        plugin = MockBukkit.load(BlueprintsPlugin.class);
        repository = new BlueprintRepository(plugin); repository.load();
        BuildStarter starter = new BuildStarter(repository, plugin.builds(), 16);
        bridge = new ClientBridge(repository, plugin.builds(), starter, (p, channel, data) -> sent.add(new Sent(channel, data)));
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    Wire.Reader request(String channel, Wire.Writer body, String expectReply) {
        sent.clear();
        bridge.onPluginMessageReceived(channel, player, body.bytes());
        Sent reply = sent.stream().filter(s -> s.channel().equals(expectReply)).findFirst()
            .orElseThrow(() -> new AssertionError("no " + expectReply + "; got " + sent.stream().map(Sent::channel).toList()));
        return new Wire.Reader(reply.data());
    }
    Chest fundedChest(int x) {
        world.getBlockAt(x, 65, 0).setType(Material.CHEST);
        BuildersChest.claim((Chest) world.getBlockAt(x, 65, 0).getState(), player);
        return (Chest) world.getBlockAt(x, 65, 0).getState();
    }

    @Test void wireRoundTripsMinecraftPrimitives() {
        byte[] data = new Wire.Writer().varInt(0).varInt(300).varInt(-1).int32(-123456).bool(true).utf("héllo").bytes();
        Wire.Reader in = new Wire.Reader(data);
        assertEquals(0, in.varInt()); assertEquals(300, in.varInt()); assertEquals(-1, in.varInt());
        assertEquals(-123456, in.int32()); assertTrue(in.bool()); assertEquals("héllo", in.utf(100)); in.end();
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, new Wire.Writer().varInt(300).bytes()); // same bytes as FriendlyByteBuf
        assertThrows(IllegalArgumentException.class, () -> new Wire.Reader(new byte[]{5, 'a'}).utf(100));
    }
    @Test void helloGetsWelcomeWithPermissionAndRadius() {
        Wire.Reader in = request(ClientBridge.HELLO, new Wire.Writer().varInt(1), ClientBridge.WELCOME);
        assertEquals(ClientBridge.PROTOCOL, in.varInt()); assertTrue(in.bool()); assertEquals(16, in.varInt()); assertTrue(in.bool());
        assertEquals(ConstructionPlan.maxBlocks(), in.varInt()); assertEquals(ConstructionPlan.maxSpan(), in.varInt());
        assertTrue(in.bool()); assertEquals(2 << 20, in.varInt()); in.end();
    }
    @Test void catalogListsBlueprintsWithSize() {
        Wire.Reader in = request(ClientBridge.CATALOG_REQUEST, new Wire.Writer(), ClientBridge.CATALOG);
        assertEquals(0, in.varInt(), "operators have no cap"); assertEquals(0, in.varInt(), "owns none");
        assertEquals(1, in.varInt());
        assertEquals("orientation-demo", in.utf(64)); assertEquals("Orientation Demo", in.utf(256));
        assertEquals(38, in.varInt()); assertEquals(5, in.varInt()); assertEquals(3, in.varInt()); assertEquals(5, in.varInt());
        assertEquals("", in.utf(64), "bundled example has no owner"); assertTrue(in.bool(), "operators may delete anything"); in.end();
    }
    @Test void planSendsEveryBlockThroughAPalette() {
        Wire.Reader in = request(ClientBridge.PLAN_REQUEST, new Wire.Writer().utf("orientation-demo"), ClientBridge.PLAN);
        assertEquals("orientation-demo", in.utf(64)); in.utf(256);
        int paletteSize = in.varInt(); List<String> palette = new ArrayList<>();
        for (int i = 0; i < paletteSize; i++) palette.add(in.utf(32767));
        int count = in.varInt(); assertEquals(38, count);
        Set<String> doors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int x = in.varInt(), y = in.varInt(), z = in.varInt(); String state = palette.get(in.varInt());
            if (state.contains("oak_door")) doors.add(x + "," + y + "," + z);
        }
        in.end();
        assertEquals(Set.of("4,1,4", "4,2,4"), doors);
    }
    @Test void materialsReportNeededAndWhatTheChestHolds() {
        Chest chest = fundedChest(-2);
        chest.getBlockInventory().addItem(new ItemStack(Material.STONE_BRICKS, 10));
        Wire.Reader in = request(ClientBridge.MATERIALS_REQUEST, new Wire.Writer().utf("orientation-demo"), ClientBridge.MATERIALS);
        assertEquals("orientation-demo", in.utf(64)); assertFalse(in.bool(), "not building"); assertTrue(in.bool());
        assertEquals(-2, in.int32()); assertEquals(65, in.int32()); assertEquals(0, in.int32());
        Map<String, int[]> rows = new HashMap<>();
        for (int i = 0, n = in.varInt(); i < n; i++) rows.put(in.utf(64), new int[]{in.varInt(), in.varInt()});
        in.end();
        assertArrayEquals(new int[]{25, 10}, rows.get("STONE_BRICKS"));
        assertArrayEquals(new int[]{1, 0}, rows.get("OAK_DOOR"));
    }
    @Test void whileBuildingMaterialsShowWhatIsLeftAndTheBuildsOwnChest() {
        Chest chest = fundedChest(-2);
        chest.getBlockInventory().addItem(new ItemStack(Material.STONE_BRICKS, 5));
        assertTrue(request(ClientBridge.START, new Wire.Writer().utf("orientation-demo").int32(4).int32(65).int32(4).varInt(0).varInt(0), ClientBridge.RESULT).bool());
        server.getScheduler().performTicks(60);                    // places the 5 funded bricks, then pauses
        BuildJob job = plugin.builds().jobs().iterator().next();
        assertEquals(5, job.nextBlock());
        chest.getBlockInventory().addItem(new ItemStack(Material.STONE_BRICKS, 3));
        Wire.Reader in = request(ClientBridge.MATERIALS_REQUEST, new Wire.Writer().utf("orientation-demo"), ClientBridge.MATERIALS);
        assertEquals("orientation-demo", in.utf(64));
        assertTrue(in.bool(), "building mode");
        assertEquals(job.id().toString(), in.utf(64)); assertEquals(5, in.varInt()); assertEquals(37, in.varInt());
        assertTrue(in.bool(), "the reserved chest is still reported");
        assertEquals(-2, in.int32()); in.int32(); in.int32();
        Map<String, int[]> rows = new HashMap<>();
        for (int i = 0, n = in.varInt(); i < n; i++) rows.put(in.utf(64), new int[]{in.varInt(), in.varInt()});
        in.end();
        assertArrayEquals(new int[]{20, 3}, rows.get("STONE_BRICKS")); // 25 total - 5 placed; 3 waiting in the chest
        assertArrayEquals(new int[]{4, 0}, rows.get("OAK_STAIRS"));
    }
    @Test void startUsesTheSameChecksAsTheCommandAndReportsJobs() {
        Wire.Reader fail = request(ClientBridge.START, new Wire.Writer().utf("orientation-demo").int32(4).int32(65).int32(4).varInt(90).varInt(1), ClientBridge.RESULT);
        assertFalse(fail.bool()); assertTrue(fail.utf(32767).contains("No free Builder's Chest"));
        fundedChest(-2);
        Wire.Reader ok = request(ClientBridge.START, new Wire.Writer().utf("orientation-demo").int32(4).int32(65).int32(4).varInt(90).varInt(1), ClientBridge.RESULT);
        assertTrue(ok.bool(), "start");
        BuildJob job = plugin.builds().jobs().iterator().next();
        assertEquals(4, job.originX());
        // 90 x transform: the plan stored in the job is already rotated and mirrored.
        assertEquals(PlanTransformer.apply(repository.get("orientation-demo").orElseThrow(), new BlueprintTransform(90, BlueprintTransform.Mirror.X)), job.plan());
        assertTrue(sent.stream().anyMatch(s -> s.channel().equals(ClientBridge.JOBS)), "jobs pushed after start");
        Wire.Reader jobs = request(ClientBridge.JOBS_REQUEST, new Wire.Writer(), ClientBridge.JOBS);
        assertEquals(1, jobs.varInt()); assertEquals(job.id().toString(), jobs.utf(64));
    }
    @Test void malformedAndBadValuesAreRejectedNotThrown() {
        Wire.Reader bad = request(ClientBridge.PLAN_REQUEST, new Wire.Writer().varInt(9999), ClientBridge.RESULT);
        assertFalse(bad.bool());
        fundedChest(-2);
        Wire.Reader rot = request(ClientBridge.START, new Wire.Writer().utf("orientation-demo").int32(4).int32(65).int32(4).varInt(45).varInt(0), ClientBridge.RESULT);
        assertFalse(rot.bool());
        assertTrue(plugin.builds().jobs().isEmpty());
    }
    @Test void captureSavesWithTheCommandsRulesAndRefreshesTheCatalog() {
        world.getBlockAt(1, 65, 1).setType(Material.STONE);
        world.getBlockAt(2, 65, 1).setType(Material.OAK_PLANKS);
        Wire.Reader survival = request(ClientBridge.CAPTURE, new Wire.Writer().utf("hut").int32(1).int32(65).int32(1).int32(2).int32(65).int32(1), ClientBridge.RESULT);
        assertFalse(survival.bool(), "capture needs Creative");
        player.setGameMode(GameMode.CREATIVE);
        Wire.Reader ok = request(ClientBridge.CAPTURE, new Wire.Writer().utf("hut").int32(2).int32(65).int32(1).int32(1).int32(65).int32(1), ClientBridge.RESULT);
        assertTrue(ok.bool()); assertTrue(ok.utf(1000).contains("hut"));
        assertTrue(sent.stream().anyMatch(s -> s.channel().equals(ClientBridge.CATALOG)), "catalog pushed");
        assertEquals(2, repository.get("hut").orElseThrow().blockCount());
        Wire.Reader dup = request(ClientBridge.CAPTURE, new Wire.Writer().utf("hut").int32(1).int32(65).int32(1).int32(2).int32(65).int32(1), ClientBridge.RESULT);
        assertFalse(dup.bool(), "no overwrite");
    }
    static byte[] gzipLitematic(int stoneCount) throws Exception {
        var palette = java.util.List.of(LitematicImportTest.state("minecraft:air"), LitematicImportTest.state("minecraft:stone"), LitematicImportTest.state("minecraft:chest"));
        int[] values = new int[stoneCount + 1]; java.util.Arrays.fill(values, 1); values[stoneCount] = 2;
        var root = LitematicImportTest.litematic("Uploaded", java.util.Map.of("r", LitematicImportTest.region(new int[]{0, 0, 0}, new int[]{stoneCount + 1, 1, 1}, palette, values)));
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(bytes))) { out.writeByte(10); out.writeUTF(""); LitematicImportTest.writeTag(out, root); }
        return bytes.toByteArray();
    }
    void sendChunks(byte[] file, int chunk) {
        for (int offset = 0; offset < file.length; offset += chunk) {
            byte[] part = java.util.Arrays.copyOfRange(file, offset, Math.min(file.length, offset + chunk));
            bridge.onPluginMessageReceived(ClientBridge.UPLOAD_CHUNK, player, new Wire.Writer().varInt(offset).bytes(part).bytes());
        }
    }
    @Test void uploadInChunksImportsAndRefreshesTheCatalog() throws Exception {
        byte[] file = gzipLitematic(40);
        Wire.Reader ready = request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("uploaded").varInt(file.length), ClientBridge.UPLOAD_READY);
        assertTrue(ready.bool());
        sendChunks(file, 100);                                       // many small chunks, beyond the request budget
        Wire.Reader done = request(ClientBridge.UPLOAD_END, new Wire.Writer(), ClientBridge.RESULT);
        assertTrue(done.bool()); String message = done.utf(10000);
        assertTrue(message.contains("40 blocks") && message.contains("chest"), message);
        assertTrue(sent.stream().anyMatch(x -> x.channel().equals(ClientBridge.CATALOG)));
        BlueprintRepository fresh = new BlueprintRepository(plugin); fresh.load();
        assertEquals(40, fresh.get("uploaded").orElseThrow().blockCount());
    }
    @Test void uploadRejectsDuplicatesOversizeDisorderAndMissingPermission() throws Exception {
        byte[] file = gzipLitematic(3);
        assertFalse(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("orientation-demo").varInt(file.length), ClientBridge.UPLOAD_READY).bool(), "duplicate");
        assertFalse(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("big").varInt(100 << 20), ClientBridge.UPLOAD_READY).bool(), "oversize");
        assertTrue(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("jumbled").varInt(file.length), ClientBridge.UPLOAD_READY).bool());
        sent.clear();
        bridge.onPluginMessageReceived(ClientBridge.UPLOAD_CHUNK, player, new Wire.Writer().varInt(5).bytes(new byte[]{1, 2}).bytes());
        assertFalse(new Wire.Reader(sent.getFirst().data()).bool(), "out of order aborts");
        assertFalse(request(ClientBridge.UPLOAD_END, new Wire.Writer(), ClientBridge.RESULT).bool(), "nothing left to finish");
        player.setOp(false);
        assertFalse(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("nope").varInt(file.length), ClientBridge.UPLOAD_READY).bool(), "permission");
    }
    @Test void corruptUploadIsReportedNotThrown() {
        byte[] junk = "definitely not gzip".getBytes();
        assertTrue(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("junk").varInt(junk.length), ClientBridge.UPLOAD_READY).bool());
        sendChunks(junk, 1000);
        Wire.Reader done = request(ClientBridge.UPLOAD_END, new Wire.Writer(), ClientBridge.RESULT);
        assertFalse(done.bool()); assertTrue(done.utf(1000).contains("Invalid blueprint YAML"));
        assertTrue(repository.get("junk").isEmpty());
    }
    @Test void uploadAcceptsABlueprintYamlAndIgnoresItsEmbeddedIdAndOwner() throws Exception {
        String yaml = "id: something-else\nname: Hut\nowner:\n  uuid: 00000000-0000-0000-0000-000000000001\n  name: Mallory\n"
            + "blocks:\n- {x: 0, y: 0, z: 0, block-data: 'minecraft:stone'}\n- {x: 1, y: 0, z: 0, block-data: 'minecraft:oak_planks'}\n";
        byte[] file = yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("hut").varInt(file.length), ClientBridge.UPLOAD_READY).bool());
        sendChunks(file, 50);
        Wire.Reader done = request(ClientBridge.UPLOAD_END, new Wire.Writer(), ClientBridge.RESULT);
        assertTrue(done.bool(), done.utf(1000));
        BlueprintRepository fresh = new BlueprintRepository(plugin); fresh.load();
        assertEquals(2, fresh.get("hut").orElseThrow().blockCount());
        assertTrue(fresh.get("something-else").isEmpty());
        assertEquals(player.getUniqueId(), fresh.owner("hut").orElseThrow().id());
    }
    @Test void uploadedYamlWithUnsupportedBlocksIsRejected() {
        byte[] file = "blocks:\n- {x: 0, y: 0, z: 0, block-data: 'minecraft:chest'}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(request(ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf("bad").varInt(file.length), ClientBridge.UPLOAD_READY).bool());
        sendChunks(file, 1000);
        assertFalse(request(ClientBridge.UPLOAD_END, new Wire.Writer(), ClientBridge.RESULT).bool());
        assertTrue(repository.get("bad").isEmpty());
    }
    @Test void floodingIsRateLimited() {
        sent.clear();
        for (int i = 0; i < 50; i++) bridge.onPluginMessageReceived(ClientBridge.CATALOG_REQUEST, player, new byte[0]);
        assertTrue(sent.size() <= 20, "replies: " + sent.size());
    }

    // ---- Ownership: allow-everyone uploads, per-player cap, owner/operator delete ----

    record Row(String id, String owner, boolean deletable) {}
    record Catalog(int limit, int owned, Map<String, Row> rows) {}
    Wire.Reader ask(ClientBridge b, Player who, String channel, Wire.Writer body, String expect) {
        sent.clear();
        b.onPluginMessageReceived(channel, who, body.bytes());
        return new Wire.Reader(sent.stream().filter(x -> x.channel().equals(expect)).reduce((a, c) -> c)
            .orElseThrow(() -> new AssertionError("no " + expect + "; got " + sent.stream().map(Sent::channel).toList())).data());
    }
    Catalog catalog(ClientBridge b, Player who) {
        Wire.Reader in = ask(b, who, ClientBridge.CATALOG_REQUEST, new Wire.Writer(), ClientBridge.CATALOG);
        int limit = in.varInt(), owned = in.varInt(), n = in.varInt();
        Map<String, Row> rows = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String id = in.utf(64); in.utf(256); in.varInt(); in.varInt(); in.varInt(); in.varInt();
            rows.put(id, new Row(id, in.utf(64), in.bool()));
        }
        in.end();
        return new Catalog(limit, owned, rows);
    }
    /** Upload a small file in one chunk; returns the begin-or-end message and whether it succeeded. */
    Map.Entry<Boolean, String> upload(ClientBridge b, Player who, String id) throws Exception {
        byte[] file = gzipLitematic(3);
        Wire.Reader ready = ask(b, who, ClientBridge.UPLOAD_BEGIN, new Wire.Writer().utf(id).varInt(file.length), ClientBridge.UPLOAD_READY);
        if (!ready.bool()) return Map.entry(false, ready.utf(1000));
        b.onPluginMessageReceived(ClientBridge.UPLOAD_CHUNK, who, new Wire.Writer().varInt(0).bytes(file).bytes());
        Wire.Reader done = ask(b, who, ClientBridge.UPLOAD_END, new Wire.Writer(), ClientBridge.RESULT);
        return Map.entry(done.bool(), done.utf(10000));
    }
    ClientBridge bridgeWith(BlueprintPolicy policy) {
        return new ClientBridge(repository, plugin.builds(), new BuildStarter(repository, plugin.builds(), 16),
            (p, channel, data) -> sent.add(new Sent(channel, data)), 2 << 20, policy);
    }

    @Test void allowEveryoneLetsPlayersUploadAndRecordsTheOwner() throws Exception {
        PlayerMock alex = server.addPlayer("Alex");
        assertFalse(upload(bridge, alex, "nope").getKey(), "default: uploads are for blueprints.upload only");
        ClientBridge open = bridgeWith(new BlueprintPolicy(true, 10));
        Wire.Reader welcome = ask(open, alex, ClientBridge.HELLO, new Wire.Writer().varInt(ClientBridge.PROTOCOL), ClientBridge.WELCOME);
        welcome.varInt(); welcome.bool(); welcome.varInt(); welcome.bool(); welcome.varInt(); welcome.varInt();
        assertTrue(welcome.bool(), "welcome says this player can upload");
        assertTrue(upload(open, alex, "alex-hut").getKey());
        Catalog c = catalog(open, alex);
        assertEquals(10, c.limit()); assertEquals(1, c.owned());
        assertEquals(new Row("alex-hut", "Alex", true), c.rows().get("alex-hut"));
        assertFalse(c.rows().get("orientation-demo").deletable(), "players can't delete what isn't theirs");
        BlueprintRepository fresh = new BlueprintRepository(plugin); fresh.load();
        assertEquals("Alex", fresh.owner("alex-hut").orElseThrow().name(), "owner survives a restart");
        assertEquals(alex.getUniqueId(), fresh.owner("alex-hut").orElseThrow().id());
    }
    @Test void perPlayerCapCountsCapturesAndUploadsButNotForOperators() throws Exception {
        PlayerMock alex = server.addPlayer("Alex");
        ClientBridge capped = bridgeWith(new BlueprintPolicy(true, 2));
        assertTrue(upload(capped, alex, "one").getKey());
        assertTrue(upload(capped, alex, "two").getKey());
        var third = upload(capped, alex, "three");
        assertFalse(third.getKey()); assertTrue(third.getValue().contains("limit is 2"), third.getValue());
        assertTrue(repository.get("three").isEmpty());
        player.setGameMode(GameMode.CREATIVE); world.getBlockAt(1, 65, 1).setType(Material.STONE);
        for (int i = 0; i < 3; i++) assertTrue(upload(capped, player, "op-" + i).getKey(), "operators have no cap");
        alex.addAttachment(plugin, "blueprints.capture", true); alex.setGameMode(GameMode.CREATIVE);
        alex.teleport(new Location(world, 0, 65, 0));
        Wire.Reader cap = ask(capped, alex, ClientBridge.CAPTURE, new Wire.Writer().utf("cap").int32(1).int32(65).int32(1).int32(1).int32(65).int32(1), ClientBridge.RESULT);
        assertFalse(cap.bool(), "captures count toward the cap too"); assertTrue(cap.utf(1000).contains("limit"));
        // Deleting one makes room again.
        assertTrue(ask(capped, alex, ClientBridge.DELETE, new Wire.Writer().utf("one"), ClientBridge.RESULT).bool());
        assertTrue(upload(capped, alex, "three").getKey());
    }
    @Test void onlyOwnersAndOperatorsDeleteAndFilesGoToTrash() throws Exception {
        PlayerMock alex = server.addPlayer("Alex"), sam = server.addPlayer("Sam");
        ClientBridge open = bridgeWith(new BlueprintPolicy(true, 10));
        assertTrue(upload(open, alex, "alex-hut").getKey());
        assertFalse(catalog(open, sam).rows().get("alex-hut").deletable());
        Wire.Reader refused = ask(open, sam, ClientBridge.DELETE, new Wire.Writer().utf("alex-hut"), ClientBridge.RESULT);
        assertFalse(refused.bool()); assertTrue(repository.get("alex-hut").isPresent());
        assertFalse(ask(open, alex, ClientBridge.DELETE, new Wire.Writer().utf("orientation-demo"), ClientBridge.RESULT).bool());

        Wire.Reader ok = ask(open, alex, ClientBridge.DELETE, new Wire.Writer().utf("alex-hut"), ClientBridge.RESULT);
        assertTrue(ok.bool(), "owner deletes their own");
        assertTrue(sent.stream().anyMatch(x -> x.channel().equals(ClientBridge.CATALOG)), "catalog refreshed");
        assertTrue(repository.get("alex-hut").isEmpty());
        var dir = plugin.getDataFolder().toPath().resolve("blueprints");
        assertTrue(java.nio.file.Files.exists(dir.resolve("trash/blueprint-alex-hut.yml")), "kept in trash for operators");
        BlueprintRepository fresh = new BlueprintRepository(plugin); fresh.load();
        assertTrue(fresh.get("alex-hut").isEmpty(), "stays deleted after a restart");
        assertEquals(0, fresh.ownedBy(alex.getUniqueId()));

        assertTrue(upload(open, alex, "alex-hut").getKey(), "the name is free again");
        assertTrue(ask(open, player, ClientBridge.DELETE, new Wire.Writer().utf("alex-hut"), ClientBridge.RESULT).bool(), "operators delete anyone's");
        assertTrue(java.nio.file.Files.list(dir.resolve("trash")).count() >= 2, "a second trashed copy doesn't overwrite the first");
        assertFalse(ask(open, player, ClientBridge.DELETE, new Wire.Writer().utf("ghost"), ClientBridge.RESULT).bool());
    }
    @Test void deletingABlueprintLeavesItsRunningBuildAlone() {
        fundedChest(-2);
        assertTrue(request(ClientBridge.START, new Wire.Writer().utf("orientation-demo").int32(3).int32(65).int32(3).varInt(0).varInt(0), ClientBridge.RESULT).bool());
        assertTrue(request(ClientBridge.DELETE, new Wire.Writer().utf("orientation-demo"), ClientBridge.RESULT).bool());
        assertEquals(1, plugin.builds().jobs().size());
        assertEquals(38, plugin.builds().jobs().iterator().next().plan().blockCount(), "job keeps its own snapshot");
    }
}
