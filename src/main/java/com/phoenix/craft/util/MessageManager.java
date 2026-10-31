package com.phoenix.craft.util;

import com.phoenix.craft.PhoenixCraft;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PhoenixCraft'ın kendi dil yöneticisi.
 *
 * <p>PhoenixFish'in MessageManager'ından tamamen bağımsızdır — PhoenixCraft'ın
 * kendi dil dosyalarını okur. Hangi dilin yükleneceği <code>config.yml</code>
 * dosyasındaki <code>settings.language</code> ayarından belirlenir.</p>
 *
 * <p><b>Dinamik dil desteği (v1.0.1):</b> Herhangi bir dil kodu kabul edilir
 * (tr, en, zh_cn, de, fr, es, ...). Dosya veri klasöründe yoksa jar'dan çıkarılır.
 * Jar'da da yoksa İngilizce fallback kullanılır.</p>
 *
 * <p><b>Cascade lookup (v1.1.1 —Console Spam Fix):</b> Bir anahtar aranırken
 * sırasıyla aşağıdaki kaynaklar kontrol edilir; ilk bulunan değer kullanılır:
 * <ol>
 *   <li><code>messages_&lt;dil&gt;.yml</code> — eski düz dosya (en yüksek öncelik,
 *       geriye dönük uyumluluk için).</li>
 *   <li><code>lang/&lt;dil&gt;/*.yml</code> — modüler dil dosyaları (v1.1.0+
 *       tarafından kullanılan yeni anahtarların kaynağı).</li>
 *   <li><code>messages_en.yml</code> — İngilizce düz dosya (fallback).</li>
 *   <li><code>lang/en/*.yml</code> — İngilizce modüler dosyalar (son çare).</li>
 * </ol>
 *
 * <p>Bu sayede <code>lang/en/crafting.yml</code></code> içine eklenen yeni bir
 * anahtar, MessageManager tarafından düzgün şekilde bulunur ve "Missing message
 * key" uyarısı ASLA konsolu spamlamaz.</p>
 *
 * <p><b>One-shot warning cache:</b> Eğer bir anahtar YUKARIDAKİ TÜM kaynaklarda
 * bulunamazsa, {@link #warnedMissingKeys} küpesine eklenir ve uyarı YALNIZCA BİR
 * KEZ loglanır. Aynı anahtar için tekrar tekrar uyarı loglanmaz.</p>
 *
 * <p>Reload desteği: {@link #reload()} metodu config + dil dosyalarını
 * yeniden yükler. <code>/pcraft reload</code> komutu bunu çağırır.</p>
 */
public class MessageManager {

    private final PhoenixCraft plugin;
    private final MiniMessage miniMessage;

    private volatile FileConfiguration langConfig;       // flat messages_<lang>.yml
    private volatile FileConfiguration modularConfig;     // merged lang/<lang>/*.yml
    private volatile FileConfiguration englishFlatConfig; // messages_en.yml fallback
    private volatile FileConfiguration englishModularConfig; // lang/en/*.yml fallback
    private volatile String prefix;
    private volatile String language;

    /** One-shot warning cache — each missing key is logged AT MOST once. */
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    /** Modular lang domains loaded by LangManager (mirrored here for cascade). */
    private static final String[] LANG_DOMAINS = {
        "crafting", "achievements", "blackmarket",
        "rental", "stats", "commands", "misc"
    };

    public MessageManager(PhoenixCraft plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadLanguage();
    }

    /**
     * config.yml'den settings.language değerini okur ve ilgili dil dosyalarını
     * yükler. v1.1.1 — Artık HEM düz dosyaları HEM de modüler lang/ dosyalarını
     * paralel olarak yükler ve cascade lookup yapar.
     */
    public void loadLanguage() {
        // Save default config if missing
        plugin.saveDefaultConfig();

        // Always extract bundled English + Turkish flat files.
        plugin.saveResource("messages_en.yml", false);
        plugin.saveResource("messages_tr.yml", false);

        // Read the requested language code from config (lowercased, trimmed).
        String requestedLang = plugin.getConfig().getString("settings.language", "en");
        if (requestedLang == null) requestedLang = "en";
        requestedLang = requestedLang.trim().toLowerCase();

        // Resolve the actual language — accept ANY code if the file exists.
        String resolvedLang = requestedLang;
        File langFile = new File(plugin.getDataFolder(), "messages_" + resolvedLang + ".yml");

        if (!langFile.exists()) {
            String resourcePath = "messages_" + resolvedLang + ".yml";
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    plugin.saveResource(resourcePath, false);
                    plugin.getLogger().info("Extracted bundled language file: " + resourcePath);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check/extract '" + resourcePath + "': " + e.getMessage());
            }
        }

