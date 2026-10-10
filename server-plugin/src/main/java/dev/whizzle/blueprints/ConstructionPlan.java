package dev.whizzle.blueprints;

import java.util.*;

/** An immutable, server-validated snapshot. Source file edits cannot change an active job. */
public record ConstructionPlan(String id, String name, List<ConstructionStep> steps) {
    public ConstructionPlan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        steps = List.copyOf(steps);
        if (steps.isEmpty() || steps.size() > 4096) throw new IllegalArgumentException("Plan must contain 1..4096 steps");
        Set<String> positions = new HashSet<>();
        for (ConstructionStep step : steps) for (BlueprintBlock block : step.blocks()) {
            if (Math.abs((long) block.x()) > 64 || Math.abs((long) block.y()) > 64 || Math.abs((long) block.z()) > 64)
                throw new IllegalArgumentException("Relative coordinates must be within -64..64");
            if (!positions.add(block.x() + "," + block.y() + "," + block.z()))
                throw new IllegalArgumentException("Duplicate blueprint coordinate");
        }
    }

    public int blockCount() { return steps.stream().mapToInt(s -> s.blocks().size()).sum(); }

    public Map<String, Integer> materials() {
        Map<String, Integer> totals = new TreeMap<>();
        for (ConstructionStep step : steps) totals.merge(step.material(), step.amount(), Integer::sum);
        return Collections.unmodifiableMap(totals);
    }
}
