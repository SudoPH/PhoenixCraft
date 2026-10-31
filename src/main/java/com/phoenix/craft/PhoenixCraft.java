package com.phoenix.craft;

import com.phoenix.craft.achievements.AchievementManager;
import com.phoenix.craft.blackmarket.BlackMarketManager;
import com.phoenix.craft.command.CraftCommand;
import com.phoenix.craft.command.CraftTabCompleter;
import com.phoenix.craft.discovery.DiscoveryManager;
import com.phoenix.craft.economy.EconomyManager;
import com.phoenix.craft.favorites.FavoritesManager;
import com.phoenix.craft.gui.CraftingGUIManager;
import com.phoenix.craft.listener.CraftingListener;
import com.phoenix.craft.recipes.RecipeManager;
import com.phoenix.craft.rental.RentalManager;
import com.phoenix.craft.stats.StatsManager;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.fish.api.PhoenixFishAPI;
import com.phoenix.fish.event.PhoenixFishReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * PhoenixCraft — categorized crafting GUI for PhoenixFish.
 *
 * <p>Modular architecture (Faz 0.3.0):
 * <ul>
 *   <li>{@link StatsManager} — per-player crafting stats (toggleable via stats.enabled)</li>
 *   <li>{@link AchievementManager} — built-in + config-defined achievements
 *       (toggleable via achievements.enabled)</li>
 *   <li>{@link CraftingGUIManager} — GUI rendering + craft logic</li>
 *   <li>{@link MessageManager} — i18n (en/tr) loaded from messages_*.yml</li>
 * </ul>
 *
 * <p>All player-facing text is loaded from {@code messages_en.yml} /
 * {@code messages_tr.yml} based on {@code settings.language} in config.yml.
 * Every system can be independently enabled/disabled in config.yml.</p>
 */
public final class PhoenixCraft extends JavaPlugin implements Listener {

    private PhoenixFishAPI fishApi;
    private CraftingGUIManager guiManager;
    private MessageManager messageManager;
    private StatsManager statsManager;
    private AchievementManager achievementManager;
    private EconomyManager economyManager;
    private RecipeManager recipeManager;
    private DiscoveryManager discoveryManager;
    private BlackMarketManager blackMarketManager;
    private RentalManager rentalManager;
    private FavoritesManager favoritesManager;

