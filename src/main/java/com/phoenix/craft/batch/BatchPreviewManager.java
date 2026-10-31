package com.phoenix.craft.batch;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Smart batch preview (F9). Before executing a bulk craft, shows a 27-slot
 * confirmation GUI summarizing: items to consume, items to produce, XP to
 * gain, fee to charge, and the player's current balance. The player clicks
 * a confirm button to proceed or cancel to back out.
 *
 * @since 1.0.7 (feature #9)
 */
public class BatchPreviewManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    public BatchPreviewManager(PhoenixCraft plugin,
                                Supplier<PhoenixFishAPI> fishApiSupplier,
                                com.phoenix.craft.gui.CraftingGUIManager guiManager) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        this.actionKey = guiManager.getActionKey();
        this.valueKey = guiManager.getValueKey();
    }

    /**
     * Open the batch preview GUI for a given recipe + count.
     */
    public void openPreview(Player player, String recipeId, int count) {
        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("craft-no-data", true));
            return;
        }
        CraftRecipe recipe = api.getCraftingRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("craft-not-found", true,
                    Map.of("%recipe_id%", recipeId)));
            return;
        }

        int playerLevel = api.getCraftingLevel(player.getUniqueId());
        int maxCraftable = api.maxCraftable(player, recipe);
        int actualCount = Math.min(count, maxCraftable);
        if (actualCount <= 0) {
            player.sendMessage(plugin.getMessageManager().getMessage("craft-fail-materials", true));
            return;
        }

        Inventory inv = Bukkit.createInventory(
                new com.phoenix.craft.gui.CraftingGUIHolder(
                        com.phoenix.craft.gui.CraftingGUIHolder.ScreenType.DETAIL,
                        recipe.category(), 0, recipe.id()),
                27,
                plugin.getMessageManager().getMessage("batch-preview-title"));

        // Slot 11: summary book
        ItemStack summary = new ItemStack(Material.BOOK);
        ItemMeta sm = summary.getItemMeta();
        if (sm != null) {
            sm.displayName(plugin.getMessageManager().getMessage("batch-preview-summary-name"));
            List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.empty());

            Map<String, String> ph = new HashMap<>();
            ph.put("%count%", String.valueOf(actualCount));
            lore.add(plugin.getMessageManager().getMessage("batch-preview-count", ph));

            ph.clear();
            ph.put("%item%", recipe.id());
            lore.add(plugin.getMessageManager().getMessage("batch-preview-item", ph));

            ph.clear();
            ph.put("%xp%", String.valueOf(recipe.xpReward() * actualCount));
            lore.add(plugin.getMessageManager().getMessage("batch-preview-xp", ph));

            // Materials breakdown
            lore.add(Component.empty());
            // v1.1.2 — NPE hardening: null ingredients list / null ingredient / null item
            java.util.List<CraftRecipe.Ingredient> ings = recipe.ingredients();
            if (ings == null) ings = java.util.List.of();
            for (CraftRecipe.Ingredient ing : ings) {
                if (ing == null) continue;
                int need = ing.amount() * actualCount;
                ph.clear();
                ItemStack ingItem = ing.item();
                String itemName = ingItem == null ? "???"
                        : ingItem.getType().name().replace("_", " ").toLowerCase();
                ph.put("%item%", itemName);
                ph.put("%needed%", String.valueOf(need));
                lore.add(plugin.getMessageManager().getMessage("batch-preview-material-line", ph));
            }

            sm.lore(lore);
            sm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            sm.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            summary.setItemMeta(sm);
        }
        inv.setItem(11, summary);

        // Slot 13: player level + max craftable
        ItemStack lvl = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta lm = lvl.getItemMeta();
        if (lm != null) {
            lm.displayName(plugin.getMessageManager().getMessage("batch-preview-status-name"));
            List<Component> lore = new java.util.ArrayList<>();
            Map<String, String> ph = new HashMap<>();
            ph.put("%current%", String.valueOf(playerLevel));
            ph.put("%required%", String.valueOf(recipe.craftingLevelRequirement()));
            lore.add(plugin.getMessageManager().getMessage("batch-preview-level-line", ph));
            ph.clear();
            ph.put("%max%", String.valueOf(maxCraftable));
            lore.add(plugin.getMessageManager().getMessage("batch-preview-max-line", ph));
            lm.lore(lore);
            lm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            lm.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            lvl.setItemMeta(lm);
        }
        inv.setItem(13, lvl);

        // Slot 15: confirm button
        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cm = confirm.getItemMeta();
        if (cm != null) {
            cm.displayName(plugin.getMessageManager().getMessage("batch-preview-confirm-name"));
            cm.lore(List.of(
                    plugin.getMessageManager().getMessage("batch-preview-confirm-lore-1"),
                    plugin.getMessageManager().getMessage("batch-preview-confirm-lore-2")));
            cm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "batch_confirm");
            cm.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING,
                    recipeId + ":" + actualCount);
            confirm.setItemMeta(cm);
        }
        inv.setItem(15, confirm);

        // Slot 14: cancel
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cam = cancel.getItemMeta();
        if (cam != null) {
            cam.displayName(plugin.getMessageManager().getMessage("batch-preview-cancel-name"));
            cam.lore(List.of(plugin.getMessageManager().getMessage("batch-preview-cancel-lore")));
            cam.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_category");
            cam.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, recipe.category());
            cancel.setItemMeta(cam);
        }
        inv.setItem(14, cancel);

        player.openInventory(inv);
    }
}
