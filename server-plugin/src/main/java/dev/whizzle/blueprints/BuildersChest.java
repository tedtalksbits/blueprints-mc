package dev.whizzle.blueprints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * A crafted, owner-bound funding chest. A build may only draw from its starter's own Builder's Chest,
 * so build permission no longer grants use of other players' chests. One chest funds one active job.
 */
public final class BuildersChest implements Listener {
    public static final NamespacedKey ITEM_KEY = new NamespacedKey("blueprints", "builders_chest");
    public static final NamespacedKey OWNER_KEY = new NamespacedKey("blueprints", "builders_chest_owner");
    public static final NamespacedKey RECIPE_KEY = new NamespacedKey("blueprints", "builders_chest");
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final JavaPlugin plugin;

    public BuildersChest(JavaPlugin plugin) { this.plugin = plugin; }

    public static ItemStack item() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Builder's Chest", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Funds one blueprint build at a time.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
    public static boolean isItem(ItemStack item) {
        return item != null && item.getType() == Material.CHEST && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(ITEM_KEY, PersistentDataType.BYTE);
    }
    public static Optional<UUID> owner(Chest chest) {
        String raw = chest.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(raw)); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
    public static boolean isOwnedBy(Chest chest, Player player) { return owner(chest).filter(player.getUniqueId()::equals).isPresent(); }
    public static boolean isBuildersChest(Block block) {
        return block.getType() == Material.CHEST && block.getState() instanceof Chest chest && owner(chest).isPresent();
    }
    /** Binds a placed chest to its owner. */
    public static void claim(Chest chest, Player owner) {
        chest.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        chest.update();
    }

    /** Turns a Builder's Chest back into a normal chest; its contents are untouched. */
    public static void release(Chest chest) {
        chest.getPersistentDataContainer().remove(OWNER_KEY);
        chest.update();
    }

    /** The player's closest Builder's Chest within radius that passes {@code available}; only loaded chunks are inspected. */
    public static Optional<Chest> nearestOwned(Player player, int radius, Predicate<Chest> available) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int px = at.getBlockX(), py = at.getBlockY(), pz = at.getBlockZ();
        int minY = Math.max(world.getMinHeight(), py - radius), maxY = Math.min(world.getMaxHeight() - 1, py + radius);
        double limit = (double) radius * radius, bestDistance = Double.MAX_VALUE;
        Chest best = null;
        for (int cx = (px - radius) >> 4; cx <= (px + radius) >> 4; cx++) {
            for (int cz = (pz - radius) >> 4; cz <= (pz + radius) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (int x = Math.max(px - radius, cx << 4); x <= Math.min(px + radius, (cx << 4) + 15); x++)
                    for (int z = Math.max(pz - radius, cz << 4); z <= Math.min(pz + radius, (cz << 4) + 15); z++)
                        for (int y = minY; y <= maxY; y++) {
                            Block block = world.getBlockAt(x, y, z);
                            if (block.getType() != Material.CHEST) continue;
                            double dx = x + 0.5 - at.getX(), dy = y + 0.5 - at.getY(), dz = z + 0.5 - at.getZ();
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (distance > limit || distance >= bestDistance) continue;
                            if (!(block.getState() instanceof Chest chest) || !isOwnedBy(chest, player) || !available.test(chest)) continue;
                            best = chest; bestDistance = distance;
                        }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Registers the configurable shaped recipe. A bad recipe is logged and skipped; operators can still use givechest. */
    public void registerRecipe() {
        Bukkit.removeRecipe(RECIPE_KEY);
        try {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("builders-chest.recipe");
            if (section == null) throw new IllegalArgumentException("builders-chest.recipe is missing");
            List<String> shape = section.getStringList("shape");
            ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
            if (shape.isEmpty() || shape.size() > 3 || ingredients == null)
                throw new IllegalArgumentException("recipe needs 1-3 shape rows and an ingredients map");
            ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, item());
            recipe.shape(shape.toArray(String[]::new));
            for (String symbol : ingredients.getKeys(false)) {
                if (symbol.length() != 1) throw new IllegalArgumentException("ingredient keys must be single characters: " + symbol);
                String id = Objects.requireNonNullElse(ingredients.getString(symbol), "");
                Material material = Material.matchMaterial(id);
                if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("unknown item: " + id);
                recipe.setIngredient(symbol.charAt(0), material);
            }
            if (!Bukkit.addRecipe(recipe)) throw new IllegalArgumentException("server rejected the recipe");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Builder's Chest recipe not registered (" + e.getMessage()
                + "). Fix builders-chest.recipe in config.yml; operators can use /blueprint givechest meanwhile.");
        }
    }
    public void unregisterRecipe() { Bukkit.removeRecipe(RECIPE_KEY); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(RECIPE_KEY); }

    // /minecraft:reload rebuilds the recipe manager and can drop plugin recipes; put ours back.
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        if (Bukkit.getRecipe(RECIPE_KEY) != null) return;
        registerRecipe();
        for (Player player : Bukkit.getOnlinePlayers()) player.discoverRecipe(RECIPE_KEY);
    }

    // A double chest cannot fund a build, so refuse merges involving a Builder's Chest. Sneak-placing avoids merging.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlaceMerge(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CHEST || !(block.getBlockData() instanceof org.bukkit.block.data.type.Chest data)
            || data.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE) return;
        boolean involved = isItem(event.getItemInHand());
        for (BlockFace face : SIDES) involved |= isBuildersChest(block.getRelative(face));
        if (!involved) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("A Builder's Chest can't join another chest. Leave a gap or sneak while placing.", NamedTextColor.RED));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        if (isItem(event.getItemInHand()) && event.getBlockPlaced().getState() instanceof Chest chest) claim(chest, event.getPlayer());
    }
    // Return the Builder's Chest item instead of a plain chest, and always hand back the stored materials.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isBuildersChest(block)) return;
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        if (block.getState() instanceof Chest chest) {
            var inventory = chest.getBlockInventory();
            for (ItemStack stack : inventory.getContents())
                if (stack != null && !stack.getType().isAir()) block.getWorld().dropItemNaturally(at, stack.clone());
            inventory.clear();
        }
        if (!event.isDropItems()) return;
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) block.getWorld().dropItemNaturally(at, item());
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().removeIf(BuildersChest::isBuildersChest); }
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().removeIf(BuildersChest::isBuildersChest); }
}
