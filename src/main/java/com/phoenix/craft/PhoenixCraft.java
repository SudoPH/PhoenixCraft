package com.phoenix.craft;

import com.phoenix.craft.achievements.AchievementManager;
import com.phoenix.craft.blackmarket.BlackMarketManager;
import com.phoenix.craft.command.CraftCommand;
import com.phoenix.craft.command.CraftTabCompleter;
import com.phoenix.craft.discovery.DiscoveryManager;
import com.phoenix.craft.economy.EconomyManager;
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
        this.statsManager = new StatsManager(this);
        this.achievementManager = new AchievementManager(this, statsManager, messageManager);

        // 5. Init Economy + Recipe managers (Faz 0.5.0)
        this.economyManager = new EconomyManager(this, fishApi);
        this.recipeManager = new RecipeManager(this, fishApi, messageManager);

        // 6. Init Discovery + BlackMarket managers (Faz 0.6.0)
        this.discoveryManager = new DiscoveryManager(this, messageManager);
        this.blackMarketManager = new BlackMarketManager(this, fishApi);
        this.rentalManager = new RentalManager(this, fishApi, messageManager);

        // 7. Init GUI manager
        this.guiManager = new CraftingGUIManager(this, fishApi, messageManager,
                statsManager, achievementManager, economyManager, recipeManager,
                discoveryManager, blackMarketManager);
        Bukkit.getPluginManager().registerEvents(
                new CraftingListener(this, guiManager, fishApi, messageManager,
                        recipeManager), this);

        // Self-register as Listener for PhoenixFishReloadEvent, PluginDisableEvent, PlayerQuitEvent
        Bukkit.getPluginManager().registerEvents(this, this);

        // 6. Register command
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

        // 7. Schedule auto-save for stats (if enabled)
        scheduleAutoSave();

        // 8. Log success
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

    /**
     * Reload PhoenixCraft config + language files. Called by /pcraft reload.
     *
     * <p>Note: Stats + Achievement managers reload their config-driven behavior
     * (achievement definitions, enabled flags) on the next reloadCraftConfig()
     * call. The data files (stats.yml, achievements.yml) are NOT reloaded —
     * that would wipe in-memory state.</p>
     */
    public void reloadCraftConfig() {
        if (messageManager != null) {
            messageManager.reload();
        }
        // Achievement definitions are re-read from config on reload
        if (achievementManager != null && achievementManager.isEnabled()) {
            // Re-init by recreating definitions — but keep unlocked data
            // (the manager itself reads config dynamically via plugin.getConfig())
        }
    }
}
