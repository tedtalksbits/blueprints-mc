package dev.whizzle.blueprints;

import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.*;

import java.util.Set;

/**
 * The survival building palette. A block is supported when one inventory item places it with its exact state,
 * it stores no contents or data (no block entity), and its rotate/mirror behavior is covered by PlanTransformer.
 * Two-block plants and doors cost one item for both halves; a double slab costs two.
 */
public final class MaterialCost {
    private static final Set<String> NAMED = Set.of("STONE", "COBBLESTONE", "STONE_BRICKS", "MOSSY_STONE_BRICKS",
        "CRACKED_STONE_BRICKS", "CHISELED_STONE_BRICKS", "BRICKS", "DIRT", "COARSE_DIRT", "GRASS_BLOCK",
        "DEEPSLATE", "COBBLED_DEEPSLATE", "POLISHED_DEEPSLATE", "DEEPSLATE_BRICKS", "DEEPSLATE_TILES",
        "ANDESITE", "DIORITE", "GRANITE", "POLISHED_ANDESITE", "POLISHED_DIORITE", "POLISHED_GRANITE",
        "GLASS", "TINTED_GLASS", "GLASS_PANE", "SANDSTONE", "SMOOTH_SANDSTONE", "RED_SANDSTONE", "QUARTZ_BLOCK",
        "SMOOTH_QUARTZ", "QUARTZ_PILLAR", "NETHERRACK", "NETHER_BRICKS", "END_STONE", "END_STONE_BRICKS",
        "PRISMARINE", "DARK_PRISMARINE", "PRISMARINE_BRICKS", "TERRACOTTA", "PACKED_MUD", "MUD_BRICKS",
        "ICE", "PACKED_ICE", "BLUE_ICE", "SLIME_BLOCK", "HONEY_BLOCK", "OBSERVER", "PISTON", "STICKY_PISTON",
        "IRON_BARS", "MOSS_CARPET", "PALE_MOSS_CARPET", "GLOWSTONE", "SHROOMLIGHT", "REDSTONE_LAMP",
        "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "BOOKSHELF", "HONEYCOMB_BLOCK", "AMETHYST_BLOCK",
        "LADDER", "END_ROD", "LEVER", "FLOWER_POT", "DIRT_PATH", "COBWEB", "LILY_PAD", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL",
        "GRINDSTONE", "CAULDRON", "COMPOSTER", "HANGING_ROOTS", "CACTUS", "AZALEA", "FLOWERING_AZALEA", "STONECUTTER",
        "REDSTONE_BLOCK", "TNT");
    private static final Set<String> PLANTS = Set.of("DEAD_BUSH", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN",
        "CRIMSON_FUNGUS", "WARPED_FUNGUS", "CRIMSON_ROOTS", "WARPED_ROOTS", "NETHER_SPROUTS", "BUSH", "FIREFLY_BUSH",
        "SHORT_DRY_GRASS", "TALL_DRY_GRASS", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "DANDELION", "POPPY",
        "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP", "PINK_TULIP",
        "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE", "TORCHFLOWER", "OPEN_EYEBLOSSOM",
        "CLOSED_EYEBLOSSOM", "PITCHER_PLANT", "BROWN_MUSHROOM", "RED_MUSHROOM", "GOLDEN_DANDELION");
    /** Unobtainable, technical, or unsafe blocks, refused even if they look like plain cubes. */
    private static final Set<String> DENY = Set.of("BEDROCK", "BARRIER", "LIGHT", "STRUCTURE_VOID", "END_PORTAL_FRAME",
        "REINFORCED_DEEPSLATE", "PETRIFIED_OAK_SLAB", "FROSTED_ICE", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY",
        "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
        "JIGSAW", "STRUCTURE_BLOCK", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "BUDDING_AMETHYST");

    /**
     * Blocks that hold items or data but are common building pieces. They are always placed EMPTY in their default
     * state: contents, cooking slots, and sign text are never read from a capture or schematic, so nothing can be
     * duplicated. Add more here as players ask (and review their transform behavior in PlanTransformer).
     */
    private static boolean placedEmpty(String name) {
        return name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE") || name.equals("BARREL") || name.endsWith("_SIGN");
    }

    private MaterialCost() {}

    public static int amount(BlockData data) {
        String reason = unsupportedReason(data);
        if (reason != null) throw new IllegalArgumentException("Unsupported block (" + reason + "): " + data.getAsString());
        return data instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE ? 2 : 1;
    }

