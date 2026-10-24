package com.phoenix.craft.command;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.achievements.AchievementManager;
import com.phoenix.craft.economy.EconomyManager;
import com.phoenix.craft.gui.CraftingGUIManager;
import com.phoenix.craft.recipes.RecipeManager;
import com.phoenix.craft.stats.StatsManager;
import com.phoenix.craft.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * /pcraft command — opens the crafting GUI.
 *
 * <p>Subcommands (cumulative through versions):
 * <ul>
 *   <li>{@code /pcraft} — open the main menu</li>
 *   <li>{@code /pcraft rods} — jump to rods category</li>
 *   <li>{@code /pcraft baits} — jump to baits category</li>
 *   <li>{@code /pcraft search <query>} — search recipes (Faz 0.4.0)</li>
 *   <li>{@code /pcraft stats} — view your crafting statistics (Faz 0.3.0)</li>
 *   <li>{@code /pcraft achievements} — view your achievements (Faz 0.3.0)</li>
 *   <li>{@code /pcraft recipes} — view your learned recipes (Faz 0.5.0)</li>
 *   <li>{@code /pcraft sell <all|hand>} — sell crafted items (Faz 0.5.0)</li>
 *   <li>{@code /pcraft giverecipe <player> <id>} — give recipe (admin, Faz 0.5.0)</li>
 *   <li>{@code /pcraft reload} — reload config (admin)</li>
 *   <li>{@code /pcraft help} — show help</li>
 * </ul>
 */
public class CraftCommand implements CommandExecutor {

    private final PhoenixCraft plugin;
    private final CraftingGUIManager gui;
    private final MessageManager messages;
    private final StatsManager statsManager;
    private final AchievementManager achievementManager;
    private final EconomyManager economyManager;
    private final RecipeManager recipeManager;

