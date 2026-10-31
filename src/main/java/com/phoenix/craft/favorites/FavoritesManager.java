package com.phoenix.craft.favorites;

import com.phoenix.craft.PhoenixCraft;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player recipe favorites/bookmarks persistence.
 *
 * <p>Stores each player's favorited recipe IDs in {@code plugins/PhoenixCraft/favorites.yml}.
 * The file is loaded once on enable, kept in a concurrent in-memory cache, and
 * saved on shutdown (and periodically by the main auto-save task).</p>
 *
 * <p>File format:</p>
 * <pre>
 * players:
 *   &lt;uuid-1&gt;:
 *     - "rod:cosmic_rod"
 *     - "bait:shiny_lure"
 *   &lt;uuid-2&gt;:
 *     - "rod:basic_rod"
 * </pre>
 *
 * <p>Thread-safety: all read/write methods on the in-memory cache are safe to
 * call from any thread. {@link #save()} is synchronized to serialise disk
 * writes.</p>
 *
 * @since 1.0.4
 */
public final class FavoritesManager {

    private final PhoenixCraft plugin;
    private final File favoritesFile;
    private final Map<UUID, Set<String>> favoritesByPlayer = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public FavoritesManager(PhoenixCraft plugin) {
        this.plugin = plugin;
        this.favoritesFile = new File(plugin.getDataFolder(), "favorites.yml");
    }

    /**
     * Load favorites from disk into the in-memory cache. Called once on
     * plugin enable. If the file does not exist, an empty cache is used
     * (no error — the file will be created on first save).
     */
    public synchronized void load() {
        if (!favoritesFile.exists()) {
            plugin.getLogger().info("favorites.yml not found — starting with empty favorites cache.");
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(favoritesFile);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        int totalLoaded = 0;
        for (String uuidStr : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("favorites.yml: invalid UUID '" + uuidStr + "' — skipping.");
                continue;
            }
            List<String> recipeIds = players.getStringList(uuidStr);
            Set<String> set = Collections.synchronizedSet(new LinkedHashSet<>(recipeIds));
            favoritesByPlayer.put(uuid, set);
            totalLoaded += set.size();
        }
        plugin.getLogger().info("Loaded " + totalLoaded + " favorited recipes across "
                + favoritesByPlayer.size() + " players.");
    }

    /**
     * Save the in-memory cache to disk. Called on shutdown and by the
     * periodic auto-save task. No-op if nothing has changed since the last
     * save (unless {@code force} is true).
     *
     * @param force if true, save even when the cache is not marked dirty
     */
    public synchronized void save(boolean force) {
        if (!dirty && !force) {
            return;
        }
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : favoritesByPlayer.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            cfg.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(favoritesFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save favorites.yml: " + e.getMessage());
        }
    }

    /** Convenience wrapper — always forces. */
    public void save() {
        save(true);
    }

    /**
     * Save + log. Called from the main plugin's {@code onDisable}.
     */
    public synchronized void shutdown() {
        save(true);
        plugin.getLogger().info("FavoritesManager shut down.");
    }

    /**
     * @return true if the favorites feature is enabled in config.yml
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("favorites.enabled", true);
    }

    /**
     * Check whether a recipe is favorited by the given player.
     *
     * @param playerUuid the player's UUID
     * @param recipeId   the recipe id (e.g. "rod:cosmic_rod")
     * @return true if the recipe is in the player's favorites list
     */
    public boolean isFavorite(UUID playerUuid, String recipeId) {
        if (recipeId == null) return false;
        Set<String> set = favoritesByPlayer.get(playerUuid);
        return set != null && set.contains(recipeId);
    }

    /**
     * Toggle the favorite state of a recipe for a player.
     *
     * @param playerUuid the player's UUID
     * @param recipeId   the recipe id
     * @return the new state — {@code true} if the recipe is now favorited,
     *         {@code false} if it was removed
     */
    public boolean toggleFavorite(UUID playerUuid, String recipeId) {
        if (recipeId == null) return false;
        Set<String> set = favoritesByPlayer.computeIfAbsent(playerUuid,
                k -> Collections.synchronizedSet(new LinkedHashSet<>()));
        boolean nowFavorite;
        synchronized (set) {
            if (set.contains(recipeId)) {
                set.remove(recipeId);
                nowFavorite = false;
            } else {
                set.add(recipeId);
                nowFavorite = true;
            }
        }
        dirty = true;
        return nowFavorite;
    }

    /**
     * Get a snapshot of all recipe IDs favorited by the given player.
     * The returned list is a copy — modifying it does not affect the cache.
     *
     * @param playerUuid the player's UUID
     * @return an unmodifiable list of recipe IDs (empty if none)
     */
    public List<String> getFavorites(UUID playerUuid) {
        Set<String> set = favoritesByPlayer.get(playerUuid);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        synchronized (set) {
            return List.copyOf(set);
        }
    }

    /**
     * @param playerUuid the player's UUID
     * @return the number of recipes the player has favorited
     */
    public int getFavoriteCount(UUID playerUuid) {
        Set<String> set = favoritesByPlayer.get(playerUuid);
        if (set == null) return 0;
        synchronized (set) {
            return set.size();
        }
    }

    /**
     * Remove all favorites for a player (e.g. on /pcraft reset). Used by
     * admin commands — not exposed in the GUI.
     */
    public void clear(UUID playerUuid) {
        Set<String> removed = favoritesByPlayer.remove(playerUuid);
        if (removed != null && !removed.isEmpty()) {
            dirty = true;
        }
    }

    /**
     * Alias for {@link #clear(UUID)} — removes all favorites for a player.
     * Some command paths (e.g. {@code /pcraft favorites clear <player>})
     * call this method by name; keep both names so the FavoritesManager
     * works regardless of which name the caller uses.
     *
     * @param playerUuid the player whose favorites to wipe
     */
    public void clearAll(UUID playerUuid) {
        clear(playerUuid);
    }
}