        // If still missing → fall back to English
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file 'messages_" + resolvedLang
                    + ".yml' not found in data folder or jar! Falling back to English.");
            resolvedLang = "en";
            langFile = new File(plugin.getDataFolder(), "messages_en.yml");
            if (!langFile.exists()) {
                plugin.saveResource("messages_en.yml", false);
            }
        }

        // ─── Layer 1: load the flat messages_<lang>.yml ───
        FileConfiguration flatCfg = YamlConfiguration.loadConfiguration(langFile);

        // ─── Layer 2: load modular lang/<lang>/*.yml files (merged into one cfg) ───
        FileConfiguration modularCfg = loadModularLangFiles(resolvedLang);

        // ─── Layer 3: English flat fallback ───
        FileConfiguration engFlat = loadJarResourceAsConfig("messages_en.yml");

        // ─── Layer 4: English modular fallback ───
        FileConfiguration engModular = loadModularLangFiles("en");

        // Attach the Bukkit defaults chain for backward compat with any code
        // that calls langConfig.getString() directly (Bukkit's setDefaults
        // mechanism walks one level deep — our own resolveKey walks deeper).
        flatCfg.setDefaults(engFlat);

        // Read prefix (prefer flat, then modular crafting.yml).
        String pfx = flatCfg.getString("prefix", "");
        if (pfx == null || pfx.isEmpty()) {
            pfx = modularCfg.getString("prefix", "");
        }

        this.langConfig = flatCfg;
        this.modularConfig = modularCfg;
        this.englishFlatConfig = engFlat;
        this.englishModularConfig = engModular;
        this.prefix = pfx;
        this.language = resolvedLang;
        // Clear the warning cache so a reload re-warns about genuinely missing keys.
        this.warnedMissingKeys.clear();

        plugin.getLogger().info("Loaded language: " + resolvedLang
                + (requestedLang.equals(resolvedLang) ? "" : " (requested '" + requestedLang + "')"));
    }

    /**
     * Load all {@code lang/<lang>/<domain>.yml} files and merge them into a
     * single FileConfiguration. Missing files / missing domains are silently
     * skipped — they'll be covered by the English fallback layer.
     */
    private FileConfiguration loadModularLangFiles(String lang) {
        FileConfiguration merged = new YamlConfiguration();
        for (String domain : LANG_DOMAINS) {
            String path = "lang/" + lang + "/" + domain + ".yml";
            // 1) On-disk file in the plugin data folder
            File diskFile = new File(plugin.getDataFolder(), path);
            if (diskFile.exists()) {
                try {
                    FileConfiguration domainCfg = YamlConfiguration.loadConfiguration(diskFile);
                    mergeConfigs(merged, domainCfg);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not load " + path + ": " + e.getMessage());
                }
                continue;
            }
            // 2) Bundled-in-jar file (UTF-8 reader)
            try (InputStream in = plugin.getResource(path)) {
                if (in != null) {
                    try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        FileConfiguration domainCfg = YamlConfiguration.loadConfiguration(reader);
                        mergeConfigs(merged, domainCfg);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read bundled " + path + ": " + e.getMessage());
            }
        }
        return merged;
    }

    /** Copy every key from {@code source} into {@code dest} (no overwrite). */
    private void mergeConfigs(FileConfiguration dest, FileConfiguration source) {
        for (String key : source.getKeys(true)) {
            if (dest.contains(key)) continue;
            dest.set(key, source.get(key));
        }
    }

    /** Load a UTF-8 resource bundled in the jar as a FileConfiguration. */
    private FileConfiguration loadJarResourceAsConfig(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return new YamlConfiguration();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load bundled " + resourcePath + ": " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    /**
     * Resolve a key by walking the 4-layer cascade: flat → modular → english-flat
     * → english-modular. Returns {@code null} if the key is missing from ALL
     * four layers (extremely unlikely — English bundled files always cover it).
     *
     * <p>This is the core of the v1.1.1 console-spam fix: a key added only to
     * {@code lang/en/crafting.yml} is now found via the modular-english layer,
     * so the missing-key warning is NEVER triggered for it.</p>
     */
    private String resolveKey(String key) {
        FileConfiguration flat = this.langConfig;
        FileConfiguration modular = this.modularConfig;
        FileConfiguration engFlat = this.englishFlatConfig;
        FileConfiguration engModular = this.englishModularConfig;

        // Layer 1: active-language flat file
        if (flat != null) {
            String v = flat.getString(key);
            if (v != null) return v;
        }
        // Layer 2: active-language modular files
        if (modular != null) {
            String v = modular.getString(key);
            if (v != null) return v;
        }
        // Layer 3: English flat fallback
        if (engFlat != null) {
            String v = engFlat.getString(key);
            if (v != null) return v;
        }
        // Layer 4: English modular fallback
        if (engModular != null) {
            String v = engModular.getString(key);
            if (v != null) return v;
        }
        return null;
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
        // v1.1.1 — Use the 4-layer cascade lookup so keys defined only in
        // lang/<lang>/crafting.yml are found. This is the fix for the
        // "Missing message key" console warnings.
        String message = resolveKey(key);
        if (message == null) {
            // One-shot warning — each missing key is logged AT MOST once
            // per reload, so the console is never spammed.
            if (warnedMissingKeys.add(key)) {
                plugin.getLogger().warning("Missing message key (no fallback found in any layer): " + key);
            }
            // Return the raw key as a last-resort placeholder so the GUI
            // shows something readable instead of an empty Component.
            return Component.text(key);
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
        // v1.1.1 — cascade lookup + one-shot warning.
        String message = resolveKey(key);
        if (message == null) {
            if (warnedMissingKeys.add(key)) {
                plugin.getLogger().warning("Missing message key (no fallback found in any layer): " + key);
            }
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
