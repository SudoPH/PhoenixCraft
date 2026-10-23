package com.phoenix.craft.listener;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.gui.CraftingGUIHolder;
import com.phoenix.craft.gui.CraftingGUIManager;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tüm PhoenixCraft GUI etkileşimlerini yakalar.
 *
 * <p>Buton PDC key'leri:
 * <ul>
 *   <li>{@code craft_action}: main | category | page | recipe | craft | craft_disabled | close | back_to_category | none</li>
 *   <li>{@code craft_value}: ek değer (kategori, sayfa, recipe id)</li>
 * </ul>
 */
public class CraftingListener implements Listener {

    private final PhoenixCraft plugin;
    private final CraftingGUIManager gui;
    private final PhoenixFishAPI fishApi;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    public CraftingListener(PhoenixCraft plugin, CraftingGUIManager gui, PhoenixFishAPI fishApi) {
        this.plugin = plugin;
        this.gui = gui;
        this.fishApi = fishApi;
        this.actionKey = gui.getActionKey();
        this.valueKey = gui.getValueKey();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null) return;

        if (!(topInv.getHolder() instanceof CraftingGUIHolder holder)) return;

        // Always cancel — these are pure view-only GUIs
        event.setCancelled(true);

        // Ignore clicks outside the top inventory (player inventory clicks)
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(topInv)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        Player player = (Player) event.getWhoClicked();
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);

        switch (action) {
            case "main" -> gui.openMainMenu(player);
            case "category" -> {
                if (value != null) gui.openCategoryMenu(player, value, 0);
            }
            case "page" -> {
                if (value != null) {
                    String[] parts = value.split(":", 2);
                    if (parts.length == 2) {
                        try {
                            int page = Integer.parseInt(parts[1]);
                            gui.openCategoryMenu(player, parts[0], page);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            case "recipe" -> {
                if (value != null) gui.openRecipeDetail(player, value);
            }
            case "craft" -> {
                if (value != null) gui.handleCraftClick(player, value);
            }
            case "craft_disabled" -> {
                player.playSound(player, org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
            case "back_to_category" -> {
                if (value != null) gui.openCategoryMenu(player, value, 0);
            }
            case "close" -> player.closeInventory();
            case "none" -> { /* informational item, do nothing */ }
            default -> { /* unknown action, ignore */ }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null) return;
        if (topInv.getHolder() instanceof CraftingGUIHolder) {
            event.setCancelled(true);
        }
    }
}
