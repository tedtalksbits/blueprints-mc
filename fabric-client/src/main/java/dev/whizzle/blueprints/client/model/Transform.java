package dev.whizzle.blueprints.client.model;

/**
 * Same math as the plugin's BlueprintTransform: mirror local X first (optional), then rotate clockwise seen from
 * above. Mirror X plus the four rotations reach all eight orientations, so the client never needs mirror Z.
 */
public record Transform(int rotation, boolean mirrorX) {
	public static final Transform IDENTITY = new Transform(0, false);

	public Transform {
		if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) throw new IllegalArgumentException("rotation");
	}

	public Transform rotatedClockwise() { return new Transform((rotation + 90) % 360, mirrorX); }
	public Transform rotatedCounterClockwise() { return new Transform((rotation + 270) % 360, mirrorX); }
	public Transform mirrored() { return new Transform(rotation, !mirrorX); }

	/** Protocol mirror code: 0 none, 1 x. */
	public int mirrorCode() { return mirrorX ? 1 : 0; }

	public int[] apply(int x, int y, int z) {
		int mx = mirrorX ? -x : x;
		return switch (rotation) {
			case 0 -> new int[]{mx, y, z};
			case 90 -> new int[]{-z, y, mx};
			case 180 -> new int[]{-mx, y, -z};
			case 270 -> new int[]{z, y, -mx};
			default -> throw new IllegalStateException();
		};
	}
}
