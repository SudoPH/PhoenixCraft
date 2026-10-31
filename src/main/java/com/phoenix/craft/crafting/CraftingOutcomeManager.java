package com.phoenix.craft.crafting;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.attributes.RecipeAttributesManager;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.CraftResult;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Wraps the PhoenixFish {@code craft()} call with extended outcome rolls:
 * success/fail (F4) and critical success (F5).
 *
 * <p>If a recipe has no extended attributes, this manager delegates straight
 * to PhoenixFish's API — zero behavior change from v1.0.6.</p>
 *
 * @since 1.0.7 (features #4, #5)
 */
public class CraftingOutcomeManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final RecipeAttributesManager attributes;

    public CraftingOutcomeManager(PhoenixCraft plugin,
                                    Supplier<PhoenixFishAPI> fishApiSupplier,
                                    RecipeAttributesManager attributes) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        this.attributes = attributes;
    }

    /**
     * Outcome of a single craft attempt with extended semantics.
     */
    public record Outcome(
            CraftResult baseResult,    // raw result from PhoenixFish (SUCCESS / LOW_LEVEL / etc.)
            boolean rolled,            // true if we applied success/crit rolls
            boolean success,           // true if the craft actually produced an item
            boolean critical,          // true if a crit roll happened (only on success)
            int outputMultiplier,      // 1 (normal) or N (crit) — applied to result count
            int xpMultiplier           // 1 (normal) or N (crit) — applied to XP reward
    ) {
        public static Outcome passthrough(CraftResult r) {
            return new Outcome(r, false, r == CraftResult.SUCCESS, false, 1, 1);
        }
        public static Outcome failed(CraftResult r) {
            return new Outcome(r, true, false, false, 0, 0);
        }
        public static Outcome successNormal(CraftResult r) {
            return new Outcome(r, true, true, false, 1, 1);
        }
        public static Outcome successCrit(CraftResult r, int outMult, int xpMult) {
            return new Outcome(r, true, true, true, outMult, xpMult);
        }
    }

    /**
     * Apply success/crit rolls on top of an ALREADY-SUCCESSFUL PhoenixFish
     * craft. The caller has already invoked {@code api.craft(player, recipe)}
     * and received {@link CraftResult#SUCCESS}. This method:
     * <ul>
     *   <li>Rolls the success chance (F4). On failure, revokes the result
     *       item + XP that PhoenixFish just granted.</li>
     *   <li>Rolls the crit chance (F5). On crit, grants extra output items
     *       + extra XP.</li>
     * </ul>
     *
     * <p>If the recipe has no extended attributes (both chances at defaults),
     * this method is a no-op passthrough.</p>
     */
    public Outcome applyRolls(Player player, CraftRecipe recipe) {
        double successChance = attributes.getSuccessChance(recipe.id());
        double critChance = attributes.getCritChance(recipe.id());

        // If both are at defaults (1.0 / 0.0), skip rolling entirely.
        if (successChance >= 1.0 && critChance <= 0.0) {
            return Outcome.passthrough(CraftResult.SUCCESS);
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        PhoenixFishAPI api = fishApiSupplier.get();

        // F4: success roll
        if (successChance < 1.0 && rng.nextDouble() >= successChance) {
            // Failure — revoke the result item + XP that PhoenixFish just granted.
            if (api != null) {
                try { api.addCraftingXp(player, -recipe.xpReward()); } catch (Throwable ignored) {}
            }
            removeOneResultItem(player, recipe.resultItem());
            return Outcome.failed(CraftResult.SUCCESS);
        }

        // F5: crit roll
        if (critChance > 0.0 && rng.nextDouble() < critChance) {
            int outMult = (int) Math.round(attributes.getCritOutputMultiplier(recipe.id()));
            int xpMult = (int) Math.round(attributes.getCritXpMultiplier(recipe.id()));
            if (outMult < 1) outMult = 1;
            if (xpMult < 1) xpMult = 1;
            for (int i = 1; i < outMult; i++) {
                giveItem(player, recipe.resultItem());
            }
            if (xpMult > 1 && api != null) {
                try { api.addCraftingXp(player, recipe.xpReward() * (xpMult - 1)); }
                catch (Throwable ignored) {}
            }
            return Outcome.successCrit(CraftResult.SUCCESS, outMult, xpMult);
        }

        return Outcome.successNormal(CraftResult.SUCCESS);
    }

    /**
     * Legacy entry point — kept for API completeness but NOT used by the
     * main craft path (which calls {@link #applyRolls} after PhoenixFish's
     * own {@code craft()} succeeds). Calling this method directly will
     * execute a SECOND craft via PhoenixFish — usually not what you want.
     */
    public Outcome craft(Player player, CraftRecipe recipe) {
        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) return Outcome.passthrough(CraftResult.NO_DATA);
        CraftResult base;
        try {
            base = api.craft(player, recipe);
        } catch (Throwable t) {
            return Outcome.passthrough(CraftResult.INTERNAL_ERROR);
        }
        if (base != CraftResult.SUCCESS) return Outcome.passthrough(base);
        return applyRolls(player, recipe);
    }

    private void removeOneResultItem(Player player, ItemStack result) {
        if (result == null) return;
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != result.getType()) continue;
            if (is.getAmount() <= 1) {
                inv.setItem(i, null);
            } else {
                is.setAmount(is.getAmount() - 1);
                inv.setItem(i, is);
            }
            player.updateInventory();
            return;
        }
    }

    private void giveItem(Player player, ItemStack item) {
        if (item == null) return;
        var overflow = player.getInventory().addItem(item.clone());
        for (ItemStack leftover : overflow.values()) {
            if (player.getWorld() != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }
}
