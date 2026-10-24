package com.phoenix.craft.command;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.achievements.AchievementManager;
import com.phoenix.craft.blackmarket.BlackMarketManager;
import com.phoenix.craft.economy.EconomyManager;
import com.phoenix.craft.gui.CraftingGUIManager;
import com.phoenix.craft.recipes.RecipeManager;
import com.phoenix.craft.rental.RentalManager;
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
import java.util.List;
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
    private final RentalManager rentalManager;
    private final BlackMarketManager blackMarketManager;

    public CraftCommand(PhoenixCraft plugin, CraftingGUIManager gui, MessageManager messages,
                         StatsManager statsManager, AchievementManager achievementManager,
                         EconomyManager economyManager, RecipeManager recipeManager,
                         RentalManager rentalManager, BlackMarketManager blackMarketManager) {
        this.plugin = plugin;
        this.gui = gui;
        this.messages = messages;
        this.statsManager = statsManager;
        this.achievementManager = achievementManager;
        this.economyManager = economyManager;
        this.recipeManager = recipeManager;
        this.rentalManager = rentalManager;
        this.blackMarketManager = blackMarketManager;
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
            case "craftbook", "kitap" -> handleCraftBook(player, args);
            case "rent", "kiralık" -> handleRent(player, args);
            case "blackmarket", "karaborsa" -> handleBlackMarket(player, args);
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
     * Faz 0.7.0: /pcraft craftbook <recipe-id> — craft a recipe book.
     * Checks materials, level, and require-learned from config.
     */
    private void handleCraftBook(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("recipe-book-crafting.enabled", false)) {
            player.sendMessage(messages.getMessage("craftbook-disabled", true));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(messages.getMessage("craftbook-usage", true));
            return;
        }

        String recipeId = args[1].toLowerCase();

        // Check recipe exists in PhoenixFish
        if (plugin.getFishApi().getCraftingRecipe(recipeId) == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            player.sendMessage(messages.getMessage("craftbook-recipe-not-found", true, ph));
            return;
        }

        // Check book recipe exists in config
        String path = "recipe-book-crafting.recipes." + recipeId;
        if (!plugin.getConfig().contains(path)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            player.sendMessage(messages.getMessage("craftbook-no-recipe-defined", true, ph));
            return;
        }

        // Check crafting level
        int requiredLevel = plugin.getConfig().getInt("recipe-book-crafting.crafting-level-requirement", 0);
        int playerLevel = plugin.getFishApi().getCraftingLevel(player.getUniqueId());
        if (requiredLevel > 0 && playerLevel < requiredLevel) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%required%", String.valueOf(requiredLevel));
            ph.put("%current%", String.valueOf(playerLevel));
            player.sendMessage(messages.getMessage("craftbook-low-level", true, ph));
            return;
        }

        // Check require-learned
        boolean requireLearned = plugin.getConfig().getBoolean(path + ".require-learned", false);
        if (requireLearned && recipeManager != null && recipeManager.isEnabled()
                && !recipeManager.hasLearned(player.getUniqueId(), recipeId)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            player.sendMessage(messages.getMessage("craftbook-require-learned", true, ph));
            return;
        }

        // Check materials
        List<Map<String, Object>> materials = (List<Map<String, Object>>)
                plugin.getConfig().getList(path + ".materials");
        if (materials == null || materials.isEmpty()) {
            // No materials defined — give the book for free
            giveRecipeBook(player, recipeId);
            return;
        }

        // Check each material
        for (Map<String, Object> matEntry : materials) {
            String matName = (String) matEntry.get("material");
            int needed = matEntry.get("amount") instanceof Number
                    ? ((Number) matEntry.get("amount")).intValue() : 1;

            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName.toUpperCase());
            if (mat == null) continue;

            int owned = 0;
            for (org.bukkit.inventory.ItemStack inv : player.getInventory().getContents()) {
                if (inv != null && inv.getType() == mat) {
                    owned += inv.getAmount();
                }
            }

            if (owned < needed) {
                player.sendMessage(messages.getMessage("craftbook-missing-materials", true));
                return;
            }
        }

        // Consume materials
        for (Map<String, Object> matEntry : materials) {
            String matName = (String) matEntry.get("material");
            int needed = matEntry.get("amount") instanceof Number
                    ? ((Number) matEntry.get("amount")).intValue() : 1;

            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName.toUpperCase());
            if (mat == null) continue;

            int remaining = needed;
            org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                org.bukkit.inventory.ItemStack inv = contents[i];
                if (inv == null || inv.getType() != mat) continue;
                int take = Math.min(inv.getAmount(), remaining);
                if (take >= inv.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    inv.setAmount(inv.getAmount() - take);
                    player.getInventory().setItem(i, inv);
                }
                remaining -= take;
            }
        }

        // Give the recipe book
        giveRecipeBook(player, recipeId);
    }

    private void giveRecipeBook(Player player, String recipeId) {
        if (recipeManager == null) return;
        org.bukkit.inventory.ItemStack book = recipeManager.createRecipeBook(recipeId);
        if (book == null) return;

        Map<Integer, org.bukkit.inventory.ItemStack> overflow = player.getInventory().addItem(book);
        for (org.bukkit.inventory.ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%recipe%", recipeId);
        player.sendMessage(messages.getMessage("craftbook-success", true, ph));
        try {
            player.playSound(player, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        } catch (Exception ignored) {
        }
    }

    /**
     * Faz 1.0.0: /pcraft rent — full recipe rental system.
     * Subcommands: offer, accept, list, cancel
     */
    private void handleRent(Player player, String[] args) {
        if (rentalManager == null || !rentalManager.isEnabled()) {
            player.sendMessage(messages.getMessage("rental-disabled", true));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(messages.getMessage("rental-usage", true));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "offer" -> handleRentOffer(player, args);
            case "accept" -> handleRentAccept(player, args);
            case "list" -> handleRentList(player);
            case "cancel" -> handleRentCancel(player, args);
            default -> player.sendMessage(messages.getMessage("rental-usage", true));
        }
    }

    private void handleRentOffer(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(messages.getMessage("rental-offer-usage", true));
            return;
        }

        String recipeId = args[2].toLowerCase();
        double price;
        int minutes;
        try {
            price = Double.parseDouble(args[3]);
            minutes = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            player.sendMessage(messages.getMessage("rental-offer-usage", true));
            return;
        }

        // Check if player has learned the recipe
        if (recipeManager != null && recipeManager.isEnabled()
                && !recipeManager.hasLearned(player.getUniqueId(), recipeId)) {
            player.sendMessage(messages.getMessage("rental-offer-not-learned", true));
            return;
        }

        String error = rentalManager.createOffer(player, recipeId, price, minutes);
        if (error != null) {
            // Send the error message with appropriate placeholders
            sendRentalError(player, error, recipeId, price, minutes);
        } else {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            ph.put("%price%", String.format("%.2f", price));
            ph.put("%minutes%", String.valueOf(minutes));
            player.sendMessage(messages.getMessage("rental-offer-success", true, ph));
        }
    }

    private void handleRentAccept(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(messages.getMessage("rental-accept-usage", true));
            return;
        }

        String ownerName = args[2];
        String recipeId = args[3].toLowerCase();

        Player owner = Bukkit.getPlayerExact(ownerName);
        if (owner == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%player%", ownerName);
            player.sendMessage(messages.getMessage("rental-accept-not-found", true, ph));
            return;
        }

        String error = rentalManager.acceptOffer(player, owner.getUniqueId(), recipeId);
        if (error != null) {
            sendRentalError(player, error, recipeId, 0, 0);
        } else {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            ph.put("%owner%", ownerName);
            // Find the offer to get price and duration
            for (var offer : rentalManager.getAllOffers()) {
                if (offer.ownerUuid.equals(owner.getUniqueId())
                        && offer.recipeId.equalsIgnoreCase(recipeId)) {
                    ph.put("%price%", String.format("%.2f", offer.price));
                    ph.put("%minutes%", String.valueOf(offer.durationMinutes));
                    break;
                }
            }
            player.sendMessage(messages.getMessage("rental-accept-success", true, ph));
        }
    }

    private void handleRentList(Player player) {
        var offers = rentalManager.getAllOffers();
        if (offers.isEmpty()) {
            player.sendMessage(messages.getMessage("rental-list-empty", true));
            return;
        }

        player.sendMessage(messages.getMessage("rental-list-header", false));
        for (var offer : offers) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%player%", offer.ownerName);
            ph.put("%recipe%", offer.recipeId);
            ph.put("%price%", String.format("%.2f", offer.price));
            ph.put("%minutes%", String.valueOf(offer.durationMinutes));
            player.sendMessage(messages.getMessage("rental-list-line", false, ph));
        }
    }

    private void handleRentCancel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(messages.getMessage("rental-cancel-usage", true));
            return;
        }

        String recipeId = args[2].toLowerCase();
        String error = rentalManager.cancelOffer(player, recipeId);
        if (error != null) {
            sendRentalError(player, error, recipeId, 0, 0);
        } else {
            Map<String, String> ph = new HashMap<>();
            ph.put("%recipe%", recipeId);
            player.sendMessage(messages.getMessage("rental-cancel-success", true, ph));
        }
    }

    private void sendRentalError(Player player, String errorKey,
                                   String recipe, double price, int minutes) {
        Map<String, String> ph = new HashMap<>();
        ph.put("%recipe%", recipe);
        ph.put("%price%", String.format("%.2f", price));
        ph.put("%minutes%", String.valueOf(minutes));
        ph.put("%player%", player.getName());
        ph.put("%balance%", String.format("%.2f", plugin.getFishApi().getBalance(player.getUniqueId())));

        // Set %min% and %max% based on error type
        if (errorKey.contains("duration")) {
            ph.put("%min%", String.valueOf(plugin.getConfig().getInt("recipe-rental.min-duration-minutes", 5)));
            ph.put("%max%", String.valueOf(plugin.getConfig().getInt("recipe-rental.max-duration-minutes", 60)));
        } else if (errorKey.contains("price")) {
            ph.put("%min%", String.format("%.2f", plugin.getConfig().getDouble("recipe-rental.min-price", 10)));
            double maxP = plugin.getConfig().getDouble("recipe-rental.max-price", 0);
            ph.put("%max%", maxP > 0 ? String.format("%.2f", maxP) : "∞");
        } else if (errorKey.contains("too-many")) {
            ph.put("%max%", String.valueOf(plugin.getConfig().getInt("recipe-rental.max-offers-per-player", 10)));
        } else if (errorKey.contains("accept-too-many")) {
            ph.put("%max%", String.valueOf(plugin.getConfig().getInt("recipe-rental.max-active-rentals-per-player", 5)));
        }

        player.sendMessage(messages.getMessage(errorKey, true, ph));
    }

    /**
     * Faz 1.0.0: /pcraft blackmarket — open the black market GUI.
     */
    private void handleBlackMarket(Player player, String[] args) {
        if (blackMarketManager == null || !blackMarketManager.isEnabled()) {
            player.sendMessage(messages.getMessage("blackmarket-refresh-disabled", true));
            return;
        }

        // Admin subcommand: refresh
        if (args.length >= 2 && "refresh".equalsIgnoreCase(args[1])) {
            if (!player.hasPermission("phoenixcraft.admin")) {
                player.sendMessage(messages.getMessage("blackmarket-no-permission", true));
                return;
            }
            blackMarketManager.refreshStock(true);
            Map<String, String> ph = new HashMap<>();
            ph.put("%count%", String.valueOf(blackMarketManager.getStock().size()));
            player.sendMessage(messages.getMessage("blackmarket-refreshed", true, ph));
            return;
        }

        // Open the GUI
        gui.openBlackMarketMenu(player);
    }

    /**
     * Faz 1.0.0: /pcraft sell <all|hand> — sell crafted items.
     * Scans inventory for items matching recipe results and sells them.
     */
    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.getMessage("sell-usage", true));
            return;
        }

        var fishApi = plugin.getFishApi();
        var allRecipes = fishApi.getCraftingRecipes();

        switch (args[1].toLowerCase()) {
            case "all" -> {
                double totalPrice = 0;
                int totalSold = 0;

                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item == null || item.getType().isAir()) continue;

                    // Check if this item matches any recipe result
                    for (var recipe : allRecipes) {
                        var result = recipe.resultItem();
                        if (item.getType() == result.getType()
                                && getCmd(item) == getCmd(result)) {
                            // Calculate price: use a base price formula
                            // price = recipe.xpReward * 2 * item.getAmount()
                            double itemPrice = recipe.xpReward() * 2.0 * item.getAmount();
                            totalPrice += itemPrice;
                            totalSold += item.getAmount();
                            player.getInventory().setItem(i, null);
                            break;
                        }
                    }
                }

                if (totalSold == 0) {
                    player.sendMessage(messages.getMessage("sell-no-items", true));
                    return;
                }

                // Deposit money
                fishApi.depositPlayer(player.getUniqueId(), totalPrice);

                Map<String, String> ph = new HashMap<>();
                ph.put("%count%", String.valueOf(totalSold));
                ph.put("%price%", String.format("%.2f", totalPrice));
                player.sendMessage(messages.getMessage("sell-success", true, ph));
            }
            case "hand" -> {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    player.sendMessage(messages.getMessage("sell-no-items", true));
                    return;
                }

                // Find matching recipe
                for (var recipe : allRecipes) {
                    var result = recipe.resultItem();
                    if (item.getType() == result.getType()
                            && getCmd(item) == getCmd(result)) {
                        double price = recipe.xpReward() * 2.0 * item.getAmount();
                        int amount = item.getAmount();
                        player.getInventory().setItemInMainHand(null);
                        fishApi.depositPlayer(player.getUniqueId(), price);

                        Map<String, String> ph = new HashMap<>();
                        ph.put("%count%", String.valueOf(amount));
                        ph.put("%price%", String.format("%.2f", price));
                        player.sendMessage(messages.getMessage("sell-success", true, ph));
                        return;
                    }
                }

                player.sendMessage(messages.getMessage("sell-no-items", true));
            }
            default -> player.sendMessage(messages.getMessage("sell-usage", true));
        }
    }

    @SuppressWarnings("deprecation")
    private int getCmd(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        var meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return 0;
        try {
            Integer cmd = meta.getCustomModelData();
            return cmd != null ? cmd : 0;
        } catch (Exception e) {
            return 0;
        }
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
        if (plugin.getConfig().getBoolean("recipe-book-crafting.enabled", false)) {
            player.sendMessage(messages.getMessage("command-help-craftbook", false));
        }
        if (plugin.getConfig().getBoolean("recipe-rental.enabled", false)) {
            player.sendMessage(messages.getMessage("command-help-rent", false));
        }
        if (blackMarketManager != null && blackMarketManager.isEnabled()) {
            player.sendMessage(messages.getMessage("command-help-blackmarket", false));
        }
        player.sendMessage(messages.getMessage("command-help-sell", false));
        if (player.hasPermission("phoenixcraft.admin")) {
            player.sendMessage(messages.getMessage("command-help-giverecipe", false));
            player.sendMessage(messages.getMessage("command-help-reload", false));
        }
    }
}
