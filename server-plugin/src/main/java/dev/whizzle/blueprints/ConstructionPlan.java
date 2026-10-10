package dev.whizzle.blueprints;

import java.util.*;

/** An immutable, server-validated snapshot. Source file edits cannot change an active job. */
public record ConstructionPlan(String id, String name, List<ConstructionStep> steps) {
    public static final int DEFAULT_MAX_BLOCKS = 4096, DEFAULT_MAX_SPAN = 65, CEILING_BLOCKS = 32768, CEILING_SPAN = 128;
    private static volatile int maxBlocks = DEFAULT_MAX_BLOCKS, maxSpan = DEFAULT_MAX_SPAN;

    /** Server-wide size limits from config.yml (limits.*), clamped to what the engine is built for. */
    public static void configureLimits(int blocks, int span) {
        maxBlocks = Math.clamp(blocks, 1, CEILING_BLOCKS);
        maxSpan = Math.clamp(span, 1, CEILING_SPAN);
    }
    public static int maxBlocks() { return maxBlocks; }
    /** Longest side in blocks; transformed offsets may range over -(maxSpan-1)..(maxSpan-1). */
    public static int maxSpan() { return maxSpan; }
    public ConstructionPlan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        steps = List.copyOf(steps);
        if (steps.isEmpty() || steps.size() > maxBlocks) throw new IllegalArgumentException("Plan must contain 1.." + maxBlocks + " steps");
        Set<String> positions = new HashSet<>();
        for (ConstructionStep step : steps) for (BlueprintBlock block : step.blocks()) {
            int reach = maxSpan - 1;
            if (Math.abs((long) block.x()) > reach || Math.abs((long) block.y()) > reach || Math.abs((long) block.z()) > reach)
                throw new IllegalArgumentException("Relative coordinates must be within -" + reach + ".." + reach + " (limits.max-span " + maxSpan + ")");
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