    /** The inventory item that places this block (a wall torch is paid with a torch). */
    public static Material item(BlockData data) {
        Material placement = null;
        try { placement = data.getPlacementMaterial(); } catch (RuntimeException unavailable) { /* test doubles */ }
        if (placement != null && !placement.isAir()) return placement;
        Material material = data.getMaterial();
        // Wall-mounted variants share the floor item: WALL_TORCH -> TORCH, SOUL_WALL_TORCH -> SOUL_TORCH.
        if (!material.isItem() && material.name().contains("WALL_")) {
            Material floor = Material.getMaterial(material.name().replaceFirst("WALL_", ""));
            if (floor != null && floor.isItem()) return floor;
        }
        return material;
    }

    /** Null when the exact state is supported; otherwise a short reason for players. */
    public static String unsupportedReason(BlockData data) {
        Material material = data.getMaterial();
        String name = material.name();
        if (DENY.contains(name) || name.startsWith("INFESTED_")) return "not obtainable or unsafe";
        if (!item(data).isItem()) return "has no item";
        boolean empty = placedEmpty(name);
        if (!empty && hasBlockEntity(data)) return "stores contents or data";
        if (data instanceof Waterlogged w && w.isWaterlogged()) return "waterlogged";
        if (data instanceof Powerable p && p.isPowered()) return "powered";
        if (data instanceof Piston piston && piston.isExtended()) return "extended piston";
        if (data instanceof Leaves leaves && !leaves.isPersistent()) return "decaying leaves";
        // Stored value would be free materials: a charged anchor holds glowstone, a full composter yields bone meal.
        if (data instanceof RespawnAnchor anchor && anchor.getCharges() > 0) return "charged";
        if (data instanceof Levelled levelled && material == Material.COMPOSTER && levelled.getLevel() > 0) return "filled composter";
        boolean family = empty || NAMED.contains(name) || PLANTS.contains(name)
            || name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM")
            || name.endsWith("_HYPHAE") || name.endsWith("_WOOL") || name.endsWith("_CONCRETE") || name.endsWith("_CONCRETE_POWDER")
            || name.endsWith("_GLASS") || name.endsWith("_GLASS_PANE") || name.endsWith("_TERRACOTTA") || name.endsWith("_CARPET")
            || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.endsWith("LIGHTNING_ROD")
            || name.endsWith("_GRATE") || name.endsWith("_BULB") || name.endsWith("_BARS") || name.endsWith("_CHAIN") || name.endsWith("_WALL") || name.endsWith("LANTERN") || name.endsWith("_SAPLING") || name.endsWith("_LEAVES") || isTorch(name)
            || data instanceof Stairs || data instanceof Slab || data instanceof TrapDoor || data instanceof Door
            || data instanceof Wall || data instanceof Fence || data instanceof Gate || data instanceof Lantern
            || data instanceof Orientable || plainCube(material);
        return family ? null : "not in the building palette yet";
    }

    /** Fixes states that would otherwise be refused or misbehave: water and power off, leaves made persistent. */
    public static BlockData normalize(BlockData original) {
        BlockData data = original.clone();
        if (data instanceof Waterlogged w && w.isWaterlogged()) w.setWaterlogged(false);
        if (data instanceof Powerable p && p.isPowered()) p.setPowered(false);
        if (data instanceof Leaves leaves && !leaves.isPersistent()) leaves.setPersistent(true);
        if (data instanceof RespawnAnchor anchor) anchor.setCharges(0);
        if (data instanceof Levelled levelled && data.getMaterial() == Material.COMPOSTER) levelled.setLevel(0);
        if (data instanceof Barrel barrel && barrel.isOpen()) barrel.setOpen(false);   // "open" only means someone is looking inside
        return data;
    }

    private static boolean isTorch(String name) {
        return name.equals("TORCH") || name.equals("WALL_TORCH") || (name.endsWith("_TORCH") && !name.startsWith("REDSTONE_"));
    }
    private static boolean plainCube(Material material) {
        try { return material.isSolid() && material.isOccluding(); }
        catch (RuntimeException unavailable) { return false; }
    }
    private static boolean hasBlockEntity(BlockData data) {
        try { return data.createBlockState() instanceof TileState; }
        catch (RuntimeException unavailable) { return false; }
    }
}
