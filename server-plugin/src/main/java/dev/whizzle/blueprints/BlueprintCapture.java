package dev.whizzle.blueprints;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

/** Small Creative selections only. Reads loaded chunks, never copies inventories or entities. */
public final class BlueprintCapture {
    private BlueprintCapture() {}

    public static ConstructionPlan capture(Player player, String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!player.hasPermission("blueprints.capture") || player.getGameMode() != GameMode.CREATIVE)
            throw new IllegalArgumentException("Capture requires Creative mode and blueprints.capture permission");
        BlueprintRepository.validateId(id);
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        long width = (long) maxX - minX + 1, height = (long) maxY - minY + 1, depth = (long) maxZ - minZ + 1;
        // Check individual spans first, before multiplying, to prevent volume overflow.
        if (width > 65 || height > 65 || depth > 65 || width * height * depth > 4096)
            throw new IllegalArgumentException("Selection must be at most 4096 cells, with no side longer than 65 blocks");
        World world = player.getWorld();
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight())
            throw new IllegalArgumentException("Selection exceeds world height");
        var location = player.getLocation();
        double farX = Math.max(Math.abs(location.getX() - minX), Math.abs(location.getX() - maxX));
        double farY = Math.max(Math.abs(location.getY() - minY), Math.abs(location.getY() - maxY));
        double farZ = Math.max(Math.abs(location.getZ() - minZ), Math.abs(location.getZ() - maxZ));
        if (farX * farX + farY * farY + farZ * farZ > 128 * 128)
            throw new IllegalArgumentException("Entire selection must be within 128 blocks of you");
        for (int cx = minX >> 4; cx <= (maxX >> 4); cx++) for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++) {
            if (!world.isChunkLoaded(cx, cz)) throw new IllegalArgumentException("Selection includes an unloaded chunk: " + cx + "," + cz);
        }
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) {
            Block block = world.getBlockAt(minX + dx, minY + dy, minZ + dz);
            if (!world.getWorldBorder().isInside(block.getLocation())) throw new IllegalArgumentException("Selection exceeds world border");
            if (block.getType().isAir()) continue;
            try { MaterialCost.amount(block.getBlockData()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException(e.getMessage() + " at " + block.getX() + "," + block.getY() + "," + block.getZ()); }
            blocks.add(new BlueprintBlock(dx, dy, dz, block.getBlockData().getAsString()));
        }
        return PlanCompiler.compile(new BlueprintDefinition(id, id, blocks));
    }
}
