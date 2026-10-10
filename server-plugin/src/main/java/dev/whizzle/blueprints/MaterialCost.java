package dev.whizzle.blueprints;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public final class MaterialCost {
    private MaterialCost() {}

    public static Material forBlockData(String serialized) {
        BlockData data = Bukkit.createBlockData(serialized);
        Material block = data.getMaterial();
        // V0 supports normal placeable blocks whose item form uses the same Material.
        // Special cases (wall torches, wall signs, crops, fluids, etc.) belong in a dedicated mapper later.
        return block.isItem() ? block : null;
    }
}