    public CraftCommand(PhoenixCraft plugin, CraftingGUIManager gui, MessageManager messages,
                         StatsManager statsManager, AchievementManager achievementManager,
                         EconomyManager economyManager, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.gui = gui;
        this.messages = messages;
        this.statsManager = statsManager;
        this.achievementManager = achievementManager;
        this.economyManager = economyManager;
        this.recipeManager = recipeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getMessage("command-player-only", false));
            return true;
        }

        if (!player.hasPermission("phoenixcraft.use")) {
            player.sendMessage(messages.getMessage("command-no-permission", true));
            return true;
        }

        if (args.length == 0) {
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "rods", "rod", "oltalar" -> gui.openCategoryMenu(player, "rod", 0);
            case "baits", "bait", "yemler" -> gui.openCategoryMenu(player, "bait", 0);
            case "stats", "stat", "istatistik" -> handleStats(player);
            case "achievements", "achievement", "ach", "başarım" -> handleAchievements(player);
            case "search", "ara" -> handleSearch(player, args);
            case "recipes", "recipe", "tarifler" -> handleRecipes(player);
            case "sell", "sat" -> handleSell(player, args);
            case "giverecipe", "givetarif" -> handleGiveRecipe(player, args);
            case "reload", "rl" -> handleReload(player);
            case "help", "?" -> sendHelp(player);
            default -> player.sendMessage(messages.getMessage("command-unknown-subcommand", true));
        }
        return true;
    }

    private void handleStats(Player player) {
        if (statsManager == null || !statsManager.isEnabled()) {
            player.sendMessage(messages.getMessage("stats-disabled-message", true));
            return;
        }
        gui.openStatsMenu(player);
    }

    private void handleAchievements(Player player) {
        if (achievementManager == null || !achievementManager.isEnabled()) {
            player.sendMessage(messages.getMessage("achievements-disabled-message", true));
            return;
        }
        gui.openAchievementsMenu(player);
    }

    private void handleSearch(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("search.enabled", true)) {
            player.sendMessage(messages.getMessage("search-disabled", true));
            return;
        }

        if (args.length < 2) {
            gui.startSearchInput(player, null);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        gui.performSearch(player, sb.toString(), null);
    }

    /**
     * Faz 0.5.0: /pcraft recipes — view learned recipes.
     */
    private void handleRecipes(Player player) {
        if (recipeManager == null || !recipeManager.isEnabled()) {
            player.sendMessage(messages.getMessage("recipe-sharing-disabled", true));
            return;
        }
        gui.openRecipesMenu(player);
    }

    /**
     * Faz 0.5.0: /pcraft sell <all|hand> — sell crafted items.
     * Sells items that match a recipe's result (by material + custom-model-data).
     */
    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.getMessage("sell-usage", true));
            return;
        }

        // For now, this is a stub — actual sell logic would scan the inventory
        // for items matching recipe results and sell them via PhoenixFish economy.
        // This is a simplified placeholder.
        player.sendMessage(messages.getMessage("sell-no-items", true));
    }

    /**
     * Faz 0.5.0: /pcraft giverecipe <player> <recipe-id> — admin command
     * to teach a recipe to a player (or give them a recipe book).
     */
    private void handleGiveRecipe(Player player, String[] args) {
        if (!player.hasPermission("phoenixcraft.admin")) {
            player.sendMessage(messages.getMessage("giverecipe-no-permission", true));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(messages.getMessage("giverecipe-usage", true));
            return;
        }

        String targetName = args[1];
        String recipeId = args[2].toLowerCase();

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%player%", targetName);
            player.sendMessage(messages.getMessage("giverecipe-player-not-found", true, ph));
            return;
        }

        if (plugin.getFishApi().getCraftingRecipe(recipeId) == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            player.sendMessage(messages.getMessage("giverecipe-recipe-not-found", true, ph));
            return;
        }

        // If recipe-sharing is enabled, learn the recipe directly
        if (recipeManager != null && recipeManager.isEnabled()) {
            recipeManager.learnRecipe(target.getUniqueId(), recipeId);
        }

        // Also give a recipe book item (works whether recipe-sharing is on or off)
        if (recipeManager != null) {
            ItemStack book = recipeManager.createRecipeBook(recipeId);
            if (book != null) {
                Map<Integer, ItemStack> overflow = target.getInventory().addItem(book);
                for (ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
            }
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%recipe%", recipeId);
        ph.put("%player%", target.getName());
        player.sendMessage(messages.getMessage("giverecipe-success", true, ph));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("phoenixcraft.admin")) {
            player.sendMessage(messages.getMessage("reload-no-permission", true));
            return;
        }

        if (plugin.getFishApi() == null) {
            player.sendMessage(messages.getMessage("reload-no-phoenixfish", true));
            return;
        }

        player.sendMessage(messages.getMessage("reload-started", true));
        try {
            plugin.reloadCraftConfig();
        } catch (Exception e) {
            plugin.getLogger().severe("PhoenixCraft config reload failed: " + e.getMessage());
            player.sendMessage(messages.getMessage("reload-failed", true));
            return;
        }
        player.sendMessage(messages.getMessage("reload-success", true));

        player.sendMessage(messages.getMessage("reload-phoenixfish-started", true));
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                boolean success = Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(), "phoenixfish reload");

                if (success) {
                    player.sendMessage(messages.getMessage("reload-phoenixfish-success", true));
                } else {
                    player.sendMessage(messages.getMessage("reload-phoenixfish-failed", true));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("PhoenixFish reload dispatch failed: " + e.getMessage());
                player.sendMessage(messages.getMessage("reload-phoenixfish-failed", true));
            }
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage(messages.getMessage("command-help-header", false));
        player.sendMessage(messages.getMessage("command-help-main", false));
        player.sendMessage(messages.getMessage("command-help-rods", false));
        player.sendMessage(messages.getMessage("command-help-baits", false));
        if (plugin.getConfig().getBoolean("search.enabled", true)) {
            player.sendMessage(messages.getMessage("command-help-search", false));
        }
        if (statsManager != null && statsManager.isEnabled()) {
            player.sendMessage(messages.getMessage("command-help-stats", false));
        }
        if (achievementManager != null && achievementManager.isEnabled()) {
            player.sendMessage(messages.getMessage("command-help-achievements", false));
        }
        if (recipeManager != null && recipeManager.isEnabled()) {
            player.sendMessage(messages.getMessage("command-help-recipes", false));
        }
        player.sendMessage(messages.getMessage("command-help-sell", false));
        if (player.hasPermission("phoenixcraft.admin")) {
            player.sendMessage(messages.getMessage("command-help-giverecipe", false));
            player.sendMessage(messages.getMessage("command-help-reload", false));
        }
    }
}
