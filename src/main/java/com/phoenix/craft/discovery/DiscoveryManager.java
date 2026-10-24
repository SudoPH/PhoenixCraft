package com.phoenix.craft.discovery;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.fish.api.CraftRecipe;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks first-time recipe crafts and awards bonus XP.
 *
 * <p>When a player crafts a recipe for the very first time, they receive
 * bonus XP (calculated from the recipe's normal XP × a configurable
 * multiplier). This encourages variety — players are rewarded for trying
 * new recipes instead of spamming the same one.</p>
 *
 * <p>Storage: {@code plugins/PhoenixCraft/discovered_recipes.yml}</p>
 *
 * @since 0.6.0
 */
public class DiscoveryManager {

    private final PhoenixCraft plugin;
    private final MessageManager messages;
    private final boolean enabled;

    private final Map<UUID, Set<String>> discovered = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public DiscoveryManager(PhoenixCraft plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.enabled = plugin.getConfig().getBoolean("discovery.enabled", true);

        if (enabled) {
            load();
            plugin.getLogger().info("DiscoveryManager enabled — tracking "
                    + discovered.size() + " players' discoveries.");
        } else {
            plugin.getLogger().info("DiscoveryManager disabled (discovery.enabled=false).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private File getFile() {
        return new File(plugin.getDataFolder(), "discovered_recipes.yml");
    }

    private void load() {
        File file = getFile();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                discovered.put(uuid, new HashSet<>(players.getStringList(uuidStr)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        if (!enabled || !dirty) return;
        File file = getFile();
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : discovered.entrySet()) {
            cfg.set("players." + entry.getKey().toString(),
                    new java.util.ArrayList<>(entry.getValue()));
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save discovered_recipes.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        save();
    }

    public boolean hasDiscovered(UUID uuid, String recipeId) {
        if (!enabled) return true;
        Set<String> set = discovered.get(uuid);
        return set != null && set.contains(recipeId.toLowerCase());
    }

    public int getDiscoveredCount(UUID uuid) {
        if (!enabled) return 0;
        Set<String> set = discovered.get(uuid);
        return set != null ? set.size() : 0;
    }

    /**
     * Check if this is the player's first craft of this recipe.
     * If so, return the bonus XP to award and notify the player.
     *
     * @return bonus XP (0 if not first craft or disabled)
     */
    public int checkDiscovery(Player player, CraftRecipe recipe, int craftedCount) {
        if (!enabled) return 0;
        UUID uuid = player.getUniqueId();
        String recipeId = recipe.id().toLowerCase();
        if (hasDiscovered(uuid, recipeId)) return 0;

        Set<String> set = discovered.computeIfAbsent(uuid, k -> new HashSet<>());
        set.add(recipeId);
        dirty = true;
        save();

        int bonusXp = calculateBonusXp(recipe);
        if (bonusXp > 0) notifyDiscovery(player, recipe, bonusXp);
        return bonusXp;
    }

    private int calculateBonusXp(CraftRecipe recipe) {
        double multiplier = plugin.getConfig().getDouble("discovery.bonus-xp-multiplier", 4.0);
        int minBonus = plugin.getConfig().getInt("discovery.min-bonus-xp", 10);
        int maxBonus = plugin.getConfig().getInt("discovery.max-bonus-xp", 500);
        int calculated = (int) (recipe.xpReward() * multiplier);
        if (calculated < minBonus) calculated = minBonus;
        if (maxBonus > 0 && calculated > maxBonus) calculated = maxBonus;
        return calculated;
    }

    private void notifyDiscovery(Player player, CraftRecipe recipe, int bonusXp) {
        String recipeName = recipe.resultItem().hasItemMeta()
                && recipe.resultItem().getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(recipe.resultItem().getItemMeta().displayName())
                : recipe.id();

        Map<String, String> ph = Map.of("%recipe%", recipeName, "%xp%", String.valueOf(bonusXp));
        player.sendMessage(messages.getMessage("discovery-first-craft", true, ph));

        if (plugin.getConfig().getBoolean("discovery.show-title", true)) {
            try {
                Title.Times times = Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(3000),
                        java.time.Duration.ofMillis(800));
                Title title = Title.title(
                        messages.getMessage("discovery-title", ph),
                        messages.getMessage("discovery-subtitle", ph), times);
                player.showTitle(title);
            } catch (Exception ignored) {
            }
        }

        String soundName = plugin.getConfig().getString("discovery.discovery-sound",
                "UI_TOAST_CHALLENGE_COMPLETE");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player, sound, 0.7f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
