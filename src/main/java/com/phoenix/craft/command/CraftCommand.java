package com.phoenix.craft.command;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.gui.CraftingGUIManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /pcraft komutu — crafting GUI'sini açar.
 *
 * <p>Kullanım:
 * <ul>
 *   <li>{@code /pcraft} — ana menü</li>
 *   <li>{@code /pcraft rods} — direkt oltalar kategorisi</li>
 *   <li>{@code /pcraft baits} — direkt yemler kategorisi</li>
 *   <li>{@code /pcraft reload} — (admin) config'i yeniden yükle</li>
 * </ul>
 */
public class CraftCommand implements CommandExecutor {

    private final PhoenixCraft plugin;
    private final CraftingGUIManager gui;
    private final MiniMessage miniMessage;

    public CraftCommand(PhoenixCraft plugin, CraftingGUIManager gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Bu komut sadece oyuncular için.</red>"));
            return true;
        }

        if (!player.hasPermission("phoenixcraft.use")) {
            player.sendMessage(miniMessage.deserialize("<red>Bu komutu kullanma yetkin yok!</red>"));
            return true;
        }

        if (args.length == 0) {
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "rods", "rod", "oltalar" -> gui.openCategoryMenu(player, "rod", 0);
            case "baits", "bait", "yemler" -> gui.openCategoryMenu(player, "bait", 0);
            case "help", "?" -> sendHelp(player);
            default -> {
                player.sendMessage(miniMessage.deserialize(
                        "<red>Bilinmeyen altkomut. <yellow>/pcraft help</yellow>"));
            }
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(miniMessage.deserialize("<gold>===== PhoenixCraft ====="));
        player.sendMessage(miniMessage.deserialize("<yellow>/pcraft <gray>- Üretim menüsünü aç"));
        player.sendMessage(miniMessage.deserialize("<yellow>/pcraft rods <gray>- Oltalar kategorisi"));
        player.sendMessage(miniMessage.deserialize("<yellow>/pcraft baits <gray>- Yemler kategorisi"));
    }
}
