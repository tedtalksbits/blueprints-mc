package dev.whizzle.blueprints;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Reads the block states of a Litematica {@code .litematic} file into a blueprint.
 * Block entities, entities, and scheduled ticks are ignored. Unsupported blocks are skipped and reported;
 * waterlogged/powered states are switched off so they fit the survival palette.
 */
public final class LitematicImport {
    public static final long MAX_FILE_BYTES = 8L << 20, MAX_NBT_BYTES = 64L << 20, MAX_REGION_CELLS = 1L << 24;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    /** A converted schematic. {@code skipped} counts unsupported blocks by id; {@code normalized} counts states changed to fit. */
    public record Result(ConstructionPlan plan, Map<String, Integer> skipped, int normalized) {
        public int skippedTotal() { return skipped.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    private LitematicImport() {}

    public static Result read(Path file, String id) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IllegalArgumentException("File is larger than " + (MAX_FILE_BYTES >> 20) + " MB");
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return convert(Nbt.readRoot(in), id);
        }
    }

    @SuppressWarnings("unchecked")
    static Result convert(Map<String, Object> root, String id) {
        Map<String, Object> regions = Nbt.compound(root, "Regions");
        Object metadata = root.get("Metadata");
        // Litematica writes "Unnamed" when the player never named the schematic; the file-based id reads better.
        String name = metadata instanceof Map<?, ?> m && m.get("Name") instanceof String s && !s.isBlank() && !s.equalsIgnoreCase("Unnamed") ? s : id;
        Map<String, Integer> skipped = new TreeMap<>();
        int normalized = 0;
        List<int[]> positions = new ArrayList<>();
        List<String> states = new ArrayList<>();
        for (Object regionValue : regions.values()) {
            if (!(regionValue instanceof Map<?, ?>)) throw new IllegalArgumentException("Malformed region");
            Map<String, Object> region = (Map<String, Object>) regionValue;
            Map<String, Object> pos = Nbt.compound(region, "Position"), size = Nbt.compound(region, "Size");
            int sx = Nbt.integer(size, "x"), sy = Nbt.integer(size, "y"), sz = Nbt.integer(size, "z");
            if (sx == 0 || sy == 0 || sz == 0) continue;
            long ax = Math.abs((long) sx), ay = Math.abs((long) sy), az = Math.abs((long) sz);
            if (ax * ay * az > MAX_REGION_CELLS) throw new IllegalArgumentException("Region is too large");
            // A negative size extends from Position toward negative coordinates.
            long ox = Nbt.integer(pos, "x") + (sx < 0 ? sx + 1L : 0), oy = Nbt.integer(pos, "y") + (sy < 0 ? sy + 1L : 0),
                oz = Nbt.integer(pos, "z") + (sz < 0 ? sz + 1L : 0);
            List<Object> palette = Nbt.list(region, "BlockStatePalette");
            if (palette.isEmpty()) throw new IllegalArgumentException("Region has an empty palette");
            if (!(region.get("BlockStates") instanceof long[] packed)) throw new IllegalArgumentException("Region is missing BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            if ((long) packed.length * 64 < ax * ay * az * bits) throw new IllegalArgumentException("BlockStates array is too short");
            Resolved[] resolved = new Resolved[palette.size()];
            for (int i = 0; i < resolved.length; i++) resolved[i] = resolve(palette.get(i));
            for (int y = 0; y < ay; y++) for (int z = 0; z < az; z++) for (int x = 0; x < ax; x++) {
                long index = ((long) y * az + z) * ax + x;
                int value = get(packed, index, bits);
                if (value >= resolved.length) throw new IllegalArgumentException("Palette index out of range");
                Resolved r = resolved[value];
                if (r.air()) continue;
                if (r.data() == null) { skipped.merge(r.id(), 1, Integer::sum); continue; }
                if (r.normalized()) normalized++;
                if (states.size() > ConstructionPlan.MAX_BLOCKS) continue; // keep counting, stop storing
                positions.add(new int[]{Math.toIntExact(ox + x), Math.toIntExact(oy + y), Math.toIntExact(oz + z)});
                states.add(r.data());
            }
        }
        if (states.isEmpty()) throw new IllegalArgumentException("No supported blocks found" + (skipped.isEmpty() ? "" : " (skipped " + skipped.keySet() + ")"));
        if (states.size() > ConstructionPlan.MAX_BLOCKS)
            throw new IllegalArgumentException("Schematic has more than " + ConstructionPlan.MAX_BLOCKS + " supported blocks; the current limit is " + ConstructionPlan.MAX_BLOCKS);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : positions) {
            minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]); maxZ = Math.max(maxZ, p[2]);
        }
        long w = (long) maxX - minX + 1, h = (long) maxY - minY + 1, d = (long) maxZ - minZ + 1;
        if (w > ConstructionPlan.MAX_SPAN || h > ConstructionPlan.MAX_SPAN || d > ConstructionPlan.MAX_SPAN)
            throw new IllegalArgumentException("Schematic is " + w + "x" + h + "x" + d + "; the current limit is "
                + ConstructionPlan.MAX_SPAN + " blocks per side");
        List<BlueprintBlock> blocks = new ArrayList<>(states.size());
        for (int i = 0; i < states.size(); i++) {
            int[] p = positions.get(i);
            blocks.add(new BlueprintBlock(Math.subtractExact(p[0], minX), Math.subtractExact(p[1], minY), Math.subtractExact(p[2], minZ), states.get(i)));
        }
        return new Result(PlanCompiler.compile(new BlueprintDefinition(id, name, blocks)), Collections.unmodifiableMap(skipped), normalized);
    }

    private record Resolved(String id, String data, boolean air, boolean normalized) {}

    @SuppressWarnings("unchecked")
    private static Resolved resolve(Object entry) {
        if (!(entry instanceof Map<?, ?>)) throw new IllegalArgumentException("Malformed palette entry");
        Map<String, Object> map = (Map<String, Object>) entry;
        if (!(map.get("Name") instanceof String id)) throw new IllegalArgumentException("Palette entry has no Name");
        if (AIR.contains(id)) return new Resolved(id, null, true, false);
        StringBuilder text = new StringBuilder(id);
        if (map.get("Properties") instanceof Map<?, ?> properties && !properties.isEmpty()) {
            StringJoiner joined = new StringJoiner(",", "[", "]");
            properties.forEach((k, v) -> joined.add(k + "=" + v));
            text.append(joined);
        }
        BlockData original;
        try { original = Bukkit.createBlockData(text.toString()); }
        catch (IllegalArgumentException e) { return new Resolved(id, null, false, false); }
        BlockData data = MaterialCost.normalize(original);
        if (MaterialCost.unsupportedReason(data) != null) return new Resolved(id, null, false, false);
        return new Resolved(id, data.getAsString(), false, !data.equals(original));
    }

    /** Litematica packs entries tightly; an entry may span two longs. */
    static int get(long[] packed, long index, int bits) {
        long start = index * bits;
        int word = (int) (start >>> 6), offset = (int) (start & 63);
        long mask = (1L << bits) - 1;
        long value = packed[word] >>> offset;
        if (offset + bits > 64) value |= packed[word + 1] << (64 - offset);
        return (int) (value & mask);
    }

    /** Minimal bounded NBT reader (big-endian, Java edition). */
    static final class Nbt {
        private Nbt() {}

        @SuppressWarnings("unchecked")
        static Map<String, Object> readRoot(InputStream raw) throws IOException {
            Limited limited = new Limited(raw, MAX_NBT_BYTES);
            DataInputStream in = new DataInputStream(limited);
            if (in.readUnsignedByte() != 10) throw new IllegalArgumentException("Not a .litematic file (root is not an NBT compound)");
            in.readUTF();
            return (Map<String, Object>) payload(in, limited, 10, 0);
        }
        private static Object payload(DataInputStream in, Limited limit, int type, int depth) throws IOException {
            if (depth > 64) throw new IllegalArgumentException("NBT nesting too deep");
            return switch (type) {
                case 1 -> in.readByte();
                case 2 -> in.readShort();
                case 3 -> in.readInt();
                case 4 -> in.readLong();
                case 5 -> in.readFloat();
                case 6 -> in.readDouble();
                case 7 -> { byte[] a = new byte[length(in, limit, 1)]; in.readFully(a); yield a; }
                case 8 -> in.readUTF();
                case 9 -> {
                    int element = in.readUnsignedByte(), n = length(in, limit, 1);
                    List<Object> list = new ArrayList<>(Math.min(n, 1024));
                    for (int i = 0; i < n; i++) list.add(payload(in, limit, element, depth + 1));
                    yield list;
                }
                case 10 -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int t = in.readUnsignedByte(); t != 0; t = in.readUnsignedByte()) map.put(in.readUTF(), payload(in, limit, t, depth + 1));
                    yield map;
                }
                case 11 -> { int[] a = new int[length(in, limit, 4)]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); yield a; }
                case 12 -> { long[] a = new long[length(in, limit, 8)]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); yield a; }
                default -> throw new IllegalArgumentException("Corrupt NBT (tag type " + type + ")");
            };
        }
        private static int length(DataInputStream in, Limited limit, int elementBytes) throws IOException {
            int n = in.readInt();
            if (n < 0 || (long) n * elementBytes > limit.remaining()) throw new IllegalArgumentException("Corrupt or oversized NBT array");
            return n;
        }
        @SuppressWarnings("unchecked")
        static Map<String, Object> compound(Map<String, Object> parent, String key) {
            if (parent.get(key) instanceof Map<?, ?> m) return (Map<String, Object>) m;
            throw new IllegalArgumentException("Missing " + key);
        }
        @SuppressWarnings("unchecked")
        static List<Object> list(Map<String, Object> parent, String key) {
            if (parent.get(key) instanceof List<?> l) return (List<Object>) l;
            throw new IllegalArgumentException("Missing " + key);
        }
        static int integer(Map<String, Object> parent, String key) {
            if (parent.get(key) instanceof Number n) return n.intValue();
            throw new IllegalArgumentException("Missing " + key);
        }
    }

    /** Caps decompressed bytes so a small file cannot expand without bound. */
    private static final class Limited extends FilterInputStream {
        private long remaining;
        Limited(InputStream in, long limit) { super(in); remaining = limit; }
        long remaining() { return remaining; }
        private void take(long n) throws IOException {
            remaining -= n;
            if (remaining < 0) throw new IOException("Decompressed data exceeds " + (MAX_NBT_BYTES >> 20) + " MB");
        }
        @Override public int read() throws IOException { int b = super.read(); if (b >= 0) take(1); return b; }
        @Override public int read(byte[] b, int off, int len) throws IOException { int n = super.read(b, off, len); if (n > 0) take(n); return n; }
    }
}
