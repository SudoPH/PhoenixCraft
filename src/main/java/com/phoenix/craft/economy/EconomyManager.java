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
 * <p><b>Reload support (v1.0.1):</b> All config values (enabled, default-fee,
 * charge-per-item-in-bulk) are read on every call to the getters — so
 * {@code /pcraft reload} picks them up without re-creating the manager.</p>
 *
 * @since 0.5.0
 */
public class EconomyManager {

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;

    public EconomyManager(PhoenixCraft plugin, PhoenixFishAPI fishApi) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        // Log initial state so server admins can confirm the config was applied
        plugin.getLogger().info("EconomyManager initialized — default fee: $"
                + getFeeConfig() + ", charge per item in bulk: "
                + getChargePerItemInBulkConfig()
                + (isEnabled() ? "" : " (disabled)"));
    }

    /**
     * Read the enabled flag from config every time. This makes /pcraft reload
     * pick up changes immediately.
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("economy.enabled", false);
    }

    /**
     * Read charge-per-item-in-bulk from config every time.
     */
    private boolean getChargePerItemInBulkConfig() {
        return plugin.getConfig().getBoolean("economy.charge-per-item-in-bulk", true);
    }

    /**
     * Read default-fee from config every time.
     */
    private double getFeeConfig() {
        return plugin.getConfig().getDouble("economy.default-fee", 0.0);
    }

    /**
     * Get the crafting fee for a recipe.
     * Returns 0 if economy is disabled or the recipe has no fee.
     */
    public double getFee(CraftRecipe recipe) {
        if (!isEnabled()) return 0.0;
        if (recipe == null) return 0.0;
        // Use default-fee. Future: PhoenixFish could expose crafting-fee on CraftRecipe.
        return getFeeConfig();
    }

    /**
     * Calculate the total fee for a bulk craft.
     */
    public double calculateBulkFee(CraftRecipe recipe, int count) {
        if (!isEnabled()) return 0.0;
        double perUnit = getFee(recipe);
        if (getChargePerItemInBulkConfig()) {
            return perUnit * count;
        } else {
            return perUnit; // flat fee
        }
    }

    /**
     * Check if the player can afford the fee for a single craft.
     */
    public boolean canAfford(UUID uuid, CraftRecipe recipe) {
        if (!isEnabled()) return true;
        double fee = getFee(recipe);
        if (fee <= 0) return true;
        double balance = fishApi.getBalance(uuid);
        return balance >= fee;
    }

    /**
     * Check if the player can afford a bulk craft.
     */
    public boolean canAffordBulk(UUID uuid, CraftRecipe recipe, int count) {
        if (!isEnabled()) return true;
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
        if (!isEnabled()) return true;
        double fee = getFee(recipe);
        if (fee <= 0) return true;
        return fishApi.withdrawPlayer(uuid, fee);
    }

    /**
     * Charge the fee for a bulk craft. Returns true if successful.
     */
    public boolean chargeBulkFee(UUID uuid, CraftRecipe recipe, int count) {
        if (!isEnabled()) return true;
        double totalFee = calculateBulkFee(recipe, count);
        if (totalFee <= 0) return true;
        return fishApi.withdrawPlayer(uuid, totalFee);
    }

    /**
     * Get the player's current balance (from PhoenixFish's DB-backed economy).
     */
    public double getBalance(UUID uuid) {
        return fishApi.getBalance(uuid);
    }
}
