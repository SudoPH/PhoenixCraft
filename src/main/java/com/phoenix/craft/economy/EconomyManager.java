package com.phoenix.craft.economy;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;

import java.util.UUID;

/**
 * Handles crafting fees via PhoenixFish's internal economy (no Vault).
 *
 * <p>Modularity: this entire system can be disabled in {@code config.yml} via
 * {@code economy.enabled: false}. When disabled, all crafts are free.</p>
 *
 * <p>Recipe fees are read from {@code custom_recipes.yml}'s {@code crafting-fee}
 * field (a PhoenixFish-side config addition). Recipes without that field use
 * {@code economy.default-fee} from PhoenixCraft's config.</p>
 *
 * @since 0.5.0
 */
public class EconomyManager {

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;
    private final boolean enabled;
    private final boolean chargePerItemInBulk;
    private final double defaultFee;

    public EconomyManager(PhoenixCraft plugin, PhoenixFishAPI fishApi) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        this.enabled = plugin.getConfig().getBoolean("economy.enabled", false);
        this.chargePerItemInBulk = plugin.getConfig().getBoolean("economy.charge-per-item-in-bulk", true);
        this.defaultFee = plugin.getConfig().getDouble("economy.default-fee", 0.0);

        if (enabled) {
            plugin.getLogger().info("EconomyManager enabled — default fee: $" + defaultFee
                    + ", charge per item in bulk: " + chargePerItemInBulk);
        } else {
            plugin.getLogger().info("EconomyManager disabled (economy.enabled=false).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the crafting fee for a recipe.
     * Returns 0 if economy is disabled or the recipe has no fee.
     */
    public double getFee(CraftRecipe recipe) {
        if (!enabled) return 0.0;
        if (recipe == null) return 0.0;
        // Use default-fee. Future: PhoenixFish could expose crafting-fee on CraftRecipe.
        return defaultFee;
    }

    /**
     * Calculate the total fee for a bulk craft.
     */
    public double calculateBulkFee(CraftRecipe recipe, int count) {
        if (!enabled) return 0.0;
        double perUnit = getFee(recipe);
        if (chargePerItemInBulk) {
            return perUnit * count;
        } else {
            return perUnit; // flat fee
        }
    }

    /**
     * Check if the player can afford the fee for a single craft.
     */
    public boolean canAfford(UUID uuid, CraftRecipe recipe) {
        if (!enabled) return true;
        double fee = getFee(recipe);
        if (fee <= 0) return true;
        double balance = fishApi.getBalance(uuid);
        return balance >= fee;
    }

    /**
     * Check if the player can afford a bulk craft.
     */
    public boolean canAffordBulk(UUID uuid, CraftRecipe recipe, int count) {
        if (!enabled) return true;
        double totalFee = calculateBulkFee(recipe, count);
        if (totalFee <= 0) return true;
        double balance = fishApi.getBalance(uuid);
        return balance >= totalFee;
    }

    /**
     * Charge the fee for a single craft. Returns true if successful.
     * Does NOT charge if economy is disabled or fee is 0.
     */
    public boolean chargeFee(UUID uuid, CraftRecipe recipe) {
        if (!enabled) return true;
        double fee = getFee(recipe);
        if (fee <= 0) return true;
        return fishApi.withdrawPlayer(uuid, fee);
    }

    /**
     * Charge the fee for a bulk craft. Returns true if successful.
     */
    public boolean chargeBulkFee(UUID uuid, CraftRecipe recipe, int count) {
        if (!enabled) return true;
        double totalFee = calculateBulkFee(recipe, count);
        if (totalFee <= 0) return true;
        return fishApi.withdrawPlayer(uuid, totalFee);
    }

    /**
     * Get the player's current balance.
     */
    public double getBalance(UUID uuid) {
        return fishApi.getBalance(uuid);
    }
}
