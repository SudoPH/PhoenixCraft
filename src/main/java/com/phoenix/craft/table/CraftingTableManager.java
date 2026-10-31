package com.phoenix.craft.table;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Custom crafting-table GUI (F2). A 3×3 grid (slots 11–13, 20–22, 29–31)
 * where the player physically places ingredients. The "Craft" button
 * (slot 23) matches the placed pattern against PhoenixFish's recipe pool
 * and produces the result if a match is found.
 *
 * <p>Pattern matching: for each {@link CraftRecipe}, check that the 9 grid
 * slots contain at least the recipe's ingredients (order-independent for
 * now; a future enhancement could support shaped recipes). On success,
 * consume the ingredients and grant the result item.</p>
 *
 * @since 1.0.7 (feature #2)
 */
public class CraftingTableManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final org.bukkit.NamespacedKey actionKey;
    private final org.bukkit.NamespacedKey valueKey;

    /** Slot indices of the 3×3 grid in the 54-slot inventory. */
    private static final int[] GRID_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int CRAFT_BUTTON_SLOT = 23;
    private static final int RESULT_SLOT = 25;
    private static final int CLOSE_SLOT = 49;

    public CraftingTableManager(PhoenixCraft plugin,
                                 Supplier<PhoenixFishAPI> fishApiSupplier,
                                 com.phoenix.craft.gui.CraftingGUIManager guiManager) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        this.actionKey = guiManager.getActionKey();
        this.valueKey = guiManager.getValueKey();
    }

    public void openTable(Player player) {
        Inventory inv = Bukkit.createInventory(
                new com.phoenix.craft.gui.CraftingGUIHolder(
                        com.phoenix.craft.gui.CraftingGUIHolder.ScreenType.DETAIL,
                        "table", 0, "table"),
                54,
                plugin.getMessageManager().getMessage("crafting-table-title"));

        // Fill borders with gray panes.
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) { pm.displayName(Component.empty()); pane.setItemMeta(pm); }
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) inv.setItem(i, pane);
        }

        // Craft button (slot 23)
        ItemStack craftBtn = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta cm = craftBtn.getItemMeta();
        if (cm != null) {
            cm.displayName(plugin.getMessageManager().getMessage("crafting-table-craft-name"));
            cm.lore(List.of(plugin.getMessageManager().getMessage("crafting-table-craft-lore")));
            cm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "table_craft");
            cm.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            craftBtn.setItemMeta(cm);
        }
        inv.setItem(CRAFT_BUTTON_SLOT, craftBtn);

        // Result slot placeholder (slot 25)
        ItemStack result = new ItemStack(Material.BARRIER);
        ItemMeta rm = result.getItemMeta();
        if (rm != null) {
            rm.displayName(plugin.getMessageManager().getMessage("crafting-table-result-name"));
            rm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            rm.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            result.setItemMeta(rm);
        }
        inv.setItem(RESULT_SLOT, result);

        // Close button (slot 49)
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta clm = close.getItemMeta();
        if (clm != null) {
            clm.displayName(plugin.getMessageManager().getMessage("crafting-table-close-name"));
            clm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
            clm.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            close.setItemMeta(clm);
        }
        inv.setItem(CLOSE_SLOT, close);

        player.openInventory(inv);
    }

    /**
     * Attempt to match the current grid contents against the recipe pool.
     * Called when the player clicks the "Craft" button.
     *
     * @return the matched recipe, or null if no match
     */
    public CraftRecipe matchGrid(Player player, Inventory inv) {
        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) return null;
        List<CraftRecipe> all;
        try { all = api.getCraftingRecipes(); }
        catch (Throwable t) { return null; }
        if (all == null) return null;

        // Collect grid contents (materials + amounts).
        Map<Material, Integer> gridMats = new HashMap<>();
        for (int slot : GRID_SLOTS) {
            ItemStack is = inv.getItem(slot);
            if (is == null || is.getType().isAir()) continue;
            gridMats.merge(is.getType(), is.getAmount(), Integer::sum);
        }
        if (gridMats.isEmpty()) return null;

        // Find a recipe whose ingredients are all present in the grid with
        // sufficient amounts. (Order-independent matching.)
        for (CraftRecipe r : all) {
            if (r.ingredients() == null || r.ingredients().isEmpty()) continue;
            boolean matched = true;
            for (CraftRecipe.Ingredient ing : r.ingredients()) {
                // v1.1.2 — NPE guard: null ingredient entry or null item.
                if (ing == null || ing.item() == null) { matched = false; break; }
                int have = gridMats.getOrDefault(ing.item().getType(), 0);
                if (have < ing.amount()) { matched = false; break; }
            }
            if (matched) return r;
        }
        return null;
    }

    /** Consume the recipe's ingredients from the grid. */
    public void consumeFromGrid(Inventory inv, CraftRecipe recipe) {
        // v1.1.2 — NPE guard: null ingredients list / null ingredient / null item.
        java.util.List<CraftRecipe.Ingredient> ings = recipe.ingredients();
        if (ings == null) return;
        for (CraftRecipe.Ingredient ing : ings) {
            if (ing == null || ing.item() == null) continue;
            int remaining = ing.amount();
            for (int slot : GRID_SLOTS) {
                if (remaining <= 0) break;
                ItemStack is = inv.getItem(slot);
                if (is == null || is.getType() != ing.item().getType()) continue;
                int take = Math.min(is.getAmount(), remaining);
                if (take >= is.getAmount()) {
                    inv.setItem(slot, null);
                } else {
                    is.setAmount(is.getAmount() - take);
                    inv.setItem(slot, is);
                }
                remaining -= take;
            }
        }
    }

    public int[] getGridSlots() { return GRID_SLOTS; }
    public int getResultSlot() { return RESULT_SLOT; }
}
