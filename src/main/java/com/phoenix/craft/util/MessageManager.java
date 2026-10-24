package com.phoenix.craft.util;

import com.phoenix.craft.PhoenixCraft;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

/**
 * PhoenixCraft'ın kendi dil yöneticisi.
 *
 * <p>PhoenixFish'in MessageManager'ından tamamen bağımsızdır — PhoenixCraft'ın
 * kendi <code>messages_en.yml</code> / <code>messages_tr.yml</code> dosyalarını
 * okur. Hangi dilin yükleneceği <code>config.yml</code> dosyasındaki
 * <code>settings.language</code> ayarından belirlenir.</p>
 *
 * <p>Reload desteği: {@link #reload()} metodu config + dil dosyalarını
 * yeniden yükler. <code>/pcraft reload</code> komutu bunu çağırır.</p>
 */
public class MessageManager {

    private final PhoenixCraft plugin;
    private final MiniMessage miniMessage;

    private volatile FileConfiguration langConfig;
    private volatile String prefix;
    private volatile String language;

    public MessageManager(PhoenixCraft plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadLanguage();
    }

    /**
     * config.yml'den settings.language değerini okur ve ilgili dil dosyasını yükler.
     * Dil dosyası yoksa İngilizce'ye fallback yapar.
     */
    public void loadLanguage() {
        // Save default config if missing
        plugin.saveDefaultConfig();

        // Save both language files (so admins can edit them)
        plugin.saveResource("messages_en.yml", false);
        plugin.saveResource("messages_tr.yml", false);

        String lang = plugin.getConfig().getString("settings.language", "tr").toLowerCase();
        if (!"tr".equals(lang) && !"en".equals(lang)) {
            plugin.getLogger().warning("Unknown language '" + lang + "' — defaulting to 'tr'.");
            lang = "tr";
        }

        File langFile = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file 'messages_" + lang + ".yml' not found! Falling back to English.");
            langFile = new File(plugin.getDataFolder(), "messages_en.yml");
            lang = "en";
            if (!langFile.exists()) {
                plugin.saveResource("messages_en.yml", false);
            }
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(langFile);
        String pfx = cfg.getString("prefix", "");

        this.langConfig = cfg;
        this.prefix = pfx;
        this.language = lang;

        plugin.getLogger().info("Loaded language: " + lang);
    }

    /**
     * Reload config + language files. Called by /pcraft reload.
     */
    public void reload() {
        plugin.reloadConfig();
        loadLanguage();
    }

    public String getLanguage() {
        return language;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    /**
     * Replace placeholders in a message string. Values are MiniMessage-escaped
     * to prevent injection from player input (e.g. search queries, player names).
     *
     * Use {@link #replacePlaceholdersUnsafe} for internal data that may contain
     * valid MiniMessage tags (e.g. tier colors, recipe display names from config).
     */
    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        return replacePlaceholdersUnsafe(message, placeholders, true);
    }

    /**
     * Replace placeholders WITHOUT escaping MiniMessage tags.
     * Use this for internal data (tier colors, config-defined names) that
     * should preserve their MiniMessage formatting.
     */
    private String replacePlaceholdersUnsafe(String message, Map<String, String> placeholders) {
        return replacePlaceholdersUnsafe(message, placeholders, false);
    }

    private String replacePlaceholdersUnsafe(String message, Map<String, String> placeholders,
                                               boolean escape) {
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue();
                if (escape) {
                    value = miniMessage.escapeTags(value);
                }
                message = message.replace(entry.getKey(), value);
            }
        }
        return message;
    }

    /**
     * Get a message as a Component, with prefix and placeholders.
     * Placeholder values are ESCAPED (safe for player input).
     *
     * @param key           message key (e.g. "craft-success-single")
     * @param addPrefix     whether to prepend the prefix
     * @param placeholders  placeholder map (e.g. %item% → "Hardwood Rod")
     */
    public Component getMessage(String key, boolean addPrefix, Map<String, String> placeholders) {
        return getMessageRaw(key, addPrefix, placeholders, true);
    }

    /**
     * Get a message as a Component, with prefix and placeholders.
     * Placeholder values are NOT escaped — use for internal data that
     * may contain valid MiniMessage tags (tier colors, config names).
     */
    public Component getMessageRaw(String key, boolean addPrefix,
                                     Map<String, String> placeholders) {
        return getMessageRaw(key, addPrefix, placeholders, false);
    }

    /**
     * Get a message as a Component, no prefix, with raw (unescaped) placeholders.
     */
    public Component getMessageRaw(String key, Map<String, String> placeholders) {
        return getMessageRaw(key, false, placeholders, false);
    }

    private Component getMessageRaw(String key, boolean addPrefix,
                                      Map<String, String> placeholders, boolean escape) {
        FileConfiguration cfg = this.langConfig;
        if (cfg == null) {
            return Component.text("Missing language configuration");
        }

        String message = cfg.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return Component.empty();
        }

        message = replacePlaceholdersUnsafe(message, placeholders, escape);

        if (addPrefix && prefix != null && !prefix.isEmpty()) {
            message = prefix + message;
        }

        return miniMessage.deserialize(message);
    }

    /**
     * Get a message as a Component, with prefix, no placeholders.
     */
    public Component getMessage(String key, boolean addPrefix) {
        return getMessage(key, addPrefix, null);
    }

    /**
     * Get a message as a Component, no prefix, with placeholders.
     * Useful for inventory item names and lore lines (prefix would clutter them).
     */
    public Component getMessage(String key, Map<String, String> placeholders) {
        return getMessage(key, false, placeholders);
    }

    /**
     * Get a message as a Component, no prefix, no placeholders.
     * Convenience method for inventory titles, names, lore lines without dynamic content.
     */
    public Component getMessage(String key) {
        return getMessage(key, false, null);
    }

    /**
     * Get a plain (un-parsed) message string with placeholders replaced.
     * Useful when the caller needs to do further string manipulation before
     * passing it to MiniMessage (e.g. when building multi-line lore).
     */
    public String getPlainMessage(String key, Map<String, String> placeholders) {
        FileConfiguration cfg = this.langConfig;
        if (cfg == null) {
            return "Missing language configuration";
        }

        String message = cfg.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return key;
        }

        return replacePlaceholders(message, placeholders);
    }

    /**
     * Get a plain message string, no placeholders.
     */
    public String getPlainMessage(String key) {
        return getPlainMessage(key, null);
    }

    /**
     * Get the prefix (already MiniMessage-parsed or not, caller decides).
     */
    public String getPrefix() {
        return prefix != null ? prefix : "";
    }
}
