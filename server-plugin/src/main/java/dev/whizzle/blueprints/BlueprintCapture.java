package dev.whizzle.blueprints;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.data.BlockData;
import java.util.*;

/** Creative selections up to 65 blocks per side. Reads loaded chunks, never copies inventories or entities. */
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
        int span = ConstructionPlan.maxSpan();
        if (width > span || height > span || depth > span)
            throw new IllegalArgumentException("Selection is " + width + "x" + height + "x" + depth + "; each side can be at most " + span + " blocks");
        World world = player.getWorld();
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight())
            throw new IllegalArgumentException("Selection exceeds world height");
        var location = player.getLocation();
        double farX = Math.max(Math.abs(location.getX() - minX), Math.abs(location.getX() - maxX));
        double farY = Math.max(Math.abs(location.getY() - minY), Math.abs(location.getY() - maxY));
        double farZ = Math.max(Math.abs(location.getZ() - minZ), Math.abs(location.getZ() - maxZ));
        int reach = 128 + span;
        if (farX * farX + farY * farY + farZ * farZ > (double) reach * reach)
            throw new IllegalArgumentException("Entire selection must be within " + reach + " blocks of you");
        for (int cx = minX >> 4; cx <= (maxX >> 4); cx++) for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++) {
            if (!world.isChunkLoaded(cx, cz)) throw new IllegalArgumentException("Selection includes an unloaded chunk: " + cx + "," + cz);
        }
        List<BlueprintBlock> blocks = new ArrayList<>();
        Map<String, Integer> unsupported = new TreeMap<>();
        String firstUnsupported = null;
        for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) {
            Block block = world.getBlockAt(minX + dx, minY + dy, minZ + dz);
            if (!world.getWorldBorder().isInside(block.getLocation())) throw new IllegalArgumentException("Selection exceeds world border");
            if (block.getType().isAir()) continue;
            BlockData data = MaterialCost.normalize(block.getBlockData());
            if (MaterialCost.unsupportedReason(data) != null) {
                unsupported.merge(block.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
                if (firstUnsupported == null) firstUnsupported = block.getX() + " " + block.getY() + " " + block.getZ();
                continue;
            }
            if (blocks.size() <= ConstructionPlan.maxBlocks()) blocks.add(new BlueprintBlock(dx, dy, dz, data.getAsString()));
        }
        if (!unsupported.isEmpty()) {
            StringJoiner list = new StringJoiner(", ");
            unsupported.forEach((name, count) -> list.add(count + " " + name));
            throw new IllegalArgumentException("Blueprints can't build these yet: " + list + " (first at " + firstUnsupported
                + "). Remove them from the selection and place them by hand after the build.");
        }
        if (blocks.size() > ConstructionPlan.maxBlocks())
            throw new IllegalArgumentException("Selection has more than " + ConstructionPlan.maxBlocks() + " blocks; the current limit is " + ConstructionPlan.maxBlocks());
        return PlanCompiler.compile(new BlueprintDefinition(id, id, blocks));
    }
}
