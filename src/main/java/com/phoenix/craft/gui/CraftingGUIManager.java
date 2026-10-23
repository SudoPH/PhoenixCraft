package com.phoenix.craft.gui;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.CraftResult;
import com.phoenix.fish.api.PhoenixFishAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Crafting GUI manager for PhoenixCraft.
 *
 * <p>Üç ekran tipi:
 * <ul>
 *   <li><b>MAIN</b> — 27 slot, kategori seçimi (Oltalar / Yemler / Kapat)</li>
 *   <li><b>CATEGORY</b> — 54 slot, tarif ikonları + sayfalama</li>
 *   <li><b>DETAIL</b> — 27 slot, tek tarifin detayı + Üret butonu</li>
 * </ul>
 *
 * <p>Tüm butonlar PDC key'leri ile işaretlenir:
 * <ul>
 *   <li>{@code craft_action} — "main" | "category" | "page" | "recipe" | "craft" | "close"</li>
 *   <li>{@code craft_value} — ek değer (kategori adı, tarif id, sayfa numarası)</li>
 * </ul>
 */
public class CraftingGUIManager {

    private static final int ITEMS_PER_PAGE = 45;

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;
    private final MiniMessage miniMessage;

    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    public CraftingGUIManager(PhoenixCraft plugin, PhoenixFishAPI fishApi) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        this.miniMessage = MiniMessage.miniMessage();
        this.actionKey = new NamespacedKey(plugin, "craft_action");
        this.valueKey = new NamespacedKey(plugin, "craft_value");
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getValueKey() {
        return valueKey;
    }

