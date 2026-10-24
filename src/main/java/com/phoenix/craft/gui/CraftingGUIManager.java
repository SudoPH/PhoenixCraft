package com.phoenix.craft.gui;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.achievements.AchievementManager;
import com.phoenix.craft.blackmarket.BlackMarketManager;
import com.phoenix.craft.discovery.DiscoveryManager;
import com.phoenix.craft.economy.EconomyManager;
import com.phoenix.craft.recipes.RecipeManager;
import com.phoenix.craft.stats.StatsManager;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.craft.util.NotificationHelper;
import com.phoenix.fish.api.CraftBulkResult;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.CraftResult;
import com.phoenix.fish.api.PhoenixFishAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

/**
 * Crafting GUI manager for PhoenixCraft.
 *
 * <p>Faz 0.1.0: All player-facing text loaded from {@link MessageManager}
 * (config.yml + messages_*.yml). No hardcoded strings.</p>
 *
 * <p>Faz 0.2.0: Particle effects, tier sounds, and "MAX CRAFT" button added.
 * All configurable via config.yml.</p>
 *
 * <p>Faz 0.3.0: Stats + Achievements integration — every successful craft
 * is recorded, achievements are checked, and two new GUI screens are
 * available (stats menu, achievements menu).</p>
 *
 * <p>Screen types:
 * <ul>
 *   <li><b>MAIN</b> — 27 slots, category selection + stats/achievements</li>
 *   <li><b>CATEGORY</b> — 54 slots, recipe icons + pagination</li>
 *   <li><b>DETAIL</b> — 27 slots, single recipe + 3 craft buttons</li>
 *   <li><b>STATS</b> — 27 slots, player stats + leaderboard</li>
 *   <li><b>ACHIEVEMENTS</b> — 54 slots, achievement list (paginated)</li>
 * </ul>
 */
public class CraftingGUIManager {

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;
    private final MessageManager messages;
    private final MiniMessage miniMessage;
    private final StatsManager statsManager;
    private final AchievementManager achievementManager;
    private final EconomyManager economyManager;
    private final RecipeManager recipeManager;
    private final DiscoveryManager discoveryManager;
    private final BlackMarketManager blackMarketManager;

    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    /** Per-player craft cooldown timestamps (config-driven). */
    private final Map<UUID, Long> craftCooldowns = new ConcurrentHashMap<>();

    /** Per-player last successful craft timestamp. */
    private final Map<UUID, Long> lastCraftTimestamps = new ConcurrentHashMap<>();

    /** Per-player filter/sort/search state (Faz 0.4.0). */
    private final PlayerGUIState playerState = new PlayerGUIState();

    public PlayerGUIState getPlayerState() {
        return playerState;
    }

    public CraftingGUIManager(PhoenixCraft plugin, PhoenixFishAPI fishApi, MessageManager messages,
                               StatsManager statsManager, AchievementManager achievementManager,
                               EconomyManager economyManager, RecipeManager recipeManager,
                               DiscoveryManager discoveryManager, BlackMarketManager blackMarketManager) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        this.messages = messages;
        this.miniMessage = messages.getMiniMessage();
        this.statsManager = statsManager;
        this.achievementManager = achievementManager;
        this.economyManager = economyManager;
        this.recipeManager = recipeManager;
        this.discoveryManager = discoveryManager;
        this.blackMarketManager = blackMarketManager;
        this.actionKey = new NamespacedKey(plugin, "craft_action");
        this.valueKey = new NamespacedKey(plugin, "craft_value");
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getValueKey() {
        return valueKey;
    }

    private long getCraftCooldownMs() {
        return plugin.getConfig().getLong("settings.craft-cooldown-ms", 150L);
    }

    private int getBulkCraftCap() {
        return plugin.getConfig().getInt("settings.bulk-craft-cap", 1000);
    }

    private int getItemsPerPage() {
        return plugin.getConfig().getInt("pagination.items-per-page", 45);
    }

