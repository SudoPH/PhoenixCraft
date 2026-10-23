package com.phoenix.craft;

import com.phoenix.craft.command.CraftCommand;
import com.phoenix.craft.command.CraftTabCompleter;
import com.phoenix.craft.gui.CraftingGUIManager;
import com.phoenix.craft.listener.CraftingListener;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PhoenixCraft — kategorize crafting GUI for PhoenixFish.
 *
 * <p>Tüm iş mantığı ve veri PhoenixFish tarafında kalır. Bu eklenti sadece
 * GUI ve etkileşim sağlar. {@link PhoenixFishAPI} PhoenixFish tarafından
 * Bukkit ServicesManager'a kaydedilir; PhoenixCraft onu yükler.</p>
 */
public final class PhoenixCraft extends JavaPlugin {

    private PhoenixFishAPI fishApi;
    private CraftingGUIManager guiManager;

    @Override
    public void onEnable() {
        Plugin fishPlugin = Bukkit.getPluginManager().getPlugin("PhoenixFish");
        if (fishPlugin == null || !fishPlugin.isEnabled()) {
            getLogger().severe("======================================================");
            getLogger().severe(" PhoenixFish bulunamadı! PhoenixCraft devre dışı bırakılıyor.");
            getLogger().severe(" Lütfen önce PhoenixFish'i kurun, sonra PhoenixCraft'ı.");
            getLogger().severe("======================================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.fishApi = Bukkit.getServicesManager().load(PhoenixFishAPI.class);
        if (this.fishApi == null) {
            getLogger().severe("PhoenixFish yüklü ama PhoenixFishAPI servisi kayıtlı değil!");
            getLogger().severe("PhoenixFish sürümünüzün güncel olduğundan emin olun.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!fishApi.isCraftingAvailable()) {
            getLogger().warning("PhoenixFish'in XP sistemi kapalı (xp-system.enabled=false).");
            getLogger().warning("Crafting menüleri açılacak ama üretim engellenecek.");
        }

        this.guiManager = new CraftingGUIManager(this, fishApi);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(this, guiManager, fishApi), this);

        PluginCommand cmd = getCommand("pcraft");
        if (cmd != null) {
            CraftCommand executor = new CraftCommand(this, guiManager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(new CraftTabCompleter());
        } else {
            getLogger().severe("plugin.yml'de 'pcraft' komutu tanımlı değil!");
        }

        getLogger().info("PhoenixCraft v" + getPluginMeta().getVersion() + " etkinleştirildi.");
        getLogger().info("Bağlı PhoenixFishAPI: " + fishApi.getClass().getName());
    }

    @Override
    public void onDisable() {
        getLogger().info("PhoenixCraft devre dışı bırakıldı.");
    }

    public PhoenixFishAPI getFishApi() {
        return fishApi;
    }

    public CraftingGUIManager getGuiManager() {
        return guiManager;
    }
}
