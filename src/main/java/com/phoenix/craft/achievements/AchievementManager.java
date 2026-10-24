package com.phoenix.craft.achievements;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.stats.StatsManager;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.fish.api.CraftRecipe;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and rewards crafting achievements.
 *
 * <p>Achievements are defined in {@code config.yml} under the
 * {@code achievements.list} section. Each achievement has:
 * <ul>
 *   <li>{@code id} — unique identifier (YAML key)</li>
 *   <li>{@code name} — display name (message key)</li>
 *   <li>{@code description} — what to do (message key)</li>
 *   <li>{@code type} — one of: TOTAL_CRAFTS, RECIPE_CRAFTED,
 *       UNIQUE_RECIPES, BULK_CRAFT, FIRST_CRAFT</li>
 *   <li>{@code target} — numeric threshold (for TOTAL_CRAFTS, UNIQUE_RECIPES,
 *       BULK_CRAFT) or recipe ID (for RECIPE_CRAFTED)</li>
 *   <li>{@code reward-commands} — list of console commands to run on unlock</li>
 * </ul>
 *
 * <p>Modularity: this entire system can be disabled in {@code config.yml} via
 * {@code achievements.enabled: false}.</p>
 *
 * <p>Unlocks are persisted in {@code plugins/PhoenixCraft/achievements.yml}.</p>
 *
 * @since 0.3.0
 */
public class AchievementManager {

    private final PhoenixCraft plugin;
    private final StatsManager statsManager;
    private final MessageManager messages;
    private final boolean enabled;

    /** Loaded achievement definitions, keyed by id. */
    private final Map<String, Achievement> achievements = new LinkedHashMap<>();

    /** Per-player unlocked achievement IDs. */
    private final Map<UUID, Set<String>> unlocked = new ConcurrentHashMap<>();

    /** Dirty flag for unlocks persistence. */
    private volatile boolean dirty = false;

    public AchievementManager(PhoenixCraft plugin, StatsManager statsManager,
                               MessageManager messages) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.messages = messages;
        this.enabled = plugin.getConfig().getBoolean("achievements.enabled", true);

        if (enabled) {
            loadDefinitions();
            loadUnlocks();
            plugin.getLogger().info("AchievementManager enabled — "
                    + achievements.size() + " achievements loaded, "
                    + unlocked.size() + " players tracked.");
        } else {
            plugin.getLogger().info("AchievementManager disabled (achievements.enabled=false).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Achievement> getAchievements() {
        return Collections.unmodifiableMap(achievements);
    }

    // =====================================================================
    //  Achievement definitions (loaded from config.yml)
    // =====================================================================

    private void loadDefinitions() {
        achievements.clear();

        ConfigurationSection list = plugin.getConfig().getConfigurationSection("achievements.list");
        if (list == null) {
            plugin.getLogger().warning("No 'achievements.list' section in config.yml — no achievements will be active.");
            return;
        }

        for (String id : list.getKeys(false)) {
            ConfigurationSection sec = list.getConfigurationSection(id);
            if (sec == null) continue;

            try {
                String nameKey = sec.getString("name", "achievement-" + id + "-name");
                String descKey = sec.getString("description", "achievement-" + id + "-desc");
                String typeStr = sec.getString("type", "TOTAL_CRAFTS").toUpperCase();
                AchievementType type;
                try {
                    type = AchievementType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Achievement " + id + " has invalid type '"
                            + typeStr + "' — skipping.");
                    continue;
                }

                String target = sec.getString("target", "");
                List<String> rewardCommands = sec.getStringList("reward-commands");

                Achievement ach = new Achievement(id, nameKey, descKey, type, target, rewardCommands);
                achievements.put(id, ach);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load achievement " + id + ": " + e.getMessage());
            }
        }
    }

    // =====================================================================
    //  Unlocks persistence (plugins/PhoenixCraft/achievements.yml)
    // =====================================================================

    private File getUnlocksFile() {
        return new File(plugin.getDataFolder(), "achievements.yml");
    }