    // =====================================================================
    //  MAIN MENU
    // =====================================================================

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                27,
                messages.getMessage("main-menu-title"));

        String borderMat = plugin.getConfig().getString("gui.main-menu-border", "GRAY_STAINED_GLASS_PANE");
        fillBorders(inv, 27, parseMaterial(borderMat, Material.GRAY_STAINED_GLASS_PANE));

        // Rods button (slot 11)
        String rodsMat = plugin.getConfig().getString("gui.category-icons.rods", "FISHING_ROD");
        inv.setItem(11, createButton(
                parseMaterial(rodsMat, Material.FISHING_ROD),
                "main-menu-rods-name",
                List.of("main-menu-rods-lore-1", "main-menu-rods-lore-2"),
                "category", "rod"));

        // Info button (slot 13)
        Map<String, String> infoPh = new HashMap<>();
        infoPh.put("%xp%", String.valueOf(fishApi.getCraftingXp(player.getUniqueId())));
        infoPh.put("%xp_required%", String.valueOf(fishApi.getCraftingXpRequired(player.getUniqueId())));
        infoPh.put("%level%", String.valueOf(fishApi.getCraftingLevel(player.getUniqueId())));
        inv.setItem(13, createInfoButton(infoPh));

        // Baits button (slot 15)
        String baitsMat = plugin.getConfig().getString("gui.category-icons.baits", "STICK");
        inv.setItem(15, createButton(
                parseMaterial(baitsMat, Material.STICK),
                "main-menu-baits-name",
                List.of("main-menu-baits-lore-1", "main-menu-baits-lore-2"),
                "category", "bait"));

        // Faz 0.3.0: Stats button (slot 4) — only if stats enabled
        if (statsManager != null && statsManager.isEnabled()) {
            String statsMat = plugin.getConfig().getString("gui.buttons.stats", "BOOK");
            inv.setItem(4, createButton(
                    parseMaterial(statsMat, Material.BOOK),
                    "main-menu-stats-name",
                    List.of("main-menu-stats-lore-1", "main-menu-stats-lore-2"),
                    "open_stats", "none"));
        }

        // Faz 0.5.0: Recipes button (slot 3) — only if recipe-sharing enabled
        if (recipeManager != null && recipeManager.isEnabled()) {
            String recipesMat = plugin.getConfig().getString("gui.buttons.recipes", "WRITTEN_BOOK");
            inv.setItem(3, createButton(
                    parseMaterial(recipesMat, Material.WRITTEN_BOOK),
                    "main-menu-recipes-name",
                    List.of("main-menu-recipes-lore-1", "main-menu-recipes-lore-2"),
                    "open_recipes", "none"));
        }

        // Faz 0.3.0: Achievements button (slot 22) — only if achievements enabled
        if (achievementManager != null && achievementManager.isEnabled()) {
            String achMat = plugin.getConfig().getString("gui.buttons.achievements", "GOLDEN_APPLE");
            inv.setItem(22, createButton(
                    parseMaterial(achMat, Material.GOLDEN_APPLE),
                    "main-menu-achievements-name",
                    List.of("main-menu-achievements-lore-1", "main-menu-achievements-lore-2"),
                    "open_achievements", "none"));
        } else {
            // Close button fallback
            String closeMat = plugin.getConfig().getString("gui.buttons.close", "BARRIER");
            inv.setItem(22, createButton(
                    parseMaterial(closeMat, Material.BARRIER),
                    "main-menu-close-name",
                    List.of(),
                    "close", "none"));
        }

        // Close button (slot 26) — always available
        String closeMat = plugin.getConfig().getString("gui.buttons.close", "BARRIER");
        inv.setItem(26, createButton(
                parseMaterial(closeMat, Material.BARRIER),
                "main-menu-close-name",
                List.of(),
                "close", "none"));

        player.openInventory(inv);
    }

    private ItemStack createInfoButton(Map<String, String> ph) {
        String matName = plugin.getConfig().getString("gui.buttons.info", "CRAFTING_TABLE");
        Material mat = parseMaterial(matName, Material.CRAFTING_TABLE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(messages.getMessage("main-menu-info-name"));
        List<Component> lore = new ArrayList<>();
        lore.add(messages.getMessage("main-menu-info-line-1", ph));
        lore.add(messages.getMessage("main-menu-info-line-2", ph));
        lore.add(messages.getMessage("main-menu-info-line-3", ph));
        lore.add(messages.getMessage("main-menu-info-line-4", ph));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
        item.setItemMeta(meta);
        return item;
    }

    // =====================================================================
    //  CATEGORY MENU (paginated)
    // =====================================================================

    public void openCategoryMenu(Player player, String category, int page) {
        List<CraftRecipe> allRecipes = fishApi.getCraftingRecipesByCategory(category);

        if (allRecipes == null || allRecipes.isEmpty()) {
            openEmptyCategoryMenu(player, category);
            return;
        }

        // Faz 0.4.0: apply filter + sort
        UUID uuid = player.getUniqueId();
        List<CraftRecipe> recipes = new ArrayList<>(allRecipes);
        recipes = applyFilter(player, recipes);
        recipes = applySort(player, recipes);

        // Re-check empty after filter
        if (recipes.isEmpty()) {
            // Filtered everything out — recipes DO exist in this category,
            // they're just hidden by the active filter. Show a distinct
            // placeholder (not the "no recipes — admin must add" one).
            openFilterExcludedMenu(player, category,
                    playerState.getFilterMode(player.getUniqueId()));
            return;
        }

        int itemsPerPage = getItemsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) recipes.size() / itemsPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        // Title
        String titleKey;
        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%page%", String.valueOf(page + 1));
        titlePh.put("%total%", String.valueOf(totalPages));
        titlePh.put("%category%", category);
        if ("rod".equals(category)) {
            titleKey = "category-rods-title";
        } else if ("bait".equals(category)) {
            titleKey = "category-baits-title";
        } else {
            titleKey = "category-misc-title";
        }
        Component title = messages.getMessage(titleKey, titlePh);

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.CATEGORY, category, page),
                54,
                title);

        // Border
        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 54, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        // Recipe icons
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, recipes.size());
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            CraftRecipe recipe = recipes.get(i);
            ItemStack icon = buildRecipeIcon(player, recipe);
            inv.setItem(slot, icon);
            slot++;
        }

        // Navigation + toolbar (slot 45-53)
        String arrowMat = plugin.getConfig().getString("gui.buttons.prev-page", "ARROW");
        String backMat = plugin.getConfig().getString("gui.buttons.back", "BARRIER");
        String nextMat = plugin.getConfig().getString("gui.buttons.next-page", "ARROW");

        // Slot 45: prev page
        if (page > 0) {
            inv.setItem(45, createButton(parseMaterial(arrowMat, Material.ARROW),
                    "category-prev-page", List.of(),
                    "page", category + ":" + (page - 1)));
        }
        // Slot 46: search (Faz 0.4.0) — only if enabled
        if (plugin.getConfig().getBoolean("search.enabled", true)) {
            String searchMat = plugin.getConfig().getString("gui.buttons.search", "COMPASS");
            inv.setItem(46, createButton(parseMaterial(searchMat, Material.COMPASS),
                    "category-search", List.of("category-search-lore"),
                    "search", category));
        }
        // Slot 47: filter (Faz 0.4.0) — only if enabled
        if (plugin.getConfig().getBoolean("filters.enabled", true)) {
            inv.setItem(47, buildFilterButton(player, category));
        }
        // Slot 48: sort (Faz 0.4.0) — only if enabled
        if (plugin.getConfig().getBoolean("sort.enabled", true)) {
            inv.setItem(48, buildSortButton(player, category));
        }
        // Slot 49: back to main
        inv.setItem(49, createButton(parseMaterial(backMat, Material.BARRIER),
                "category-back-to-main", List.of(),
                "main", "none"));
        // Slot 53: next page
        if (page < totalPages - 1) {
            inv.setItem(53, createButton(parseMaterial(nextMat, Material.ARROW),
                    "category-next-page", List.of(),
                    "page", category + ":" + (page + 1)));
        }

        player.openInventory(inv);
    }

    /**
     * Faz 0.4.0: Apply the player's current filter mode to a recipe list.
     */
    private List<CraftRecipe> applyFilter(Player player, List<CraftRecipe> recipes) {
        if (!plugin.getConfig().getBoolean("filters.enabled", true)) {
            return recipes;
        }
        PlayerGUIState.FilterMode mode = playerState.getFilterMode(player.getUniqueId());
        if (mode == PlayerGUIState.FilterMode.ALL) {
            return recipes;
        }
        List<CraftRecipe> filtered = new ArrayList<>();
        for (CraftRecipe r : recipes) {
            boolean canCraft = fishApi.canCraft(player, r);
            if (mode == PlayerGUIState.FilterMode.CRAFTABLE && canCraft) {
                filtered.add(r);
            } else if (mode == PlayerGUIState.FilterMode.LOCKED && !canCraft) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    /**
     * Faz 0.4.0: Apply the player's current sort mode to a recipe list.
     */
    private List<CraftRecipe> applySort(Player player, List<CraftRecipe> recipes) {
        if (!plugin.getConfig().getBoolean("sort.enabled", true)) {
            return recipes;
        }
        PlayerGUIState.SortMode mode = playerState.getSortMode(player.getUniqueId());
        List<CraftRecipe> sorted = new ArrayList<>(recipes);
        switch (mode) {
            case ID:
                sorted.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
                break;
            case LEVEL:
                sorted.sort((a, b) -> Integer.compare(
                        a.craftingLevelRequirement(), b.craftingLevelRequirement()));
                break;
            case XP:
                // Descending — biggest XP first
                sorted.sort((a, b) -> Integer.compare(b.xpReward(), a.xpReward()));
                break;
            case TIER:
                // By result item's custom-model-data (ascending)
                sorted.sort((a, b) -> Integer.compare(
                        getCmd(a.resultItem()), getCmd(b.resultItem())));
                break;
        }
        return sorted;
    }

    /**
     * Faz 0.4.0: Build the filter button showing the current mode.
     * @param category the current category (stored in PDC value so the
     *                 listener knows which category to reopen after cycling)
     */
    private ItemStack buildFilterButton(Player player, String category) {
        PlayerGUIState.FilterMode mode = playerState.getFilterMode(player.getUniqueId());
        String modeKey;
        String matName;
        switch (mode) {
            case CRAFTABLE:
                modeKey = "filter-mode-craftable";
                matName = plugin.getConfig().getString("gui.buttons.filter-craftable", "LIME_DYE");
                break;
            case LOCKED:
                modeKey = "filter-mode-locked";
                matName = plugin.getConfig().getString("gui.buttons.filter-locked", "RED_DYE");
                break;
            case ALL:
            default:
                modeKey = "filter-mode-all";
                matName = plugin.getConfig().getString("gui.buttons.filter-all", "HOPPER");
                break;
        }
        String modeStr = messages.getPlainMessage(modeKey);
        Map<String, String> ph = new HashMap<>();
        ph.put("%mode%", modeStr);
        return createButton(parseMaterial(matName, Material.HOPPER),
                "filter-button-name", List.of("filter-button-lore"),
                "cycle_filter", ph, category != null ? category : "none");
    }

    /**
     * Faz 0.4.0: Build the sort button showing the current mode.
     * @param category the current category (stored in PDC value so the
     *                 listener knows which category to reopen after cycling)
     */
    private ItemStack buildSortButton(Player player, String category) {
        PlayerGUIState.SortMode mode = playerState.getSortMode(player.getUniqueId());
        String modeKey;
        String matName;
        switch (mode) {
            case LEVEL:
                modeKey = "sort-mode-level";
                matName = plugin.getConfig().getString("gui.buttons.sort-level", "EXPERIENCE_BOTTLE");
                break;
            case XP:
                modeKey = "sort-mode-xp";
                matName = plugin.getConfig().getString("gui.buttons.sort-xp", "GOLD_INGOT");
                break;
            case TIER:
                modeKey = "sort-mode-tier";
                matName = plugin.getConfig().getString("gui.buttons.sort-tier", "DIAMOND");
                break;
            case ID:
            default:
                modeKey = "sort-mode-id";
                matName = plugin.getConfig().getString("gui.buttons.sort-id", "PAPER");
                break;
        }
        String modeStr = messages.getPlainMessage(modeKey);
        Map<String, String> ph = new HashMap<>();
        ph.put("%mode%", modeStr);
        return createButton(parseMaterial(matName, Material.PAPER),
                "sort-button-name", List.of("sort-button-lore"),
                "cycle_sort", ph, category != null ? category : "none");
    }

    /**
     * Empty state for a category with no recipes.
     */
    private void openEmptyCategoryMenu(Player player, String category) {
        String titleKey;
        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%category%", category);
        if ("rod".equals(category)) {
            titleKey = "empty-rods-title";
        } else if ("bait".equals(category)) {
            titleKey = "empty-baits-title";
        } else {
            titleKey = "empty-misc-title";
        }
        Component title = messages.getMessage(titleKey, titlePh);

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.CATEGORY, category, 0),
                27,
                title);

        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 27, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        // Center placeholder (slot 13)
        String placeholderMat = plugin.getConfig().getString("gui.buttons.empty-category", "BARRIER");
        ItemStack placeholder = new ItemStack(parseMaterial(placeholderMat, Material.BARRIER));
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.getMessage("empty-category-name"));
            Map<String, String> ph = new HashMap<>();
            ph.put("%category%", category);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(messages.getMessage("empty-category-lore-1"));
            lore.add(messages.getMessage("empty-category-lore-2"));
            lore.add(Component.empty());
            lore.add(messages.getMessage("empty-category-lore-3", ph));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            placeholder.setItemMeta(meta);
        }
        inv.setItem(13, placeholder);

        // Back to main menu (slot 22)
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(22, createButton(parseMaterial(backMat, Material.ARROW),
                "empty-back-to-main", List.of(),
                "main", "none"));

        player.openInventory(inv);
    }

    /**
     * Empty state shown when the player's current filter excluded every
     * recipe in the category. Distinct from {@link #openEmptyCategoryMenu}
     * because the recipes DO exist — they're just hidden by the filter.
     *
     * <p>Provides a working filter button (slot 11) so the player can
     * cycle the filter back to a mode that shows recipes, plus a back
     * button (slot 15) to the main menu. If PhoenixFish's crafting is
     * unavailable (e.g. {@code xp-system.enabled: false}), an extra
     * hint line tells the player what to do.</p>
     *
     * @param player   the player viewing the GUI
     * @param category the current category (e.g. {@code "rod"} or {@code "bait"})
     * @param mode     the filter mode that excluded everything
     * @since 0.4.1
     */
    private void openFilterExcludedMenu(Player player, String category,
                                          PlayerGUIState.FilterMode mode) {
        String titleKey;
        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%category%", category);
        if ("rod".equals(category)) {
            titleKey = "empty-rods-title";
        } else if ("bait".equals(category)) {
            titleKey = "empty-baits-title";
        } else {
            titleKey = "empty-misc-title";
        }
        Component title = messages.getMessage(titleKey, titlePh);

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.CATEGORY, category, 0),
                27,
                title);

        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 27, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        // Resolve the localized label for the active filter mode
        String modeKey;
        switch (mode) {
            case CRAFTABLE -> modeKey = "filter-mode-craftable";
            case LOCKED -> modeKey = "filter-mode-locked";
            case ALL -> modeKey = "filter-mode-all";
            default -> modeKey = "filter-mode-all";
        }
        String modeLabel = messages.getPlainMessage(modeKey);
        Map<String, String> ph = new HashMap<>();
        ph.put("%category%", category);
        ph.put("%mode%", modeLabel);

        // Center placeholder (slot 13) — explains the filter excluded everything
        String placeholderMat = plugin.getConfig().getString("gui.buttons.empty-category", "BARRIER");
        ItemStack placeholder = new ItemStack(parseMaterial(placeholderMat, Material.BARRIER));
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.getMessage("filter-no-match-name"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(messages.getMessage("filter-no-match-lore-1", ph));
            lore.add(messages.getMessage("filter-no-match-lore-2", ph));
            lore.add(messages.getMessage("filter-no-match-lore-3", ph));

            // If PhoenixFish reports crafting is unavailable, show an extra
            // hint line telling the player (and any admin reading the chat)
            // what to do. This catches the most common root cause: the
            // server left xp-system.enabled=false in PhoenixFish config.
            //
            // We use reflection to call getCraftingUnavailableReason() so
            // this code compiles against OLD PhoenixFish builds too (where
            // the method doesn't exist yet). If the new PhoenixFish is
            // loaded at runtime, we get the detailed reason code; otherwise
            // we fall back to the legacy isCraftingAvailable() flag.
            String reason = invokeCraftingUnavailableReason(fishApi);
            if (reason == null) {
                // Legacy fallback: works against any PhoenixFish version
                try {
                    if (!fishApi.isCraftingAvailable()) {
                        reason = "xp-system-disabled";
                    }
                } catch (Throwable ignored) {
                    // ignore — we just won't show the extra hint
                }
            }
            if (reason != null) {
                Map<String, String> reasonPh = new HashMap<>();
                reasonPh.put("%reason%", reason);
                lore.add(Component.empty());
                lore.add(messages.getMessage("filter-no-match-system-disabled", reasonPh));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            placeholder.setItemMeta(meta);
        }
        inv.setItem(13, placeholder);

        // Filter button at slot 11 — lets the player cycle the filter back
        // (e.g. CRAFTABLE → LOCKED → ALL). Clicking it triggers the normal
        // cycle_filter handler, which reopens this category menu.
        inv.setItem(11, buildFilterButton(player, category));

        // Back to main menu (slot 15)
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(15, createButton(parseMaterial(backMat, Material.ARROW),
                "empty-back-to-main", List.of(),
                "main", "none"));

        player.openInventory(inv);
    }

    /**
     * Reflectively call {@code PhoenixFishAPI.getCraftingUnavailableReason()}
     * if the method is available on the loaded PhoenixFish build. This lets
     * PhoenixCraft compile against OLDER PhoenixFish jars (which don't
     * expose this method yet) and only use the detailed reason code when
     * the runtime PhoenixFish is new enough.
     *
     * @param fishApi the PhoenixFish API instance (never null)
     * @return the reason code (e.g. {@code "xp-system-disabled"}), or
     *         {@code null} if the method is unavailable or crafting is
     *         fully available.
     * @since 0.4.1
     */
    private String invokeCraftingUnavailableReason(PhoenixFishAPI fishApi) {
        if (fishApi == null) return null;
        try {
            Method m = fishApi.getClass().getMethod("getCraftingUnavailableReason");
            Object result = m.invoke(fishApi);
            return result instanceof String ? (String) result : null;
        } catch (NoSuchMethodException ignored) {
            // Old PhoenixFish build — method doesn't exist; fall back.
            return null;
        } catch (Throwable ignored) {
            // Any other reflection/runtime error — fail soft.
            return null;
        }
    }

    /**
     * Build the recipe's result item as the icon, with lore listing required materials.
     */
    private ItemStack buildRecipeIcon(Player player, CraftRecipe recipe) {
        ItemStack icon = recipe.resultItem().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(messages.getMessage("recipe-ingredients-header"));

        boolean hasAllMaterials = true;
        for (CraftRecipe.Ingredient ing : recipe.ingredients()) {
            int owned = countInInventory(player, ing.item());
            int needed = ing.amount();
            boolean ok = owned >= needed;
            if (!ok) hasAllMaterials = false;

            Map<String, String> ph = new HashMap<>();
            ph.put("%color%", ok ? "<green>" : "<red>");
            ph.put("%name%", describeItem(ing.item()));
            ph.put("%owned%", String.valueOf(owned));
            ph.put("%needed%", String.valueOf(needed));
            lore.add(messages.getMessageRaw("recipe-ingredient-line", ph));
        }

        lore.add(Component.empty());
        int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
        boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
        Map<String, String> levelPh = new HashMap<>();
        levelPh.put("%color%", levelOk ? "<green>" : "<red>");
        levelPh.put("%required%", String.valueOf(recipe.craftingLevelRequirement()));
        levelPh.put("%current%", String.valueOf(playerLevel));
        lore.add(messages.getMessageRaw("recipe-level-line", levelPh));

        Map<String, String> xpPh = new HashMap<>();
        xpPh.put("%xp%", String.valueOf(recipe.xpReward()));
        lore.add(messages.getMessage("recipe-xp-line", xpPh));

        lore.add(Component.empty());
        if (levelOk && hasAllMaterials) {
            lore.add(messages.getMessage("recipe-status-craftable"));
        } else if (!levelOk) {
            lore.add(messages.getMessage("recipe-status-low-level"));
        } else {
            lore.add(messages.getMessage("recipe-status-missing-materials"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "recipe");
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, recipe.id());
        icon.setItemMeta(meta);
        return icon;
    }

    // =====================================================================
    //  RECIPE DETAIL
    // =====================================================================

    public void openRecipeDetail(Player player, String recipeId) {
        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe_id%", recipeId);
            player.sendMessage(messages.getMessage("craft-not-found", true, ph));
            return;
        }

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.DETAIL,
                        recipe.category(), 0, recipe.id()),
                27,
                messages.getMessage("detail-title"));

        String borderMat = plugin.getConfig().getString("gui.detail-border", "GRAY_STAINED_GLASS_PANE");
        fillBorders(inv, 27, parseMaterial(borderMat, Material.GRAY_STAINED_GLASS_PANE));

        // Result preview at slot 13
        ItemStack preview = recipe.resultItem().clone();
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            List<Component> lore = new ArrayList<>(previewMeta.lore() != null ? previewMeta.lore() : List.of());
            lore.add(Component.empty());
            lore.add(messages.getMessage("detail-result-lore"));
            previewMeta.lore(lore);
            preview.setItemMeta(previewMeta);
        }
        inv.setItem(13, preview);

        // Ingredients list at slot 11
        inv.setItem(11, buildIngredientsItem(player, recipe));

        // Faz 0.2.0: Three craft buttons now
        boolean canCraft = fishApi.canCraft(player, recipe);
        int maxCraftable = fishApi.maxCraftable(player, recipe);

        String craftReadyMat = plugin.getConfig().getString("gui.buttons.craft-ready", "LIME_CONCRETE");
        String craftCloseMat = plugin.getConfig().getString("gui.buttons.craft-ready-close", "GREEN_CONCRETE");
        String craftMaxMat = plugin.getConfig().getString("gui.buttons.craft-max", "YELLOW_CONCRETE");
        String disabledMat = plugin.getConfig().getString("gui.buttons.craft-disabled", "RED_CONCRETE");

        if (canCraft) {
            // Slot 14: CRAFT (1x, ekran açık kalır)
            inv.setItem(14, buildCraftButton(craftReadyMat, "detail-craft-name",
                    buildCraftLore("detail-craft-lore-1", "detail-craft-lore-2",
                            "detail-craft-shift-hint", maxCraftable),
                    "craft", recipe.id()));

            // Slot 15: CRAFT AND CLOSE (1x, ekran kapanır)
            inv.setItem(15, buildCraftButton(craftCloseMat, "detail-craft-close-name",
                    buildCraftLore("detail-craft-close-lore-1", null,
                            "detail-craft-close-shift-hint", maxCraftable),
                    "craft_and_close", recipe.id()));

            // Slot 22: MAX CRAFT (toplu, ekran kapanmaz) — Faz 0.2.0
            // Back button'u slot 22'den 21'e taşıyalım (MAX butonu 22'ye)
            // Aslında en güzeli: slot 21 = MAX, slot 22 = BACK
            if (maxCraftable > 1) {
                Map<String, String> maxPh = new HashMap<>();
                maxPh.put("%count%", String.valueOf(maxCraftable));
                List<Component> maxLore = new ArrayList<>();
                maxLore.add(messages.getMessage("detail-craft-max-lore-1", maxPh));
                maxLore.add(messages.getMessage("detail-craft-max-lore-2"));
                inv.setItem(21, buildCraftButton(craftMaxMat, "detail-craft-max-name",
                        maxLore, "craft_max", recipe.id()));
            } else {
                // maxCraftable == 1 → MAX butonu gereksiz, boş bırak
                // (slot 21'de zaten border yok, glass pane koyalım)
            }
        } else {
            // Disabled — both slots show disabled button
            int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
            boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
            String reasonKey = levelOk
                    ? "detail-craft-disabled-reason-materials"
                    : "detail-craft-disabled-reason-level";

            List<Component> disabledLore = new ArrayList<>();
            disabledLore.add(messages.getMessage(reasonKey));
            disabledLore.add(messages.getMessage("detail-craft-disabled-lore-2"));

            inv.setItem(14, buildCraftButton(disabledMat, "detail-craft-disabled-name",
                    disabledLore, "craft_disabled", recipe.id()));
            inv.setItem(15, buildCraftButton(disabledMat, "detail-craft-disabled-name",
                    disabledLore, "craft_disabled", recipe.id()));
        }

        // Back button at slot 22 (or slot 23 if MAX is at 21)
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        // Eğer MAX butonu slot 21'deyse, back'i 22'ye koy
        // Eğer MAX butonu yoksa (maxCraftable <= 1), back'i 22'ye koy (yine)
        int backSlot = 22;
        // Override: slot 22'ye back koyalım, MAX butonu 21'de
        // (eğer MAX butonu 21'e konulduysa, back 22'de olur — çakışma yok)
        // Aslında yukarıda MAX butonu 21'e kondu, back 22'ye — sorun yok
        // Ama eğer MAX konmadıysa (maxCraftable <= 1), back 22'de olur — yine sorun yok
        // Override: 21'de MAX varsa 22 = back; 21'de MAX yoksa 22 = back. Her durumda 22 = back. OK.

        inv.setItem(backSlot, createButton(parseMaterial(backMat, Material.ARROW),
                "detail-back-name", List.of("detail-back-lore"),
                "back_to_category", recipe.category()));

        player.openInventory(inv);
    }

    private ItemStack buildIngredientsItem(Player player, CraftRecipe recipe) {
        ItemStack ingredientsItem = new ItemStack(Material.BOOK);
        ItemMeta ingMeta = ingredientsItem.getItemMeta();
        if (ingMeta == null) return ingredientsItem;

        ingMeta.displayName(messages.getMessage("detail-ingredients-name"));
        List<Component> ingLore = new ArrayList<>();
        for (CraftRecipe.Ingredient ing : recipe.ingredients()) {
            int owned = countInInventory(player, ing.item());
            boolean ok = owned >= ing.amount();
            Map<String, String> ph = new HashMap<>();
            ph.put("%color%", ok ? "<green>" : "<red>");
            ph.put("%name%", describeItem(ing.item()));
            ph.put("%needed%", String.valueOf(ing.amount()));
            ph.put("%owned%", String.valueOf(owned));
            ingLore.add(messages.getMessageRaw("detail-ingredient-line", ph));
        }
        ingLore.add(Component.empty());

        int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
        boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
        Map<String, String> levelPh = new HashMap<>();
        levelPh.put("%color%", levelOk ? "<green>" : "<red>");
        levelPh.put("%required%", String.valueOf(recipe.craftingLevelRequirement()));
        levelPh.put("%current%", String.valueOf(playerLevel));
        ingLore.add(messages.getMessageRaw("detail-level-line", levelPh));

        Map<String, String> xpPh = new HashMap<>();
        xpPh.put("%xp%", String.valueOf(recipe.xpReward()));
        ingLore.add(messages.getMessage("detail-xp-line", xpPh));

        // Faz 0.0.3: last craft time + max craftable
        Long lastTs = lastCraftTimestamps.get(player.getUniqueId());
        if (lastTs != null) {
            Map<String, String> timePh = new HashMap<>();
            timePh.put("%time%", formatTimeAgo(lastTs));
            ingLore.add(messages.getMessage("detail-last-craft", timePh));
        }

        int maxCraftable = fishApi.maxCraftable(player, recipe);
        Map<String, String> maxPh = new HashMap<>();
        maxPh.put("%count%", String.valueOf(maxCraftable));
        ingLore.add(messages.getMessage("detail-max-craftable", maxPh));

        ingMeta.lore(ingLore);
        ingredientsItem.setItemMeta(ingMeta);
        return ingredientsItem;
    }

    private List<Component> buildCraftLore(String line1Key, String line2Key,
                                            String shiftHintKey, int maxCraftable) {
        List<Component> lore = new ArrayList<>();
        lore.add(messages.getMessage(line1Key));
        if (line2Key != null) {
            lore.add(messages.getMessage(line2Key));
        }
        if (maxCraftable > 1 && shiftHintKey != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%count%", String.valueOf(maxCraftable));
            lore.add(Component.empty());
            lore.add(messages.getMessage(shiftHintKey, ph));
        }
        return lore;
    }

    private ItemStack buildCraftButton(String matName, String nameKey,
                                        List<Component> lore, String action, String value) {
        Material mat = parseMaterial(matName, Material.LIME_CONCRETE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(messages.getMessage(nameKey));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    // =====================================================================
    //  CRAFT ACTION
    // =====================================================================

    /**
     * Handle a click on a craft button.
     *
     * @param player       the clicking player
     * @param recipeId     the recipe id from the button's PDC value
     * @param shiftClick   true if the player shift-clicked → bulk craft
     * @param closeAfter   true if the GUI should close after a successful craft
     * @param forceMax     true if this is the MAX CRAFT button (always bulk)
     */
    public void handleCraftClick(Player player, String recipeId, boolean shiftClick,
                                  boolean closeAfter, boolean forceMax) {
        // Cooldown check
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = craftCooldowns.get(uuid);
        long cooldown = getCraftCooldownMs();
        if (last != null && (now - last) < cooldown) {
            return;
        }
        craftCooldowns.put(uuid, now);

        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe_id%", recipeId);
            player.sendMessage(messages.getMessage("craft-not-found", true, ph));
            return;
        }

        // Decide: single or bulk?
        boolean doBulk = shiftClick || forceMax;
        if (doBulk) {
            handleBulkCraft(player, recipe, closeAfter);
        } else {
            handleSingleCraft(player, recipe, closeAfter);
        }
    }

    private void handleSingleCraft(Player player, CraftRecipe recipe, boolean closeAfter) {
        // Faz 0.5.0: economy + recipe-learning checks
        if (economyManager != null && economyManager.isEnabled()
                && !economyManager.canAfford(player.getUniqueId(), recipe)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%needed%", String.format("%.2f", economyManager.getFee(recipe)));
            ph.put("%balance%", String.format("%.2f", economyManager.getBalance(player.getUniqueId())));
            player.sendMessage(messages.getMessage("economy-insufficient-funds", true, ph));
            playFailSound(player);
            return;
        }

        if (recipeManager != null && recipeManager.isEnabled()
                && recipeManager.isRequireLearning()
                && !recipeManager.hasLearned(player.getUniqueId(), recipe.id())) {
            player.sendMessage(messages.getMessage("recipe-not-learned", true));
            playFailSound(player);
            return;
        }

        CraftResult result = fishApi.craft(player, recipe);
        switch (result) {
            case SUCCESS -> {
                // Faz 0.5.0: charge the fee
                if (economyManager != null && economyManager.isEnabled()) {
                    economyManager.chargeFee(player.getUniqueId(), recipe);
                }

                Map<String, String> ph = new HashMap<>();
                ph.put("%item%", describeItem(recipe.resultItem()));
                ph.put("%xp%", String.valueOf(recipe.xpReward()));
                if (NotificationHelper.shouldSendCraftSuccessChat(plugin)) {
                    player.sendMessage(messages.getMessageRaw("craft-success-single", true, ph));
                }

                playCraftSound(player, recipe);
                spawnSuccessParticles(player, 1);
                lastCraftTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

                recordCraftAndCheckAchievements(player, recipe, 1, recipe.xpReward());

                if (closeAfter) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                } else {
                    final String recipeIdFinal = recipe.id();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (isPlayerViewingCraftingGUI(player)) {
                            openRecipeDetail(player, recipeIdFinal);
                        }
                    });
                }
            }
            case LOW_LEVEL -> sendFailMessage(player, "craft-fail-level", recipe, "level");
            case MISSING_MATERIALS -> player.sendMessage(messages.getMessage("craft-fail-materials", true));
            case NO_DATA -> player.sendMessage(messages.getMessage("craft-no-data", true));
            case UNKNOWN_RECIPE -> player.sendMessage(messages.getMessage("craft-recipe-unknown", true));
            case INTERNAL_ERROR -> player.sendMessage(messages.getMessage("craft-internal-error", true));
        }
    }

    private void handleBulkCraft(Player player, CraftRecipe recipe, boolean closeAfter) {
        // Faz 0.5.0: recipe-learning check
        if (recipeManager != null && recipeManager.isEnabled()
                && recipeManager.isRequireLearning()
                && !recipeManager.hasLearned(player.getUniqueId(), recipe.id())) {
            player.sendMessage(messages.getMessage("recipe-not-learned", true));
            playFailSound(player);
            return;
        }

        int maxCraftable = fishApi.maxCraftable(player, recipe);
        if (maxCraftable <= 0) {
            // Diagnose why
            int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
            if (playerLevel < recipe.craftingLevelRequirement()) {
                sendFailMessage(player, "craft-fail-level", recipe, "level");
            } else {
                player.sendMessage(messages.getMessage("craft-fail-materials", true));
            }
            playFailSound(player);
            return;
        }

        int toCraft = Math.min(maxCraftable, getBulkCraftCap());

        // Faz 0.5.0: economy check for bulk
        if (economyManager != null && economyManager.isEnabled()
                && !economyManager.canAffordBulk(player.getUniqueId(), recipe, toCraft)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%needed%", String.format("%.2f", economyManager.calculateBulkFee(recipe, toCraft)));
            ph.put("%balance%", String.format("%.2f", economyManager.getBalance(player.getUniqueId())));
            player.sendMessage(messages.getMessage("economy-insufficient-funds", true, ph));
            playFailSound(player);
            return;
        }

        CraftBulkResult bulkResult = fishApi.craftBulk(player, recipe, toCraft);
        switch (bulkResult.result()) {
            case SUCCESS -> {
                // Faz 0.5.0: charge the bulk fee
                if (economyManager != null && economyManager.isEnabled()) {
                    economyManager.chargeBulkFee(player.getUniqueId(), recipe, bulkResult.craftedCount());
                }

                Map<String, String> ph = new HashMap<>();
                ph.put("%count%", String.valueOf(bulkResult.craftedCount()));
                ph.put("%item%", describeItem(recipe.resultItem()));
                ph.put("%xp%", String.valueOf(bulkResult.xpAwarded()));
                if (NotificationHelper.shouldSendCraftSuccessChat(plugin)) {
                    player.sendMessage(messages.getMessageRaw("craft-success-bulk", true, ph));
                }

                playCraftSound(player, recipe);
                spawnSuccessParticles(player, bulkResult.craftedCount());
                lastCraftTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

                recordCraftAndCheckAchievements(player, recipe,
                        bulkResult.craftedCount(), bulkResult.xpAwarded());

                if (closeAfter) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                } else {
                    final String recipeIdFinal = recipe.id();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (isPlayerViewingCraftingGUI(player)) {
                            openRecipeDetail(player, recipeIdFinal);
                        }
                    });
                }
            }
            case LOW_LEVEL -> sendFailMessage(player, "craft-fail-level", recipe, "level");
            case MISSING_MATERIALS -> player.sendMessage(messages.getMessage("craft-fail-materials", true));
            case NO_DATA -> player.sendMessage(messages.getMessage("craft-no-data", true));
            case UNKNOWN_RECIPE -> player.sendMessage(messages.getMessage("craft-recipe-unknown", true));
            case INTERNAL_ERROR -> player.sendMessage(messages.getMessage("craft-bulk-internal-error", true));
        }
    }

    private void sendFailMessage(Player player, String key, CraftRecipe recipe, String reason) {
        Map<String, String> ph = new HashMap<>();
        ph.put("%required%", String.valueOf(recipe.craftingLevelRequirement()));
        ph.put("%current%", String.valueOf(fishApi.getCraftingLevel(player.getUniqueId())));
        player.sendMessage(messages.getMessage(key, true, ph));
        playFailSound(player);
    }

    /**
     * Faz 0.3.0: Record this craft in the stats system, then check all
     * achievements for newly-unlocked ones. Both systems are no-ops if
     * disabled in config.
     */
    private void recordCraftAndCheckAchievements(Player player, CraftRecipe recipe,
                                                   int craftedCount, int xpAwarded) {
        if (statsManager != null && statsManager.isEnabled()) {
            statsManager.recordCraft(player, recipe, craftedCount, xpAwarded);
        }
        // Faz 0.6.0: Discovery bonus XP check — award silently, notification
        // is handled by DiscoveryManager itself (title + chat)
        if (discoveryManager != null && discoveryManager.isEnabled()) {
            int bonusXp = discoveryManager.checkDiscovery(player, recipe, craftedCount);
            if (bonusXp > 0) {
                fishApi.addCraftingXp(player, bonusXp);
            }
        }
        if (achievementManager != null && achievementManager.isEnabled()) {
            achievementManager.checkAchievements(player, recipe, craftedCount);
        }
    }

    // =====================================================================
    //  BLACK MARKET MENU — Faz 1.0.0
    // =====================================================================

    public void openBlackMarketMenu(Player player) {
        if (blackMarketManager == null || !blackMarketManager.isEnabled()) {
            player.sendMessage(messages.getMessage("blackmarket-refresh-disabled", true));
            return;
        }

        var stock = blackMarketManager.getStock();
        long untilRefresh = blackMarketManager.getTimeUntilRefresh();
        String timeStr = formatBlackMarketTime(untilRefresh);

        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%time%", timeStr);

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("blackmarket-title", titlePh));

        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 54, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        int slot = 0;
        for (Map.Entry<String, Double> entry : stock.entrySet()) {
            String recipeId = entry.getKey();
            double price = entry.getValue();

            com.phoenix.fish.api.CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
            if (recipe == null) continue;

            ItemStack icon = recipe.resultItem().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;

            // Set display name with dark_red color
            String recipeName = describeItem(recipe.resultItem());
            Map<String, String> namePh = new HashMap<>();
            namePh.put("%recipe%", recipeName);
            meta.displayName(messages.getMessageRaw("blackmarket-item-name", namePh));

            // Lore
            Map<String, String> pricePh = new HashMap<>();
            pricePh.put("%price%", String.format("%.2f", price));
            List<Component> lore = new ArrayList<>();
            lore.add(messages.getMessage("blackmarket-item-lore-1", pricePh));
            lore.add(messages.getMessage("blackmarket-item-lore-2"));
            lore.add(messages.getMessage("blackmarket-item-lore-3"));
            meta.lore(lore);

            // PDC: action = "blackmarket_purchase", value = recipeId + ":" + price
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "blackmarket_purchase");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING,
                    recipeId + ":" + String.format("%.2f", price));

            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
            if (slot >= 45) break;
        }

        // Slot 49: back to main menu
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(49, createButton(parseMaterial(backMat, Material.ARROW),
                "category-back-to-main", List.of(),
                "main", "none"));

        player.openInventory(inv);
    }

    /**
     * Handle a black market purchase click.
     */
    public void handleBlackMarketPurchase(Player player, String recipeId, double price) {
        if (blackMarketManager == null || !blackMarketManager.isEnabled()) return;

        // Check if still in stock
        if (!blackMarketManager.isInStock(recipeId)) {
            player.sendMessage(messages.getMessage("blackmarket-purchase-sold-out", true));
            return;
        }

        // Check if already bought this cycle
        if (!blackMarketManager.canPurchase(player.getUniqueId())) {
            player.sendMessage(messages.getMessage("blackmarket-purchase-already-bought", true));
            return;
        }

        // Check if already learned
        if (recipeManager != null && recipeManager.isEnabled()
                && recipeManager.hasLearned(player.getUniqueId(), recipeId)) {
            player.sendMessage(messages.getMessage("blackmarket-purchase-already-learned", true));
            return;
        }

        // Check balance
        double balance = fishApi.getBalance(player.getUniqueId());
        if (balance < price) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%price%", String.format("%.2f", price));
            ph.put("%balance%", String.format("%.2f", balance));
            player.sendMessage(messages.getMessage("blackmarket-purchase-insufficient-funds", true, ph));
            return;
        }

        // Charge
        fishApi.withdrawPlayer(player.getUniqueId(), price);

        // Give recipe book
        if (recipeManager != null) {
            ItemStack book = recipeManager.createRecipeBook(recipeId);
            if (book != null) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(book);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }

        // Record purchase
        blackMarketManager.recordPurchase(player.getUniqueId());

        // Notify
        Map<String, String> ph = new HashMap<>();
        ph.put("%recipe%", recipeId);
        ph.put("%price%", String.format("%.2f", price));
        player.sendMessage(messages.getMessage("blackmarket-purchase-success", true, ph));

        // Refresh the GUI
        openBlackMarketMenu(player);
    }

    private String formatBlackMarketTime(long ms) {
        if (ms <= 0) return messages.getPlainMessage("blackmarket-time-expired");
        long minutes = ms / 60_000L;
        long hours = minutes / 60;
        if (hours > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%count%", String.valueOf(hours));
            ph.put("%minutes%", String.valueOf(minutes % 60));
            return messages.getPlainMessage("blackmarket-time-hours", ph);
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("%count%", String.valueOf(minutes));
        return messages.getPlainMessage("blackmarket-time-minutes", ph);
    }

    // =====================================================================
    //  RECIPES MENU — Faz 0.5.0
    // =====================================================================

    public void openRecipesMenu(Player player) {
        if (recipeManager == null || !recipeManager.isEnabled()) {
            player.sendMessage(messages.getMessage("recipe-sharing-disabled", true));
            return;
        }

        java.util.List<CraftRecipe> allRecipes = fishApi.getCraftingRecipes();
        int total = allRecipes.size();
        int learnedCount = recipeManager.getLearnedCount(player.getUniqueId());

        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%learned%", String.valueOf(learnedCount));
        titlePh.put("%total%", String.valueOf(total));

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("recipes-title", titlePh));

        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 54, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        String lockedMat = plugin.getConfig().getString("gui.buttons.recipe-locked", "WRITTEN_BOOK");
        String learnedMat = plugin.getConfig().getString("gui.buttons.recipe-learned", "KNOWLEDGE_BOOK");

        int slot = 0;
        for (CraftRecipe recipe : allRecipes) {
            boolean isLearned = recipeManager.hasLearned(player.getUniqueId(), recipe.id());
            Material mat = parseMaterial(
                    isLearned ? learnedMat : lockedMat,
                    isLearned ? Material.KNOWLEDGE_BOOK : Material.WRITTEN_BOOK);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String nameKey = isLearned ? "recipe-learned-name" : "recipe-locked-name";
            Map<String, String> namePh = new HashMap<>();
            namePh.put("%name%", describeItem(recipe.resultItem()));
            meta.displayName(messages.getMessage(nameKey, namePh));

            List<Component> lore = new ArrayList<>();
            lore.add(messages.getMessage(isLearned
                    ? "recipe-learned-lore-1"
                    : "recipe-locked-lore-1"));
            lore.add(Component.empty());
            lore.add(messages.getMessage(isLearned
                    ? "recipe-learned-lore-2"
                    : "recipe-locked-lore-3"));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
            if (slot >= 45) break;
        }

        // Slot 49: back to main menu
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(49, createButton(parseMaterial(backMat, Material.ARROW),
                "category-back-to-main", List.of(),
                "main", "none"));

        player.openInventory(inv);
    }

    // =====================================================================
    //  STATS MENU — Faz 0.3.0
    // =====================================================================

    public void openStatsMenu(Player player) {
        if (statsManager == null || !statsManager.isEnabled()) {
            player.sendMessage(messages.getMessage("stats-disabled-message", true));
            return;
        }

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),  // reuse MAIN holder type
                27,
                messages.getMessage("stats-title"));

        String borderMat = plugin.getConfig().getString("gui.detail-border", "GRAY_STAINED_GLASS_PANE");
        fillBorders(inv, 27, parseMaterial(borderMat, Material.GRAY_STAINED_GLASS_PANE));

        UUID uuid = player.getUniqueId();
        StatsManager.PlayerStats stats = statsManager.getStats(uuid);

        // Slot 11: Personal stats book
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        if (statsMeta != null) {
            statsMeta.displayName(messages.getMessage("stats-title"));
            List<Component> lore = new ArrayList<>();

            Map<String, String> ph = new HashMap<>();
            ph.put("%count%", String.valueOf(stats.totalCrafts));
            lore.add(messages.getMessage("stats-total-crafts", ph));

            ph.clear();
            ph.put("%xp%", String.valueOf(stats.totalXpEarned));
            lore.add(messages.getMessage("stats-total-xp", ph));

            ph.clear();
            ph.put("%count%", String.valueOf(stats.recipeCounts.size()));
            lore.add(messages.getMessage("stats-unique-recipes", ph));

            ph.clear();
            ph.put("%time%", formatTimeAgo(stats.firstCraftTimestamp));
            lore.add(messages.getMessage("stats-first-craft", ph));

            ph.clear();
            ph.put("%time%", formatTimeAgo(stats.lastCraftTimestamp));
            lore.add(messages.getMessage("stats-last-craft", ph));

            statsMeta.lore(lore);
            statsMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            statsMeta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            statsItem.setItemMeta(statsMeta);
        }
        inv.setItem(11, statsItem);

        // Slot 13: Leaderboard
        int topN = plugin.getConfig().getInt("stats.leaderboard-top-n", 10);
        List<Map.Entry<UUID, Integer>> top = statsManager.getTopCrafters(topN);

        ItemStack leaderboard = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta lbMeta = leaderboard.getItemMeta();
        if (lbMeta != null) {
            Map<String, String> lbTitlePh = new HashMap<>();
            lbTitlePh.put("%n%", String.valueOf(topN));
            lbMeta.displayName(messages.getMessage("stats-leaderboard-header", lbTitlePh));

            List<Component> lbLore = new ArrayList<>();
            if (top.isEmpty()) {
                lbLore.add(messages.getMessage("stats-leaderboard-empty"));
            } else {
                int rank = 1;
                for (Map.Entry<UUID, Integer> entry : top) {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    if (name == null) name = "???";
                    Map<String, String> ph = new HashMap<>();
                    ph.put("%rank%", String.valueOf(rank));
                    ph.put("%player%", name);
                    ph.put("%count%", String.valueOf(entry.getValue()));
                    lbLore.add(messages.getMessage("stats-leaderboard-line", ph));
                    rank++;
                }
            }
            lbMeta.lore(lbLore);
            lbMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            lbMeta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            leaderboard.setItemMeta(lbMeta);
        }
        inv.setItem(13, leaderboard);

        // Slot 22: Back button
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(22, createButton(parseMaterial(backMat, Material.ARROW),
                "detail-back-name", List.of("detail-back-lore"),
                "main", "none"));

        player.openInventory(inv);
    }

    // =====================================================================
    //  SEARCH MENU — Faz 0.4.0
    // =====================================================================

    /**
     * Start chat-based search input. The player is prompted to type a query
     * in chat. The CraftingListener's AsyncPlayerChatEvent handler will
     * capture the next message and call {@link #performSearch}.
     *
     * @param category restrict search to this category, or null for all
     */
    public void startSearchInput(Player player, String category) {
        if (!plugin.getConfig().getBoolean("search.enabled", true)) {
            player.sendMessage(messages.getMessage("search-disabled", true));
            return;
        }

        // Close the inventory so the player can type in chat
        player.closeInventory();

        long timeoutSec = plugin.getConfig().getLong("search.chat-input-timeout-seconds", 30);
        long timeoutMs = timeoutSec * 1000L;
        playerState.startSearchInput(player.getUniqueId(), timeoutMs);

        Map<String, String> ph = new HashMap<>();
        ph.put("%seconds%", String.valueOf(timeoutSec));
        player.sendMessage(messages.getMessage("search-prompt", true, ph));
        player.sendMessage(messages.getMessage("search-prompt-cancel", true));

        // Schedule a timeout check
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (playerState.isPendingSearch(uuid)) {
                playerState.clearSearchInput(uuid);
                if (player.isOnline()) {
                    player.sendMessage(messages.getMessage("search-timeout", true));
                }
            }
        }, timeoutSec * 20L);
    }

    /**
     * Perform a search across all recipes (or a specific category) and open
     * the results menu. Called by CraftingListener when the player types
     * a chat message during search input mode.
     *
     * @param player   the searching player
     * @param query    the search query
     * @param category restrict to this category, or null for all
     */
    public void performSearch(Player player, String query, String category) {
        if (!plugin.getConfig().getBoolean("search.enabled", true)) {
            player.sendMessage(messages.getMessage("search-disabled", true));
            return;
        }

        int minLen = plugin.getConfig().getInt("search.min-query-length", 2);
        if (query.length() < minLen) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%min%", String.valueOf(minLen));
            player.sendMessage(messages.getMessage("search-too-short", true, ph));
            return;
        }

        // Search across all recipes
        List<CraftRecipe> allRecipes = fishApi.getCraftingRecipes();
        if (category != null && !category.isEmpty() && !"all".equalsIgnoreCase(category)) {
            allRecipes = allRecipes.stream()
                    .filter(r -> category.equalsIgnoreCase(r.category()))
                    .toList();
        }

        String q = query.toLowerCase();
        List<CraftRecipe> results = new ArrayList<>();
        for (CraftRecipe r : allRecipes) {
            // Match by id
            if (r.id().toLowerCase().contains(q)) {
                results.add(r);
                continue;
            }
            // Match by result item display name
            ItemStack result = r.resultItem();
            if (result != null && result.hasItemMeta()) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    String name = plainText(meta.displayName()).toLowerCase();
                    if (name.contains(q)) {
                        results.add(r);
                    }
                }
            }
        }

        int maxResults = plugin.getConfig().getInt("search.max-results", 45);
        if (results.size() > maxResults) {
            results = results.subList(0, maxResults);
        }

        if (results.isEmpty()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%query%", query);
            player.sendMessage(messages.getMessage("search-no-results", true, ph));
            return;
        }

        openSearchResultMenu(player, query, results);
    }

    /**
     * Open the search results menu.
     */
    private void openSearchResultMenu(Player player, String query, List<CraftRecipe> results) {
        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%query%", query);
        titlePh.put("%count%", String.valueOf(results.size()));
        Component title = messages.getMessage("search-title-with-count", titlePh);

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                title);

        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 54, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        // Recipe icons (slot 0-44)
        int maxSlots = getItemsPerPage();
        int slot = 0;
        for (CraftRecipe recipe : results) {
            if (slot >= maxSlots) break;
            ItemStack icon = buildRecipeIcon(player, recipe);
            inv.setItem(slot, icon);
            slot++;
        }

        // Slot 49: back to main menu
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(49, createButton(parseMaterial(backMat, Material.ARROW),
                "category-back-to-main", List.of(),
                "main", "none"));

        player.openInventory(inv);
    }

    // =====================================================================
    //  ACHIEVEMENTS MENU
    // =====================================================================

    public void openAchievementsMenu(Player player) {
        if (achievementManager == null || !achievementManager.isEnabled()) {
            player.sendMessage(messages.getMessage("achievements-disabled-message", true));
            return;
        }

        Map<String, AchievementManager.Achievement> achievements = achievementManager.getAchievements();
        int total = achievements.size();
        int unlocked = achievementManager.getUnlockedCount(player.getUniqueId());

        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%unlocked%", String.valueOf(unlocked));
        titlePh.put("%total%", String.valueOf(total));

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("achievements-title", titlePh));

        String borderMat = plugin.getConfig().getString("gui.category-border", "BLACK_STAINED_GLASS_PANE");
        fillBorders(inv, 54, parseMaterial(borderMat, Material.BLACK_STAINED_GLASS_PANE));

        Set<String> unlockedIds = achievementManager.getUnlocked(player.getUniqueId());
        String lockedMat = plugin.getConfig().getString("gui.buttons.achievement-locked", "GRAY_DYE");
        String unlockedMat = plugin.getConfig().getString("gui.buttons.achievement-unlocked", "GOLD_INGOT");

        int slot = 0;
        for (AchievementManager.Achievement ach : achievements.values()) {
            boolean isUnlocked = unlockedIds.contains(ach.id);
            Material mat = parseMaterial(
                    isUnlocked ? unlockedMat : lockedMat,
                    isUnlocked ? Material.GOLD_INGOT : Material.GRAY_DYE);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            // Faz 0.7.0: tier prefix in name — use getMessageRaw to preserve MiniMessage tags
            String tierKey = "achievement-tier-" + ach.tier;
            String tierPrefix = messages.getPlainMessage(tierKey);
            String achName = messages.getPlainMessage(ach.nameKey);
            String displayName = (tierPrefix != null && !tierPrefix.isEmpty())
                    ? tierPrefix + " " + achName : achName;

            String nameKey = isUnlocked ? "achievement-unlocked-name" : "achievement-locked-name";
            Map<String, String> namePh = new HashMap<>();
            namePh.put("%name%", displayName);
            // Use getMessageRaw — tier prefixes contain MiniMessage tags that should NOT be escaped
            meta.displayName(messages.getMessageRaw(nameKey, namePh));

            Map<String, String> descPh = new HashMap<>();
            descPh.put("%description%", messages.getPlainMessage(ach.descKey));

            List<Component> lore = new ArrayList<>();
            lore.add(messages.getMessage(isUnlocked
                    ? "achievement-unlocked-lore-1"
                    : "achievement-locked-lore-1", descPh));

            // Faz 0.7.0: progress line (only for locked achievements with progress)
            if (!isUnlocked) {
                String progressStr = achievementManager.getProgressString(player.getUniqueId(), ach);
                if (!progressStr.equals("—")) {
                    String[] parts = progressStr.split("/");
                    if (parts.length == 2) {
                        Map<String, String> progPh = new HashMap<>();
                        progPh.put("%current%", parts[0]);
                        progPh.put("%target%", parts[1]);
                        lore.add(messages.getMessage("achievement-progress", progPh));
                    }
                }
            }

            lore.add(Component.empty());
            lore.add(messages.getMessage(isUnlocked
                    ? "achievement-unlocked-lore-3"
                    : "achievement-locked-lore-3"));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
            if (slot >= 45) break; // border is at row 0 and 5
        }

        // Slot 49: Back button
        String backMat = plugin.getConfig().getString("gui.buttons.back", "ARROW");
        inv.setItem(49, createButton(parseMaterial(backMat, Material.ARROW),
                "category-back-to-main", List.of(),
                "main", "none"));

        player.openInventory(inv);
    }

    /**
     * Format a timestamp as "X seconds/minutes/hours/days ago".
     * Returns "never" if timestamp is 0.
     */
    private String formatTimeAgo(long timestampMs) {
        if (timestampMs == 0L) {
            return messages.getPlainMessage("time-never");
        }
        long secondsAgo = (System.currentTimeMillis() - timestampMs) / 1000L;
        Map<String, String> ph = new HashMap<>();
        ph.put("%count%", String.valueOf(secondsAgo));
        if (secondsAgo < 5) {
            return messages.getPlainMessage("time-just-now");
        } else if (secondsAgo < 60) {
            return messages.getPlainMessage("time-seconds-ago", ph);
        } else if (secondsAgo < 3600) {
            ph.put("%count%", String.valueOf(secondsAgo / 60));
            return messages.getPlainMessage("time-minutes-ago", ph);
        } else if (secondsAgo < 86400) {
            ph.put("%count%", String.valueOf(secondsAgo / 3600));
            return messages.getPlainMessage("time-hours-ago", ph);
        } else {
            ph.put("%count%", String.valueOf(secondsAgo / 86400));
            return messages.getPlainMessage("time-days-ago", ph);
        }
    }

    // =====================================================================
    //  Sounds + Particles — Faz 0.2.0
    // =====================================================================

    /**
     * Play a craft-success sound. Tier-specific sounds come from config.yml's
     * tier-sounds section (matched by result item's custom-model-data).
     * Falls back to sounds.craft-success.
     */
    private void playCraftSound(Player player, CraftRecipe recipe) {
        if (!NotificationHelper.shouldPlayCraftSuccessSound(plugin)) return;
        try {
            Sound sound = pickTierSound(recipe);
            if (sound == null) {
                String defaultName = plugin.getConfig().getString("sounds.craft-success", "BLOCK_ANVIL_USE");
                sound = parseSound(defaultName, Sound.BLOCK_ANVIL_USE);
            }
            player.playSound(player, sound, 0.7f, 1.2f);

            // Bulk bonus chime
            int bulkCap = getBulkCraftCap();
            if (bulkCap >= 10) {
                String bonusName = plugin.getConfig().getString("sounds.bulk-craft-bonus", "UI_TOAST_CHALLENGE_COMPLETE");
                Sound bonusSound = parseSound(bonusName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                player.playSound(player, bonusSound, 0.5f, 1.0f);
            }
        } catch (Exception ignored) {
        }
    }

    private void playFailSound(Player player) {
        if (!NotificationHelper.shouldPlayCraftFailSound(plugin)) return;
        try {
            String name = plugin.getConfig().getString("sounds.craft-fail", "ENTITY_VILLAGER_NO");
            Sound sound = parseSound(name, Sound.ENTITY_VILLAGER_NO);
            player.playSound(player, sound, 0.5f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    /**
     * Look up the tier-specific sound for a recipe from config.yml's tier-sounds section.
     */
    private Sound pickTierSound(CraftRecipe recipe) {
        ItemStack result = recipe.resultItem();
        if (result == null || !result.hasItemMeta()) return null;
        int cmd = getCmd(result);
        if (cmd == 0) return null;

        ConfigurationSection tierSounds = plugin.getConfig().getConfigurationSection("tier-sounds");
        if (tierSounds == null) return null;

        String soundName = tierSounds.getString(String.valueOf(cmd));
        if (soundName == null) return null;
        return parseSound(soundName, null);
    }

    /**
     * Spawn success particles at the player's location. Count scales with
     * crafted items (config: particles.bulk-multiplier).
     */
    private void spawnSuccessParticles(Player player, int craftedCount) {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) return;

        try {
            String particleName = plugin.getConfig().getString("particles.success-particle", "HAPPY_VILLAGER");
            Particle particle = parseParticle(particleName, Particle.HAPPY_VILLAGER);
            int baseCount = plugin.getConfig().getInt("particles.success-count", 5);
            double radius = plugin.getConfig().getDouble("particles.success-radius", 0.5);
            double bulkMultiplier = plugin.getConfig().getDouble("particles.bulk-multiplier", 0.2);

            int total = baseCount + (int) (craftedCount * bulkMultiplier);
            if (total > 100) total = 100; // hard cap

            Location loc = player.getLocation().add(0, 1, 0); // chest height
            player.getWorld().spawnParticle(particle, loc, total,
                    radius, radius, radius, 0.05);
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    //  Cleanup
    // =====================================================================

    public void clearCooldown(UUID uuid) {
        craftCooldowns.remove(uuid);
        lastCraftTimestamps.remove(uuid);
    }

    public void closeAllGUIs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.getOpenInventory() == null
                        || player.getOpenInventory().getTopInventory() == null) {
                    continue;
                }
                if (player.getOpenInventory().getTopInventory().getHolder()
                        instanceof CraftingGUIHolder) {
                    player.closeInventory();
                    player.sendMessage(messages.getMessage("gui-closed-reload", true));
                }
            } catch (Exception ignored) {
            }
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private boolean isPlayerViewingCraftingGUI(Player player) {
        return player.isOnline()
                && player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder()
                        instanceof CraftingGUIHolder;
    }

    private ItemStack createButton(Material mat, String nameKey, List<String> loreKeys,
                                     String action, String value) {
        return createButton(mat, nameKey, loreKeys, action, null, value);
    }

    /**
     * Create a button with placeholder support for the display name.
     * Faz 0.4.0: needed for filter/sort buttons that show the current mode.
     *
     * @param namePlaceholders  placeholders applied to the name key only
     *                          (lore keys are rendered without placeholders)
     */
    private ItemStack createButton(Material mat, String nameKey, List<String> loreKeys,
                                     String action, Map<String, String> namePlaceholders,
                                     String value) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (namePlaceholders != null && !namePlaceholders.isEmpty()) {
            meta.displayName(messages.getMessage(nameKey, namePlaceholders));
        } else {
            meta.displayName(messages.getMessage(nameKey));
        }
        if (loreKeys != null && !loreKeys.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String key : loreKeys) {
                lore.add(messages.getMessage(key));
            }
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory inv, int size, Material paneMat) {
        ItemStack pane = new ItemStack(paneMat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, pane);
            }
        }
    }

    private int countInInventory(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack inv : player.getInventory().getContents()) {
            if (inv == null || inv.getType().isAir()) continue;
            if (matchesTemplate(inv, template)) {
                total += inv.getAmount();
            }
        }
        return total;
    }

    @SuppressWarnings("deprecation")
    private boolean matchesTemplate(ItemStack inv, ItemStack template) {
        if (inv.getType() != template.getType()) return false;
        return getCmd(inv) == getCmd(template);
    }

    @SuppressWarnings("deprecation")
    private int getCmd(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta m = item.getItemMeta();
        if (m == null || !m.hasCustomModelData()) return 0;
        try {
            Integer cmd = m.getCustomModelData();
            return cmd != null ? cmd : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get the display name of an item as a MiniMessage-compatible string.
     * If the item has a custom display name, returns it as a MiniMessage string
     * (preserving color tags). Otherwise returns the material name.
     */
    private String describeItem(ItemStack item) {
        if (item == null) return "???";
        if (item.hasItemMeta()) {
            ItemMeta m = item.getItemMeta();
            if (m != null && m.hasDisplayName()) {
                // Convert Component back to MiniMessage string to preserve colors
                return miniMessage.serialize(m.displayName());
            }
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private String plainText(Component component) {
        if (component == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        if (m == null) {
            plugin.getLogger().warning("Invalid material in config: " + name + " — using " + fallback);
            return fallback;
        }
        return m;
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null) return fallback;
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound in config: " + name + " — using " + fallback);
            return fallback;
        }
    }

    private Particle parseParticle(String name, Particle fallback) {
        if (name == null) return fallback;
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle in config: " + name + " — using " + fallback);
            return fallback;
        }
    }
}
