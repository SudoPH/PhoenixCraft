package com.phoenix.craft.gui;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.achievements.AchievementManager;
import com.phoenix.craft.blackmarket.BlackMarketManager;
import com.phoenix.craft.config.FeatureFlags;
import com.phoenix.craft.discovery.DiscoveryManager;
import com.phoenix.craft.economy.EconomyManager;
import com.phoenix.craft.favorites.FavoritesManager;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
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
    private final FavoritesManager favoritesManager;

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
                               DiscoveryManager discoveryManager, BlackMarketManager blackMarketManager,
                               FavoritesManager favoritesManager) {
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
        this.favoritesManager = favoritesManager;
        this.actionKey = new NamespacedKey(plugin, "craft_action");
        this.valueKey = new NamespacedKey(plugin, "craft_value");

        // ─── v1.2.0 — Load slot indexes from config.yml ───
        // Every value has a hardcoded default fallback so a missing or
        // malformed `gui.slots:` section never crashes the plugin.
        this.contentSlots = loadContentSlots();
        this.contentSlotsPerPage = contentSlots.length;
        this.slotInfoLeft    = loadSlot("gui.slots.info-left",    DEFAULT_SLOT_INFO_LEFT);
        this.slotPrevPage    = loadSlot("gui.slots.prev-page",    DEFAULT_SLOT_PREV_PAGE);
        this.slotSearch      = loadSlot("gui.slots.search",       DEFAULT_SLOT_SEARCH);
        this.slotFilter      = loadSlot("gui.slots.filter",       DEFAULT_SLOT_FILTER);
        this.slotBackClose   = loadSlot("gui.slots.back-close",   DEFAULT_SLOT_BACK_CLOSE);
        this.slotSort        = loadSlot("gui.slots.sort",         DEFAULT_SLOT_SORT);
        this.slotFavorite    = loadSlot("gui.slots.favorite",     DEFAULT_SLOT_FAVORITE);
        this.slotNextPage    = loadSlot("gui.slots.next-page",    DEFAULT_SLOT_NEXT_PAGE);
        this.slotInfoRight   = loadSlot("gui.slots.info-right",   DEFAULT_SLOT_INFO_RIGHT);
        this.slotResultPreview    = loadSlot("gui.recipe-detail.slots.result-preview",   13);
        this.slotIngredientsList  = loadSlot("gui.recipe-detail.slots.ingredients-list", 11);
        this.slotCraftSingle      = loadSlot("gui.recipe-detail.slots.craft-single",     14);
        this.slotCraftClose       = loadSlot("gui.recipe-detail.slots.craft-close",      15);
        this.slotCraftMax         = loadSlot("gui.recipe-detail.slots.craft-max",        22);

        // Log the resolved layout so admins can verify their config took effect.
        plugin.getLogger().info("GUI layout loaded: " + contentSlotsPerPage + " content slots, "
                + "action-bar=[" + slotPrevPage + "◀," + slotSearch + "🔍,"
                + slotFilter + "FilterWhere," + slotBackClose + "✖,"
                + slotSort + "SortWhere," + slotFavorite + "★,"
                + slotNextPage + "▶]");
    }

    /**
     * Read a single slot index from config.yml with validation.
     * Falls back to {@code def} if the value is missing, out of [0,53]
     * range, or the config read throws.
     *
     * @since v1.2.0
     */
    private int loadSlot(String path, int def) {
        try {
            int v = plugin.getConfig().getInt(path, def);
            if (v < 0 || v > 53) {
                plugin.getLogger().warning("Slot '" + path + "'=" + v
                        + " is out of range [0,53] — using default " + def + ".");
                return def;
            }
            return v;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed reading slot '" + path + "': "
                    + t.getMessage() + " — using default " + def + ".");
            return def;
        }
    }

    /**
     * Read the content-slot list from config.yml with full validation.
     * Skips out-of-range slots, deduplicates, and caps at 28 entries.
     * Falls back to {@link #DEFAULT_CONTENT_SLOTS} if anything goes wrong.
     *
     * @since v1.2.0
     */
    private int[] loadContentSlots() {
        try {
            java.util.List<Integer> raw = plugin.getConfig().getIntegerList("gui.slots.content-slots");
            if (raw == null || raw.isEmpty()) return DEFAULT_CONTENT_SLOTS.clone();
            java.util.List<Integer> cleaned = new java.util.ArrayList<>();
            for (Integer s : raw) {
                if (s == null) continue;
                if (s < 0 || s > 53) {
                    plugin.getLogger().warning("Content slot " + s + " out of range [0,53] — skipped.");
                    continue;
                }
                if (cleaned.contains(s)) {
                    plugin.getLogger().warning("Duplicate content slot " + s + " — skipped.");
                    continue;
                }
                cleaned.add(s);
                if (cleaned.size() >= 28) {
                    plugin.getLogger().warning("Content slot list capped at 28 entries — extras ignored.");
                    break;
                }
            }
            if (cleaned.isEmpty()) {
                plugin.getLogger().warning("gui.slots.content-slots is empty — using default layout.");
                return DEFAULT_CONTENT_SLOTS.clone();
            }
            int[] result = new int[cleaned.size()];
            for (int i = 0; i < cleaned.size(); i++) result[i] = cleaned.get(i);
            return result;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed loading content-slots: "
                    + t.getMessage() + " — using default layout.");
            return DEFAULT_CONTENT_SLOTS.clone();
        }
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getValueKey() {
        return valueKey;
    }

    private long getCraftCooldownMs() {
        // v1.2.0 — try the new crafting-modifiers section first, fall back to
        // the legacy settings.craft-cooldown-ms key, then to the hardcoded default.
        try {
            long v = plugin.getConfig().getLong("crafting-modifiers.craft-cooldown-ms",
                    plugin.getConfig().getLong("settings.craft-cooldown-ms", 150L));
            return Math.max(0, v);
        } catch (Throwable t) {
            return 150L;
        }
    }

    private int getBulkCraftCap() {
        // v1.2.0 — try crafting-modifiers.bulk-craft-cap → settings.bulk-craft-cap → default.
        try {
            int v = plugin.getConfig().getInt("crafting-modifiers.bulk-craft-cap",
                    plugin.getConfig().getInt("settings.bulk-craft-cap", 1000));
            return Math.max(1, v);
        } catch (Throwable t) {
            return 1000;
        }
    }

    private int getItemsPerPage() {
        int configured = plugin.getConfig().getInt("pagination.items-per-page", contentSlotsPerPage);
        return Math.max(1, Math.min(configured, contentSlotsPerPage));
    }

    // =====================================================================
    //  LAYOUT CONSTANTS — strict 54-slot blueprint (single source of truth)
    // =====================================================================
    //  v1.1.0 — Standardized Row 6 navigation across EVERY menu:
    //
    //    Slot 45: info / decoration         (GRAY pane on menus without info)
    //    Slot 46: ◀ PREVIOUS PAGE           (ARROW or GRAY pane if disabled)
    //    Slot 47: SEARCH                    (category menus only)
    //    Slot 48: FILTER                    (category menus only)
    //    Slot 49: ✖ CLOSE / BACK BARRIER    (always — centre, never moves)
    //    Slot 50: SORT                      (category menus only)
    //    Slot 51: ★ FAVORITE STAR           (toggle on detail; opens favorites
    //                                        GUI from main menu / list menus)
    //    Slot 52: ▶ NEXT PAGE               (ARROW or GRAY pane if disabled)
    //    Slot 53: refresh / decoration      (GRAY pane on menus without refresh)
    //
    //  Row 1 (0-8):   Outer border — GRAY Stained Glass (always)
    //  Rows 2-5:      Content area — 4 rows × 7 columns = 28 slots
    //                 (slots 10-43, skipping the left/right GRAY columns)
    //                 The remaining border-column slots on rows 2-5 are
    //                 filled with BLACK Stained Glass padding so there
    //                 are NO stray/floating items anywhere in the GUI.
    //  Row 6 (45-53): Action bar — see above.
    // =====================================================================
    //  v1.2.0 — Slot indexes are now CONFIG-DRIVEN via the `gui.slots`
    //  section in config.yml. The static finals below are DEFAULTS used
    //  only when the config is missing/malformed. At construction time
    //  the manager reads the config and populates the instance fields
    //  (contentSlots, slotPrevPage, slotNextPage, etc.) — all rendering
    //  code uses the instance fields, never the static finals directly.
    // =====================================================================
    private static final int[] DEFAULT_CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int DEFAULT_SLOT_INFO_LEFT  = 45;
    private static final int DEFAULT_SLOT_PREV_PAGE  = 46;
    private static final int DEFAULT_SLOT_SEARCH     = 47;
    private static final int DEFAULT_SLOT_FILTER     = 48;
    private static final int DEFAULT_SLOT_BACK_CLOSE = 49;
    private static final int DEFAULT_SLOT_SORT       = 50;
    private static final int DEFAULT_SLOT_FAVORITE   = 51;
    private static final int DEFAULT_SLOT_NEXT_PAGE  = 52;
    private static final int DEFAULT_SLOT_INFO_RIGHT = 53;

    // ─── Instance fields — populated from config.yml at construction ───
    private final int[] contentSlots;
    private final int contentSlotsPerPage;
    private final int slotInfoLeft;
    private final int slotPrevPage;
    private final int slotSearch;
    private final int slotFilter;
    private final int slotBackClose;
    private final int slotSort;
    private final int slotFavorite;
    private final int slotNextPage;
    private final int slotInfoRight;

    // Recipe-detail slots — also config-driven
    private final int slotResultPreview;
    private final int slotIngredientsList;
    private final int slotCraftSingle;
    private final int slotCraftClose;
    private final int slotCraftMax;

    // ─── Border + padding materials (defaults; the actual values come from
    //     gui.outer-border / gui.inner-padding in config.yml, read lazily by
    //     fillChrome54 at render time so a /pcraft reload picks them up) ───
    private static final String DEFAULT_OUTER_BORDER = "GRAY_STAINED_GLASS_PANE";
    private static final String DEFAULT_INNER_PADDING = "BLACK_STAINED_GLASS_PANE";

    // ─── Menu-type identifiers used by the generic pagination helpers ───
    //   These are encoded into button PDC values so the listener knows
    //   which openXMenu(...) method to call when the arrow is clicked.
    public static final String MENU_CATEGORY     = "category";
    public static final String MENU_RECIPES      = "recipes";
    public static final String MENU_ACHIEVEMENTS = "achievements";
    public static final String MENU_BLACKMARKET  = "blackmarket";
    public static final String MENU_SEARCH       = "search";
    public static final String MENU_FAVORITES    = "favorites";

    private int populateContent(Inventory inv, List<ItemStack> items) {
        int placed = 0;
        for (int i = 0; i < contentSlots.length && i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item == null) continue;
            inv.setItem(contentSlots[i], item);
            placed++;
        }
        return placed;
    }

    /**
     * Fill the standardized 54-slot chrome:
     *   • Row 1 (0-8):      outer GRAY border
     *   • Rows 2-5 cols 0/8: outer GRAY border (left + right edges)
     *   • Rows 2-5 cols 1-7: inner BLACK padding — INCLUDING unused content
     *     slots, so the GUI never has empty/null slots that look "spilled".
     *   • Row 6 (45-53):    outer GRAY border (action-bar slots overlay this)
     *
     * <p>v1.1.1 — CRITICAL FIX: Previously this method skipped content slots
     * (10-43), leaving them as null ItemStacks when the caller didn't place
     * an icon there. That made the main menu look like items were "spilling
     * into padding rows" because half the content area was empty. Now we
     * fill EVERY non-action-bar slot — the caller overwrites the ones they
     * want with icons, and the rest stay as clean BLACK padding.</p>
     *
     * <p>Action-bar slots (45-53) are still reserved for the caller —
     * {@link #renderActionBar54} fills them explicitly.</p>
     */
    private void fillChrome54(Inventory inv) {
        Material outerMat = parseMaterial(
                plugin.getConfig().getString("gui.outer-border", DEFAULT_OUTER_BORDER),
                Material.GRAY_STAINED_GLASS_PANE);
        Material innerMat = parseMaterial(
                plugin.getConfig().getString("gui.inner-padding", DEFAULT_INNER_PADDING),
                Material.BLACK_STAINED_GLASS_PANE);

        ItemStack outer = borderPane(outerMat);
        ItemStack inner = borderPane(innerMat);

        // Action-bar slots are reserved — renderActionBar54 fills them.
        // Everything else (border + content area) gets a pane here; the
        // caller overwrites specific content slots with icons afterwards.
        boolean[] isActionBar = new boolean[54];
        int[] actionBar = {slotInfoLeft, slotPrevPage, slotSearch, slotFilter,
                slotBackClose, slotSort, slotFavorite, slotNextPage, slotInfoRight};
        for (int s : actionBar) isActionBar[s] = true;

        for (int i = 0; i < 54; i++) {
            if (isActionBar[i]) continue; // renderActionBar54 fills these
            int row = i / 9;
            int col = i % 9;
            // Outer border = top row, bottom row, left col, right col.
            // Inner padding = everything else (including unused content slots).
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inv.setItem(i, outer);
            } else {
                inv.setItem(i, inner);
            }
        }
    }

    /**
     * Build a "blank" glass pane for borders/padding — empty name, no lore,
     * HIDE_ENCHANTS + HIDE_ATTRIBUTES so it shows no tooltip at all.
     */
    private ItemStack borderPane(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Generic previous-page button. The {@code menuType} tells the listener
     * which menu to reopen, and {@code context} carries any extra info
     * needed (e.g. category id for category menus, search query for search).
     *
     * <p>PDC value format: {@code menuType:context:pageNumber}. The listener
     * splits on ":" and dispatches to the right open method.</p>
     */
    private ItemStack buildPrevPageButton(String menuType, String context,
                                            int currentPage, int totalPages) {
        // Always render — keeps the action bar symmetric on page 1/1
        if (currentPage > 0) {
            String arrowMat = plugin.getConfig().getString("gui.buttons.prev-page", "ARROW");
            return createButton(parseMaterial(arrowMat, Material.ARROW),
                    "category-prev-page", List.of(),
                    "paginate_prev", menuType + ":" + (context == null ? "" : context) + ":" + (currentPage - 1));
        }
        String disabledMat = plugin.getConfig().getString("gui.buttons.page-disabled", "GRAY_STAINED_GLASS_PANE");
        return createButton(parseMaterial(disabledMat, Material.GRAY_STAINED_GLASS_PANE),
                "category-prev-page-disabled", List.of(),
                "none", "none");
    }

    private ItemStack buildNextPageButton(String menuType, String context,
                                            int currentPage, int totalPages) {
        if (currentPage < totalPages - 1) {
            String arrowMat = plugin.getConfig().getString("gui.buttons.next-page", "ARROW");
            return createButton(parseMaterial(arrowMat, Material.ARROW),
                    "category-next-page", List.of(),
                    "paginate_next", menuType + ":" + (context == null ? "" : context) + ":" + (currentPage + 1));
        }
        String disabledMat = plugin.getConfig().getString("gui.buttons.page-disabled", "GRAY_STAINED_GLASS_PANE");
        return createButton(parseMaterial(disabledMat, Material.GRAY_STAINED_GLASS_PANE),
                "category-next-page-disabled", List.of(),
                "none", "none");
    }

    /**
     * Render the standardized action bar (Row 6) into a 54-slot inventory.
     * Caller passes in the menu-type + context so pagination arrows know
     * where to go, plus optional flags for whether search/filter/sort/fav
     * buttons should be shown (some menus don't have those concepts).
     *
     * <p>v1.1.0: Honors the {@code features.gui.*} sub-flags from config.yml.
     * If a flag is false, the corresponding button is replaced with a black
     * padding pane — no tooltip, no click handler.</p>
     *
     * <p>v1.1.1: New {@code showSearchOnly} flag for the main dashboard —
     * renders ONLY the Search button at slot 47 (no Filter/Sort). This locks
     * Search into the action bar per the user's spec, instead of letting it
     * float in the content area.</p>
     *
     * @param showSearchFilterSort  true on category menus (renders Search +
     *                              Filter + Sort at slots 47/48/50)
     * @param showSearchOnly        true on the main menu (renders ONLY Search
     *                              at slot 47, leaves Filter/Sort as padding)
     * @param favoriteRecipeId      non-null on recipe detail (renders the
     *                              ★ toggle button); null on list menus
     *                              (renders the "open favorites GUI" button)
     */
    private void renderActionBar54(Inventory inv, Player player,
                                     String menuType, String context,
                                     int currentPage, int totalPages,
                                     boolean showSearchFilterSort,
                                     boolean showSearchOnly,
                                     String favoriteRecipeId) {
        // Determine which GUI affordances are enabled.
        boolean paginationEnabled = FeatureFlags.isGuiEnabled(plugin, "pagination-arrows");
        boolean favoriteStarEnabled = FeatureFlags.isGuiEnabled(plugin, "favorite-star");
        boolean searchBtnEnabled = FeatureFlags.isGuiEnabled(plugin, "search-button")
                && plugin.getConfig().getBoolean("search.enabled", true);
        boolean filterBtnEnabled = FeatureFlags.isGuiEnabled(plugin, "filter-button")
                && plugin.getConfig().getBoolean("filters.enabled", true);
        boolean sortBtnEnabled = FeatureFlags.isGuiEnabled(plugin, "sort-button")
                && plugin.getConfig().getBoolean("sort.enabled", true);

        // Black padding pane used to fill disabled slots (no tooltip).
        Material innerMat = parseMaterial(
                plugin.getConfig().getString("gui.inner-padding", DEFAULT_INNER_PADDING),
                Material.BLACK_STAINED_GLASS_PANE);
        ItemStack paddingPane = borderPane(innerMat);

        // ─── Slot 46: ◀ PREV / Slot 52: ▶ NEXT ───
        //   Always rendered — symmetric layout. Disabled (gray pane) when no
        //   previous/next page OR when features.gui.pagination-arrows is false.
        if (paginationEnabled) {
            inv.setItem(slotPrevPage, buildPrevPageButton(menuType, context, currentPage, totalPages));
            inv.setItem(slotNextPage, buildNextPageButton(menuType, context, currentPage, totalPages));
        } else {
            inv.setItem(slotPrevPage, paddingPane);
            inv.setItem(slotNextPage, paddingPane);
        }

        // ─── Slot 47: SEARCH ───
        //   v1.1.1 — Locked here on the main menu too (per user spec).
        //   Rendered when EITHER showSearchFilterSort OR showSearchOnly is true
        //   (and the search-button GUI flag + search.enabled config are both on).
        boolean wantSearch = (showSearchFilterSort || showSearchOnly) && searchBtnEnabled;
        if (wantSearch) {
            // On the main menu we use the "main-menu-search-name" key so the
            // tooltip matches the dashboard's visual language; on category
            // menus we keep the legacy "category-search" key for backward compat.
            String searchNameKey = showSearchOnly ? "main-menu-search-name" : "category-search";
            List<String> searchLoreKeys = showSearchOnly
                    ? List.of("main-menu-search-lore-1")
                    : List.of("category-search-lore");
            String searchMat = plugin.getConfig().getString("gui.buttons.search", "COMPASS");
            inv.setItem(slotSearch, createButton(parseMaterial(searchMat, Material.COMPASS),
                    searchNameKey, searchLoreKeys,
                    "search", context != null ? context : "all"));
        } else {
            inv.setItem(slotSearch, paddingPane);
        }

        // ─── Slot 48: FILTER / Slot 50: SORT ───
        //   Only on category menus (showSearchFilterSort=true). On the main
        //   menu (showSearchOnly=true) and everywhere else, these slots are
        //   clean BLACK padding — nothing floats.
        if (showSearchFilterSort) {
            if (filterBtnEnabled) {
                inv.setItem(slotFilter, buildFilterButton(player, context));
            } else {
                inv.setItem(slotFilter, paddingPane);
            }
            if (sortBtnEnabled) {
                inv.setItem(slotSort, buildSortButton(player, context));
            } else {
                inv.setItem(slotSort, paddingPane);
            }
        } else {
            inv.setItem(slotFilter, paddingPane);
            inv.setItem(slotSort, paddingPane);
        }

        // ─── Slot 49: ✖ CLOSE / BACK BARRIER (centre, ALWAYS rendered) ───
        //   This is the user's primary escape hatch — it must never be empty.
        String backMat = plugin.getConfig().getString("gui.buttons.back", "BARRIER");
        inv.setItem(slotBackClose, createButton(parseMaterial(backMat, Material.BARRIER),
                "category-back-to-main", List.of(),
                "main", "none"));

        // ─── Slot 51: ★ FAVORITE STAR ───
        //   • On recipe DETAIL: toggles favorite for that specific recipe.
        //   • On list/main menus: opens the Favorites GUI (list of favorites).
        //   • Disabled entirely if features.gui.favorite-star.enabled=false
        //     OR if the favorites system itself is disabled.
        if (!favoriteStarEnabled || favoritesManager == null || !favoritesManager.isEnabled()) {
            inv.setItem(slotFavorite, paddingPane);
        } else if (favoriteRecipeId != null) {
            inv.setItem(slotFavorite, buildFavoriteButton(player, favoriteRecipeId));
        } else if (FeatureFlags.isGuiEnabled(plugin, "favorites-gui")) {
            // Show the "open favorites list" button (golden nether star)
            String favMat = plugin.getConfig().getString("gui.buttons.favorite-on", "NETHER_STAR");
            ItemStack favBtn = createButton(parseMaterial(favMat, Material.NETHER_STAR),
                    "main-menu-favorites-name",
                    List.of("main-menu-favorites-lore-1", "main-menu-favorites-lore-2"),
                    "open_favorites", "none");
            // Add enchant glint to make it stand out
            ItemMeta m = favBtn.getItemMeta();
            if (m != null) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                favBtn.setItemMeta(m);
            }
            inv.setItem(slotFavorite, favBtn);
        } else {
            inv.setItem(slotFavorite, paddingPane);
        }
    }

    /** Legacy overload — keep old callers compiling. Equivalent to showSearchOnly=false. */
    private void renderActionBar54(Inventory inv, Player player,
                                     String menuType, String context,
                                     int currentPage, int totalPages,
                                     boolean showSearchFilterSort,
                                     String favoriteRecipeId) {
        renderActionBar54(inv, player, menuType, context, currentPage, totalPages,
                showSearchFilterSort, false, favoriteRecipeId);
    }

    // =====================================================================
    //  MAIN MENU
    // =====================================================================

    public void openMainMenu(Player player) {
        // ─── v1.2.0 — Config-driven main dashboard ───
        //
        //   The icon layout is read from `gui.main-menu.icons` in config.yml.
        //   Server owners can rearrange icons, swap materials, change tooltip
        //   lang keys, or remove icons entirely by editing the config — no
        //   Java code changes required.
        //
        //   Each icon entry has: slot, material, name-key, lore-keys, action,
        //   value, and optional `feature` (gates visibility by a feature flag)
        //   and `dynamic` (fills placeholders at render time).
        //
        //   The chrome (gray border + black padding) and the action bar
        //   (slots 46/49/51/52) are still rendered by fillChrome54 +
        //   renderActionBar54, so the layout is always symmetric regardless
        //   of which icons are configured.
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("main-menu-title"));

        // Fill the standardized chrome (gray border + black padding, no stray items).
        fillChrome54(inv);

        // ─── Render config-driven icons ───
        renderConfigDrivenMainMenuIcons(inv, player);

        // ─── Standardized action bar (Row 6) ───
        //   Slot 46: ◀ PREV (disabled — main menu is page 1/1)
        //   Slot 47: 🔍 SEARCH (locked here per user spec — v1.1.1)
        //   Slot 49: ✖ CLOSE BARRIER (centre, locked)
        //   Slot 51: ★ FAVORITES (locked here per user spec)
        //   Slot 52: ▶ NEXT (disabled)
        renderActionBar54(inv, player, MENU_CATEGORY, null,
                0, 1, false, true, null);

        player.openInventory(inv);
    }

    /**
     * Iterate the {@code gui.main-menu.icons} config list and render each
     * icon at its configured slot. Skips icons whose {@code feature} flag
     * is disabled, or whose slot is out of range / collides with an
     * action-bar slot.
     *
     * <p>v1.2.0 — every config read is wrapped in try/catch so a malformed
     * icon entry (missing material, invalid slot, etc.) is skipped with a
     * console warning instead of crashing the entire main-menu render.</p>
     *
     * @since v1.2.0
     */
    private void renderConfigDrivenMainMenuIcons(Inventory inv, Player player) {
        java.util.List<java.util.Map<?, ?>> icons;
        try {
            icons = plugin.getConfig().getMapList("gui.main-menu.icons");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed reading gui.main-menu.icons: "
                    + t.getMessage() + " — main menu will show no icons.");
            return;
        }
        if (icons == null || icons.isEmpty()) {
            // No icon list configured — fall back to the hardcoded defaults
            // so the main menu is never empty.
            renderDefaultMainMenuIcons(inv, player);
            return;
        }

        // Build a set of action-bar slots so we don't overwrite them.
        java.util.Set<Integer> reserved = new java.util.HashSet<>();
        reserved.add(slotInfoLeft); reserved.add(slotPrevPage); reserved.add(slotSearch);
        reserved.add(slotFilter); reserved.add(slotBackClose); reserved.add(slotSort);
        reserved.add(slotFavorite); reserved.add(slotNextPage); reserved.add(slotInfoRight);

        for (int i = 0; i < icons.size(); i++) {
            java.util.Map<?, ?> entry = icons.get(i);
            if (entry == null) continue;
            try {
                Object slotObj = entry.get("slot");
                if (!(slotObj instanceof Integer)) {
                    plugin.getLogger().warning("gui.main-menu.icons[" + i + "]: missing/invalid 'slot' — skipping.");
                    continue;
                }
                int slot = (Integer) slotObj;
                if (slot < 0 || slot > 53 || reserved.contains(slot)) {
                    plugin.getLogger().warning("gui.main-menu.icons[" + i + "]: slot " + slot
                            + " is out of range or reserved (action-bar) — skipping.");
                    continue;
                }

                // Feature flag gate — skip if the named feature is disabled.
                String feature = entry.get("feature") instanceof String ? (String) entry.get("feature") : null;
                if (feature != null && !feature.isEmpty()
                        && !"info-icon".equals(feature)   // info-icon is always shown
                        && !FeatureFlags.isEnabled(plugin, feature)
                        && !FeatureFlags.isGuiEnabled(plugin, feature)) {
                    continue;
                }

                String material = entry.get("material") instanceof String ? (String) entry.get("material") : "STONE";
                String nameKey = entry.get("name-key") instanceof String ? (String) entry.get("name-key") : "main-menu-rods-name";
                java.util.List<String> loreKeys = new java.util.ArrayList<>();
                Object loreObj = entry.get("lore-keys");
                if (loreObj instanceof java.util.List<?> ll) {
                    for (Object o : ll) if (o instanceof String s) loreKeys.add(s);
                }
                String action = entry.get("action") instanceof String ? (String) entry.get("action") : "none";
                String value = entry.get("value") instanceof String ? (String) entry.get("value") : "none";
                boolean dynamic = Boolean.TRUE.equals(entry.get("dynamic"));

                ItemStack icon;
                if (dynamic) {
                    // The "info-icon" is dynamic — fill %xp%, %xp_required%, %level%.
                    Map<String, String> ph = new HashMap<>();
                    ph.put("%xp%", String.valueOf(fishApi.getCraftingXp(player.getUniqueId())));
                    ph.put("%xp_required%", String.valueOf(fishApi.getCraftingXpRequired(player.getUniqueId())));
                    ph.put("%level%", String.valueOf(fishApi.getCraftingLevel(player.getUniqueId())));
                    icon = createInfoButton(ph);
                } else {
                    icon = createButton(parseMaterial(material, Material.STONE),
                            nameKey, loreKeys, action, value);
                }
                inv.setItem(slot, icon);
            } catch (Throwable t) {
                plugin.getLogger().warning("gui.main-menu.icons[" + i + "]: render failed — "
                        + t.getMessage() + " — skipping.");
            }
        }
    }

    /**
     * Hardcoded fallback for when {@code gui.main-menu.icons} is missing or
     * empty. Renders the v1.1.1 default 3×2 central matrix.
     *
     * @since v1.2.0
     */
    private void renderDefaultMainMenuIcons(Inventory inv, Player player) {
        // RODS (slot 21)
        String rodsMat = plugin.getConfig().getString("gui.category-icons.rods", "FISHING_ROD");
        inv.setItem(21, createButton(parseMaterial(rodsMat, Material.FISHING_ROD),
                "main-menu-rods-name",
                List.of("main-menu-rods-lore-1", "main-menu-rods-lore-2"),
                "category", "rod"));
        // INFO (slot 22)
        Map<String, String> infoPh = new HashMap<>();
        infoPh.put("%xp%", String.valueOf(fishApi.getCraftingXp(player.getUniqueId())));
        infoPh.put("%xp_required%", String.valueOf(fishApi.getCraftingXpRequired(player.getUniqueId())));
        infoPh.put("%level%", String.valueOf(fishApi.getCraftingLevel(player.getUniqueId())));
        inv.setItem(22, createInfoButton(infoPh));
        // BAITS (slot 23)
        String baitsMat = plugin.getConfig().getString("gui.category-icons.baits", "STICK");
        inv.setItem(23, createButton(parseMaterial(baitsMat, Material.STICK),
                "main-menu-baits-name",
                List.of("main-menu-baits-lore-1", "main-menu-baits-lore-2"),
                "category", "bait"));
        // STATS (slot 30)
        if (statsManager != null && statsManager.isEnabled()) {
            String statsMat = plugin.getConfig().getString("gui.buttons.stats", "BOOK");
            inv.setItem(30, createButton(parseMaterial(statsMat, Material.BOOK),
                    "main-menu-stats-name",
                    List.of("main-menu-stats-lore-1", "main-menu-stats-lore-2"),
                    "open_stats", "none"));
        }
        // RECIPES (slot 31)
        if (recipeManager != null && recipeManager.isEnabled()) {
            String recipesMat = plugin.getConfig().getString("gui.buttons.recipes", "WRITTEN_BOOK");
            inv.setItem(31, createButton(parseMaterial(recipesMat, Material.WRITTEN_BOOK),
                    "main-menu-recipes-name",
                    List.of("main-menu-recipes-lore-1", "main-menu-recipes-lore-2"),
                    "open_recipes", "none"));
        }
        // ACHIEVEMENTS (slot 32)
        if (achievementManager != null && achievementManager.isEnabled()) {
            String achMat = plugin.getConfig().getString("gui.buttons.achievements", "GOLDEN_APPLE");
            inv.setItem(32, createButton(parseMaterial(achMat, Material.GOLDEN_APPLE),
                    "main-menu-achievements-name",
                    List.of("main-menu-achievements-lore-1", "main-menu-achievements-lore-2"),
                    "open_achievements", "none"));
        }
    }

    /**
     * Open the Favorites GUI — a paginated list of the player's favorited
     * recipes. Each entry is the recipe's result-item icon (with a star
     * marker in the lore), and clicking opens the recipe detail. Slot 51
     * shows the "remove-from-favorites" toggle on the recipe detail screen.
     *
     * @param player the viewing player
     * @since v1.1.0
     */
    public void openFavoritesMenu(Player player) {
        openFavoritesMenu(player, 0);
    }

    public void openFavoritesMenu(Player player, int page) {
        if (favoritesManager == null || !favoritesManager.isEnabled()) {
            player.sendMessage(messages.getMessage("favorite-disabled", true));
            return;
        }
        List<String> favIds = favoritesManager.getFavorites(player.getUniqueId());
        if (favIds.isEmpty()) {
            // Show the empty state in a 54-slot GUI for visual consistency.
            Inventory inv = Bukkit.createInventory(
                    new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                    54,
                    messages.getMessage("favorites-gui-title"));
            fillChrome54(inv);
            ItemStack placeholder = new ItemStack(parseMaterial(
                    plugin.getConfig().getString("gui.buttons.empty-category", "BARRIER"),
                    Material.BARRIER));
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.displayName(messages.getMessage("favorites-empty-name"));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(messages.getMessage("favorites-empty-lore-1"));
                lore.add(messages.getMessage("favorites-empty-lore-2"));
                lore.add(Component.empty());
                lore.add(messages.getMessage("favorites-gui-empty-lore"));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
                meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
                placeholder.setItemMeta(meta);
            }
            inv.setItem(22, placeholder); // center
            renderActionBar54(inv, player, MENU_FAVORITES, null, 0, 1, false, null);
            player.openInventory(inv);
            return;
        }

        // Resolve recipe objects for each favorite (skip recipes that no
        // longer exist in PhoenixFish — they're orphaned favorites).
        List<CraftRecipe> favRecipes = new ArrayList<>();
        for (String id : favIds) {
            CraftRecipe r = fishApi.getCraftingRecipe(id);
            if (r != null) favRecipes.add(r);
        }
        if (favRecipes.isEmpty()) {
            // All favorited recipes were removed from PhoenixFish — show empty
            openFavoritesMenu(player); // re-enter the empty-state branch
            return;
        }

        int itemsPerPage = getItemsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) favRecipes.size() / itemsPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("favorites-gui-title"));
        fillChrome54(inv);

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, favRecipes.size());
        List<ItemStack> pageItems = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            pageItems.add(buildFavoriteRecipeIcon(player, favRecipes.get(i)));
        }
        populateContent(inv, pageItems);

        renderActionBar54(inv, player, MENU_FAVORITES, null, page, totalPages, false, null);
        player.openInventory(inv);
    }

    /**
     * Build a recipe icon for the Favorites GUI — same look as the standard
     * recipe icon, but with an extra "★ Favorited" lore line at the top so
     * the player can tell which favorites they're looking at.
     */
    private ItemStack buildFavoriteRecipeIcon(Player player, CraftRecipe recipe) {
        ItemStack icon = buildRecipeIcon(player, recipe);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
        lore.add(0, messages.getMessage("favorite-lore-on-1"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
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

        // v1.1.0 — Standardized chrome (gray border + black padding, no stray items)
        fillChrome54(inv);

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, recipes.size());
        List<ItemStack> pageItems = new ArrayList<>(endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            pageItems.add(buildRecipeIcon(player, recipes.get(i)));
        }
        populateContent(inv, pageItems);

        // Standardized action bar (Row 6) — search/filter/sort enabled
        renderActionBar54(inv, player, MENU_CATEGORY, category, page, totalPages,
                true, null);

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

        // v1.1.0 — use 54-slot standardized chrome so the empty state matches
        // the rest of the GUI's visual language.
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.CATEGORY, category, 0),
                54,
                title);
        fillChrome54(inv);

        // Center placeholder (slot 22)
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
        inv.setItem(22, placeholder);

        // Standardized action bar — Back to main at slot 49
        renderActionBar54(inv, player, MENU_CATEGORY, category, 0, 1, false, null);

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

        // v1.1.0 — use 54-slot standardized chrome.
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.CATEGORY, category, 0),
                54,
                title);
        fillChrome54(inv);

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

        // Center placeholder (slot 22) — explains the filter excluded everything
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

            String reason = invokeCraftingUnavailableReason(fishApi);
            if (reason == null) {
                try {
                    if (!fishApi.isCraftingAvailable()) {
                        reason = "xp-system-disabled";
                    }
                } catch (Throwable ignored) {
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
        inv.setItem(22, placeholder);

        // Filter button at slot 11 — lets the player cycle the filter back
        inv.setItem(11, buildFilterButton(player, category));

        // Standardized action bar — Back to main at slot 49
        renderActionBar54(inv, player, MENU_CATEGORY, category, 0, 1, false, null);

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
     *
     * <p>v1.1.2 — NPE hardening: every PhoenixFish API return value is now
     * null-checked. If {@code recipe.resultItem()} returns null (corrupted
     * recipe definition, race during PhoenixFish reload, etc.), we substitute
     * a BARRIER placeholder so the GUI still renders and the player sees a
     * clear "broken recipe" indicator instead of a stack trace. Same for
     * null {@code recipe.ingredients()} and null {@code ing.item()}.</p>
     */
    private ItemStack buildRecipeIcon(Player player, CraftRecipe recipe) {
        ItemStack resultItem = recipe.resultItem();
        if (resultItem == null) {
            // Corrupted recipe — render a BARRIER so the slot isn't empty.
            plugin.getLogger().warning("Recipe '" + recipe.id()
                    + "' has null resultItem() — rendering BARRIER placeholder.");
            ItemStack broken = new ItemStack(Material.BARRIER);
            ItemMeta brokenMeta = broken.getItemMeta();
            if (brokenMeta != null) {
                brokenMeta.displayName(messages.getMessage("craft-recipe-unknown"));
                brokenMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
                brokenMeta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
                broken.setItemMeta(brokenMeta);
            }
            return broken;
        }
        ItemStack icon = resultItem.clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(messages.getMessage("recipe-ingredients-header"));

        boolean hasAllMaterials = true;
        List<CraftRecipe.Ingredient> ingredients = recipe.ingredients();
        if (ingredients == null) ingredients = List.of();   // defensive — null list
        for (CraftRecipe.Ingredient ing : ingredients) {
            if (ing == null) continue;                       // defensive — null entry
            ItemStack ingItem = ing.item();
            int owned = ingItem == null ? 0 : countInInventory(player, ingItem);
            int needed = ing.amount();
            boolean ok = owned >= needed;
            if (!ok) hasAllMaterials = false;

            Map<String, String> ph = new HashMap<>();
            ph.put("%color%", ok ? "<green>" : "<red>");
            ph.put("%name%", describeItem(ingItem));
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

        // ─── v1.1.0 — 54-slot recipe detail GUI ───
        //   Row 1 (0-8):    GRAY outer border
        //   Rows 2-5:       BLACK padding + result/ingredients/craft buttons
        //                   slot 11 = ingredients | slot 13 = result preview
        //                   slot 14 = CRAFT       | slot 15 = CRAFT & CLOSE
        //                   slot 22 = MAX CRAFT (only if maxCraftable > 1)
        //   Row 6 (45-53):  standardized action bar — slot 46 prev-disabled,
        //                   slot 49 BACK BARRIER, slot 51 FAVORITE toggle,
        //                   slot 52 next-disabled.
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.DETAIL,
                        recipe.category(), 0, recipe.id()),
                54,
                messages.getMessage("detail-title"));

        // v1.1.0 — Standardized chrome (gray border + black padding)
        fillChrome54(inv);

        // Result preview at slot 13
        // v1.1.2 — NPE guard: recipe.resultItem() may return null if the
        // PhoenixFish recipe definition is corrupted or being reloaded.
        ItemStack resultRaw = recipe.resultItem();
        ItemStack preview;
        if (resultRaw == null) {
            plugin.getLogger().warning("Recipe '" + recipe.id()
                    + "' has null resultItem() in openRecipeDetail — using BARRIER.");
            preview = new ItemStack(Material.BARRIER);
        } else {
            preview = resultRaw.clone();
        }
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            List<Component> lore = new ArrayList<>(previewMeta.lore() != null ? previewMeta.lore() : List.of());
            lore.add(Component.empty());
            lore.add(messages.getMessage("detail-result-lore"));
            previewMeta.lore(lore);
            preview.setItemMeta(previewMeta);
        }
        inv.setItem(slotResultPreview, preview);

        // Ingredients list — slot from gui.recipe-detail.slots.ingredients-list
        inv.setItem(slotIngredientsList, buildIngredientsItem(player, recipe));

        // Three craft buttons (Faz 0.2.0)
        boolean canCraft = fishApi.canCraft(player, recipe);
        int maxCraftable = fishApi.maxCraftable(player, recipe);

        String craftReadyMat = plugin.getConfig().getString("gui.buttons.craft-ready", "LIME_CONCRETE");
        String craftCloseMat = plugin.getConfig().getString("gui.buttons.craft-ready-close", "GREEN_CONCRETE");
        String craftMaxMat = plugin.getConfig().getString("gui.buttons.craft-max", "YELLOW_CONCRETE");
        String disabledMat = plugin.getConfig().getString("gui.buttons.craft-disabled", "RED_CONCRETE");

        if (canCraft) {
            inv.setItem(slotCraftSingle, buildCraftButton(craftReadyMat, "detail-craft-name",
                    buildCraftLore("detail-craft-lore-1", "detail-craft-lore-2",
                            "detail-craft-shift-hint", maxCraftable),
                    "craft", recipe.id()));

            inv.setItem(slotCraftClose, buildCraftButton(craftCloseMat, "detail-craft-close-name",
                    buildCraftLore("detail-craft-close-lore-1", null,
                            "detail-craft-close-shift-hint", maxCraftable),
                    "craft_and_close", recipe.id()));

            if (maxCraftable > 1) {
                Map<String, String> maxPh = new HashMap<>();
                maxPh.put("%count%", String.valueOf(maxCraftable));
                List<Component> maxLore = new ArrayList<>();
                maxLore.add(messages.getMessage("detail-craft-max-lore-1", maxPh));
                maxLore.add(messages.getMessage("detail-craft-max-lore-2"));
                inv.setItem(slotCraftMax, buildCraftButton(craftMaxMat, "detail-craft-max-name",
                        maxLore, "craft_max", recipe.id()));
            }
        } else {
            int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
            boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
            String reasonKey = levelOk
                    ? "detail-craft-disabled-reason-materials"
                    : "detail-craft-disabled-reason-level";

            List<Component> disabledLore = new ArrayList<>();
            disabledLore.add(messages.getMessage(reasonKey));
            disabledLore.add(messages.getMessage("detail-craft-disabled-lore-2"));

            inv.setItem(slotCraftSingle, buildCraftButton(disabledMat, "detail-craft-disabled-name",
                    disabledLore, "craft_disabled", recipe.id()));
            inv.setItem(slotCraftClose, buildCraftButton(disabledMat, "detail-craft-disabled-name",
                    disabledLore, "craft_disabled", recipe.id()));
        }

        // ─── Standardized action bar (Row 6) ───
        //   Slot 49: BACK BARRIER (centre) — overrides the default CLOSE
        //            so clicking it returns to the category menu instead
        //            of just closing. We pass favoriteRecipeId = recipe.id()
        //            so slot 51 shows the ★ FAVORITE TOGGLE button (not the
        //            "open favorites list" button used on list menus).
        renderActionBar54(inv, player, MENU_CATEGORY, recipe.category(),
                0, 1, false, recipe.id());

        // Override the centre Close button with a BACK button (recipe detail
        // returns to its parent category menu, not the main menu).
        String backMat = plugin.getConfig().getString("gui.buttons.back", "BARRIER");
        inv.setItem(slotBackClose, createButton(parseMaterial(backMat, Material.BARRIER),
                "detail-back-name", List.of("detail-back-lore"),
                "back_to_category", recipe.category()));

        player.openInventory(inv);
    }

    /**
     * Build the Favorite toggle button for the recipe detail GUI.
     *
     * <p>Two visual states:</p>
     * <ul>
     *   <li><b>Not favorited</b> — plain {@code NETHER_STAR}, name
     *       "☆ Favorite Recipe", lore "Click to favorite this recipe."</li>
     *   <li><b>Favorited</b> — enchanted {@code NETHER_STAR} (glowing),
     *       name "★ Favorited!", lore "Status: Favorited" +
     *       "Click to remove from favorites."</li>
     * </ul>
     *
     * <p>PDC: action = {@code "toggle_favorite"}, value = recipeId.</p>
     *
     * @param player   the player viewing the detail GUI
     * @param recipeId the recipe id whose favorite state to reflect
     * @return the button itemstack
     */
    private ItemStack buildFavoriteButton(Player player, String recipeId) {
        boolean isFavorite = favoritesManager != null
                && favoritesManager.isEnabled()
                && favoritesManager.isFavorite(player.getUniqueId(), recipeId);

        String matName = plugin.getConfig().getString(
                isFavorite ? "gui.buttons.favorite-on" : "gui.buttons.favorite-off",
                "NETHER_STAR");
        Material mat = parseMaterial(matName, Material.NETHER_STAR);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String nameKey = isFavorite ? "favorite-name-on" : "favorite-name-off";
        meta.displayName(messages.getMessage(nameKey));

        List<Component> lore = new ArrayList<>();
        if (isFavorite) {
            lore.add(messages.getMessage("favorite-lore-on-1"));
            lore.add(messages.getMessage("favorite-lore-on-2"));
            // Glowing enchant glint — hide the enchant text so only the glint shows
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(messages.getMessage("favorite-lore-off-1"));
            lore.add(messages.getMessage("favorite-lore-off-2"));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "toggle_favorite");
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, recipeId);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Handle a click on the Favorite toggle button. Toggles the recipe's
     * favorite state in the player's profile, plays a sound, refreshes the
     * button in-place (no GUI close/reopen), and sends a chat confirmation.
     *
     * @param player   the clicking player
     * @param recipeId the recipe id (from the button's PDC value)
     */
    public void handleFavoriteToggle(Player player, String recipeId) {
        if (favoritesManager == null || !favoritesManager.isEnabled()) {
            player.sendMessage(messages.getMessage("favorite-disabled", true));
            return;
        }

        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe_id%", recipeId);
            player.sendMessage(messages.getMessage("craft-not-found", true, ph));
            return;
        }

        boolean nowFavorite = favoritesManager.toggleFavorite(player.getUniqueId(), recipeId);

        // Play a feedback sound
        try {
            Sound sound = nowFavorite
                    ? parseSound(plugin.getConfig().getString("sounds.favorite-add", "UI_TOAST_CHALLENGE_COMPLETE"),
                            Sound.UI_TOAST_CHALLENGE_COMPLETE)
                    : parseSound(plugin.getConfig().getString("sounds.favorite-remove", "ENTITY_ITEM_PICKUP"),
                            Sound.ENTITY_ITEM_PICKUP);
            player.playSound(player, sound, 0.6f, 1.2f);
        } catch (Exception ignored) {
        }

        // Send a chat confirmation.
        // IMPORTANT: use getMessageRaw (NOT getMessage) so the recipe's
        // display-name MiniMessage tags (e.g. <bold><#00CED1>Ocean's Wrath)
        // survive placeholder substitution and are deserialized by MiniMessage
        // instead of being escaped and shown as raw text in chat.
        Map<String, String> ph = new HashMap<>();
        ph.put("%recipe%", describeItem(recipe.resultItem()));
        String msgKey = nowFavorite ? "favorite-added" : "favorite-removed";
        player.sendMessage(messages.getMessageRaw(msgKey, true, ph));

        // ─── Refresh ONLY slot 51 in-place — no GUI close/reopen ───
        // This keeps the player's cursor and view position stable.
        if (isPlayerViewingCraftingGUI(player)) {
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv.getHolder() instanceof CraftingGUIHolder holder
                    && holder.getScreenType() == CraftingGUIHolder.ScreenType.DETAIL
                    && recipeId.equals(holder.getRecipeId())) {
                topInv.setItem(slotFavorite, buildFavoriteButton(player, recipeId));
            }
        }
    }

    private ItemStack buildIngredientsItem(Player player, CraftRecipe recipe) {
        ItemStack ingredientsItem = new ItemStack(Material.BOOK);
        ItemMeta ingMeta = ingredientsItem.getItemMeta();
        if (ingMeta == null) return ingredientsItem;

        ingMeta.displayName(messages.getMessage("detail-ingredients-name"));
        List<Component> ingLore = new ArrayList<>();
        // v1.1.2 — NPE hardening: null ingredients list / null ingredient entry / null item
        List<CraftRecipe.Ingredient> ingredients = recipe.ingredients();
        if (ingredients == null) ingredients = List.of();
        for (CraftRecipe.Ingredient ing : ingredients) {
            if (ing == null) continue;
            ItemStack ingItem = ing.item();
            int owned = ingItem == null ? 0 : countInInventory(player, ingItem);
            boolean ok = owned >= ing.amount();
            Map<String, String> ph = new HashMap<>();
            ph.put("%color%", ok ? "<green>" : "<red>");
            ph.put("%name%", describeItem(ingItem));
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

        // Üretim ücreti satırı — sadece ekonomi açıksa göster (Faz 1.0.1)
        // Tek üretim ücreti ve (maxCraftable > 1 ise) toplu üretim ücretini göster.
        // Bakiye yetmiyorsa kırmızı, yeterliyse yeşil olsun.
        if (economyManager != null && economyManager.isEnabled()) {
            double singleFee = economyManager.getFee(recipe);
            double balance = economyManager.getBalance(player.getUniqueId());
            boolean canAffordSingle = balance >= singleFee;
            Map<String, String> feePh = new HashMap<>();
            feePh.put("%color%", canAffordSingle ? "<green>" : "<red>");
            feePh.put("%fee%", String.format(java.util.Locale.US, "%.2f", singleFee));
            // getMessageRaw — %color% carries a real MiniMessage tag (<green>/<red>)
            // that MUST be deserialized, not escaped. This is the fix for the
            // raw "<red>Craft Fee: $100.00" rendering bug.
            ingLore.add(messages.getMessageRaw("detail-craft-fee-line", feePh));

            if (maxCraftable > 1) {
                double bulkFee = economyManager.calculateBulkFee(recipe, maxCraftable);
                boolean canAffordBulk = balance >= bulkFee;
                Map<String, String> bulkPh = new HashMap<>();
                bulkPh.put("%color%", canAffordBulk ? "<green>" : "<red>");
                bulkPh.put("%fee%", String.format(java.util.Locale.US, "%.2f", bulkFee));
                bulkPh.put("%count%", String.valueOf(maxCraftable));
                // getMessageRaw — same reason as the single fee line above.
                ingLore.add(messages.getMessageRaw("detail-craft-fee-bulk-line", bulkPh));
            }
        }

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
        openBlackMarketMenu(player, 0);
    }

    public void openBlackMarketMenu(Player player, int page) {
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

        // v1.1.0 — standardized chrome + paginated stock
        fillChrome54(inv);

        // Pre-fetch player state once (avoid hitting PhoenixFish's cache repeatedly
        // inside the loop — balance is per-player, learned-state is per-recipe)
        UUID playerUuid = player.getUniqueId();
        double playerBalance = fishApi.getBalance(playerUuid);
        int purchaseLimit = blackMarketManager.getPurchaseLimit();
        int purchaseCount = blackMarketManager.getPurchaseCount(playerUuid);
        boolean alreadyBoughtThisCycle = purchaseLimit > 0 && purchaseCount >= purchaseLimit;

        // Build the full list of stock items (resolved to recipes), then slice.
        List<ItemStack> allStockItems = new ArrayList<>();
        for (Map.Entry<String, Double> entry : stock.entrySet()) {
            String recipeId = entry.getKey();
            double price = entry.getValue();

            com.phoenix.fish.api.CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
            if (recipe == null) continue;

            // v1.1.2 — NPE guard: skip recipes with null resultItem (corrupted
            // PhoenixFish data). Previously this would NPE on .clone() and
            // abort the entire black-market render, leaving an empty GUI.
            ItemStack resultRaw = recipe.resultItem();
            if (resultRaw == null) {
                plugin.getLogger().warning("Black market recipe '" + recipeId
                        + "' has null resultItem() — skipping.");
                continue;
            }
            ItemStack icon = resultRaw.clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;

            String recipeName = describeItem(resultRaw);
            Map<String, String> namePh = new HashMap<>();
            namePh.put("%recipe%", recipeName);
            meta.displayName(messages.getMessageRaw("blackmarket-item-name", namePh));

            boolean alreadyLearned = recipeManager != null
                    && recipeManager.isEnabled()
                    && recipeManager.hasLearned(playerUuid, recipeId);
            boolean canAfford = playerBalance >= price;

            Map<String, String> pricePh = new HashMap<>();
            pricePh.put("%price%", String.format("%.2f", price));
            Map<String, String> balancePh = new HashMap<>();
            balancePh.put("%balance%", String.format("%.2f", playerBalance));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(messages.getMessage("blackmarket-item-lore-1", pricePh));
            lore.add(messages.getMessage("blackmarket-item-lore-balance", balancePh));
            if (canAfford) {
                lore.add(messages.getMessage("blackmarket-item-affordable"));
            } else {
                lore.add(messages.getMessage("blackmarket-item-not-affordable"));
            }
            lore.add(Component.empty());
            if (alreadyLearned) {
                lore.add(messages.getMessage("blackmarket-item-learned"));
            } else if (alreadyBoughtThisCycle) {
                Map<String, String> limitPh = new HashMap<>();
                limitPh.put("%count%", String.valueOf(purchaseCount));
                limitPh.put("%limit%", String.valueOf(purchaseLimit));
                lore.add(messages.getMessage("blackmarket-item-already-bought", limitPh));
            } else {
                lore.add(messages.getMessage("blackmarket-item-lore-2"));
            }
            lore.add(messages.getMessage("blackmarket-item-lore-3"));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "blackmarket_purchase");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING,
                    recipeId + ":" + String.format("%.2f", price));
            icon.setItemMeta(meta);
            allStockItems.add(icon);
        }

        // Paginate
        int itemsPerPage = getItemsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) allStockItems.size() / itemsPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allStockItems.size());
        populateContent(inv, allStockItems.subList(start, end));

        renderActionBar54(inv, player, MENU_BLACKMARKET, null, page, totalPages, false, null);

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
        openRecipesMenu(player, 0);
    }

    public void openRecipesMenu(Player player, int page) {
        if (recipeManager == null || !recipeManager.isEnabled()) {
            player.sendMessage(messages.getMessage("recipe-sharing-disabled", true));
            return;
        }

        // v1.1.2 — NPE guard: PhoenixFish may return null during a reload race.
        java.util.List<CraftRecipe> allRecipes = fishApi.getCraftingRecipes();
        if (allRecipes == null) allRecipes = java.util.List.of();
        int total = allRecipes.size();
        int learnedCount = recipeManager.getLearnedCount(player.getUniqueId());

        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%learned%", String.valueOf(learnedCount));
        titlePh.put("%total%", String.valueOf(total));

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("recipes-title", titlePh));

        // v1.1.0 — standardized chrome + multi-page pagination
        fillChrome54(inv);

        String lockedMat = plugin.getConfig().getString("gui.buttons.recipe-locked", "WRITTEN_BOOK");
        String learnedMat = plugin.getConfig().getString("gui.buttons.recipe-learned", "KNOWLEDGE_BOOK");

        // Build the full list of recipe items, then slice for the current page.
        List<ItemStack> recipeItems = new ArrayList<>();
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
            // getMessageRaw — %name% may contain MiniMessage tags from rod names
            meta.displayName(messages.getMessageRaw(nameKey, namePh));

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

            recipeItems.add(item);
        }

        // Paginate
        int itemsPerPage = getItemsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) recipeItems.size() / itemsPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, recipeItems.size());
        populateContent(inv, recipeItems.subList(start, end));

        renderActionBar54(inv, player, MENU_RECIPES, null, page, totalPages, false, null);

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

        // v1.1.0 — use 54-slot standardized chrome for visual consistency.
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                messages.getMessage("stats-title"));
        fillChrome54(inv);

        UUID uuid = player.getUniqueId();
        StatsManager.PlayerStats stats = statsManager.getStats(uuid);

        // Slot 21: Personal stats book (left of center top)
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

            if (economyManager != null && economyManager.isEnabled()) {
                double balance = economyManager.getBalance(uuid);
                Map<String, String> balPh = new HashMap<>();
                balPh.put("%balance%", String.format(java.util.Locale.US, "%.2f", balance));
                lore.add(messages.getMessage("stats-balance", balPh));
            }

            statsMeta.lore(lore);
            statsMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "none");
            statsMeta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "none");
            statsItem.setItemMeta(statsMeta);
        }
        inv.setItem(21, statsItem);

        // Slot 23: Leaderboard (right of center top)
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
        inv.setItem(23, leaderboard);

        // Standardized action bar — Back to main at slot 49
        renderActionBar54(inv, player, MENU_CATEGORY, null, 0, 1, false, null);

        player.openInventory(inv);
    }

    /**
     * Re-run the player's last search and open the results at the given page.
     * Called by the listener when the player clicks the next/prev pagination
     * arrow on the search results menu.
     *
     * @param player the player
     * @param page   zero-indexed page number
     * @since v1.1.0
     */
    public void openSearchPage(Player player, int page) {
        String query = playerState.getLastSearchQuery(player.getUniqueId());
        if (query == null || query.isEmpty()) {
            openMainMenu(player);
            return;
        }
        performSearch(player, query, playerState.getLastSearchCategory(player.getUniqueId()));
        // performSearch opens page 0; if the caller asked for a non-zero page,
        // re-open at the requested page (results list is rebuilt).
        if (page > 0) {
            // Re-fetch the results — performSearch has already validated the
            // query length and the search system is enabled at this point.
            // v1.1.2 — NPE guard: getCraftingRecipes() may return null during
            // a PhoenixFish reload race.
            List<CraftRecipe> allRecipes = fishApi.getCraftingRecipes();
            if (allRecipes == null) allRecipes = java.util.List.of();
            String q = query.toLowerCase();
            List<CraftRecipe> results = new ArrayList<>();
            for (CraftRecipe r : allRecipes) {
                if (r.id().toLowerCase().contains(q)) {
                    results.add(r);
                    continue;
                }
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
            if (!results.isEmpty()) {
                openSearchResultMenu(player, query, results, page);
            }
        }
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
        // v1.1.2 — NPE guard: getCraftingRecipes() may return null during
        // a PhoenixFish reload race.
        List<CraftRecipe> allRecipes = fishApi.getCraftingRecipes();
        if (allRecipes == null) allRecipes = new ArrayList<>();
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

        // Remember the query so the search-results menu can paginate
        // without asking the player to retype it (v1.1.5).
        playerState.setLastSearchQuery(player.getUniqueId(), query);
        playerState.setLastSearchCategory(player.getUniqueId(), null);

        openSearchResultMenu(player, query, results, 0);
    }

    /**
     * Open the search results menu (page 0).
     */
    private void openSearchResultMenu(Player player, String query, List<CraftRecipe> results) {
        openSearchResultMenu(player, query, results, 0);
    }

    /**
     * Open the search results menu at a specific page (v1.1.0 — paginated).
     */
    private void openSearchResultMenu(Player player, String query,
                                        List<CraftRecipe> results, int page) {
        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("%query%", query);
        titlePh.put("%count%", String.valueOf(results.size()));
        Component title = messages.getMessageRaw("search-title-with-count", titlePh);

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                54,
                title);

        // v1.1.0 — standardized chrome
        fillChrome54(inv);

        // Build icons for ALL results, then slice for the current page.
        List<ItemStack> allItems = new ArrayList<>();
        for (CraftRecipe recipe : results) {
            allItems.add(buildRecipeIcon(player, recipe));
        }

        int itemsPerPage = getItemsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) allItems.size() / itemsPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allItems.size());
        populateContent(inv, allItems.subList(start, end));

        renderActionBar54(inv, player, MENU_SEARCH, null, page, totalPages, false, null);

        player.openInventory(inv);
    }

    // =====================================================================
    //  ACHIEVEMENTS MENU
    // =====================================================================

    public void openAchievementsMenu(Player player) {
        openAchievementsMenu(player, 0);
    }

    public void openAchievementsMenu(Player player, int page) {
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

        // v1.1.0 — standardized chrome + multi-page pagination
        fillChrome54(inv);

        Set<String> unlockedIds = achievementManager.getUnlocked(player.getUniqueId());
        String lockedMat = plugin.getConfig().getString("gui.buttons.achievement-locked", "GRAY_DYE");
        String unlockedMat = plugin.getConfig().getString("gui.buttons.achievement-unlocked", "GOLD_INGOT");

        List<ItemStack> achItems = new ArrayList<>();
        for (AchievementManager.Achievement ach : achievements.values()) {
            boolean isUnlocked = unlockedIds.contains(ach.id);
            Material mat = parseMaterial(
                    isUnlocked ? unlockedMat : lockedMat,
                    isUnlocked ? Material.GOLD_INGOT : Material.GRAY_DYE);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String tierKey = "achievement-tier-" + ach.tier;
            String tierPrefix = messages.getPlainMessage(tierKey);
            String achName = messages.getPlainMessage(ach.nameKey);
            String displayName = (tierPrefix != null && !tierPrefix.isEmpty())
                    ? tierPrefix + " " + achName : achName;

            String nameKey = isUnlocked ? "achievement-unlocked-name" : "achievement-locked-name";
            Map<String, String> namePh = new HashMap<>();
            namePh.put("%name%", displayName);
            meta.displayName(messages.getMessageRaw(nameKey, namePh));

            Map<String, String> descPh = new HashMap<>();
            descPh.put("%description%", messages.getPlainMessage(ach.descKey));

            List<Component> lore = new ArrayList<>();
            lore.add(messages.getMessage(isUnlocked
                    ? "achievement-unlocked-lore-1"
                    : "achievement-locked-lore-1", descPh));

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

            achItems.add(item);
        }

        // Paginate
        int itemsPerPage = getItemsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) achItems.size() / itemsPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, achItems.size());
        populateContent(inv, achItems.subList(start, end));

        renderActionBar54(inv, player, MENU_ACHIEVEMENTS, null, page, totalPages, false, null);

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
        // v1.1.2 — NPE guard: a null template (corrupted ingredient) never
        // matches anything; previously this NPE'd at template.getType().
        if (template == null || inv == null) return false;
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

        // 1) If the item has a custom display name (e.g. "Cosmic Rod" set by
        //    PhoenixFish's rods.yml/baits.yml), use it — preserve colors via
        //    MiniMessage serialization so the lore stays styled.
        if (item.hasItemMeta()) {
            ItemMeta m = item.getItemMeta();
            if (m != null && m.hasDisplayName()) {
                return miniMessage.serialize(m.displayName());
            }
        }

        // 2) For vanilla items without a custom display name, return a
        //    MiniMessage <lang:...> tag that points at the vanilla
        //    translation key. The CLIENT resolves this against its own
        //    language file, so a player on a Chinese client sees "末影水晶"
        //    instead of the raw enum name "end crystal".
        //
        //    Paper API: Material.translationKey() (since 1.13.1) returns
        //    e.g. "block.minecraft.fishing_rod" or "item.minecraft.end_crystal".
        //    MiniMessage: <lang:block.minecraft.fishing_rod> — supported by
        //    Adventure 4.x (bundled with Paper 1.21).
        try {
            String translationKey = item.getType().translationKey();
            if (translationKey != null && !translationKey.isEmpty()) {
                return "<lang:" + translationKey + ">";
            }
        } catch (Throwable ignored) {
            // Old Bukkit builds may not have translationKey() — fall through.
        }

        // 3) Last-resort fallback: raw enum name (legacy behavior).
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
