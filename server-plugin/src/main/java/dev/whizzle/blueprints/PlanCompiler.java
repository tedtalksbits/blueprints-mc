package dev.whizzle.blueprints;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import java.util.*;

/** Minecraft-version-specific normalization lives here, outside the persisted model. */
public final class PlanCompiler {
    private PlanCompiler() {}
    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }
    public static ConstructionPlan compile(BlueprintDefinition definition) {
        if (definition.blocks().isEmpty() || definition.blocks().size() > 4096)
            throw new IllegalArgumentException("Blueprint must contain 1..4096 blocks");
        Map<String, BlueprintBlock> blocks = new HashMap<>();
        Map<String, BlockData> states = new HashMap<>();
        for (BlueprintBlock spec : definition.blocks()) {
            BlockData data = Bukkit.createBlockData(spec.blockData());
            MaterialCost.amount(data);
            String key = key(spec.x(), spec.y(), spec.z());
            if (blocks.put(key, new BlueprintBlock(spec.x(), spec.y(), spec.z(), data.getAsString())) != null)
                throw new IllegalArgumentException("Duplicate coordinate: " + key);
            states.put(key, data);
        }
        List<BlueprintBlock> ordered = blocks.values().stream().sorted(Comparator.comparingInt(BlueprintBlock::y)
            .thenComparingInt(BlueprintBlock::x).thenComparingInt(BlueprintBlock::z)).toList();
        Set<String> handled = new HashSet<>();
        List<ConstructionStep> steps = new ArrayList<>();
        for (BlueprintBlock spec : ordered) {
            String key = key(spec.x(), spec.y(), spec.z());
            if (!handled.add(key)) continue;
            BlockData data = states.get(key);
            List<BlueprintBlock> group = List.of(spec);
            if (data instanceof Door door) {
                if (door.getHalf() != Bisected.Half.BOTTOM) throw new IllegalArgumentException("Orphan upper door at " + key);
                String upperKey = key(spec.x(), Math.addExact(spec.y(), 1), spec.z());
                BlockData upper = states.get(upperKey);
                Door expectedUpper = (Door) door.clone();
                expectedUpper.setHalf(Bisected.Half.TOP);
                if (upper == null || !upper.getAsString().equals(expectedUpper.getAsString()))
                    throw new IllegalArgumentException("Door requires matching upper half at " + upperKey);
                handled.add(upperKey);
                group = List.of(spec, blocks.get(upperKey));
            }
            steps.add(new ConstructionStep(group, data.getMaterial().name(), MaterialCost.amount(data)));
        }
        return new ConstructionPlan(definition.id(), definition.name(), steps);
    }
}
