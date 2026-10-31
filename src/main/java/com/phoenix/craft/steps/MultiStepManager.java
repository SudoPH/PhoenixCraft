package com.phoenix.craft.steps;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.attributes.RecipeAttributesManager;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Multi-step crafting sequences (F6). A recipe may declare an ordered list
 * of prerequisite "step" recipes that must be completed in sequence before
 * the final recipe can be crafted. Each step's result item becomes a required
 * ingredient for the next step (the admin wires this in custom_recipes.yml).
 *
 * <p>Progress is tracked per-player: a (player, recipe) → completed-step-count
 * map persisted in {@code steps_progress.yml}. The GUI shows "Step 2/3" in
 * the recipe detail lore.</p>
 *
 * @since 1.0.7 (feature #6)
 */
public class MultiStepManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final RecipeAttributesManager attributes;
    /** (playerUuid|recipeId) → completed step count */
    private final java.util.Map<UUID, java.util.Map<String, Integer>> progress = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public MultiStepManager(PhoenixCraft plugin,
                              Supplier<PhoenixFishAPI> fishApiSupplier,
                              RecipeAttributesManager attributes) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        this.attributes = attributes;
        load();
        plugin.getLogger().info("MultiStepManager enabled — tracking "
                + progress.size() + " players' step progress.");
    }

    /** Returns the ordered list of step recipe IDs for a final recipe. Empty = single-step. */
    public List<String> getSteps(String recipeId) {
        return attributes.getSteps(recipeId);
    }

    /** Returns the player's current step index (0 = not started, N = all steps done). */
    public int getCurrentStep(UUID uuid, String recipeId) {
        java.util.Map<String, Integer> m = progress.get(uuid);
        if (m == null) return 0;
        return m.getOrDefault(recipeId.toLowerCase(), 0);
    }

    /** Returns the total number of steps (NOT counting the final recipe itself). */
    public int getTotalSteps(String recipeId) {
        return getSteps(recipeId).size();
    }

    /**
     * Called after the player crafts a step recipe. Advances the player's
     * progress for the final recipe if this step was the next one expected.
     *
     * @return true if progress was advanced
     */
    public boolean recordStepCraft(UUID uuid, String stepRecipeId) {
        if (stepRecipeId == null) return false;
        stepRecipeId = stepRecipeId.toLowerCase();

        // Find any final recipe that has this step as the next expected one.
        for (java.util.Map.Entry<UUID, java.util.Map<String, Integer>> entry : progress.entrySet()) {
            if (!entry.getKey().equals(uuid)) continue;
            for (java.util.Map.Entry<String, Integer> pe : entry.getValue().entrySet()) {
                String finalRecipeId = pe.getKey();
                int currentStep = pe.getValue();
                List<String> steps = getSteps(finalRecipeId);
                if (currentStep < steps.size()
                        && steps.get(currentStep).equalsIgnoreCase(stepRecipeId)) {
                    entry.getValue().put(finalRecipeId, currentStep + 1);
                    dirty = true;
                    saveAsync();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Initialize progress tracking for a final recipe on first view.
     * Called from the GUI when a player opens a multi-step recipe's detail.
     */
    public void ensureTracking(UUID uuid, String recipeId) {
        if (!attributes.hasSteps(recipeId)) return;
        recipeId = recipeId.toLowerCase();
        progress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .putIfAbsent(recipeId, 0);
    }

    /** Reset progress for a final recipe (e.g., on admin reset). */
    public void resetProgress(UUID uuid, String recipeId) {
        recipeId = recipeId.toLowerCase();
        java.util.Map<String, Integer> m = progress.get(uuid);
        if (m != null && m.remove(recipeId) != null) {
            dirty = true;
            saveAsync();
        }
    }

    /**
     * Check if the player has completed all steps for the final recipe.
     * The final recipe itself is NOT a step — it's crafted normally after
     * all steps are done.
     */
    public boolean isUnlocked(UUID uuid, String recipeId) {
        if (!attributes.hasSteps(recipeId)) return true;
        return getCurrentStep(uuid, recipeId) >= getTotalSteps(recipeId);
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    private java.io.File getFile() {
        return new java.io.File(plugin.getDataFolder(), "steps_progress.yml");
    }

    private void load() {
        java.io.File file = getFile();
        if (!file.exists()) return;
        org.bukkit.configuration.file.FileConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                org.bukkit.configuration.ConfigurationSection rec = players.getConfigurationSection(uuidStr);
                if (rec == null) continue;
                java.util.Map<String, Integer> m = new ConcurrentHashMap<>();
                for (String rId : rec.getKeys(false)) {
                    m.put(rId.toLowerCase(), rec.getInt(rId, 0));
                }
                progress.put(uuid, m);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        org.bukkit.configuration.file.FileConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();
        for (java.util.Map.Entry<UUID, java.util.Map<String, Integer>> e : progress.entrySet()) {
            for (java.util.Map.Entry<String, Integer> pe : e.getValue().entrySet()) {
                cfg.set("players." + e.getKey() + "." + pe.getKey(), pe.getValue());
            }
        }
        try {
            cfg.save(getFile());
            dirty = false;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save steps_progress.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        if (!dirty) return;
        final org.bukkit.configuration.file.FileConfiguration snapshot;
        synchronized (this) {
            if (!dirty) return;
            snapshot = new org.bukkit.configuration.file.YamlConfiguration();
            for (java.util.Map.Entry<UUID, java.util.Map<String, Integer>> e : progress.entrySet()) {
                for (java.util.Map.Entry<String, Integer> pe : e.getValue().entrySet()) {
                    snapshot.set("players." + e.getKey() + "." + pe.getKey(), pe.getValue());
                }
            }
            dirty = false;
        }
        try {
            snapshot.save(getFile());
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save steps_progress.yml (async): " + e.getMessage());
            dirty = true;
        }
    }

    public void shutdown() { save(); }
}
