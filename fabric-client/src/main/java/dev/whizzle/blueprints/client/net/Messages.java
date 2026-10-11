package dev.whizzle.blueprints.client.net;

import dev.whizzle.blueprints.client.model.Placement;

import java.util.ArrayList;
import java.util.List;

/** Typed protocol messages and their encodings (pure Java; Minecraft-specific sending lives in Net). */
public final class Messages {
	public static final int PROTOCOL = 1;
	public static final String HELLO = "hello", WELCOME = "welcome", CATALOG_REQUEST = "catalog_request", CATALOG = "catalog",
		PLAN_REQUEST = "plan_request", PLAN = "plan", MATERIALS_REQUEST = "materials_request", MATERIALS = "materials",
		START = "start", CANCEL = "cancel", RESULT = "result", JOBS_REQUEST = "jobs_request", JOBS = "jobs", CAPTURE = "capture",
		UPLOAD_BEGIN = "upload_begin", UPLOAD_CHUNK = "upload_chunk", UPLOAD_END = "upload_end", UPLOAD_READY = "upload_ready";
	public static final List<String> CLIENTBOUND = List.of(WELCOME, CATALOG, PLAN, MATERIALS, RESULT, JOBS, UPLOAD_READY);
	public static final List<String> SERVERBOUND = List.of(HELLO, CATALOG_REQUEST, PLAN_REQUEST, MATERIALS_REQUEST, START, CANCEL, JOBS_REQUEST, CAPTURE, UPLOAD_BEGIN, UPLOAD_CHUNK, UPLOAD_END);
	private static final int MAX = 1 << 16;

	private Messages() {}

	public record Welcome(int protocol, boolean canBuild, int chestRadius, boolean canCapture, int maxBlocks, int maxSpan, boolean canUpload, int maxUploadBytes) {}
	public record Entry(String id, String name, int blocks, int sizeX, int sizeY, int sizeZ) {}
	public record Plan(String id, String name, List<String> palette, List<Placement.Block> blocks) {}
	public record Material(String material, int needed, int inChest) {
		public int missing() { return Math.max(0, needed - inChest); }
	}
	/** {@code building}: rows are what's left for the player's build of this blueprint, against that build's chest. */
	public record Materials(String id, boolean building, String jobId, int done, int total,
	                        boolean chestFound, int chestX, int chestY, int chestZ, List<Material> rows) {
		public int totalMissing() { return rows.stream().mapToInt(Material::missing).sum(); }
	}
	public record Result(boolean ok, String message) {}
	public record Job(String id, String blueprintId, String name, int done, int total, String state, String reason, int x, int y, int z) {
		public boolean active() { return !state.equals("COMPLETE") && !state.equals("CANCELLED"); }
	}

	public static Welcome welcome(byte[] data) { Wire.Reader in = new Wire.Reader(data); return new Welcome(in.varInt(), in.bool(), in.varInt(), in.bool(), in.varInt(), in.varInt(), in.bool(), in.varInt()); }

	public static List<Entry> catalog(byte[] data) {
		Wire.Reader in = new Wire.Reader(data);
		int n = in.count(MAX);
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) out.add(new Entry(in.utf(), in.utf(), in.varInt(), in.varInt(), in.varInt(), in.varInt()));
		return out;
	}

	public static Plan plan(byte[] data) {
		Wire.Reader in = new Wire.Reader(data);
		String id = in.utf(), name = in.utf();
		int p = in.count(MAX);
		List<String> palette = new ArrayList<>(p);
		for (int i = 0; i < p; i++) palette.add(in.utf());
		int n = in.count(MAX);
		List<Placement.Block> blocks = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int x = in.varInt(), y = in.varInt(), z = in.varInt(), s = in.varInt();
			if (s < 0 || s >= p) throw new IllegalArgumentException("palette index");
			blocks.add(new Placement.Block(x, y, z, s));
		}
		return new Plan(id, name, palette, blocks);
	}

	public static Materials materials(byte[] data) {
		Wire.Reader in = new Wire.Reader(data);
		String id = in.utf();
		boolean building = in.bool();
		String jobId = ""; int done = 0, total = 0;
		if (building) { jobId = in.utf(); done = in.varInt(); total = in.varInt(); }
		boolean found = in.bool();
		int x = 0, y = 0, z = 0;
		if (found) { x = in.int32(); y = in.int32(); z = in.int32(); }
		int n = in.count(MAX);
		List<Material> rows = new ArrayList<>(n);
		for (int i = 0; i < n; i++) rows.add(new Material(in.utf(), in.varInt(), in.varInt()));
		return new Materials(id, building, jobId, done, total, found, x, y, z, rows);
	}

	public static Result result(byte[] data) { Wire.Reader in = new Wire.Reader(data); return new Result(in.bool(), in.utf()); }

	public static List<Job> jobs(byte[] data) {
		Wire.Reader in = new Wire.Reader(data);
		int n = in.count(MAX);
		List<Job> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++)
			out.add(new Job(in.utf(), in.utf(), in.utf(), in.varInt(), in.varInt(), in.utf(), in.utf(), in.int32(), in.int32(), in.int32()));
		return out;
	}

	public static byte[] hello() { return new Wire.Writer().varInt(PROTOCOL).bytes(); }
	public static byte[] empty() { return new byte[0]; }
	public static byte[] id(String id) { return new Wire.Writer().utf(id).bytes(); }
	public static byte[] uploadBegin(String id, int size) { return new Wire.Writer().utf(id).varInt(size).bytes(); }
	public static byte[] uploadChunk(int offset, byte[] data) { return new Wire.Writer().varInt(offset).bytes(data).bytes(); }

	public static byte[] capture(String id, int[] a, int[] b) {
		return new Wire.Writer().utf(id).int32(a[0]).int32(a[1]).int32(a[2]).int32(b[0]).int32(b[1]).int32(b[2]).bytes();
	}

	/** A valid blueprint id from what the player typed: lowercase letters, digits, '_' and '-', up to 64. */
	public static String toId(String name) {
		String id = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^[^a-z0-9]+", "").replaceAll("-+$", "");
		return id.length() > 64 ? id.substring(0, 64) : id;
	}

	public static byte[] start(String id, int[] origin, int rotation, int mirror) {
		return new Wire.Writer().utf(id).int32(origin[0]).int32(origin[1]).int32(origin[2]).varInt(rotation).varInt(mirror).bytes();
	}
}
