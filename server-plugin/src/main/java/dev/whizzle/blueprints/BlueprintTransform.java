package dev.whizzle.blueprints;

import java.util.Locale;
import java.util.Objects;

/** Mirror a local axis first, then rotate clockwise about the blueprint's origin (viewed from above). */
public record BlueprintTransform(int rotation, Mirror mirror) {
    public enum Mirror { NONE, X, Z }
    public record Position(int x, int y, int z) {}
    public static final BlueprintTransform IDENTITY = new BlueprintTransform(0, Mirror.NONE);

    public BlueprintTransform {
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
            throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees");
        Objects.requireNonNull(mirror);
    }

    public static BlueprintTransform parse(String rotation, String mirror) {
        try { return new BlueprintTransform(Integer.parseInt(rotation), Mirror.valueOf(mirror.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Use rotation 0/90/180/270 and mirror none/x/z"); }
    }

    public Position apply(int x, int y, int z) {
        int mx = mirror == Mirror.X ? Math.negateExact(x) : x;
        int mz = mirror == Mirror.Z ? Math.negateExact(z) : z;
        return switch (rotation) {
            case 0 -> new Position(mx, y, mz);
            case 90 -> new Position(Math.negateExact(mz), y, mx);
            case 180 -> new Position(Math.negateExact(mx), y, Math.negateExact(mz));
            case 270 -> new Position(mz, y, Math.negateExact(mx));
            default -> throw new IllegalStateException("Unvalidated rotation");
        };
    }
}
