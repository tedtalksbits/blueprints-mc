package dev.whizzle.blueprints;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import java.util.ArrayList;
import java.util.List;

/** Geometric transforms for the validated building palette, including handed stair shapes and door hinges. */
public final class PlanTransformer {
    private PlanTransformer() {}

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final BlockFace[] AXES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    private static BlockFace turn(BlockFace face, BlueprintTransform transform) {
        BlueprintTransform.Position d = transform.apply(face.getModX(), face.getModY(), face.getModZ());
        for (BlockFace candidate : AXES)
            if (candidate.getModX() == d.x() && candidate.getModY() == d.y() && candidate.getModZ() == d.z()) return candidate;
        throw new IllegalArgumentException("Unsupported transformed face: " + face);
    }

    public static ConstructionPlan apply(ConstructionPlan plan, BlueprintTransform transform) {
        List<BlueprintBlock> transformed = new ArrayList<>();
        for (ConstructionStep step : plan.steps()) for (BlueprintBlock block : step.blocks()) {
            BlueprintTransform.Position at = transform.apply(block.x(), block.y(), block.z());
            BlockData data = Bukkit.createBlockData(block.blockData());
            MaterialCost.amount(data); // New block families must have their transform semantics reviewed here, too.
            if (data instanceof Directional directional) {
                BlockFace facing = directional.getFacing();
                BlueprintTransform.Position direction = transform.apply(facing.getModX(), facing.getModY(), facing.getModZ());
                BlockFace result = directional.getFaces().stream().filter(f -> f.getModX() == direction.x()
                    && f.getModY() == direction.y() && f.getModZ() == direction.z()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported transformed facing: " + data.getAsString()));
                directional.setFacing(result);
            }
            // Fences, panes, bars, mushroom blocks: move each connected side.
            if (data instanceof MultipleFacing multi) {
                Set<BlockFace> connected = new HashSet<>(multi.getFaces());
                for (BlockFace face : multi.getAllowedFaces()) multi.setFace(face, false);
                for (BlockFace face : connected) multi.setFace(turn(face, transform), true);
            }
            // Walls: move each side's height (none/low/tall).
            if (data instanceof Wall wall) {
                Map<BlockFace, Wall.Height> heights = new EnumMap<>(BlockFace.class);
                for (BlockFace face : SIDES) heights.put(face, wall.getHeight(face));
                for (BlockFace face : SIDES) wall.setHeight(turn(face, transform), heights.get(face));
            }
            if (data instanceof Orientable orientable && transform.rotation() % 180 != 0) {
                orientable.setAxis(switch (orientable.getAxis()) { case X -> Axis.Z; case Z -> Axis.X; case Y -> Axis.Y; });
            }
            if (transform.mirror() != BlueprintTransform.Mirror.NONE) {
                if (data instanceof Door door) door.setHinge(door.getHinge() == Door.Hinge.LEFT ? Door.Hinge.RIGHT : Door.Hinge.LEFT);
                if (data instanceof Stairs stairs) stairs.setShape(switch (stairs.getShape()) {
                    case INNER_LEFT -> Stairs.Shape.INNER_RIGHT;
                    case INNER_RIGHT -> Stairs.Shape.INNER_LEFT;
                    case OUTER_LEFT -> Stairs.Shape.OUTER_RIGHT;
                    case OUTER_RIGHT -> Stairs.Shape.OUTER_LEFT;
                    case STRAIGHT -> Stairs.Shape.STRAIGHT;
                });
            }
            transformed.add(new BlueprintBlock(at.x(), at.y(), at.z(), data.getAsString()));
        }
        // Reorder and revalidate whole steps, so transformed doors still cost exactly one item.
        return PlanCompiler.compile(new BlueprintDefinition(plan.id(), plan.name(), transformed));
    }
}
