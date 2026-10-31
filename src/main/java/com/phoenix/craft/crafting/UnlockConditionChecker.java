package com.phoenix.craft.crafting;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.attributes.RecipeAttributesManager;
import com.phoenix.craft.discovery.DiscoveryManager;
import com.phoenix.craft.stats.StatsManager;
import com.phoenix.fish.api.PhoenixFishAPI;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Evaluates a recipe's unlock conditions (F8).
 *
 * <p>Each condition is a string of form {@code "type:arg1:arg2:..."}:
 * <ul>
 *   <li>{@code craft:recipeId:count} — player has crafted recipe X at least N times</li>
 *   <li>{@code discover:count}       — player has discovered at least N distinct recipes</li>
 *   <li>{@code level:N}              — player has reached crafting level N</li>
 *   <li>{@code fish:count}           — player has caught at least N fish (PhoenixFish)</li>
 * </ul>
 *
 * <p>If the condition string is malformed, it is silently treated as satisfied
 * (fail-open) so a typo doesn't permanently lock players out.</p>
 *
 * @since 1.0.7 (feature #8)
 */
public class UnlockConditionChecker {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final RecipeAttributesManager attributes;
    private final StatsManager statsManager;
    private final DiscoveryManager discoveryManager;

    public UnlockConditionChecker(PhoenixCraft plugin,
                                    Supplier<PhoenixFishAPI> fishApiSupplier,
                                    RecipeAttributesManager attributes,
                                    StatsManager statsManager,
                                    DiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        this.attributes = attributes;
        this.statsManager = statsManager;
        this.discoveryManager = discoveryManager;
    }

    /**
     * Returns the first unsatisfied condition, or {@code null} if all are
     * satisfied (recipe is unlocked).
     */
    public String firstUnsatisfied(UUID uuid, String recipeId) {
        for (String cond : attributes.getUnlockConditions(recipeId)) {
            if (!isSatisfied(uuid, cond)) return cond;
        }
        return null;
    }

    public boolean isUnlocked(UUID uuid, String recipeId) {
        return firstUnsatisfied(uuid, recipeId) == null;
    }

    public boolean isSatisfied(UUID uuid, String condition) {
        if (condition == null || condition.isBlank()) return true;
        String[] parts = condition.split(":");
        if (parts.length == 0) return true;
        String type = parts[0].toLowerCase();
        try {
            switch (type) {
                case "craft": {
                    if (parts.length < 3) return true;
                    String reqRecipe = parts[1].toLowerCase();
                    int needed = Integer.parseInt(parts[2]);
                    int have = (statsManager != null)
                            ? statsManager.getRecipeCount(uuid, reqRecipe) : 0;
                    return have >= needed;
                }
                case "discover": {
                    if (parts.length < 2) return true;
                    int needed = Integer.parseInt(parts[1]);
                    int have = (discoveryManager != null)
                            ? discoveryManager.getDiscoveredCount(uuid) : 0;
                    return have >= needed;
                }
                case "level": {
                    if (parts.length < 2) return true;
                    int needed = Integer.parseInt(parts[1]);
                    PhoenixFishAPI api = fishApiSupplier.get();
                    if (api == null) return false;
                    int have = api.getCraftingLevel(uuid);
                    return have >= needed;
                }
                case "fish": {
                    if (parts.length < 2) return true;
                    int needed = Integer.parseInt(parts[1]);
                    PhoenixFishAPI api = fishApiSupplier.get();
                    if (api == null) return false;
                    // PhoenixFish doesn't expose a per-player fish count in the
                    // public API as of 1.3.4. Try reflectively; fail-open if missing.
                    try {
                        java.lang.reflect.Method m = api.getClass().getMethod("getFishCaughtCount", UUID.class);
                        Object result = m.invoke(api, uuid);
                        if (result instanceof Number n) return n.intValue() >= needed;
                    } catch (Throwable ignored) {}
                    return true; // fail-open
                }
                default:
                    return true; // unknown condition type — fail-open
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Malformed unlock condition '" + condition
                    + "' for recipe — treating as satisfied.");
            return true;
        }
    }
}
