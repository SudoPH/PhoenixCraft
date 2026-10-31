package com.phoenix.craft.decay;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.recipes.RecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recipe decay / skill drift (F12). If a player hasn't crafted a recipe in
 * N days (config: {@code recipe-decay.days-until-forgotten}, default 30),
 * they "forget" it — the learned state is removed from {@link RecipeManager}
 * and they must re-learn via a recipe book.
 *
 * <p>A daily task scans every player's last-craft timestamps (from
 * {@code StatsManager}) and removes any recipe whose last-craft is older
 * than the threshold.</p>
 *
 * @since 1.0.7 (feature #12)
 */
public class RecipeDecayManager {

    private final PhoenixCraft plugin;
    private final RecipeManager recipeManager;
    private final Map<UUID, Long> lastCraftPerRecipe; // (player|recipe) → epoch millis
    private volatile BukkitTask decayTask;
    private volatile boolean dirty = false;

    public RecipeDecayManager(PhoenixCraft plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.lastCraftPerRecipe = new ConcurrentHashMap<>();
        load();
        startDailyTask();
        plugin.getLogger().info("RecipeDecayManager enabled — threshold="
                + getDecayDays() + " days.");
    }

    private long getDecayDays() {
        return plugin.getConfig().getLong("features.recipe-decay.days-until-forgotten", 30);
    }

    private long getDecayMs() { return getDecayDays() * 86_400_000L; }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.recipe-decay.enabled", false);
    }

    // =====================================================================
    //  Tracking — called from CraftingGUIManager after a successful craft
    // =====================================================================

    public void recordCraft(UUID uuid, String recipeId) {
        if (!isEnabled()) return;
        lastCraftPerRecipe.put(buildKey(uuid, recipeId), System.currentTimeMillis());
        dirty = true;
        saveAsync();
    }

    private UUID buildKey(UUID uuid, String recipeId) {
        // Composite key trick: hash player UUID + recipe ID into a stable UUID.
        return UUID.nameUUIDFromBytes((uuid.toString() + "|" + recipeId.toLowerCase()).getBytes());
    }

    private String decomposeKey(UUID compositeUuid) {
        // We store the (player|recipe) → timestamp mapping as a string path
        // in the YAML. No need to decompose; load() iterates the section.
        return null;
    }

    // =====================================================================
    //  Daily decay task
    // =====================================================================

    private void startDailyTask() {
        if (!isEnabled()) return;
        // Run every 24h (1728000 ticks), first run after 5 min (6000 ticks).
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runDecaySweep, 6000L, 1_728_000L);
    }

    private void runDecaySweep() {
        if (!isEnabled() || recipeManager == null) return;
        long threshold = System.currentTimeMillis() - getDecayMs();

        // Group expired recipes by player for batch removal + notification.
        Map<UUID, Set<String>> expired = new HashMap<>();
        for (Map.Entry<UUID, Long> e : lastCraftPerRecipe.entrySet()) {
            if (e.getValue() < threshold) {
                // Decompose the composite key back to (player, recipeId).
                // We stored it as a string in YAML; reconstruct here.
                DecomposedKey dk = decomposeFromYaml(e.getKey());
                if (dk != null) {
                    expired.computeIfAbsent(dk.playerUuid, k -> new HashSet<>()).add(dk.recipeId);
                }
            }
        }

        for (Map.Entry<UUID, Set<String>> e : expired.entrySet()) {
            UUID uuid = e.getKey();
            for (String recipeId : e.getValue()) {
                // Remove from RecipeManager's learned set.
                recipeManager.forgetPlayerRecipe(uuid, recipeId);
                lastCraftPerRecipe.remove(buildKey(uuid, recipeId));
            }
            dirty = true;
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                for (String recipeId : e.getValue()) {
                    online.sendMessage(plugin.getMessageManager().getMessage(
                            "recipe-decay-forgotten", true,
                            Map.of("%recipe%", recipeId)));
                }
            }
        }
        if (dirty) saveAsync();
    }

    private record DecomposedKey(UUID playerUuid, String recipeId) {}

    private DecomposedKey decomposeFromYaml(UUID compositeUuid) {
        // We don't actually store composite → (player,recipe) in memory; we
        // only store it in the YAML. Iterate the YAML section on save() and
        // reload on load() to recover both. For runDecaySweep, we use the
        // in-memory map which is keyed by the composite UUID — to decompose
        // we need the YAML. Simplest: store the (player, recipe) as the
        // VALUE side instead. Let's reload from disk to be safe.
        // Actually: simpler still — keep an aux map populated on load().
        return auxDecompose.get(compositeUuid);
    }

    private final Map<UUID, DecomposedKey> auxDecompose = new ConcurrentHashMap<>();

    // =====================================================================
    //  Persistence
    // =====================================================================

    private File getFile() { return new File(plugin.getDataFolder(), "recipe_decay.yml"); }

    private void load() {
        File file = getFile();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            UUID playerUuid;
            try { playerUuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }
            ConfigurationSection recipes = players.getConfigurationSection(uuidStr);
            if (recipes == null) continue;
            for (String recipeId : recipes.getKeys(false)) {
                long ts = recipes.getLong(recipeId, 0L);
                if (ts <= 0) continue;
                UUID composite = buildKey(playerUuid, recipeId);
                lastCraftPerRecipe.put(composite, ts);
                auxDecompose.put(composite, new DecomposedKey(playerUuid, recipeId.toLowerCase()));
            }
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, DecomposedKey> e : auxDecompose.entrySet()) {
            Long ts = lastCraftPerRecipe.get(e.getKey());
            if (ts == null) continue;
            cfg.set("players." + e.getValue().playerUuid + "." + e.getValue().recipeId, ts);
        }
        try {
            cfg.save(getFile());
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save recipe_decay.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        if (!dirty) return;
        final FileConfiguration snapshot;
        synchronized (this) {
            if (!dirty) return;
            snapshot = new YamlConfiguration();
            for (Map.Entry<UUID, DecomposedKey> e : auxDecompose.entrySet()) {
                Long ts = lastCraftPerRecipe.get(e.getKey());
                if (ts == null) continue;
                snapshot.set("players." + e.getValue().playerUuid + "." + e.getValue().recipeId, ts);
            }
            dirty = false;
        }
        try {
            snapshot.save(getFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save recipe_decay.yml (async): " + e.getMessage());
            dirty = true;
        }
    }

    public void shutdown() {
        if (decayTask != null) decayTask.cancel();
        save();
    }

    public void cancelTasks() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }
}
