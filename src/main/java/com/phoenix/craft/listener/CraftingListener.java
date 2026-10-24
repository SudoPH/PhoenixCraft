package com.phoenix.craft.listener;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.gui.CraftingGUIHolder;
import com.phoenix.craft.gui.CraftingGUIManager;
import com.phoenix.craft.gui.PlayerGUIState;
import com.phoenix.craft.recipes.RecipeManager;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * All PhoenixCraft GUI + chat interactions are captured here.
 *
 * <p>Faz 0.4.0: Added chat event handler for search input, plus
 * {@code cycle_filter}, {@code cycle_sort}, and {@code search} click actions.</p>
 */
public class CraftingListener implements Listener {

    private final PhoenixCraft plugin;
    private final CraftingGUIManager gui;
    private final PhoenixFishAPI fishApi;
    private final MessageManager messages;
    private final RecipeManager recipeManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    public CraftingListener(PhoenixCraft plugin, CraftingGUIManager gui,
                             PhoenixFishAPI fishApi, MessageManager messages,
                             RecipeManager recipeManager) {
        this.plugin = plugin;
        this.gui = gui;
        this.fishApi = fishApi;
        this.messages = messages;
        this.recipeManager = recipeManager;
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

        // Detect shift-click for bulk crafting
        boolean shiftClick = event.getClick() != null && event.getClick().isShiftClick();

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
                if (value != null) gui.handleCraftClick(player, value, shiftClick, false, false);
            }
            case "craft_and_close" -> {
                if (value != null) gui.handleCraftClick(player, value, shiftClick, true, false);
            }
            case "craft_max" -> {
                if (value != null) gui.handleCraftClick(player, value, false, false, true);
            }
            case "open_stats" -> gui.openStatsMenu(player);
            case "open_achievements" -> gui.openAchievementsMenu(player);
            case "open_recipes" -> gui.openRecipesMenu(player);
            case "blackmarket_purchase" -> {
                // Faz 1.0.0: Black market purchase
                // value format: "recipeId:price"
                if (value != null) {
                    String[] parts = value.split(":", 2);
                    if (parts.length == 2) {
                        try {
                            String recipeId = parts[0];
                            double price = Double.parseDouble(parts[1]);
                            gui.handleBlackMarketPurchase(player, recipeId, price);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            // Faz 0.4.0: search + filter + sort
            case "search" -> {
                // value = category (e.g. "rod" or "bait"), or "all"
                gui.startSearchInput(player, value);
            }
            case "cycle_filter" -> {
                handleCycleFilter(player, value);
            }
            case "cycle_sort" -> {
                handleCycleSort(player, value);
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

    /**
     * Faz 0.4.0: Handle filter mode cycling. The {@code value} parameter
     * carries the category the player is currently viewing (so we can
     * reopen the same category after cycling).
     */
    private void handleCycleFilter(Player player, String category) {
        PlayerGUIState state = gui.getPlayerState();
        // Build available modes from config
        List<PlayerGUIState.FilterMode> availableModes = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("filters.modes")) {
            try {
                availableModes.add(PlayerGUIState.FilterMode.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (availableModes.isEmpty()) {
            availableModes.add(PlayerGUIState.FilterMode.ALL);
        }

        state.cycleFilterMode(player.getUniqueId(), availableModes);

        // Reopen the current category menu (page 0 — list may have changed)
        if (category != null && !"none".equals(category)) {
            gui.openCategoryMenu(player, category, 0);
        }
    }

    /**
     * Faz 0.4.0: Handle sort mode cycling.
     */
    private void handleCycleSort(Player player, String category) {
        PlayerGUIState state = gui.getPlayerState();
        List<PlayerGUIState.SortMode> availableModes = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("sort.modes")) {
            try {
                availableModes.add(PlayerGUIState.SortMode.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (availableModes.isEmpty()) {
            availableModes.add(PlayerGUIState.SortMode.ID);
        }

        state.cycleSortMode(player.getUniqueId(), availableModes);

        if (category != null && !"none".equals(category)) {
            gui.openCategoryMenu(player, category, 0);
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

    /**
     * Faz 0.4.0: Capture chat messages when a player is in search input mode.
     * The first message they type becomes the search query.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!gui.getPlayerState().isPendingSearch(player.getUniqueId())) {
            return;
        }

        // Cancel the chat message — we don't want it to broadcast
        event.setCancelled(true);

        String message = event.getMessage().trim();
        gui.getPlayerState().clearSearchInput(player.getUniqueId());

        // Handle cancel
        if ("cancel".equalsIgnoreCase(message)) {
            // Run sync — close inventory is a sync op
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(messages.getMessage("search-cancelled", true));
                    gui.openMainMenu(player);
                }
            });
            return;
        }

        // Perform the search (sync — opens inventory)
        final String query = message;
        // We need to know the category — store it alongside the pending state.
        // For simplicity, search across ALL categories when triggered via chat.
        // The category-scoped search button stores the category in the value
        // key, but we don't have access to it here. So we pass null (all).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                gui.performSearch(player, query, null);
            }
        });
    }

    /**
     * Faz 0.5.0: Handle right-click of recipe book items.
     * When a player right-clicks while holding a recipe book, teach them
     * the recipe and consume the book.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRecipeBookUse(PlayerInteractEvent event) {
        if (recipeManager == null || !recipeManager.isEnabled()) return;
        if (event.getHand() == null) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        // Check if this is a recipe book via PDC
        String recipeId = recipeManager.getRecipeIdFromBook(item);
        if (recipeId == null) return;

        // Cancel the interact event so blocks aren't activated
        event.setCancelled(true);

        // Handle the book use (teaches recipe + consumes book)
        recipeManager.handleBookUse(player, item);
    }
}
