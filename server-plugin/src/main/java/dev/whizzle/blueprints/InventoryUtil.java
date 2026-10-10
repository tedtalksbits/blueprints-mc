package dev.whizzle.blueprints;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {}
    private static boolean eligible(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && stack.isSimilar(new ItemStack(material));
    }
    /** Plain (unrenamed, uncustomized) items of this type that a build could consume. */
    public static int count(Inventory inventory, Material material) {
        int found = 0;
        for (ItemStack stack : inventory.getStorageContents()) if (eligible(stack, material)) found += stack.getAmount();
        return found;
    }
    /** Count first; insufficient materials never partially debit the inventory. Named/custom items are excluded. */
    public static boolean consume(Inventory inventory, Material material, int amount) {
        if (amount < 1) throw new IllegalArgumentException("Positive material cost required");
        int found = 0;
        for (ItemStack stack : inventory.getStorageContents()) if (eligible(stack, material)) found += stack.getAmount();
        if (found < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getStorageContents().length && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!eligible(stack, material)) continue;
            int take = Math.min(remaining, stack.getAmount());
            if (take == stack.getAmount()) inventory.setItem(slot, null);
            else {
                ItemStack replacement = stack.clone();
                replacement.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, replacement);
            }
            remaining -= take;
        }
        return true;
    }
}
