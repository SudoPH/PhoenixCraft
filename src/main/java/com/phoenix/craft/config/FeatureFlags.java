package com.phoenix.craft.config;

import com.phoenix.craft.PhoenixCraft;
import java.util.Map;

/**
 * Central feature-flag registry. Every manager's enable/disable state is
 * read through this class so there's a single source of truth.
 *
 * <p>Flags live in {@code config.yml → features.<name>.enabled}. When a flag
 * is {@code false}, {@link com.phoenix.craft.PhoenixCraft} never instantiates
 * the corresponding manager — zero CPU, zero RAM. On {@code /pcraft reload},
 * the plugin tears down managers whose flag flipped to false and spins up
 * managers whose flag flipped to true, without a server restart.</p>
 *
 * <h3>v1.1.0 — GUI sub-flags</h3>
 * <p>GUI affordances have their own sub-namespace: {@code features.gui.<x>.enabled}.
 * These gate specific buttons/slots inside the GUIs WITHOUT disabling the
 * underlying crafting system. When a GUI flag is false, the button/slot is
 * replaced with a black padding pane — no error, no tooltip, no click
 * handler. Use {@link #isGuiEnabled(PhoenixCraft, String)} for these.</p>
 *
 * @since 1.0.9
 */
public final class FeatureFlags {

    private FeatureFlags() {}

    /** Default values for every feature flag. false = opt-in, true = opt-out. */
    public static final Map<String, Boolean> DEFAULTS = Map.ofEntries(
            // Core systems (existing)
            Map.entry("stats",               true),
            Map.entry("achievements",        true),
            Map.entry("economy",             false),
            Map.entry("recipe-sharing",      false),
            Map.entry("discovery",           true),
            Map.entry("blackmarket",         false),
            Map.entry("recipe-rental",       false),
            // v1.0.7 advanced crafting features
            Map.entry("favorites",           true),
            Map.entry("tabs",                true),
            Map.entry("crafting-table",      true),
            Map.entry("success-failure-rate",true),
            Map.entry("critical-success",    true),
            Map.entry("multi-step",          true),
            Map.entry("material-scaling",    true),
            Map.entry("conditional-unlock",  true),
            Map.entry("batch-preview",       true),
            Map.entry("crafting-queue",      true),
            Map.entry("crafting-coop",       true),
            Map.entry("recipe-decay",        false),
            Map.entry("crafting-animation",  true)
    );

    /**
     * Default values for the v1.1.0 GUI sub-flags
     * ({@code features.gui.<name>.enabled}). All default to true so a fresh
     * install shows every affordance — admins opt OUT of individual GUI
     * elements by setting them to false here.
     */
    public static final Map<String, Boolean> GUI_DEFAULTS = Map.ofEntries(
            Map.entry("main-menu",         true),
            Map.entry("recipe-detail",     true),
            Map.entry("favorites-gui",     true),
            Map.entry("search-button",     true),
            Map.entry("filter-button",     true),
            Map.entry("sort-button",       true),
            Map.entry("pagination-arrows", true),
            Map.entry("favorite-star",     true),
            // v1.2.0 — per-subsystem GUI affordance toggles
            Map.entry("custom-rods-gui",          true),
            Map.entry("custom-baits-gui",         true),
            Map.entry("crafting-recipe-gui",      true),
            Map.entry("success-rate-display",     true),
            Map.entry("batch-crafting-button",    true),
            Map.entry("dynamic-scaling-display",  true),
            Map.entry("coop-crafting-button",     false)
    );

    /**
     * Read a feature flag from {@code features.<name>.enabled} in config.yml.
     * Falls back to {@link #DEFAULTS} if the key is missing.
     *
     * <p>v1.2.0 — wrapped in try/catch so a malformed config.yml never
     * crashes the plugin. On any exception, the default value is returned.</p>
     */
    public static boolean isEnabled(PhoenixCraft plugin, String feature) {
        try {
            boolean def = DEFAULTS.getOrDefault(feature, false);
            return plugin.getConfig().getBoolean("features." + feature + ".enabled", def);
        } catch (Throwable t) {
            plugin.getLogger().warning("FeatureFlags.isEnabled('" + feature
                    + "') failed: " + t.getMessage() + " — using default.");
            return DEFAULTS.getOrDefault(feature, false);
        }
    }

    /**
     * Read a GUI sub-flag from {@code features.gui.<name>.enabled} in config.yml.
     * Falls back to {@link #GUI_DEFAULTS} if the key is missing.
     *
     * <p>GUI sub-flags only affect the rendering of specific buttons/slots —
     * they NEVER instantiate or tear down managers. The underlying crafting
     * system continues to work even if its GUI button is hidden.</p>
     *
     * <p>v1.2.0 — wrapped in try/catch; on any exception returns the default.</p>
     *
     * @since v1.1.0
     */
    public static boolean isGuiEnabled(PhoenixCraft plugin, String guiFlag) {
        try {
            boolean def = GUI_DEFAULTS.getOrDefault(guiFlag, true);
            return plugin.getConfig().getBoolean("features.gui." + guiFlag + ".enabled", def);
        } catch (Throwable t) {
            plugin.getLogger().warning("FeatureFlags.isGuiEnabled('" + guiFlag
                    + "') failed: " + t.getMessage() + " — using default.");
            return GUI_DEFAULTS.getOrDefault(guiFlag, true);
        }
    }

    /** Convenience: returns true if ANY of the specified features is enabled. */
    public static boolean anyEnabled(PhoenixCraft plugin, String... features) {
        for (String f : features) {
            if (isEnabled(plugin, f)) return true;
        }
        return false;
    }
}
