package com.phoenix.craft.queue;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.CraftResult;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Persistent crafting queue (F10). Players queue up to N crafts; a 1-tick
 * interval executor drains the queue one craft at a time, surviving player
 * disconnect (state persists in {@code queue.yml}).
 *
 * <p>Use case: bulk-crafting 1000 baits without freezing the GUI. The player
 * pushes 1000 craft requests; this manager executes one per tick (~50ms),
 * taking ~50 seconds total instead of a single blocking call.</p>
 *
 * @since 1.0.7 (feature #10)
 */
public class CraftingQueueManager {

    private final PhoenixCraft plugin;
    private final Supplier<PhoenixFishAPI> fishApiSupplier;
    private final int maxQueueSize;
    private final ConcurrentLinkedQueue<QueuedCraft> queue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Integer> perPlayerCount = new ConcurrentHashMap<>();
    private volatile BukkitTask executorTask;
    private volatile boolean dirty = false;

    public record QueuedCraft(UUID playerUuid, String recipeId, long queuedAt) {}

    public CraftingQueueManager(PhoenixCraft plugin,
                                 Supplier<PhoenixFishAPI> fishApiSupplier) {
        this.plugin = plugin;
        this.fishApiSupplier = fishApiSupplier;
        this.maxQueueSize = plugin.getConfig().getInt("features.crafting-queue.max-per-player", 100);
        load();
        startExecutor();
        plugin.getLogger().info("CraftingQueueManager enabled — "
                + queue.size() + " queued crafts restored.");
    }

    // =====================================================================
    //  Public API
    // =====================================================================

    /**
     * Enqueue a craft. Returns null on success, or an error key on failure:
     * <ul>
     *   <li>{@code queue-full} — player hit their per-player cap</li>
     *   <li>{@code queue-recipe-unknown} — recipe ID not in PhoenixFish</li>
     * </ul>
     */
    public String enqueue(Player player, String recipeId) {
        if (recipeId == null) return "queue-recipe-unknown";
        recipeId = recipeId.toLowerCase();

        int current = perPlayerCount.getOrDefault(player.getUniqueId(), 0);
        if (current >= maxQueueSize) return "queue-full";

        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) return "queue-no-api";
        if (api.getCraftingRecipe(recipeId) == null) return "queue-recipe-unknown";

        queue.add(new QueuedCraft(player.getUniqueId(), recipeId, System.currentTimeMillis()));
        perPlayerCount.merge(player.getUniqueId(), 1, Integer::sum);
        dirty = true;
        saveAsync();
        return null;
    }

    /** Cancel all queued crafts for a player. Returns the count removed. */
    public int cancelAll(UUID uuid) {
        int removed = 0;
        Iterator<QueuedCraft> it = queue.iterator();
        while (it.hasNext()) {
            QueuedCraft qc = it.next();
            if (qc.playerUuid.equals(uuid)) {
                it.remove();
                removed++;
            }
        }
        perPlayerCount.remove(uuid);
        if (removed > 0) { dirty = true; saveAsync(); }
        return removed;
    }

    public int getQueuedCount(UUID uuid) {
        return perPlayerCount.getOrDefault(uuid, 0);
    }

    public int getTotalQueued() { return queue.size(); }

    public List<QueuedCraft> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    // =====================================================================
    //  Executor — drains one craft per tick
    // =====================================================================

    private void startExecutor() {
        long intervalTicks = plugin.getConfig().getLong("features.crafting-queue.tick-interval", 1L);
        if (intervalTicks < 1L) intervalTicks = 1L;
        executorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drainOne,
                intervalTicks, intervalTicks);
    }

    private void drainOne() {
        QueuedCraft qc = queue.poll();
        if (qc == null) return;
        perPlayerCount.computeIfPresent(qc.playerUuid, (k, v) -> v <= 1 ? null : v - 1);

        Player player = Bukkit.getPlayer(qc.playerUuid);
        if (player == null || !player.isOnline()) {
            // Player offline — leave the craft in the queue (re-add) so it
            // resumes when they rejoin. Actually we polled it, so re-add.
            queue.add(qc);
            perPlayerCount.merge(qc.playerUuid, 1, Integer::sum);
            return;
        }

        PhoenixFishAPI api = fishApiSupplier.get();
        if (api == null) {
            // API down — re-queue and try next tick.
            queue.add(qc);
            perPlayerCount.merge(qc.playerUuid, 1, Integer::sum);
            return;
        }
        CraftRecipe recipe = api.getCraftingRecipe(qc.recipeId);
        if (recipe == null) return; // recipe was removed — drop from queue

        try {
            CraftResult result = api.craft(player, recipe);
            if (result == CraftResult.MISSING_MATERIALS || result == CraftResult.LOW_LEVEL) {
                // Stop processing this player's queue — they can't craft anymore.
                cancelAll(qc.playerUuid);
                if (player.isOnline()) {
                    player.sendMessage(plugin.getMessageManager().getMessage(
                            "queue-stopped", true,
                            Map.of("%reason%", result.name().toLowerCase(),
                                   "%recipe%", qc.recipeId)));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Queue craft failed for " + player.getName()
                    + " recipe=" + qc.recipeId + ": " + t.getMessage());
        }
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    private File getFile() { return new File(plugin.getDataFolder(), "queue.yml"); }

    private void load() {
        File file = getFile();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection list = cfg.getConfigurationSection("queue");
        if (list == null) return;
        for (String idx : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(idx);
            if (s == null) continue;
            try {
                UUID uuid = UUID.fromString(s.getString("player"));
                String recipeId = s.getString("recipe", "");
                long queuedAt = s.getLong("queued-at", System.currentTimeMillis());
                if (recipeId.isBlank()) continue;
                queue.add(new QueuedCraft(uuid, recipeId.toLowerCase(), queuedAt));
                perPlayerCount.merge(uuid, 1, Integer::sum);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (QueuedCraft qc : queue) {
            cfg.set("queue." + i + ".player", qc.playerUuid.toString());
            cfg.set("queue." + i + ".recipe", qc.recipeId);
            cfg.set("queue." + i + ".queued-at", qc.queuedAt);
            i++;
        }
        try {
            cfg.save(getFile());
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save queue.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        if (!dirty) return;
        final FileConfiguration snapshot;
        final int size;
        synchronized (this) {
            if (!dirty) return;
            snapshot = new YamlConfiguration();
            int i = 0;
            for (QueuedCraft qc : queue) {
                snapshot.set("queue." + i + ".player", qc.playerUuid.toString());
                snapshot.set("queue." + i + ".recipe", qc.recipeId);
                snapshot.set("queue." + i + ".queued-at", qc.queuedAt);
                i++;
            }
            size = i;
            dirty = false;
        }
        try {
            snapshot.save(getFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save queue.yml (async): " + e.getMessage());
            dirty = true;
        }
    }

    public void shutdown() {
        if (executorTask != null) executorTask.cancel();
        save();
    }

    public void cancelTasks() {
        if (executorTask != null) {
            executorTask.cancel();
            executorTask = null;
        }
    }
}
