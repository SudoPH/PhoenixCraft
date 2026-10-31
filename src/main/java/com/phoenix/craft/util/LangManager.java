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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modular multi-language manager for PhoenixCraft.
 *
 * <p>Replaces the legacy flat {@code messages_<lang>.yml} layout with a
 * namespace-based layout:
 * <pre>
 *   lang/
 *     en/
 *       crafting.yml
 *       achievements.yml
 *       blackmarket.yml
 *       rental.yml
 *       stats.yml
 *       commands.yml
 *       misc.yml
 *     tr/
 *       crafting.yml
 *       ...
 *     zh_cn/
 *       crafting.yml
 *       ...
 * </pre>
 *
 * <h3>Key resolution</h3>
 * <ul>
 *   <li>Active language is read from {@code config.yml → settings.language}.</li>
 *   <li>Each key is looked up in the active language's namespace files.</li>
 *   <li>If missing, the English equivalent is tried.</li>
 *   <li>If still missing, the raw key is returned (and a one-shot warning is
 *       logged so the console isn't spammed).</li>
 * </ul>
 *
 * <h3>MiniMessage escape semantics (the raw-tag bug fix)</h3>
 * <p>Two accessors are provided:</p>
 * <ul>
 *   <li>{@link #get(String, boolean, Map)} — escapes placeholder values via
 *       {@code miniMessage.escapeTags(value)}. Use this for any line where
 *       placeholder values come from USER INPUT (e.g. search query, player
 *       names) to prevent MiniMessage injection.</li>
 *   <li>{@link #getRaw(String, boolean, Map)} — does NOT escape. Use this
 *       for any line where a placeholder value contains a legitimate
 *       MiniMessage tag (e.g. {@code %color%} → {@code "<red>"} /
 *       {@code "<green>"}). This is the fix for the raw
 *       {@code <red>Craft Fee: $100.00} rendering bug.</li>
 * </ul>
 *
 * <h3>Caching</h3>
 * <p>Each (language, namespace) pair is loaded into a {@link FileConfiguration}
 * on first access and cached. English defaults are loaded once at construction
 * and attached as the Bukkit defaults chain via {@link FileConfiguration#setDefaults}.
 * A single {@link #invalidate()} call reloads everything on {@code /pcraft reload}.</p>
 */
public class LangManager {

    private final PhoenixCraft plugin;
    private final MiniMessage miniMessage;

    /** Domains mirrored as lang/&lt;code&gt;/&lt;domain&gt;.yml files. */
    public static final String[] DOMAINS = {
            "crafting", "achievements", "blackmarket",
            "rental", "stats", "commands", "misc"
    };

    /** Cached: lang -> domain -> FileConfiguration (active language only). */
    private final Map<String, FileConfiguration> activeConfigs = new ConcurrentHashMap<>();

    /** Cached: domain -> FileConfiguration (English fallback, immutable after load). */
    private final Map<String, FileConfiguration> englishDefaults = new ConcurrentHashMap<>();

    private volatile String prefix = "";
    private volatile String language = "en";

    /** Track already-warned missing keys to avoid console spam. */
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    public LangManager(PhoenixCraft plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        load();
    }

    // =====================================================================
    //  Loading
    // =====================================================================

    /**
     * Load the active language + English defaults. Called from constructor
     * and from {@link #reload()}.
     */
    public void load() {
        plugin.saveDefaultConfig();

        // Always extract bundled English files (replace=false → no-op if exists).
        for (String domain : DOMAINS) {
            plugin.saveResource("lang/en/" + domain + ".yml", false);
        }

        // Resolve the requested language code.
        String requested = plugin.getConfig().getString("settings.language", "en");
        if (requested == null) requested = "en";
        requested = requested.trim().toLowerCase();

        // If the requested language has at least one domain file (in data
        // folder OR bundled in the jar), use it. Otherwise fall back to English.
        String resolved = requested;
        if (!hasAnyDomainFile(requested) && !"en".equals(requested)) {
            plugin.getLogger().warning("Language '" + requested
                    + "' has no lang/" + requested + "/ files — falling back to English.");
            resolved = "en";
        }

        // Extract the requested language's bundled files (if any) so admins
        // can edit them on disk.
        if (!"en".equals(resolved)) {
            for (String domain : DOMAINS) {
                String path = "lang/" + resolved + "/" + domain + ".yml";
                try (InputStream in = plugin.getResource(path)) {
                    if (in != null) plugin.saveResource(path, false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not extract " + path + ": " + e.getMessage());
                }
            }
        }

        // Clear caches.
        activeConfigs.clear();
        englishDefaults.clear();
        warnedMissingKeys.clear();

        // Load English defaults first (used as Bukkit setDefaults fallback).
        for (String domain : DOMAINS) {
            FileConfiguration cfg = loadDomainFile("en", domain);
            if (cfg != null) englishDefaults.put(domain, cfg);
        }

        // Load active language. For English, activeConfigs just aliases the defaults.
        if ("en".equals(resolved)) {
            activeConfigs.putAll(englishDefaults);
        } else {
            for (String domain : DOMAINS) {
                FileConfiguration cfg = loadDomainFile(resolved, domain);
                if (cfg != null) {
                    // Attach the English defaults so missing keys fall through.
                    FileConfiguration eng = englishDefaults.get(domain);
                    if (eng != null) cfg.setDefaults(eng);
                    activeConfigs.put(domain, cfg);
                } else {
                    // No file for this domain in the active language — use English directly.
                    FileConfiguration eng = englishDefaults.get(domain);
                    if (eng != null) activeConfigs.put(domain, eng);
                }
            }
        }

        // Read prefix from the crafting domain (legacy location).
        FileConfiguration crafting = activeConfigs.get("crafting");
        if (crafting != null) {
            this.prefix = crafting.getString("prefix", "");
        }

        this.language = resolved;
        plugin.getLogger().info("Loaded language: " + resolved
                + (requested.equals(resolved) ? "" : " (requested '" + requested + "')"));
    }

    /** Reload config + language files. */
    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public String getLanguage() { return language; }
    public MiniMessage getMiniMessage() { return miniMessage; }
    public String getPrefix() { return prefix != null ? prefix : ""; }

    // =====================================================================
    //  Internal: file loading + key lookup
    // =====================================================================

    private boolean hasAnyDomainFile(String lang) {
        for (String domain : DOMAINS) {
            String path = "lang/" + lang + "/" + domain + ".yml";
            if (new File(plugin.getDataFolder(), path).exists()) return true;
            try (InputStream in = plugin.getResource(path)) {
                if (in != null) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private FileConfiguration loadDomainFile(String lang, String domain) {
        String path = "lang/" + lang + "/" + domain + ".yml";
        File file = new File(plugin.getDataFolder(), path);

        // If the on-disk file is missing, try extracting from the jar.
        if (!file.exists()) {
            try (InputStream in = plugin.getResource(path)) {
                if (in != null) plugin.saveResource(path, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not extract " + path + ": " + e.getMessage());
            }
        }
        if (!file.exists()) return null;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Also overlay the bundled-jar version of this exact (lang, domain)
        // as a secondary defaults chain — handles the case where the admin
        // deleted keys from the on-disk file but they still exist in the jar.
        try (InputStream in = plugin.getResource(path)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    FileConfiguration jarCfg = YamlConfiguration.loadConfiguration(reader);
                    // Merge jar keys into cfg without overwriting on-disk values.
                    for (String key : jarCfg.getKeys(false)) {
                        if (!cfg.contains(key)) cfg.set(key, jarCfg.get(key));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not overlay jar defaults for " + path + ": " + e.getMessage());
        }

        return cfg;
    }

    /**
     * Resolve a key to its raw string value (placeholders not yet substituted)
     * by walking the active language → English fallback → raw-key-return path.
     */
    private String resolveRawString(String key) {
        for (FileConfiguration cfg : activeConfigs.values()) {
            String val = cfg.getString(key);
            if (val != null) return val;
        }
        // Last resort: also check English defaults directly (in case the
        // active language's domain file is missing entirely and was NOT
        // aliased above).
        for (FileConfiguration cfg : englishDefaults.values()) {
            String val = cfg.getString(key);
            if (val != null) return val;
        }
        // Log a one-shot warning so the console isn't spammed.
        if (warnedMissingKeys.add(key)) {
            plugin.getLogger().warning("Missing lang key (no fallback found): " + key
                    + " — returning the raw key as a placeholder.");
        }
        return key;
    }

    // =====================================================================
    //  Public API — Component accessors
    // =====================================================================

    /**
     * Get a message as a Component, with prefix and ESCAPED placeholders.
     * Use this when placeholder values come from user input (search queries,
     * player names) — prevents MiniMessage injection.
     */
    public Component get(String key, boolean addPrefix, Map<String, String> placeholders) {
        return render(key, addPrefix, placeholders, true);
    }

    /**
     * Get a message as a Component, with prefix and UNESCAPED placeholders.
     * Use this when a placeholder value is itself a legitimate MiniMessage
     * tag (e.g. {@code %color%} → {@code "<red>"}). This is the fix for the
     * raw {@code <red>Craft Fee: $100.00} rendering bug.
     */
    public Component getRaw(String key, boolean addPrefix, Map<String, String> placeholders) {
        return render(key, addPrefix, placeholders, false);
    }

    /** Component, no placeholders, no prefix. */
    public Component get(String key) {
        return render(key, false, null, true);
    }

    /** Component, no placeholders, with optional prefix. */
    public Component get(String key, boolean addPrefix) {
        return render(key, addPrefix, null, true);
    }

    /** Component, with placeholders, no prefix. */
    public Component get(String key, Map<String, String> placeholders) {
        return render(key, false, placeholders, true);
    }

    /** Raw-variant: Component, no prefix, with UNESCAPED placeholders. */
    public Component getRaw(String key, Map<String, String> placeholders) {
        return render(key, false, placeholders, false);
    }

    private Component render(String key, boolean addPrefix,
                              Map<String, String> placeholders, boolean escape) {
        String message = resolveRawString(key);
        message = substitute(message, placeholders, escape);
        if (addPrefix && prefix != null && !prefix.isEmpty()) {
            message = prefix + message;
        }
        try {
            return miniMessage.deserialize(message);
        } catch (Throwable t) {
            // MiniMessage parse failure — log + return plain text so the
            // GUI doesn't break entirely.
            if (warnedMissingKeys.add("parse-fail:" + key)) {
                plugin.getLogger().warning("MiniMessage parse failed for key '" + key
                        + "': " + t.getMessage() + " — returning plain text.");
            }
            return Component.text(message);
        }
    }

    // =====================================================================
    //  Public API — plain-string accessors (for console logs, etc.)
    // =====================================================================

    public String getPlain(String key, Map<String, String> placeholders) {
        return substitute(resolveRawString(key), placeholders, true);
    }

    public String getPlain(String key) {
        return getPlain(key, null);
    }

    // =====================================================================
    //  Placeholder substitution
    // =====================================================================

    private String substitute(String message, Map<String, String> placeholders, boolean escape) {
        if (placeholders == null || placeholders.isEmpty()) return message;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String value = e.getValue();
            if (value == null) {
                value = "";
            } else if (escape) {
                value = miniMessage.escapeTags(value);
            }
            message = message.replace(e.getKey(), value);
        }
        return message;
    }

    // =====================================================================
    //  Diagnostic — for /pcraft debug
    // =====================================================================

    /** Returns the set of domains currently loaded for the active language. */
    public List<String> getLoadedDomains() {
        return new ArrayList<>(activeConfigs.keySet());
    }

    /** Total key count across all loaded domain files for the active language. */
    public int getTotalKeyCount() {
        int total = 0;
        for (FileConfiguration cfg : activeConfigs.values()) {
            total += cfg.getKeys(true).size();
        }
        return total;
    }
}
