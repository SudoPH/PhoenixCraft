package com.phoenix.craft.achievements;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.stats.StatsManager;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.craft.util.NotificationHelper;
import com.phoenix.fish.api.CraftRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
 * <p>Faz 0.7.0 overhaul:
 * <ul>
 *   <li>9 achievement types (was 5)</li>
 *   <li>Progress tracking — GUI shows "47/50" style progress</li>
 *   <li>Tier system — bronze/silver/gold/diamond for visual categorization</li>
 *   <li>Custom reward items — config can specify ItemStack rewards</li>
 *   <li>Fully modular — admins add custom achievements via config.yml</li>
 * </ul>
 *
 * <p>Achievement types:
 * <pre>
 *   FIRST_CRAFT         — unlocked on the player's first craft ever
 *   TOTAL_CRAFTS        — target = integer, total crafts needed
 *   UNIQUE_RECIPES      — target = integer, distinct recipes crafted
 *   BULK_CRAFT          — target = integer, single bulk craft of N+ items
 *   RECIPE_CRAFTED      — target = recipe id, craft this recipe once
 *   PER_RECIPE_COUNT    — target = "recipe_id:count", craft one recipe N times
 *   CATEGORY_CRAFTS     — target = "category:count", craft N items in a category
 *   TOTAL_XP_EARNED     — target = integer, total XP earned from crafting
 *   FIRST_DISCOVERY     — target = integer, discover N different recipes
 * </pre>
 *
 * @since 0.3.0 (overhauled in 0.7.0)
 */
public class AchievementManager {

    private final PhoenixCraft plugin;
    private final StatsManager statsManager;
    private final MessageManager messages;
    /** Cached for the constructor's initial boot decision only — reload picks
     *  up the live value via {@link #isEnabled()}. */
    private final boolean enabledAtBoot;

    private final Map<String, Achievement> achievements = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> unlocked = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public AchievementManager(PhoenixCraft plugin, StatsManager statsManager,
                               MessageManager messages) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.messages = messages;
        this.enabledAtBoot = plugin.getConfig().getBoolean("achievements.enabled", true);