    @Override
    public void onEnable() {
        // 1. Save default config + language files
        saveDefaultConfig();
        saveResource("messages_en.yml", false);
        saveResource("messages_tr.yml", false);

        // 2. Load MessageManager (reads config.yml → settings.language)
        this.messageManager = new MessageManager(this);

        // 3. Hook into PhoenixFish
        Plugin fishPlugin = Bukkit.getPluginManager().getPlugin("PhoenixFish");
        if (fishPlugin == null || !fishPlugin.isEnabled()) {
            getLogger().severe("======================================================");
            getLogger().severe(messageManager.getPlainMessage("console-phoenixfish-not-found"));
            getLogger().severe("======================================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.fishApi = Bukkit.getServicesManager().load(PhoenixFishAPI.class);
        if (this.fishApi == null) {
            getLogger().severe(messageManager.getPlainMessage("console-phoenixfish-api-not-registered"));
            getLogger().severe(messageManager.getPlainMessage("console-phoenixfish-api-not-registered-hint"));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!fishApi.isCraftingAvailable()) {
            getLogger().warning(messageManager.getPlainMessage("console-xp-system-disabled"));
            getLogger().warning(messageManager.getPlainMessage("console-xp-system-disabled-hint"));
        }

        // 4. Init Stats + Achievement managers (Faz 0.3.0)
        //    v1.2.0 — Conditionally instantiate based on FeatureFlags so a
        //    disabled feature costs ZERO CPU/RAM (no manager object, no
        //    listener, no scheduled task). The GUI manager accepts null
        //    for any of these and skips rendering the corresponding button.
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "stats")) {
            this.statsManager = new StatsManager(this);
        } else {
            getLogger().info("StatsManager disabled by features.stats.enabled=false — zero overhead.");
        }
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "achievements")) {
            this.achievementManager = new AchievementManager(this, statsManager, messageManager);
        } else {
            getLogger().info("AchievementManager disabled by features.achievements.enabled=false — zero overhead.");
        }

        // 5. Init Economy + Recipe managers (Faz 0.5.0)
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "economy")) {
            this.economyManager = new EconomyManager(this, fishApi);
        } else {
            getLogger().info("EconomyManager disabled by features.economy.enabled=false — zero overhead.");
        }
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "recipe-sharing")) {
            this.recipeManager = new RecipeManager(this, fishApi, messageManager);
        } else {
            getLogger().info("RecipeManager disabled by features.recipe-sharing.enabled=false — zero overhead.");
        }

        // 6. Init Discovery + BlackMarket + Rental managers (Faz 0.6.0)
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "discovery")) {
            this.discoveryManager = new DiscoveryManager(this, messageManager);
        } else {
            getLogger().info("DiscoveryManager disabled by features.discovery.enabled=false — zero overhead.");
        }
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "blackmarket")) {
            this.blackMarketManager = new BlackMarketManager(this, fishApi);
        } else {
            getLogger().info("BlackMarketManager disabled by features.blackmarket.enabled=false — zero overhead.");
        }
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "recipe-rental")) {
            this.rentalManager = new RentalManager(this, fishApi, messageManager);
        } else {
            getLogger().info("RentalManager disabled by features.recipe-rental.enabled=false — zero overhead.");
        }

        // 6b. Init Favorites manager (Faz 1.0.4) — recipe bookmarks persisted to favorites.yml
        if (com.phoenix.craft.config.FeatureFlags.isEnabled(this, "favorites")) {
            this.favoritesManager = new FavoritesManager(this);
            this.favoritesManager.load();
        } else {
            getLogger().info("FavoritesManager disabled by features.favorites.enabled=false — zero overhead.");
        }

        // 7. Init GUI manager — accepts nullable managers and skips rendering
        //    the corresponding buttons when a manager is null.
        //    v1.2.0 — the GUI itself is gated by features.gui.main-menu.enabled;
        //    if disabled, we still construct the manager (so /pcraft commands
        //    can open it programmatically) but we DON'T register the listener
        //    if the GUI is fully disabled.
        this.guiManager = new CraftingGUIManager(this, fishApi, messageManager,
                statsManager, achievementManager, economyManager, recipeManager,
                discoveryManager, blackMarketManager, favoritesManager);

        // Register the inventory-click listener only if at least one GUI
        // affordance is enabled. If every features.gui.* flag is false,
        // there's no point paying the event-dispatch cost.
        if (com.phoenix.craft.config.FeatureFlags.isGuiEnabled(this, "main-menu")
                || com.phoenix.craft.config.FeatureFlags.isGuiEnabled(this, "recipe-detail")
                || com.phoenix.craft.config.FeatureFlags.isGuiEnabled(this, "favorites-gui")) {
            Bukkit.getPluginManager().registerEvents(
                    new CraftingListener(this, guiManager, fishApi, messageManager,
                            recipeManager), this);
        } else {
            getLogger().info("CraftingListener not registered — all GUI affordances disabled.");
        }

        // Self-register as Listener for PhoenixFishReloadEvent, PluginDisableEvent, PlayerQuitEvent
        Bukkit.getPluginManager().registerEvents(this, this);

        // 8. Register command
        PluginCommand cmd = getCommand("pcraft");
        if (cmd != null) {
            CraftCommand executor = new CraftCommand(this, guiManager, messageManager,
                    statsManager, achievementManager, economyManager, recipeManager,
                    rentalManager, blackMarketManager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(new CraftTabCompleter());
        } else {
            getLogger().severe("plugin.yml'de 'pcraft' komutu tanımlı değil!");
        }

        // 9. Schedule auto-save for stats (if enabled)
        scheduleAutoSave();

        // 10. Log success
        Map<String, String> ph = new HashMap<>();
        ph.put("%version%", getPluginMeta().getVersion());
        ph.put("%class%", fishApi.getClass().getName());
        getLogger().info(messageManager.getPlainMessage("console-plugin-enabled", ph));
        getLogger().info(messageManager.getPlainMessage("console-connected-api", ph));
    }

    @Override
    public void onDisable() {
        // Save stats + achievements + recipes on shutdown
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (achievementManager != null) {
            achievementManager.shutdown();
        }
        if (recipeManager != null) {
            recipeManager.shutdown();
        }
        if (discoveryManager != null) {
            discoveryManager.shutdown();
        }
        if (blackMarketManager != null) {
            blackMarketManager.shutdown();
        }
        if (rentalManager != null) {
            rentalManager.shutdown();
        }
        if (favoritesManager != null) {
            favoritesManager.shutdown();
        }

        // Close any open crafting GUIs before we go
        if (guiManager != null) {
            guiManager.closeAllGUIs();
        }
        if (messageManager != null) {
            getLogger().info(messageManager.getPlainMessage("console-plugin-disabled"));
        } else {
            getLogger().info("PhoenixCraft disabled.");
        }
    }

    /**
     * Schedule periodic auto-save of stats data, if enabled in config.
     */
    private void scheduleAutoSave() {
        if (statsManager == null || !statsManager.isEnabled()) return;
        long intervalSec = getConfig().getLong("stats.auto-save-interval-seconds", 60);
        if (intervalSec <= 0) return;

        long intervalTicks = intervalSec * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Faz 0.9.0: save all dirty managers in one pass
            if (statsManager != null) {
                statsManager.save();
            }
            if (achievementManager != null) {
                achievementManager.saveUnlocks();
            }
            if (recipeManager != null) {
                recipeManager.save();
            }
            if (discoveryManager != null) {
                discoveryManager.save();
            }
            if (rentalManager != null) {
                rentalManager.save();
            }
            if (favoritesManager != null) {
                favoritesManager.save();
            }
        }, intervalTicks, intervalTicks);
    }

    // =====================================================================
    //  Lifecycle event handlers
    // =====================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhoenixFishReload(PhoenixFishReloadEvent event) {
        if (guiManager == null) return;
        getLogger().info("PhoenixFish reload detected (took " + event.getDurationMs()
                + "ms) — closing all open crafting GUIs.");
        // Faz 0.9.0: clear all pending search states (stale after reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            guiManager.getPlayerState().clearSearchInput(p.getUniqueId());
        }
        guiManager.closeAllGUIs();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhoenixFishDisabled(PluginDisableEvent event) {
        if (guiManager == null) return;
        if (event.getPlugin() == null) return;
        if (!"PhoenixFish".equals(event.getPlugin().getName())) return;

        getLogger().warning("PhoenixFish was disabled at runtime! Closing all crafting GUIs.");
        getLogger().warning("Crafting will be unavailable until PhoenixFish is re-enabled.");
        guiManager.closeAllGUIs();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (guiManager != null) {
            guiManager.clearCooldown(event.getPlayer().getUniqueId());
            // Faz 0.4.0: clear filter/sort/search state
            guiManager.getPlayerState().clear(event.getPlayer().getUniqueId());
        }
    }

    // =====================================================================
    //  Getters
    // =====================================================================

    public PhoenixFishAPI getFishApi() {
        return fishApi;
    }

    public CraftingGUIManager getGuiManager() {
        return guiManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public DiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public BlackMarketManager getBlackMarketManager() {
        return blackMarketManager;
    }

    public RentalManager getRentalManager() {
        return rentalManager;
    }

    public FavoritesManager getFavoritesManager() {
        return favoritesManager;
    }

    /**
     * Reload PhoenixCraft config + language files. Called by /pcraft reload.
     *
     * <p><b>v1.0.1:</b> Now properly refreshes config.yml from disk via
     * {@link org.bukkit.plugin.java.JavaPlugin#reloadConfig()} BEFORE
     * reloading MessageManager. All manager isEnabled()/isRequireLearning()
     * methods now read live from {@code plugin.getConfig()} on every call,
     * so they automatically pick up the new values without needing their
     * own explicit reload step.</p>
     *
     * <p>Note: The data files (stats.yml, achievements.yml, learned_recipes.yml,
     * discovered_recipes.yml, rentals.yml, blackmarket.yml) are NOT reloaded
     * — that would wipe in-memory state (in-progress rentals, scheduled black
     * market refreshes, leaderboard cache). The achievements DEFINITIONS are
     * also not reloaded (those are baked into the manager at construction).
     * To pick up new achievement definitions, restart the server.</p>
     */
    public void reloadCraftConfig() {
        // Step 1: re-read config.yml from disk into plugin.getConfig()
        reloadConfig();

        // Step 2: reload MessageManager (re-reads settings.language +
        // messages_<lang>.yml). MessageManager.reload() also calls
        // plugin.reloadConfig() — but we already did it above, so the
        // second call is just a no-op.
        if (messageManager != null) {
            messageManager.reload();
        }

        // Step 3: Log the new state of each subsystem so admins can confirm
        // their config changes took effect.
        if (economyManager != null) {
            getLogger().info("EconomyManager after reload: enabled="
                    + economyManager.isEnabled()
                    + ", default-fee=$" + getConfig().getDouble("economy.default-fee", 0.0)
                    + ", charge-per-item-in-bulk="
                    + getConfig().getBoolean("economy.charge-per-item-in-bulk", true));
        }
        if (statsManager != null) {
            getLogger().info("StatsManager after reload: enabled=" + statsManager.isEnabled());
        }
        if (achievementManager != null) {
            getLogger().info("AchievementManager after reload: enabled=" + achievementManager.isEnabled());
        }
        if (discoveryManager != null) {
            getLogger().info("DiscoveryManager after reload: enabled=" + discoveryManager.isEnabled()
                    + ", bonus-xp-multiplier=" + getConfig().getDouble("discovery.bonus-xp-multiplier", 4.0));
        }
        if (recipeManager != null) {
            getLogger().info("RecipeManager after reload: enabled=" + recipeManager.isEnabled()
                    + ", require-learning=" + recipeManager.isRequireLearning());
        }
        if (rentalManager != null) {
            getLogger().info("RentalManager after reload: enabled=" + rentalManager.isEnabled());
        }
        if (blackMarketManager != null) {
            getLogger().info("BlackMarketManager after reload: enabled=" + blackMarketManager.isEnabled()
                    + ", refresh-interval-min=" + getConfig().getLong("blackmarket.refresh-interval-minutes", 360));
        }
        if (favoritesManager != null) {
            getLogger().info("FavoritesManager after reload: enabled=" + favoritesManager.isEnabled());
        }
    }
}
