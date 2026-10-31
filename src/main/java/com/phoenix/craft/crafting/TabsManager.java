package com.phoenix.craft.crafting;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Multi-category tabbed view (F1). Discovers all categories present in the
 * PhoenixFish recipe pool and exposes them as an ordered tab list for the
 * GUI to render as a top bar.
 *
 * <p>Categories are sorted by a configurable priority list
 * ({@code tabs.order: [rod, bait, tool, decor, consumable]}); any categories
 * discovered at runtime that aren't in the priority list are appended
 * alphabetically.</p>
 *
 * @since 1.0.7 (feature #1)
 */
public class TabsManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private volatile List<String> cachedTabs = Collections.emptyList();

    public TabsManager(PhoenixCraft plugin, Supplier<PhoenixFishAPI> fishApiSupplier) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        refresh();
    }

    /** Re-discover categories from the live PhoenixFish recipe pool. */
    public void refresh() {
        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) {
            cachedTabs = Collections.emptyList();
            return;
        }
        List<CraftRecipe> all;
        try {
            all = api.getCraftingRecipes();
        } catch (Throwable t) {
            cachedTabs = Collections.emptyList();
            return;
        }
        if (all == null || all.isEmpty()) {
            cachedTabs = Collections.emptyList();
            return;
        }

        // Collect distinct categories preserving insertion order.
        // Use a Set<String> instead of Map<String, Boolean> — avoids the
        // null-unboxing bug where distinct.remove(pl) returns null when the
        // priority-listed category isn't in the pool.
        java.util.Set<String> distinct = new java.util.LinkedHashSet<>();
        for (CraftRecipe r : all) {
            if (r.category() != null && !r.category().isBlank()) {
                distinct.add(r.category().toLowerCase());
            }
        }

        // Order by config priority, then alphabetically for the rest.
        List<String> priority = plugin.getConfig().getStringList("features.tabs.order");
        List<String> ordered = new ArrayList<>();
        for (String p : priority) {
            if (p == null) continue;
            String pl = p.toLowerCase();
            // Set.remove returns boolean (not Boolean) — no unboxing NPE.
            if (distinct.remove(pl)) ordered.add(pl);
        }
        ordered.addAll(distinct.stream().sorted().toList());

        cachedTabs = Collections.unmodifiableList(ordered);
    }

    /** Returns the active tab list. Empty if no recipes are loaded. */
    public List<String> getTabs() { return cachedTabs; }

    /** Returns true if there are 2+ tabs (worth showing the tab bar). */
    public boolean hasMultipleTabs() { return cachedTabs.size() >= 2; }

    /** Returns the default tab (first in the list, or null if empty). */
    public String getDefaultTab() {
        return cachedTabs.isEmpty() ? null : cachedTabs.get(0);
    }

    /** Returns the localized display name for a tab. */
    public String getTabDisplayName(String tab) {
        if (tab == null) return "???";
        String key = "tab-" + tab.toLowerCase();
        String msg = plugin.getMessageManager().getPlainMessage(key);
        // Missing key returns the key itself — fall back to capitalized tab.
        return msg.equals(key) ? tab.substring(0, 1).toUpperCase() + tab.substring(1) : msg;
    }
}
