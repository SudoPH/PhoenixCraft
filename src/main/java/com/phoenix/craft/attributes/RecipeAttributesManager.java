package com.phoenix.craft.attributes;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extended per-recipe attributes that PhoenixFish's {@link CraftRecipe} record
 * doesn't carry: success chance, crit chance, material scaling, unlock
 * conditions, multi-step chains, sound overrides.
 *
 * <p>Loaded from {@code plugins/PhoenixCraft/recipe_attributes.yml} (extracted
 * from the jar on first run). Admins edit this file to enable the F4/F5/F7/F8
 * features on a per-recipe basis — PhoenixFish's own {@code custom_recipes.yml}
 * stays untouched.</p>
 *
 * <p>All values default to "feature off" — a recipe with no entry here behaves
 * exactly as it did in v1.0.6.</p>
 *
 * @since 1.0.7 (features #4, #5, #7, #8)
 */
public class RecipeAttributesManager {

    private final PhoenixCraft plugin;
    private FileConfiguration cfg;

    public RecipeAttributesManager(PhoenixCraft plugin) {
        this.plugin = plugin;
        load();
        plugin.getLogger().info("RecipeAttributesManager enabled — "
                + countConfiguredRecipes() + " recipes have extended attributes.");
    }

    private File getFile() { return new File(plugin.getDataFolder(), "recipe_attributes.yml"); }

    private void load() {
        File file = getFile();
        if (!file.exists()) {
            plugin.saveResource("recipe_attributes.yml", false);
        }
        file = getFile();
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        } else {
            cfg = new YamlConfiguration();
        }
    }

    public void reload() { load(); }

    private int countConfiguredRecipes() {
        ConfigurationSection recipes = cfg.getConfigurationSection("recipes");
        return recipes != null ? recipes.getKeys(false).size() : 0;
    }

    // =====================================================================
    //  F4: Success / failure rate
    // =====================================================================

    /** Returns the success chance [0.0, 1.0]. Default 1.0 = always succeeds. */
    public double getSuccessChance(String recipeId) {
        return Math.max(0.0, Math.min(1.0,
                cfg.getDouble("recipes." + recipeId + ".success-chance", 1.0)));
    }

    /** If true, a failed craft consumes materials anyway. Default false. */
    public boolean consumeOnFail(String recipeId) {
        return cfg.getBoolean("recipes." + recipeId + ".consume-on-fail", false);
    }

    // =====================================================================
    //  F5: Critical success bonus
    // =====================================================================

    /** Returns the crit chance [0.0, 1.0]. Default 0.0 = never crits. */
    public double getCritChance(String recipeId) {
        return Math.max(0.0, Math.min(1.0,
                cfg.getDouble("recipes." + recipeId + ".crit-chance", 0.0)));
    }

    /** Multiplier applied to output count on a crit. Default 2.0. */
    public double getCritOutputMultiplier(String recipeId) {
        return cfg.getDouble("recipes." + recipeId + ".crit-output-multiplier", 2.0);
    }

    /** Multiplier applied to XP on a crit. Default 2.0. */
    public double getCritXpMultiplier(String recipeId) {
        return cfg.getDouble("recipes." + recipeId + ".crit-xp-multiplier", 2.0);
    }

    // =====================================================================
    //  F7: Dynamic material scaling
    // =====================================================================

    /**
     * Returns a multiplier in [0.1, 2.0] applied to ingredient counts based on
     * the player's crafting level. Default 1.0 = no scaling.
     *
     * <p>Formula: {@code scale = base + (levelFactor * playerLevel)}, clamped.
     * Config: {@code recipes.<id>.material-scale.base} (default 1.0),
     * {@code recipes.<id>.material-scale.level-factor} (default 0.0 — disabled),
     * {@code recipes.<id>.material-scale.min} (default 0.1),
     * {@code recipes.<id>.material-scale.max} (default 2.0).</p>
     */
    public double getMaterialScale(String recipeId, int playerLevel) {
        String base = "recipes." + recipeId + ".material-scale";
        double b = cfg.getDouble(base + ".base", 1.0);
        double lvlFactor = cfg.getDouble(base + ".level-factor", 0.0);
        double min = cfg.getDouble(base + ".min", 0.1);
        double max = cfg.getDouble(base + ".max", 2.0);
        if (lvlFactor == 0.0) return 1.0; // feature off for this recipe
        double scale = b - (lvlFactor * playerLevel);
        return Math.max(min, Math.min(max, scale));
    }

    // =====================================================================
    //  F8: Conditional unlock
    // =====================================================================

    /**
     * Returns the list of unlock conditions for a recipe. Empty = always
     * unlocked (provided the player has learned it via the recipe book).
     *
     * <p>Each condition is a string of form {@code "type:arg1:arg2:..."}.
     * Supported types:
     * <ul>
     *   <li>{@code craft:recipeId:count} — craft recipe X at least N times</li>
     *   <li>{@code discover:count} — discover at least N distinct recipes</li>
     *   <li>{@code level:N} — reach crafting level N</li>
     *   <li>{@code fish:count} — catch at least N fish (PhoenixFish API)</li>
     * </ul>
     */
    public List<String> getUnlockConditions(String recipeId) {
        List<String> raw = cfg.getStringList("recipes." + recipeId + ".unlock-conditions");
        return raw != null ? Collections.unmodifiableList(raw) : Collections.emptyList();
    }

    /** True if the recipe has any unlock conditions defined. */
    public boolean hasUnlockConditions(String recipeId) {
        return !getUnlockConditions(recipeId).isEmpty();
    }

    // =====================================================================
    //  F6: Multi-step chain
    // =====================================================================

    /**
     * Returns the ordered list of step recipe IDs that must be completed in
     * sequence before this recipe can be crafted. Empty = single-step.
     *
     * <p>Each entry is a recipe ID whose result item is required as an
     * ingredient for the next step. The GUI shows "Step 2/3" progress.</p>
     */
    public List<String> getSteps(String recipeId) {
        List<String> raw = cfg.getStringList("recipes." + recipeId + ".steps");
        return raw != null ? Collections.unmodifiableList(raw) : Collections.emptyList();
    }

    public boolean hasSteps(String recipeId) { return !getSteps(recipeId).isEmpty(); }

    // =====================================================================
    //  F13: Per-recipe sound override
    // =====================================================================

    /** Returns the success sound override, or null to use tier-sound fallback. */
    public String getSuccessSound(String recipeId) {
        return cfg.getString("recipes." + recipeId + ".success-sound", null);
    }

    /** Returns the fail sound override, or null to use default. */
    public String getFailSound(String recipeId) {
        return cfg.getString("recipes." + recipeId + ".fail-sound", null);
    }

    /** Returns the crit sound override, or null to use default crit chime. */
    public String getCritSound(String recipeId) {
        return cfg.getString("recipes." + recipeId + ".crit-sound", null);
    }
}