    private void loadUnlocks() {
        File file = getUnlocksFile();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> ids = players.getStringList(uuidStr);
                unlocked.put(uuid, new HashSet<>(ids));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveUnlocks() {
        if (!enabled) return;
        if (!dirty) return;

        File file = getUnlocksFile();
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : unlocked.entrySet()) {
            cfg.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save achievements.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        saveUnlocks();
    }

    // =====================================================================
    //  Queries
    // =====================================================================

    public boolean isUnlocked(UUID uuid, String achievementId) {
        if (!enabled) return false;
        Set<String> set = unlocked.get(uuid);
        return set != null && set.contains(achievementId);
    }

    public Set<String> getUnlocked(UUID uuid) {
        if (!enabled) return Collections.emptySet();
        return Collections.unmodifiableSet(unlocked.getOrDefault(uuid, Collections.emptySet()));
    }

    public int getUnlockedCount(UUID uuid) {
        if (!enabled) return 0;
        Set<String> set = unlocked.get(uuid);
        return set != null ? set.size() : 0;
    }

    public int getTotalCount() {
        return achievements.size();
    }

    // =====================================================================
    //  Checking (called after each craft)
    // =====================================================================

    /**
     * Check all achievements for a player. Unlocks new ones, fires rewards,
     * notifies the player. Called by CraftingGUIManager after a successful craft.
     *
     * @param player       the crafting player
     * @param recipe       the recipe that was just crafted
     * @param craftedCount how many items were produced (1 for single, N for bulk)
     */
    public void checkAchievements(Player player, CraftRecipe recipe, int craftedCount) {
        if (!enabled) return;

        UUID uuid = player.getUniqueId();
        StatsManager.PlayerStats stats = statsManager.getStats(uuid);
        Set<String> alreadyUnlocked = unlocked.computeIfAbsent(uuid, k -> new HashSet<>());

        for (Achievement ach : achievements.values()) {
            if (alreadyUnlocked.contains(ach.id)) continue;

            boolean meets = false;
            switch (ach.type) {
                case FIRST_CRAFT:
                    // Unlocked on the very first craft ever (totalCrafts == craftedCount)
                    meets = (stats.totalCrafts == craftedCount);
                    break;
                case TOTAL_CRAFTS:
                    try {
                        int target = Integer.parseInt(ach.target);
                        meets = stats.totalCrafts >= target;
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case UNIQUE_RECIPES:
                    try {
                        int target = Integer.parseInt(ach.target);
                        meets = stats.recipeCounts.size() >= target;
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case BULK_CRAFT:
                    // Unlocked the first time the player does a bulk craft of N+
                    try {
                        int target = Integer.parseInt(ach.target);
                        meets = craftedCount >= target;
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case RECIPE_CRAFTED:
                    // Unlocked when this specific recipe is crafted at least once
                    meets = ach.target.equalsIgnoreCase(recipe.id());
                    break;
            }

            if (meets) {
                alreadyUnlocked.add(ach.id);
                dirty = true;
                unlockAchievement(player, ach);
            }
        }

        // Persist immediately (sync — small file, low overhead)
        if (dirty) {
            saveUnlocks();
        }
    }

    private void unlockAchievement(Player player, Achievement ach) {
        // 1. Chat notification
        Map<String, String> ph = new java.util.HashMap<>();
        ph.put("%achievement%", messages.getPlainMessage(ach.nameKey));
        ph.put("%description%", messages.getPlainMessage(ach.descKey));
        player.sendMessage(messages.getMessage("achievement-unlocked", true, ph));

        // 2. Title (config-driven, can be disabled)
        if (plugin.getConfig().getBoolean("achievements.show-title", true)) {
            try {
                net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(3000),
                        java.time.Duration.ofMillis(800));
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                        messages.getMessage("achievement-unlocked-title", ph),
                        messages.getMessage("achievement-unlocked-subtitle", ph),
                        times);
                player.showTitle(title);
            } catch (Exception ignored) {
            }
        }

        // 3. Sound (config-driven)
        String soundName = plugin.getConfig().getString("achievements.unlock-sound",
                "UI_TOAST_CHALLENGE_COMPLETE");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player, sound, 0.7f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid achievement sound: " + soundName);
        }

        // 4. Reward commands (console, sync)
        for (String cmd : ach.rewardCommands) {
            String finalCmd = cmd.replace("%player%", player.getName());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            } catch (Exception e) {
                plugin.getLogger().warning("Achievement reward command failed: '"
                        + finalCmd + "': " + e.getMessage());
            }
        }
    }

    // =====================================================================
    //  Types
    // =====================================================================

    public enum AchievementType {
        /** Unlocked on the very first craft ever. */
        FIRST_CRAFT,
        /** Unlocked when total crafts reach the target. */
        TOTAL_CRAFTS,
        /** Unlocked when N distinct recipes have been crafted. */
        UNIQUE_RECIPES,
        /** Unlocked when a single bulk craft produces N+ items. */
        BULK_CRAFT,
        /** Unlocked when the specified recipe ID is crafted at least once. */
        RECIPE_CRAFTED
    }

    public static class Achievement {
        public final String id;
        public final String nameKey;
        public final String descKey;
        public final AchievementType type;
        public final String target;
        public final List<String> rewardCommands;

        public Achievement(String id, String nameKey, String descKey,
                            AchievementType type, String target,
                            List<String> rewardCommands) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.type = type;
            this.target = target;
            this.rewardCommands = rewardCommands != null ? rewardCommands : Collections.emptyList();
        }
    }
}