    // =====================================================================
    //  MAIN MENU
    // =====================================================================

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.MAIN),
                27,
                miniMessage.deserialize("<dark_gray>» <gradient:#FFA500:#FF6347>PhoenixCraft</gradient>"));

        // Fill borders with light gray stained glass panes for visual structure
        fillBorders(inv, 27, Material.GRAY_STAINED_GLASS_PANE);

        inv.setItem(11, createButton(Material.FISHING_ROD,
                "<yellow>OLTALAR",
                List.of("<gray>Özel balık tutma oltalarını üret.",
                        "<dark_gray>Tıkla ve listeyi gör."),
                "category", "rod"));

        inv.setItem(13, createButton(Material.CRAFTING_TABLE,
                "<gold>ÜRETİM BİLGİSİ",
                List.of("<gray>Crafting XP: <yellow>" + fishApi.getCraftingXp(player.getUniqueId())
                                + "<dark_gray>/<white>" + fishApi.getCraftingXpRequired(player.getUniqueId()),
                        "<gray>Crafting Level: <yellow>" + fishApi.getCraftingLevel(player.getUniqueId()),
                        "",
                        "<dark_gray>Tarifler PhoenixFish'in custom_recipes.yml'sinden okunur."),
                "none", "none"));

        inv.setItem(15, createButton(Material.STICK,
                "<gold>YEMLER",
                List.of("<gray>Balıkları cezbetmek için özel yemler üret.",
                        "<dark_gray>Tıkla ve listeyi gör."),
                "category", "bait"));

        inv.setItem(22, createButton(Material.BARRIER,
                "<red>KAPAT",
                List.of(),
                "close", "none"));

        player.openInventory(inv);
    }

    // =====================================================================
    //  CATEGORY MENU (paginated)
    // =====================================================================

    public void openCategoryMenu(Player player, String category, int page) {
        List<CraftRecipe> recipes = fishApi.getCraftingRecipesByCategory(category);
        if (recipes.isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<red>Bu kategoride tarif yok.</red>"));
            openMainMenu(player);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) recipes.size() / ITEMS_PER_PAGE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        String titleText;
        if ("rod".equals(category)) {
            titleText = "<dark_gray>» <yellow>OLTALAR <dark_gray>(Sayfa " + (page + 1) + "/" + totalPages + ")";
        } else if ("bait".equals(category)) {
            titleText = "<dark_gray>» <gold>YEMLER <dark_gray>(Sayfa " + (page + 1) + "/" + totalPages + ")";
        } else {
            titleText = "<dark_gray>» <white>" + category.toUpperCase() + " <dark_gray>(Sayfa " + (page + 1) + "/" + totalPages + ")";
        }

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.CATEGORY, category, page),
                54,
                miniMessage.deserialize(titleText));

        // Top & bottom rows decorative
        fillBorders(inv, 54, Material.BLACK_STAINED_GLASS_PANE);

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, recipes.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            CraftRecipe recipe = recipes.get(i);
            ItemStack icon = buildRecipeIcon(player, recipe);
            // Skip slots 0,8,45,53 (border) — use 1-7, 9-44, 46-52
            // Simpler: use 0-44 directly, our fillBorders only fills edges.
            // Actually we already filled edges, so we want to skip those.
            // For simplicity, items occupy slots 0..44 minus 0,8 if they're on row edges.
            // Let me just put items at slots 0..44 sequentially (overwriting glass panes).
            inv.setItem(slot, icon);
            slot++;
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, createButton(Material.ARROW,
                    "<yellow>« Önceki Sayfa",
                    List.of(),
                    "page", category + ":" + (page - 1)));
        }
        inv.setItem(49, createButton(Material.BARRIER,
                "<red>ANA MENÜ",
                List.of(),
                "main", "none"));
        if (page < totalPages - 1) {
            inv.setItem(53, createButton(Material.ARROW,
                    "<yellow>Sonraki Sayfa »",
                    List.of(),
                    "page", category + ":" + (page + 1)));
        }

        player.openInventory(inv);
    }

    /**
     * Build the recipe's result item as the icon, with lore listing required materials.
     */
    private ItemStack buildRecipeIcon(Player player, CraftRecipe recipe) {
        ItemStack icon = recipe.resultItem().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(miniMessage.deserialize("<dark_gray>» <gray>Gerekli Malzemeler:"));

        boolean hasAllMaterials = true;
        for (CraftRecipe.Ingredient ing : recipe.ingredients()) {
            int owned = countInInventory(player, ing.item());
            int needed = ing.amount();
            boolean ok = owned >= needed;
            if (!ok) hasAllMaterials = false;

            String ingName = describeItem(ing.item());
            String color = ok ? "<green>" : "<red>";
            lore.add(miniMessage.deserialize(
                    color + "  • " + ingName + " <dark_gray>(" + owned + "/" + needed + ")"));
        }

        lore.add(Component.empty());
        int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
        boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
        String levelColor = levelOk ? "<green>" : "<red>";
        lore.add(miniMessage.deserialize(
                levelColor + "Gerekli Level: <white>" + recipe.craftingLevelRequirement()
                        + " <dark_gray>(Senin: " + playerLevel + ")"));
        lore.add(miniMessage.deserialize("<aqua>XP Ödülü: <white>+" + recipe.xpReward()));

        lore.add(Component.empty());
        if (levelOk && hasAllMaterials) {
            lore.add(miniMessage.deserialize("<green><bold>✓ Üretilebilir! Tıkla.</bold>"));
        } else if (!levelOk) {
            lore.add(miniMessage.deserialize("<red><bold>✖ Yetersiz level</bold>"));
        } else {
            lore.add(miniMessage.deserialize("<red><bold>✖ Eksik malzeme</bold>"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "recipe");
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, recipe.id());
        icon.setItemMeta(meta);
        return icon;
    }

    // =====================================================================
    //  RECIPE DETAIL
    // =====================================================================

    public void openRecipeDetail(Player player, String recipeId) {
        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(miniMessage.deserialize("<red>Tarif bulunamadı: " + recipeId));
            return;
        }

        Inventory inv = Bukkit.createInventory(
                new CraftingGUIHolder(CraftingGUIHolder.ScreenType.DETAIL,
                        recipe.category(), 0, recipe.id()),
                27,
                miniMessage.deserialize("<dark_gray>» <gold>Tarif Detayı"));

        fillBorders(inv, 27, Material.GRAY_STAINED_GLASS_PANE);

        // Result preview at slot 13
        ItemStack preview = recipe.resultItem().clone();
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            List<Component> lore = new ArrayList<>(previewMeta.lore() != null ? previewMeta.lore() : List.of());
            lore.add(Component.empty());
            lore.add(miniMessage.deserialize("<dark_gray>» <gray>Bu eşyayı üreteceksin"));
            previewMeta.lore(lore);
            preview.setItemMeta(previewMeta);
        }
        inv.setItem(13, preview);

        // Ingredients list at slot 11
        ItemStack ingredientsItem = new ItemStack(Material.BOOK);
        ItemMeta ingMeta = ingredientsItem.getItemMeta();
        if (ingMeta != null) {
            ingMeta.displayName(miniMessage.deserialize("<yellow>Gerekli Malzemeler"));
            List<Component> ingLore = new ArrayList<>();
            for (CraftRecipe.Ingredient ing : recipe.ingredients()) {
                int owned = countInInventory(player, ing.item());
                String color = owned >= ing.amount() ? "<green>" : "<red>";
                ingLore.add(miniMessage.deserialize(
                        color + "• " + describeItem(ing.item())
                                + " <dark_gray>×" + ing.amount()
                                + " <dark_gray>(Envanter: " + owned + ")"));
            }
            ingLore.add(Component.empty());
            int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
            boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
            String lc = levelOk ? "<green>" : "<red>";
            ingLore.add(miniMessage.deserialize(lc + "Gerekli Level: <white>" + recipe.craftingLevelRequirement()
                    + " <dark_gray>(Senin: " + playerLevel + ")"));
            ingLore.add(miniMessage.deserialize("<aqua>XP Ödülü: <white>+" + recipe.xpReward()));
            ingMeta.lore(ingLore);
            ingredientsItem.setItemMeta(ingMeta);
        }
        inv.setItem(11, ingredientsItem);

        // Craft button at slot 15
        boolean canCraft = fishApi.canCraft(player, recipe);
        ItemStack craftBtn;
        if (canCraft) {
            craftBtn = createButton(Material.LIME_CONCRETE,
                    "<green><bold>✔ ÜRET",
                    List.of("<gray>Tıkla ve eşyayı üret.",
                            "<dark_gray>Malzemeler envanterinden düşülecek."),
                    "craft", recipe.id());
        } else {
            // Determine why
            int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
            boolean levelOk = playerLevel >= recipe.craftingLevelRequirement();
            String reason = levelOk ? "<red>Eksik malzeme!" : "<red>Yetersiz level!";
            craftBtn = createButton(Material.RED_CONCRETE,
                    "<red><bold>✖ ÜRETEMEZSİN",
                    List.of(reason, "<dark_gray>Şartları sağladığında aktifleşir."),
                    "craft_disabled", recipe.id());
        }
        inv.setItem(15, craftBtn);

        // Back button at slot 22
        inv.setItem(22, createButton(Material.ARROW,
                "<yellow>« Geri Dön",
                List.of("<dark_gray>Kategori menüsüne dön."),
                "back_to_category", recipe.category()));

        player.openInventory(inv);
    }

    // =====================================================================
    //  CRAFT ACTION
    // =====================================================================

    public void handleCraftClick(Player player, String recipeId) {
        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(miniMessage.deserialize("<red>Tarif bulunamadı."));
            return;
        }

        CraftResult result = fishApi.craft(player, recipe);
        switch (result) {
            case SUCCESS -> {
                String itemName = describeItem(recipe.resultItem());
                player.sendMessage(miniMessage.deserialize(
                        "<green>✔ Üretildi: <white>" + itemName
                                + "</white> <dark_gray>(+" + recipe.xpReward() + " Zanaat XP)"));
                try {
                    player.playSound(player, Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
                } catch (Exception ignored) {
                }
                // Re-open detail to refresh material counts
                openRecipeDetail(player, recipeId);
            }
            case LOW_LEVEL -> {
                int playerLevel = fishApi.getCraftingLevel(player.getUniqueId());
                player.sendMessage(miniMessage.deserialize(
                        "<red>✖ Yetersiz level! Gerekli: <yellow>" + recipe.craftingLevelRequirement()
                                + "</yellow> | Senin: " + playerLevel));
                try {
                    player.playSound(player, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                } catch (Exception ignored) {
                }
            }
            case MISSING_MATERIALS -> {
                player.sendMessage(miniMessage.deserialize(
                        "<red>✖ Gerekli malzemelerin tamamına sahip değilsin!"));
                try {
                    player.playSound(player, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                } catch (Exception ignored) {
                }
            }
            case NO_DATA -> player.sendMessage(miniMessage.deserialize(
                    "<red>✖ Üretim sistemi şu an kullanılamıyor (XP sistemi kapalı)."));
            case UNKNOWN_RECIPE -> player.sendMessage(miniMessage.deserialize(
                    "<red>✖ Bilinmeyen tarif."));
            case INTERNAL_ERROR -> player.sendMessage(miniMessage.deserialize(
                    "<red>✖ Üretim sırasında iç hata. Lütfen yetkiliye bildir."));
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private ItemStack createButton(Material mat, String name, List<String> loreLines,
                                    String action, String value) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(miniMessage.deserialize(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(miniMessage.deserialize(line));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory inv, int size, Material paneMat) {
        ItemStack pane = new ItemStack(paneMat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, pane);
            }
        }
    }

    /**
     * Count items in player's inventory that match the template
     * (same material + same custom-model-data).
     */
    private int countInInventory(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack inv : player.getInventory().getContents()) {
            if (inv == null || inv.getType().isAir()) continue;
            if (matchesTemplate(inv, template)) {
                total += inv.getAmount();
            }
        }
        return total;
    }

    @SuppressWarnings("deprecation")
    private boolean matchesTemplate(ItemStack inv, ItemStack template) {
        if (inv.getType() != template.getType()) return false;
        int invCmd = getCmd(inv);
        int tplCmd = getCmd(template);
        return invCmd == tplCmd;
    }

    @SuppressWarnings("deprecation")
    private int getCmd(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta m = item.getItemMeta();
        if (m == null || !m.hasCustomModelData()) return 0;
        try {
            Integer cmd = m.getCustomModelData();
            return cmd != null ? cmd : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Human-readable name for an item — uses display name if set, else material name.
     */
    private String describeItem(ItemStack item) {
        if (item == null) return "???";
        if (item.hasItemMeta()) {
            ItemMeta m = item.getItemMeta();
            if (m != null && m.hasDisplayName()) {
                return plainText(m.displayName());
            }
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private String plainText(Component component) {
        if (component == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }
}
