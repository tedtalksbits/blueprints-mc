package dev.whizzle.blueprints;

import java.util.List;

/** One paid placement, potentially spanning several blocks (a door). No Bukkit types. */
public record ConstructionStep(List<BlueprintBlock> blocks, String material, int amount) {
    public ConstructionStep {
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty() || blocks.size() > 2 || material == null || material.isBlank() || amount < 1 || amount > 2)
            throw new IllegalArgumentException("Invalid construction step");
    }
}
