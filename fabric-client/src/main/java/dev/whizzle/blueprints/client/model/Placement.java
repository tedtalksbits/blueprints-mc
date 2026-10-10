package dev.whizzle.blueprints.client.model;

import java.util.List;

/**
 * Where a blueprint preview sits in the world. Pure logic (no Minecraft types) so it is unit-tested.
 *
 * <p>The player sees and moves a box. {@code anchor} is the world position of that box's minimum corner. The plugin
 * instead wants the blueprint origin, around which the transform pivots: {@link #origin()} converts.
 */
public final class Placement {
	public record Block(int x, int y, int z, int state) {}
	public record Placed(int x, int y, int z, int state) {}

	private final List<Block> blocks;
	private Transform transform = Transform.IDENTITY;
	private int anchorX, anchorY, anchorZ;
	private int minX, minY, minZ, sizeX, sizeY, sizeZ;

	public Placement(List<Block> blocks, int anchorX, int anchorY, int anchorZ) {
		if (blocks.isEmpty()) throw new IllegalArgumentException("empty blueprint");
		this.blocks = List.copyOf(blocks);
		this.anchorX = anchorX; this.anchorY = anchorY; this.anchorZ = anchorZ;
		recomputeBounds();
	}

	private void recomputeBounds() {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (Block b : blocks) {
			int[] p = transform.apply(b.x(), b.y(), b.z());
			x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); z0 = Math.min(z0, p[2]);
			x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); z1 = Math.max(z1, p[2]);
		}
		minX = x0; minY = y0; minZ = z0;
		sizeX = x1 - x0 + 1; sizeY = y1 - y0 + 1; sizeZ = z1 - z0 + 1;
	}

	public Transform transform() { return transform; }
	public int anchorX() { return anchorX; }
	public int anchorY() { return anchorY; }
	public int anchorZ() { return anchorZ; }
	public int sizeX() { return sizeX; }
	public int sizeY() { return sizeY; }
	public int sizeZ() { return sizeZ; }
	public int blockCount() { return blocks.size(); }

	/** Origin to send to the server: the world position of local (0,0,0) after the transform. */
	public int[] origin() { return new int[]{anchorX - minX, anchorY - minY, anchorZ - minZ}; }

	/** Changes orientation but keeps the box centered where it was, so it turns in place. */
	public void setTransform(Transform next) {
		double cx = anchorX + sizeX / 2.0, cz = anchorZ + sizeZ / 2.0;
		transform = next;
		recomputeBounds();
		anchorX = (int) Math.floor(cx - sizeX / 2.0);
		anchorZ = (int) Math.floor(cz - sizeZ / 2.0);
	}

	public void moveTo(int x, int y, int z) { anchorX = x; anchorY = y; anchorZ = z; }
	public void move(int dx, int dy, int dz) { anchorX += dx; anchorY += dy; anchorZ += dz; }

	/** Moves relative to where the player faces: yaw in Minecraft degrees (0 = south, 90 = west). */
	public void moveRelative(float yaw, int forward, int right, int up) {
		int[] f = facing(yaw);           // forward unit vector on X/Z
		int[] r = {-f[1], f[0]};         // right of forward, seen from above
		move(f[0] * forward + r[0] * right, up, f[1] * forward + r[1] * right);
	}

	/** Snaps yaw to the nearest horizontal direction: {dx, dz}. */
	public static int[] facing(float yaw) {
		int quadrant = Math.floorMod(Math.round(yaw / 90f), 4);
		return switch (quadrant) { case 0 -> new int[]{0, 1}; case 1 -> new int[]{-1, 0}; case 2 -> new int[]{0, -1}; default -> new int[]{1, 0}; };
	}

	/** Puts the box in front of the player: its near edge one block past the target, centered on the player's line. */
	public void placeInFront(int targetX, int targetY, int targetZ, float yaw) {
		int[] f = facing(yaw);
		int x = f[0] != 0 ? (f[0] > 0 ? targetX + 1 : targetX - sizeX) : targetX - sizeX / 2;
		int z = f[1] != 0 ? (f[1] > 0 ? targetZ + 1 : targetZ - sizeZ) : targetZ - sizeZ / 2;
		moveTo(x, targetY, z);
	}

	/** World positions of every block in the current orientation. */
	public void forEachPlaced(java.util.function.Consumer<Placed> action) {
		int[] o = origin();
		for (Block b : blocks) {
			int[] p = transform.apply(b.x(), b.y(), b.z());
			action.accept(new Placed(o[0] + p[0], o[1] + p[1], o[2] + p[2], b.state()));
		}
	}
}
