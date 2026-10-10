package dev.whizzle.blueprints;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.*;
import java.util.Set;

/** Deliberately small survival-safe palette. Extend only with accounting and placement tests. */
public final class MaterialCost {
    private static final Set<String> SOLIDS = Set.of("STONE", "COBBLESTONE", "STONE_BRICKS", "MOSSY_STONE_BRICKS",
        "CRACKED_STONE_BRICKS", "CHISELED_STONE_BRICKS", "BRICKS", "DIRT", "COARSE_DIRT", "GRASS_BLOCK",
        "DEEPSLATE", "COBBLED_DEEPSLATE", "POLISHED_DEEPSLATE", "DEEPSLATE_BRICKS", "DEEPSLATE_TILES",
        "ANDESITE", "DIORITE", "GRANITE", "POLISHED_ANDESITE", "POLISHED_DIORITE", "POLISHED_GRANITE",
        "GLASS", "TINTED_GLASS", "SANDSTONE", "SMOOTH_SANDSTONE", "RED_SANDSTONE", "QUARTZ_BLOCK",
        "SMOOTH_QUARTZ", "QUARTZ_PILLAR", "NETHERRACK", "NETHER_BRICKS", "END_STONE", "END_STONE_BRICKS",
        "PRISMARINE", "DARK_PRISMARINE", "PRISMARINE_BRICKS", "TERRACOTTA", "PACKED_MUD", "MUD_BRICKS");
    private MaterialCost() {}
    public static int amount(BlockData data) {
        Material material = data.getMaterial();
        String name = material.name();
        boolean safe = SOLIDS.contains(name) || name.endsWith("_PLANKS") || name.endsWith("_LOG")
            || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
            || name.endsWith("_WOOL") || name.endsWith("_CONCRETE") || name.endsWith("_STAINED_GLASS")
            || name.endsWith("_TERRACOTTA") || data instanceof Stairs || data instanceof Slab
            || data instanceof TrapDoor || data instanceof Door;
        // Mushroom stems are safe building blocks; all other special/stateful blocks require explicit support.
        if (!safe || !material.isItem() || (data instanceof Waterlogged w && w.isWaterlogged())
            || (data instanceof Powerable p && p.isPowered()))
            throw new IllegalArgumentException("Unsupported V1 block/state: " + data.getAsString());
        return data instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE ? 2 : 1;
    }
}
