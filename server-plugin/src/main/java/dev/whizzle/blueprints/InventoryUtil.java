package dev.whizzle.blueprints;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static boolean consume(Inventory inventory, Material material, int amount) {
        int found = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) found += stack.getAmount();
        }
        if (found < amount) return false;

        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) inventory.setItem(slot, null);
            remaining -= take;
        }
        return true;
    }
}