        if (enabledAtBoot) {
            loadDefinitions();
            loadUnlocks();
            plugin.getLogger().info("AchievementManager enabled — "
                    + achievements.size() + " achievements loaded, "
                    + unlocked.size() + " players tracked.");
        } else {
            plugin.getLogger().info("AchievementManager disabled (achievements.enabled=false).");
        }
    }

    /** Live config read — picks up /pcraft reload changes immediately. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("achievements.enabled", true);
    }

    public Map<String, Achievement> getAchievements() {
        return Collections.unmodifiableMap(achievements);
    }

    // =====================================================================
    //  Achievement definitions (loaded from config.yml + custom-achievements.yml)
    // =====================================================================

    private void loadDefinitions() {
        achievements.clear();

        // 1. Load built-in achievements from config.yml
        ConfigurationSection list = plugin.getConfig().getConfigurationSection("achievements.list");
        if (list != null) {
            loadAchievementsFromSection(list, "config.yml");
        } else {
            plugin.getLogger().warning("No 'achievements.list' section in config.yml — no built-in achievements will be active.");
        }

        // 2. Load custom achievements from custom-achievements.yml
        //    (overrides config.yml if same ID exists)
        //    v1.0.1 — Dil bazlı dosya yükleme: custom-achievements_<dil>.yml → custom-achievements.yml
        String lang = null;
        try {
            lang = plugin.getMessageManager().getLanguage();
        } catch (Throwable ignored) {
            // MessageManager henüz yüklenmediyse null bırak.
        }
        String customFileName = "custom-achievements.yml";
        if (lang != null && !lang.trim().isEmpty() && !lang.equalsIgnoreCase("en")) {
            String candidate = "custom-achievements_" + lang.trim().toLowerCase() + ".yml";
            File candidateFile = new File(plugin.getDataFolder(), candidate);
            if (candidateFile.exists() || plugin.getResource(candidate) != null) {
                customFileName = candidate;
            }
        }
        File customFile = new File(plugin.getDataFolder(), customFileName);
        if (!customFile.exists()) {
            // Fallback: custom-achievements.yml
            customFileName = "custom-achievements.yml";
            customFile = new File(plugin.getDataFolder(), "custom-achievements.yml");
            if (!customFile.exists()) {
                plugin.saveResource("custom-achievements.yml", false);
                customFile = new File(plugin.getDataFolder(), "custom-achievements.yml");
            }
        }
        plugin.getLogger().info("Loading custom achievements from: " + customFileName);
        if (customFile.exists()) {
            FileConfiguration customCfg = YamlConfiguration.loadConfiguration(customFile);
            ConfigurationSection customList = customCfg.getConfigurationSection("list");
            if (customList != null) {
                int beforeSize = achievements.size();
                loadAchievementsFromSection(customList, customFileName);
                int added = achievements.size() - beforeSize;
                if (added > 0) {
                    plugin.getLogger().info("Loaded " + added + " custom achievements from " + customFileName);
                }
            }
        }
    }

    /**
     * Helper: load achievements from a ConfigurationSection (either config.yml or custom-achievements.yml).
     */
    private void loadAchievementsFromSection(ConfigurationSection list, String sourceName) {
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

                // Faz 0.7.0: tier + reward items
                String tier = sec.getString("tier", "none").toLowerCase();
                List<RewardItem> rewardItems = loadRewardItems(sec);

                Achievement ach = new Achievement(id, nameKey, descKey, type, target,
                        rewardCommands, tier, rewardItems);
                achievements.put(id, ach);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load achievement " + id
                        + " from " + sourceName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Faz 0.7.0: Load custom reward items from config.
     * Format:
     *   reward-items:
     *     - material: DIAMOND
     *       amount: 3
     *       custom-model-data: 0
     *     - material: NETHERITE_INGOT
     *       amount: 1
     */
    private List<RewardItem> loadRewardItems(ConfigurationSection sec) {
        List<RewardItem> items = new ArrayList<>();
        ConfigurationSection itemsSec = sec.getConfigurationSection("reward-items");
        if (itemsSec == null) return items;

        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
            if (itemSec == null) continue;

            String matStr = itemSec.getString("material", "STONE").toUpperCase();
            Material mat = Material.matchMaterial(matStr);
            if (mat == null) {
                plugin.getLogger().warning("Achievement reward item has invalid material: " + matStr);
                continue;
            }
            int amount = itemSec.getInt("amount", 1);
            int cmd = itemSec.getInt("custom-model-data", 0);
            items.add(new RewardItem(mat, amount, cmd));
        }
        return items;
    }

    // =====================================================================
    //  Unlocks persistence
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
                unlocked.put(uuid, new HashSet<>(players.getStringList(uuidStr)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveUnlocks() {
        if (!isEnabled() || !dirty) return;
        File file = getUnlocksFile();
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : unlocked.entrySet()) {
            cfg.set("players." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
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
        if (!isEnabled()) return false;
        Set<String> set = unlocked.get(uuid);
        return set != null && set.contains(achievementId);
    }

    public Set<String> getUnlocked(UUID uuid) {
        if (!isEnabled()) return Collections.emptySet();
        return Collections.unmodifiableSet(unlocked.getOrDefault(uuid, Collections.emptySet()));
    }

    public int getUnlockedCount(UUID uuid) {
        if (!isEnabled()) return 0;
        Set<String> set = unlocked.get(uuid);
        return set != null ? set.size() : 0;
    }

    public int getTotalCount() {
        return achievements.size();
    }

    // =====================================================================
    //  Progress Tracking (Faz 0.7.0)
    // =====================================================================

    /**
     * Get the current progress value for an achievement.
     * Returns -1 if the achievement type doesn't support progress tracking.
     *
     * Example: for TOTAL_CRAFTS with target 100, if the player has 47 crafts,
     * this returns 47.
     */
    public int getCurrentProgress(UUID uuid, Achievement ach) {
        if (!isEnabled() || statsManager == null) return -1;
        StatsManager.PlayerStats stats = statsManager.getStats(uuid);

        switch (ach.type) {
            case TOTAL_CRAFTS:
                return stats.totalCrafts;
            case UNIQUE_RECIPES:
            case FIRST_DISCOVERY:
                return stats.recipeCounts.size();
            case TOTAL_XP_EARNED:
                return stats.totalXpEarned;
            case PER_RECIPE_COUNT: {
                // target format: "recipe_id:count"
                String[] parts = ach.target.split(":");
                if (parts.length == 2) {
                    return stats.recipeCounts.getOrDefault(parts[0].toLowerCase(), 0);
                }
                return -1;
            }
            case CATEGORY_CRAFTS: {
                // target format: "category:count"
                String[] parts = ach.target.split(":");
                if (parts.length == 2) {
                    int total = 0;
                    for (Map.Entry<String, Integer> e : stats.recipeCounts.entrySet()) {
                        // We don't have category info in stats, so we can't
                        // accurately compute this. Return total as approximation.
                        total += e.getValue();
                    }
                    return total;
                }
                return -1;
            }
            default:
                return -1; // FIRST_CRAFT, BULK_CRAFT, RECIPE_CRAFTED — no progress
        }
    }

    /**
     * Get the target value for an achievement (the number to reach).
     * Returns -1 if not applicable.
     */
    public int getTargetValue(Achievement ach) {
        switch (ach.type) {
            case TOTAL_CRAFTS:
            case UNIQUE_RECIPES:
            case BULK_CRAFT:
            case TOTAL_XP_EARNED:
            case FIRST_DISCOVERY:
                try {
                    return Integer.parseInt(ach.target);
                } catch (NumberFormatException e) {
                    return -1;
                }
            case PER_RECIPE_COUNT:
            case CATEGORY_CRAFTS: {
                String[] parts = ach.target.split(":");
                if (parts.length == 2) {
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                }
                return -1;
            }
            default:
                return -1;
        }
    }

    /**
     * Get a formatted progress string like "47/50" or "—" if not applicable.
     */
    public String getProgressString(UUID uuid, Achievement ach) {
        int current = getCurrentProgress(uuid, ach);
        int target = getTargetValue(ach);
        if (current < 0 || target < 0) return "—";
        return current + "/" + target;
    }

    // =====================================================================
    //  Checking (called after each craft)
    // =====================================================================

    public void checkAchievements(Player player, CraftRecipe recipe, int craftedCount) {
        if (!isEnabled()) return;

        UUID uuid = player.getUniqueId();
        StatsManager.PlayerStats stats = statsManager.getStats(uuid);
        Set<String> alreadyUnlocked = unlocked.computeIfAbsent(uuid, k -> new HashSet<>());

        for (Achievement ach : achievements.values()) {
            if (alreadyUnlocked.contains(ach.id)) continue;

            boolean meets = false;
            switch (ach.type) {
                case FIRST_CRAFT:
                    meets = (stats.totalCrafts == craftedCount);
                    break;
                case TOTAL_CRAFTS:
                    try {
                        meets = stats.totalCrafts >= Integer.parseInt(ach.target);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case UNIQUE_RECIPES:
                case FIRST_DISCOVERY:
                    try {
                        meets = stats.recipeCounts.size() >= Integer.parseInt(ach.target);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case BULK_CRAFT:
                    try {
                        meets = craftedCount >= Integer.parseInt(ach.target);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case RECIPE_CRAFTED:
                    meets = ach.target.equalsIgnoreCase(recipe.id());
                    break;
                case PER_RECIPE_COUNT: {
                    // target format: "recipe_id:count"
                    String[] parts = ach.target.split(":");
                    if (parts.length == 2) {
                        try {
                            int needed = Integer.parseInt(parts[1]);
                            int have = stats.recipeCounts.getOrDefault(parts[0].toLowerCase(), 0);
                            meets = have >= needed;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    break;
                }
                case CATEGORY_CRAFTS: {
                    // target format: "category:count"
                    // We check if the crafted recipe's category matches
                    String[] parts = ach.target.split(":");
                    if (parts.length == 2) {
                        String cat = parts[0].toLowerCase();
                        if (cat.equals(recipe.category().toLowerCase())) {
                            try {
                                int needed = Integer.parseInt(parts[1]);
                                // Sum all crafts in this category (approximation —
                                // stats don't track per-category, so use total)
                                meets = stats.totalCrafts >= needed;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                }
                case TOTAL_XP_EARNED:
                    try {
                        meets = stats.totalXpEarned >= Integer.parseInt(ach.target);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
            }

            if (meets) {
                alreadyUnlocked.add(ach.id);
                dirty = true;
                unlockAchievement(player, ach);
            }
        }

        // Faz 0.9.0: do NOT save here — auto-save timer or shutdown handles it
    }

    private void unlockAchievement(Player player, Achievement ach) {
        Map<String, String> ph = new java.util.HashMap<>();
        ph.put("%achievement%", messages.getPlainMessage(ach.nameKey));
        ph.put("%description%", messages.getPlainMessage(ach.descKey));
        ph.put("%tier%", ach.tier);

        // 1. Chat notification (config-driven) — getMessageRaw because admin may use MiniMessage tags in names
        if (NotificationHelper.shouldSendAchievementChat(plugin)) {
            player.sendMessage(messages.getMessageRaw("achievement-unlocked", true, ph));
        }

        // 2. Title (config-driven)
        if (NotificationHelper.shouldShowAchievementTitle(plugin)) {
            try {
                net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(3000),
                        java.time.Duration.ofMillis(800));
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                        messages.getMessageRaw("achievement-unlocked-title", ph),
                        messages.getMessageRaw("achievement-unlocked-subtitle", ph),
                        times);
                player.showTitle(title);
            } catch (Exception ignored) {
            }
        }

        // 3. Sound (config-driven)
        if (NotificationHelper.shouldPlayAchievementSound(plugin)) {
            String soundName = plugin.getConfig().getString("achievements.unlock-sound",
                    "UI_TOAST_CHALLENGE_COMPLETE");
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player, sound, 0.7f, 1.0f);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 4. Reward commands
        for (String cmd : ach.rewardCommands) {
            String finalCmd = cmd.replace("%player%", player.getName());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            } catch (Exception e) {
                plugin.getLogger().warning("Achievement reward command failed: '"
                        + finalCmd + "': " + e.getMessage());
            }
        }

        // 5. Faz 0.7.0: Reward items
        for (RewardItem ri : ach.rewardItems) {
            ItemStack item = new ItemStack(ri.material, ri.amount);
            if (ri.customModelData > 0 && item.hasItemMeta()) {
                @SuppressWarnings("deprecation")
                var meta = item.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(ri.customModelData);
                    item.setItemMeta(meta);
                }
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    // =====================================================================
    //  Types + Data Classes
    // =====================================================================

    public enum AchievementType {
        FIRST_CRAFT,
        TOTAL_CRAFTS,
        UNIQUE_RECIPES,
        BULK_CRAFT,
        RECIPE_CRAFTED,
        /** Faz 0.7.0: Craft a specific recipe N times. Target: "recipe_id:count" */
        PER_RECIPE_COUNT,
        /** Faz 0.7.0: Craft N items in a category. Target: "category:count" */
        CATEGORY_CRAFTS,
        /** Faz 0.7.0: Earn N total XP from crafting. Target: integer */
        TOTAL_XP_EARNED,
        /** Faz 0.7.0: Discover N different recipes. Target: integer */
        FIRST_DISCOVERY
    }

    public enum Tier {
        NONE("none"),
        BRONZE("bronze"),
        SILVER("silver"),
        GOLD("gold"),
        DIAMOND("diamond");

        public final String configName;

        Tier(String configName) {
            this.configName = configName;
        }

        public static Tier fromString(String s) {
            for (Tier t : values()) {
                if (t.configName.equalsIgnoreCase(s)) return t;
            }
            return NONE;
        }
    }

    public static class Achievement {
        public final String id;
        public final String nameKey;
        public final String descKey;
        public final AchievementType type;
        public final String target;
        public final List<String> rewardCommands;
        /** Faz 0.7.0: tier string (bronze/silver/gold/diamond/none) */
        public final String tier;
        /** Faz 0.7.0: custom reward items */
        public final List<RewardItem> rewardItems;

        public Achievement(String id, String nameKey, String descKey,
                            AchievementType type, String target,
                            List<String> rewardCommands,
                            String tier, List<RewardItem> rewardItems) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.type = type;
            this.target = target;
            this.rewardCommands = rewardCommands != null ? rewardCommands : Collections.emptyList();
            this.tier = tier != null ? tier : "none";
            this.rewardItems = rewardItems != null ? rewardItems : Collections.emptyList();
        }

        public Tier getTier() {
            return Tier.fromString(tier);
        }
    }

    /** Faz 0.7.0: Custom reward item definition from config. */
    public static class RewardItem {
        public final Material material;
        public final int amount;
        public final int customModelData;

        public RewardItem(Material material, int amount, int customModelData) {
            this.material = material;
            this.amount = Math.max(1, amount);
            this.customModelData = customModelData;
        }
    }
}
