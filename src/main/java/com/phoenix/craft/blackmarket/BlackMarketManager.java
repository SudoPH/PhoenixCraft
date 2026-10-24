package com.phoenix.craft.blackmarket;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Rotating shop where random recipe books appear for purchase.
 *
 * <p>The black market refreshes on a configurable timer (or manually via
 * admin command). Each refresh picks a random selection of recipes from
 * the pool and assigns them random prices. Players buy recipe books with
 * PhoenixFish balance.</p>
 *
 * <p>Storage: {@code plugins/PhoenixCraft/blackmarket.yml} — stores current
 * stock, prices, per-player purchase counts, and last refresh timestamp.</p>
 *
 * <p>Modularity: disabled when {@code blackmarket.enabled: false} in config.yml.</p>
 *
 * @since 0.6.0
 */
public class BlackMarketManager {

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;
    private final boolean enabled;

    /** Current stock: recipeId → price. */
    private final Map<String, Double> stock = new LinkedHashMap<>();

    /** Per-player purchase count this refresh cycle. */
    private final Map<UUID, Integer> purchasesThisCycle = new HashMap<>();

    /** Last refresh timestamp (epoch millis). */
    private long lastRefresh;

    /** Scheduled task ID for auto-refresh. */
    private int refreshTaskId = -1;

    public BlackMarketManager(PhoenixCraft plugin, PhoenixFishAPI fishApi) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        this.enabled = plugin.getConfig().getBoolean("blackmarket.enabled", false);

