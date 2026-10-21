package dev.whizzle.blueprints;

public record BlueprintBlock(int x, int y, int z, String blockData) {
    public BlueprintBlock {
        if (blockData == null || blockData.isBlank()) throw new IllegalArgumentException("Missing block data");
    }
}
