package dev.whizzle.blueprints;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import java.util.ArrayList;
import java.util.List;

/** Geometric transforms for the validated building palette, including handed stair shapes and door hinges. */
public final class PlanTransformer {
    private PlanTransformer() {}

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
