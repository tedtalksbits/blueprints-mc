package dev.whizzle.blueprints;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class LitematicImportTest {
    ServerMock server;
    PlayerMock player;
    BlueprintsPlugin plugin;
    @BeforeEach void setUp() {
        server = MockBukkit.mock(); WorldMock world = server.addSimpleWorld("import");
        player = server.addPlayer(); player.setOp(true); player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 0, 65, 0));
        plugin = MockBukkit.load(BlueprintsPlugin.class);
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    // ---- independent test-side writer: Litematica layout, tight bit packing, big-endian NBT ----
    static Map<String, Object> state(String name, String... props) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("Name", name);
        if (props.length > 0) { Map<String, Object> p = new LinkedHashMap<>(); for (int i = 0; i < props.length; i += 2) p.put(props[i], props[i + 1]); m.put("Properties", p); }
        return m;
    }
    static long[] pack(int[] values, int paletteSize) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        long[] out = new long[(int) (((long) values.length * bits + 63) / 64)];
        for (int i = 0; i < values.length; i++) {
            long bit = (long) i * bits; int word = (int) (bit / 64), off = (int) (bit % 64);
            out[word] |= (long) values[i] << off;
            if (off + bits > 64) out[word + 1] |= (long) values[i] >>> (64 - off);
        }
        return out;
    }
    static Map<String, Object> xyz(int x, int y, int z) { Map<String, Object> m = new LinkedHashMap<>(); m.put("x", x); m.put("y", y); m.put("z", z); return m; }
    /** values indexed (y * |sz| + z) * |sx| + x */
    static Map<String, Object> region(int[] pos, int[] size, List<Map<String, Object>> palette, int[] values) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("Position", xyz(pos[0], pos[1], pos[2])); r.put("Size", xyz(size[0], size[1], size[2]));
        r.put("BlockStatePalette", new ArrayList<Object>(palette)); r.put("BlockStates", pack(values, palette.size()));
        r.put("TileEntities", new ArrayList<Object>()); r.put("Entities", new ArrayList<Object>());
        return r;
    }
    static Map<String, Object> litematic(String name, Map<String, Object> regions) {
        Map<String, Object> root = new LinkedHashMap<>(); root.put("Version", 6); root.put("MinecraftDataVersion", 4903);
        Map<String, Object> meta = new LinkedHashMap<>(); meta.put("Name", name); root.put("Metadata", meta); root.put("Regions", regions);
        return root;
    }
    static void writeTag(DataOutputStream out, Object v) throws IOException {
        if (v instanceof Map<?, ?> m) { for (var e : m.entrySet()) { out.writeByte(type(e.getValue())); out.writeUTF((String) e.getKey()); writeTag(out, e.getValue()); } out.writeByte(0); }
        else if (v instanceof List<?> l) { out.writeByte(l.isEmpty() ? 0 : type(l.getFirst())); out.writeInt(l.size()); for (Object o : l) writeTag(out, o); }
        else if (v instanceof String s) out.writeUTF(s);
        else if (v instanceof Integer i) out.writeInt(i);
        else if (v instanceof long[] a) { out.writeInt(a.length); for (long x : a) out.writeLong(x); }
        else throw new IllegalArgumentException(String.valueOf(v));
    }
    static int type(Object v) { return v instanceof Map ? 10 : v instanceof List ? 9 : v instanceof String ? 8 : v instanceof Integer ? 3 : v instanceof long[] ? 12 : -1; }
    Path write(String fileName, Map<String, Object> root) throws IOException {
        Path dir = plugin.getDataFolder().toPath().resolve("import"); Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) { out.writeByte(10); out.writeUTF(""); writeTag(out, root); }
        return file;
    }
    static Map<String, String> byPosition(ConstructionPlan plan) {
        Map<String, String> m = new HashMap<>();
        plan.steps().forEach(s -> s.blocks().forEach(b -> m.put(b.x() + "," + b.y() + "," + b.z(), b.blockData())));
        return m;
    }

    @Test void decodesPackedEntriesThatSpanTwoLongs() throws Exception {
        // 5 palette entries -> 3 bits per entry; 50 entries = 150 bits, so several entries cross long boundaries.
        var palette = List.of(state("minecraft:air"), state("minecraft:stone"), state("minecraft:stone_bricks"),
            state("minecraft:oak_planks"), state("minecraft:cobblestone"));
        int[] values = new int[5 * 2 * 5];
        for (int i = 0; i < values.length; i++) values[i] = i % 5;
        values[0] = 1; // keep the corner occupied so the import does not shift coordinates
        var result = LitematicImport.read(write("pattern.litematic", litematic("Pattern", Map.of("main", region(new int[]{0, 0, 0}, new int[]{5, 2, 5}, palette, values)))), "pattern");
        Map<String, String> got = byPosition(result.plan());
        String[] names = {null, "minecraft:stone", "minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:cobblestone"};
        int expectedBlocks = 0;
        for (int y = 0; y < 2; y++) for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
            int index = (y * 5 + z) * 5 + x;
            String expected = names[index == 0 ? 1 : index % 5];
            assertEquals(expected, got.get(x + "," + y + "," + z), "at " + x + "," + y + "," + z);
            if (expected != null) expectedBlocks++;
        }
        assertEquals(expectedBlocks, result.plan().blockCount());
        assertEquals("Pattern", result.plan().name());
        assertEquals(0, result.skippedTotal());
    }
    @Test void negativeSizesAndMultipleRegionsAreNormalizedToTheMinimumCorner() throws Exception {
        var palette = List.of(state("minecraft:air"), state("minecraft:stone"));
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("a", region(new int[]{0, 0, 0}, new int[]{-2, 1, 1}, palette, new int[]{1, 1})); // covers x = -1..0
        regions.put("b", region(new int[]{3, 2, 0}, new int[]{1, 1, 1}, palette, new int[]{1}));
        var plan = LitematicImport.read(write("two.litematic", litematic("Two", regions)), "two").plan();
        assertEquals(Set.of("0,0,0", "1,0,0", "4,2,0"), byPosition(plan).keySet());
    }
    @Test void unsupportedBlocksAreSkippedAndWaterloggedStatesNormalized() throws Exception {
        var palette = List.of(state("minecraft:air"), state("minecraft:campfire"), state("minecraft:not_a_real_block"),
            state("minecraft:oak_stairs", "facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "true"),
            state("minecraft:stone"));
        var result = LitematicImport.read(write("mixed.litematic", litematic("Mixed", Map.of("r",
            region(new int[]{0, 0, 0}, new int[]{4, 1, 1}, palette, new int[]{1, 2, 3, 4})))), "mixed");
        assertEquals(Map.of("minecraft:campfire", 1, "minecraft:not_a_real_block", 1), result.skipped());
        assertEquals(1, result.normalized());
        // The two skipped blocks at x=0..1 leave the stairs as the new minimum corner.
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", byPosition(result.plan()).get("0,0,0"));
        assertEquals(2, result.plan().blockCount());
    }
    @Test void oversizedSchematicsAreRejectedWithAClearMessage() throws Exception {
        var palette = List.of(state("minecraft:air"), state("minecraft:stone"));
        int[] values = new int[70]; Arrays.fill(values, 1);
        Path file = write("long.litematic", litematic("Long", Map.of("r", region(new int[]{0, 0, 0}, new int[]{70, 1, 1}, palette, values))));
        var e = assertThrows(IllegalArgumentException.class, () -> LitematicImport.read(file, "long"));
        assertTrue(e.getMessage().contains("70x1x1"), e.getMessage());
    }
    @Test void corruptFilesFailCleanly() throws Exception {
        Path dir = plugin.getDataFolder().toPath().resolve("import"); Files.createDirectories(dir);
        Path file = Files.write(dir.resolve("junk.litematic"), "not gzip".getBytes());
        assertThrows(IOException.class, () -> LitematicImport.read(file, "junk"));
    }
    @Test void importCommandSavesBlueprintAndRejectsPathTricks() throws Exception {
        var palette = List.of(state("minecraft:air"), state("minecraft:stone"), state("minecraft:campfire"));
        write("My House.v2.litematic".replace(" ", "_"), litematic("My House", Map.of("r", region(new int[]{0, 0, 0}, new int[]{2, 1, 1}, palette, new int[]{1, 2}))));
        assertTrue(server.dispatchCommand(player, "blueprint import ../../plugins/x"));
        assertTrue(player.nextMessage().contains("plain file name"));
        assertTrue(server.dispatchCommand(player, "blueprint import My_House.v2"));
        String first = player.nextMessage(), second = player.nextMessage();
        assertTrue(first.contains("Imported my_house-v2"), first);
        assertTrue(second.contains("Skipped 1") && second.contains("campfire"), second);
        BlueprintRepository fresh = new BlueprintRepository(plugin); fresh.load();
        assertEquals(1, fresh.get("my_house-v2").orElseThrow().blockCount());
        assertTrue(player.nextMessage().contains("/blueprint materials"));
        assertTrue(server.dispatchCommand(player, "blueprint import My_House.v2"));
        assertTrue(player.nextMessage().contains("already exists"));
    }
    @Test void idFromFileSanitizes() {
        assertEquals("my_house-v2", BlueprintCommand.idFromFile("My_House.v2.litematic"));
        assertEquals("castle", BlueprintCommand.idFromFile("--Castle.litematic"));
    }
}