        if (enabled) {
            load();
            scheduleRefresh();
            plugin.getLogger().info("BlackMarketManager enabled — "
                    + stock.size() + " items in stock.");
        } else {
            plugin.getLogger().info("BlackMarketManager disabled (blackmarket.enabled=false).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    private File getFile() {
        return new File(plugin.getDataFolder(), "blackmarket.yml");
    }

    private void load() {
        File file = getFile();
        if (!file.exists()) {
            // First time — generate stock immediately
            refreshStock(false);
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        lastRefresh = cfg.getLong("last-refresh", 0L);

        ConfigurationSection stockSec = cfg.getConfigurationSection("stock");
        if (stockSec != null) {
            for (String recipeId : stockSec.getKeys(false)) {
                stock.put(recipeId.toLowerCase(), stockSec.getDouble(recipeId, 0.0));
            }
        }

        ConfigurationSection purchasesSec = cfg.getConfigurationSection("purchases");
        if (purchasesSec != null) {
            for (String uuidStr : purchasesSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    purchasesThisCycle.put(uuid, purchasesSec.getInt(uuidStr, 0));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Check if a refresh is overdue
        long intervalMs = getRefreshIntervalMs();
        if (intervalMs > 0 && System.currentTimeMillis() - lastRefresh >= intervalMs) {
            refreshStock(false);
        }
    }

    public synchronized void save() {
        if (!enabled) return;

        File file = getFile();
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("last-refresh", lastRefresh);

        for (Map.Entry<String, Double> entry : stock.entrySet()) {
            cfg.set("stock." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<UUID, Integer> entry : purchasesThisCycle.entrySet()) {
            cfg.set("purchases." + entry.getKey().toString(), entry.getValue());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save blackmarket.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
        }
        save();
    }

    // =====================================================================
    //  Stock Management
    // =====================================================================

    /**
     * Generate new random stock. Clears old stock and purchase counts.
     *
     * @param announce whether to broadcast a refresh announcement
     */
    public void refreshStock(boolean announce) {
        stock.clear();
        purchasesThisCycle.clear();
        lastRefresh = System.currentTimeMillis();

        // Get the recipe pool
        List<CraftRecipe> pool = getRecipePool();
        if (pool.isEmpty()) {
            plugin.getLogger().warning("Black market recipe pool is empty — no stock generated.");
            save();
            return;
        }

        // Shuffle and pick stock-count recipes
        Collections.shuffle(pool);
        int stockCount = plugin.getConfig().getInt("blackmarket.stock-count", 5);
        int count = Math.min(stockCount, pool.size());

        double minPrice = plugin.getConfig().getDouble("blackmarket.min-price", 50);
        double maxPrice = plugin.getConfig().getDouble("blackmarket.max-price", 500);
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            CraftRecipe recipe = pool.get(i);
            double price = minPrice + (maxPrice - minPrice) * random.nextDouble();
            price = Math.round(price * 100.0) / 100.0; // round to 2 decimals
            stock.put(recipe.id().toLowerCase(), price);
        }

        save();

        plugin.getLogger().info("Black market refreshed — " + stock.size() + " items in stock.");

        if (announce && plugin.getConfig().getBoolean("blackmarket.announce-refresh", true)) {
            Bukkit.broadcast(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(plugin.getMessageManager().getPlainMessage("blackmarket-refresh-announce")));
        }
    }

    /**
     * Get the list of recipes that can appear in the black market.
     */
    private List<CraftRecipe> getRecipePool() {
        Object poolObj = plugin.getConfig().get("blackmarket.pool");
        List<CraftRecipe> allRecipes = fishApi.getCraftingRecipes();

        // If pool is a string (ALL, ROD, BAIT)
        if (poolObj instanceof String) {
            String poolStr = ((String) poolObj).toUpperCase();
            return switch (poolStr) {
                case "ROD" -> allRecipes.stream()
                        .filter(r -> "rod".equalsIgnoreCase(r.category()))
                        .toList();
                case "BAIT" -> allRecipes.stream()
                        .filter(r -> "bait".equalsIgnoreCase(r.category()))
                        .toList();
                default -> allRecipes; // ALL
            };
        }

        // If pool is a list of recipe IDs
        if (poolObj instanceof List<?> poolList) {
            List<CraftRecipe> filtered = new ArrayList<>();
            for (CraftRecipe r : allRecipes) {
                if (poolList.contains(r.id())) {
                    filtered.add(r);
                }
            }
            return filtered;
        }

        return allRecipes; // fallback: all
    }

    // =====================================================================
    //  Queries
    // =====================================================================

    public Map<String, Double> getStock() {
        return Collections.unmodifiableMap(stock);
    }

    public Double getPrice(String recipeId) {
        return stock.get(recipeId.toLowerCase());
    }

    public boolean isInStock(String recipeId) {
        return stock.containsKey(recipeId.toLowerCase());
    }

    public int getPurchaseCount(UUID uuid) {
        return purchasesThisCycle.getOrDefault(uuid, 0);
    }

    public int getPurchaseLimit() {
        return plugin.getConfig().getInt("blackmarket.purchase-limit-per-refresh", 0);
    }

    public boolean canPurchase(UUID uuid) {
        int limit = getPurchaseLimit();
        if (limit <= 0) return true; // no limit
        return getPurchaseCount(uuid) < limit;
    }

    public void recordPurchase(UUID uuid) {
        purchasesThisCycle.merge(uuid, 1, Integer::sum);
        save();
    }

    public long getLastRefresh() {
        return lastRefresh;
    }

    public long getRefreshIntervalMs() {
        long minutes = plugin.getConfig().getLong("blackmarket.refresh-interval-minutes", 360);
        if (minutes <= 0) return 0; // manual only
        return minutes * 60 * 1000L;
    }

    /**
     * Get milliseconds until next refresh. Returns 0 if manual-only or overdue.
     */
    public long getTimeUntilRefresh() {
        long interval = getRefreshIntervalMs();
        if (interval <= 0) return 0;
        long elapsed = System.currentTimeMillis() - lastRefresh;
        long remaining = interval - elapsed;
        return Math.max(0, remaining);
    }

    // =====================================================================
    //  Auto-Refresh Scheduler
    // =====================================================================

    private void scheduleRefresh() {
        long interval = getRefreshIntervalMs();
        if (interval <= 0) return; // manual only

        long intervalTicks = interval / 50; // ms → ticks (50ms per tick)
        refreshTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            refreshStock(true);
        }, intervalTicks, intervalTicks).getTaskId();
    }
}
