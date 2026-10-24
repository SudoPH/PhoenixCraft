package com.phoenix.craft.stats;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player crafting statistics.
 *
 * <p>Storage: {@code plugins/PhoenixCraft/stats.yml} — single YAML file holding
 * every player's stats. Loaded fully into memory on enable, written back to
 * disk after each save() call.</p>
 *
 * <p>Modularity: this entire system can be disabled in {@code config.yml} via
 * {@code stats.enabled: false}. When disabled, the manager is constructed but
 * performs no I/O and returns zero/empty for all queries.</p>
 *
 * <p>Tracked metrics per player:
 * <ul>
 *   <li>Total crafts (single + bulk combined)</li>
 *   <li>Per-recipe craft counts</li>
 *   <li>Total XP earned from crafting</li>
 *   <li>First-craft timestamp</li>
 *   <li>Last-craft timestamp</li>
 * </ul>
 *
 * @since 0.3.0
 */
public class StatsManager {

    private final PhoenixCraft plugin;
    private final boolean enabled;

    /** In-memory stats cache, keyed by player UUID. */
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    /** Dirty flag — set to true when stats change, reset after save. */
    private volatile boolean dirty = false;

    /** Faz 0.9.0: cached leaderboard (invalidated on dirty) */
    private volatile List<Map.Entry<UUID, Integer>> cachedLeaderboard = null;

    public StatsManager(PhoenixCraft plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("stats.enabled", true);
        if (enabled) {
            load();
            plugin.getLogger().info("StatsManager enabled — tracking " + cache.size() + " players.");
        } else {
            plugin.getLogger().info("StatsManager disabled (stats.enabled=false).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    private File getStatsFile() {
        return new File(plugin.getDataFolder(), "stats.yml");
    }

    private void load() {
        File file = getStatsFile();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection sec = players.getConfigurationSection(uuidStr);
                if (sec == null) continue;

                PlayerStats stats = new PlayerStats();
                stats.totalCrafts = sec.getInt("total-crafts", 0);
                stats.totalXpEarned = sec.getInt("total-xp", 0);
                stats.firstCraftTimestamp = sec.getLong("first-craft", 0L);
                stats.lastCraftTimestamp = sec.getLong("last-craft", 0L);
                stats.lastRecipeId = sec.getString("last-recipe", null);

                ConfigurationSection recipes = sec.getConfigurationSection("recipes");
                if (recipes != null) {
                    for (String recipeId : recipes.getKeys(false)) {
                        stats.recipeCounts.put(recipeId, recipes.getInt(recipeId, 0));
                    }
                }

                cache.put(uuid, stats);
            } catch (IllegalArgumentException ignored) {
                // invalid UUID format — skip
            }
        }
    }

    /**
     * Save the entire cache to disk. Should be called from main thread or
     * a sync task (Bukkit YAML API is not thread-safe).
     */
    public synchronized void save() {
        if (!enabled) return;
        if (!dirty) return;

        File file = getStatsFile();
        FileConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<UUID, PlayerStats> entry : cache.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerStats stats = entry.getValue();

            cfg.set("players." + uuidStr + ".total-crafts", stats.totalCrafts);
            cfg.set("players." + uuidStr + ".total-xp", stats.totalXpEarned);
            cfg.set("players." + uuidStr + ".first-craft", stats.firstCraftTimestamp);
            cfg.set("players." + uuidStr + ".last-craft", stats.lastCraftTimestamp);
            cfg.set("players." + uuidStr + ".last-recipe", stats.lastRecipeId);

            for (Map.Entry<String, Integer> recipe : stats.recipeCounts.entrySet()) {
                cfg.set("players." + uuidStr + ".recipes." + recipe.getKey(),
                        recipe.getValue());
            }
        }

        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stats.yml: " + e.getMessage());
        }
    }

    /**
     * Save on plugin disable — synchronous flush.
     */
    public void shutdown() {
        save();
    }

    // =====================================================================
    //  Recording
    // =====================================================================

    /**
     * Record a successful craft. Called by CraftingGUIManager after each
     * successful single or bulk craft.
     *
     * <p>Faz 0.9.0: Does NOT save to disk immediately — just sets the dirty flag.
     * The auto-save timer (or shutdown) will persist the data.</p>
     *
     * @param player   the crafting player
     * @param recipe   the recipe that was crafted
     * @param count    how many items were produced (1 for single, N for bulk)
     * @param xpAwarded total XP awarded
     */
    public void recordCraft(Player player, CraftRecipe recipe, int count, int xpAwarded) {
        if (!enabled) return;

        UUID uuid = player.getUniqueId();
        PlayerStats stats = cache.computeIfAbsent(uuid, k -> new PlayerStats());

        long now = System.currentTimeMillis();
        if (stats.firstCraftTimestamp == 0L) {
            stats.firstCraftTimestamp = now;
        }
        stats.lastCraftTimestamp = now;
        stats.lastRecipeId = recipe.id();
        stats.totalCrafts += count;
        stats.totalXpEarned += xpAwarded;

        stats.recipeCounts.merge(recipe.id(), count, Integer::sum);

        dirty = true;
        cachedLeaderboard = null; // invalidate cache
        // Faz 0.9.0: do NOT save here — auto-save timer handles it
    }

    // =====================================================================
    //  Queries
    // =====================================================================

    public PlayerStats getStats(UUID uuid) {
        if (!enabled) return new PlayerStats();
        return cache.getOrDefault(uuid, new PlayerStats());
    }

    /**
     * Returns the top-N crafters across the whole server, sorted by total crafts.
     *
     * @param limit max entries to return
     * @return list of (uuid, totalCrafts) pairs, descending by totalCrafts
     */
    public List<Map.Entry<UUID, Integer>> getTopCrafters(int limit) {
        if (!enabled) return Collections.emptyList();

        // Faz 0.9.0: use cached leaderboard if available
        if (cachedLeaderboard != null) {
            if (cachedLeaderboard.size() <= limit) {
                return cachedLeaderboard;
            }
            return cachedLeaderboard.subList(0, limit);
        }

        List<Map.Entry<UUID, Integer>> all = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStats> entry : cache.entrySet()) {
            all.add(Map.entry(entry.getKey(), entry.getValue().totalCrafts));
        }
        all.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Cache the full sorted list
        cachedLeaderboard = all;

        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    /**
     * Get the number of distinct recipes this player has ever crafted.
     */
    public int getUniqueRecipesCrafted(UUID uuid) {
        return getStats(uuid).recipeCounts.size();
    }

    /**
     * Get the count of times a specific recipe was crafted by a player.
     */
    public int getRecipeCount(UUID uuid, String recipeId) {
        return getStats(uuid).recipeCounts.getOrDefault(recipeId, 0);
    }

    /**
     * In-memory stats holder.
     */
    public static class PlayerStats {
        public int totalCrafts = 0;
        public int totalXpEarned = 0;
        public long firstCraftTimestamp = 0L;
        public long lastCraftTimestamp = 0L;
        public String lastRecipeId = null;
        /** Recipe ID → times crafted. */
        public final Map<String, Integer> recipeCounts = new LinkedHashMap<>();
    }
}
