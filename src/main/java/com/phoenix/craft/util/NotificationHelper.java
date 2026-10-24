package com.phoenix.craft.util;

import com.phoenix.craft.PhoenixCraft;
import org.bukkit.entity.Player;

/**
 * Centralized notification control — checks config.yml's
 * {@code notifications} section before sending any message,
 * title, or sound to a player.
 *
 * <p>Every method returns false (no-op) if the corresponding
 * config toggle is off, or if the global kill switch is off.</p>
 *
 * @since 0.9.0
 */
public final class NotificationHelper {

    private NotificationHelper() {}

    private static boolean get(PhoenixCraft plugin, String path, boolean def) {
        return plugin.getConfig().getBoolean("notifications." + path, def);
    }

    // =====================================================================
    //  Chat
    // =====================================================================

    public static boolean shouldSendCraftSuccessChat(PhoenixCraft plugin) {
        return get(plugin, "all-chat", true) && get(plugin, "craft-success-chat", true);
    }

    public static boolean shouldSendCraftFailChat(PhoenixCraft plugin) {
        return get(plugin, "all-chat", true) && get(plugin, "craft-fail-chat", true);
    }

    public static boolean shouldSendLevelUpChat(PhoenixCraft plugin) {
        return get(plugin, "all-chat", true) && get(plugin, "crafting-level-up-chat", true);
    }

    public static boolean shouldSendAchievementChat(PhoenixCraft plugin) {
        return get(plugin, "all-chat", true) && get(plugin, "achievement-chat", true);
    }

    public static boolean shouldSendDiscoveryChat(PhoenixCraft plugin) {
        return get(plugin, "all-chat", true) && get(plugin, "discovery-chat", true);
    }

    public static boolean shouldSendEconomyChat(PhoenixCraft plugin) {
        return get(plugin, "all-chat", true) && get(plugin, "economy-fee-chat", false);
    }

    // =====================================================================
    //  Titles
    // =====================================================================

    public static boolean shouldShowLevelUpTitle(PhoenixCraft plugin) {
        return get(plugin, "crafting-level-up-title", true);
    }

    public static boolean shouldShowAchievementTitle(PhoenixCraft plugin) {
        return get(plugin, "achievement-title", true);
    }

    public static boolean shouldShowDiscoveryTitle(PhoenixCraft plugin) {
        return get(plugin, "discovery-title", true);
    }

    // =====================================================================
    //  Sounds
    // =====================================================================

    public static boolean shouldPlayCraftSuccessSound(PhoenixCraft plugin) {
        return get(plugin, "all-sounds", true) && get(plugin, "craft-success-sound", true);
    }

    public static boolean shouldPlayCraftFailSound(PhoenixCraft plugin) {
        return get(plugin, "all-sounds", true) && get(plugin, "craft-fail-sound", true);
    }

    public static boolean shouldPlayLevelUpSound(PhoenixCraft plugin) {
        return get(plugin, "all-sounds", true) && get(plugin, "crafting-level-up-sound", true);
    }

    public static boolean shouldPlayAchievementSound(PhoenixCraft plugin) {
        return get(plugin, "all-sounds", true) && get(plugin, "achievement-sound", true);
    }

    public static boolean shouldPlayDiscoverySound(PhoenixCraft plugin) {
        return get(plugin, "all-sounds", true) && get(plugin, "discovery-sound", true);
    }
}
