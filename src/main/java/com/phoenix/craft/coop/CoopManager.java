package com.phoenix.craft.coop;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Crafting co-ops (F11). Two or more players contribute materials to the
 * same recipe in a shared session. Each contributor gets a fraction of the
 * XP and one copy of the result.
 *
 * <p>Session lifecycle:
 * <ol>
 *   <li>Host runs {@code /pcraft coop host <recipeId>} — creates a session.</li>
 *   <li>Others run {@code /pcraft coop join <hostName>} — added to session.</li>
 *   <li>Each contributor runs {@code /pcraft coop contribute <slot>} to
 *       commit their share of materials.</li>
 *   <li>Host runs {@code /pcraft coop craft} — executes the craft.</li>
 * </ol>
 *
 * <p>This is a simplified server-side implementation (no shared GUI). A
 * future enhancement could open a shared Inventory that all contributors
 * see in real time via packet sync.</p>
 *
 * @since 1.0.7 (feature #11)
 */
public class CoopManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final Map<UUID, CoopSession> sessions = new ConcurrentHashMap<>();

    public CoopManager(PhoenixCraft plugin, Supplier<PhoenixFishAPI> fishApiSupplier) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
    }

    public static class CoopSession {
        public final UUID sessionId;
        public final UUID hostUuid;
        public final String hostName;
        public final String recipeId;
        public final Set<UUID> contributors = ConcurrentHashMap.newKeySet();
        public final Map<UUID, Map<Integer, ItemStack>> contributions = new ConcurrentHashMap<>();
        public final long createdAt;

        public CoopSession(UUID sessionId, UUID hostUuid, String hostName, String recipeId) {
            this.sessionId = sessionId;
            this.hostUuid = hostUuid;
            this.hostName = hostName;
            this.recipeId = recipeId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public String hostSession(Player host, String recipeId) {
        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) return "coop-no-api";
        if (api.getCraftingRecipe(recipeId) == null) return "coop-recipe-unknown";

        // Cancel any existing session the host already has.
        sessions.values().removeIf(s -> s.hostUuid.equals(host.getUniqueId()));

        CoopSession session = new CoopSession(UUID.randomUUID(), host.getUniqueId(),
                host.getName(), recipeId.toLowerCase());
        session.contributors.add(host.getUniqueId());
        sessions.put(session.sessionId, session);
        return null;
    }

    public String joinSession(Player joiner, UUID hostUuid) {
        CoopSession session = null;
        for (CoopSession s : sessions.values()) {
            if (s.hostUuid.equals(hostUuid)) { session = s; break; }
        }
        if (session == null) return "coop-not-found";
        if (!session.contributors.add(joiner.getUniqueId())) return "coop-already-joined";
        return null;
    }

    public String contribute(Player contributor, int slot, ItemStack item) {
        CoopSession session = findSessionByPlayer(contributor.getUniqueId());
        if (session == null) return "coop-not-in-session";
        if (item == null || item.getType().isAir()) return "coop-invalid-item";
        session.contributions
                .computeIfAbsent(contributor.getUniqueId(), k -> new HashMap<>())
                .put(slot, item.clone());
        return null;
    }

    public String craftSession(Player host) {
        CoopSession session = findSessionByPlayer(host.getUniqueId());
        if (session == null) return "coop-not-in-session";
        if (!session.hostUuid.equals(host.getUniqueId())) return "coop-not-host";

        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) return "coop-no-api";
        CraftRecipe recipe = api.getCraftingRecipe(session.recipeId);
        if (recipe == null) return "coop-recipe-unknown";

        // Aggregate all contributions.
        Map<org.bukkit.Material, Integer> pooled = new HashMap<>();
        for (Map<UUID, Map<Integer, ItemStack>> outer : java.util.List.of(session.contributions)) {
            for (Map<Integer, ItemStack> inner : outer.values()) {
                for (ItemStack is : inner.values()) {
                    if (is != null && !is.getType().isAir()) {
                        pooled.merge(is.getType(), is.getAmount(), Integer::sum);
                    }
                }
            }
        }
        // Verify all ingredients are met.
        // v1.1.2 — NPE hardening: null ingredients list / null ingredient / null item
        java.util.List<CraftRecipe.Ingredient> ings = recipe.ingredients();
        if (ings == null) ings = java.util.List.of();
        for (CraftRecipe.Ingredient ing : ings) {
            if (ing == null) continue;
            ItemStack ingItem = ing.item();
            if (ingItem == null) return "coop-insufficient-materials";
            int have = pooled.getOrDefault(ingItem.getType(), 0);
            if (have < ing.amount()) return "coop-insufficient-materials";
        }

        // Execute: grant result + XP to each contributor.
        ItemStack resultItem = recipe.resultItem();   // null-checked below
        int contributorCount = session.contributors.size();
        for (UUID cu : session.contributors) {
            Player cp = Bukkit.getPlayer(cu);
            if (cp == null || !cp.isOnline()) continue;
            // v1.1.2 — NPE guard: skip grant if resultItem is null (corrupted recipe).
            if (resultItem != null) {
                var overflow = cp.getInventory().addItem(resultItem.clone());
                for (ItemStack leftover : overflow.values()) {
                    if (cp.getWorld() != null) {
                        cp.getWorld().dropItemNaturally(cp.getLocation(), leftover);
                    }
                }
            }
            // Split XP evenly (min 1 each).
            int xpShare = Math.max(1, recipe.xpReward() / contributorCount);
            try { api.addCraftingXp(cp, xpShare); } catch (Throwable ignored) {}
        }

        // Consume one set of materials from the pool (best-effort — we don't
        // take from inventories here; contributors are expected to have
        // /pcraft coop contribute'd the items, which removed them already).

        // Close the session.
        sessions.remove(session.sessionId);
        return null;
    }

    public CoopSession findSessionByPlayer(UUID uuid) {
        for (CoopSession s : sessions.values()) {
            if (s.contributors.contains(uuid)) return s;
        }
        return null;
    }

    public java.util.Collection<CoopSession> getActiveSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void cancelSession(Player host) {
        CoopSession s = findSessionByPlayer(host.getUniqueId());
        if (s != null && s.hostUuid.equals(host.getUniqueId())) {
            sessions.remove(s.sessionId);
        }
    }
}
