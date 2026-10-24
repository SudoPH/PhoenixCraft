package com.phoenix.craft.recipes;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.fish.api.CraftRecipe;
import com.phoenix.fish.api.PhoenixFishAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player learned recipes and recipe book items.
 *
 * <p>Storage: {@code plugins/PhoenixCraft/learned_recipes.yml} — single YAML
 * file holding every player's learned recipe IDs. Loaded fully into memory
 * on enable, written back to disk after each change.</p>
 *
 * <p>Modularity: this entire system can be disabled in {@code config.yml} via
 * {@code recipe-sharing.enabled: false}. When disabled, all recipes are
 * considered "learned" (legacy behavior — everyone can craft everything).</p>
 *
 * <p>Recipe books are physical items that, when right-clicked, teach the
 * player a specific recipe. The book's recipe ID is stored in PDC.</p>
 *
 * @since 0.5.0
 */
public class RecipeManager {

    /** PDC key for storing recipe ID on book items. */
    public static final String BOOK_RECIPE_KEY = "recipe_book_id";

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;
    private final MessageManager messages;
    private final boolean enabled;
    private final boolean requireLearning;
    private final NamespacedKey bookKey;

    /** Per-player learned recipe IDs. */
    private final Map<UUID, Set<String>> learned = new ConcurrentHashMap<>();

    private volatile boolean dirty = false;

    public RecipeManager(PhoenixCraft plugin, PhoenixFishAPI fishApi,
                          MessageManager messages) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        this.messages = messages;
        this.enabled = plugin.getConfig().getBoolean("recipe-sharing.enabled", false);
        this.requireLearning = plugin.getConfig().getBoolean("recipe-sharing.require-learning", false);
        this.bookKey = new NamespacedKey(plugin, BOOK_RECIPE_KEY);

        if (enabled) {
            load();
            plugin.getLogger().info("RecipeManager enabled — "
                    + learned.size() + " players tracked, requireLearning=" + requireLearning);
        } else {
            plugin.getLogger().info("RecipeManager disabled (recipe-sharing.enabled=false).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequireLearning() {
        return requireLearning;
    }

    public NamespacedKey getBookKey() {
        return bookKey;
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    private File getLearnedFile() {
        return new File(plugin.getDataFolder(), "learned_recipes.yml");
    }

    private void load() {
        File file = getLearnedFile();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> ids = players.getStringList(uuidStr);
                learned.put(uuid, new HashSet<>(ids));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        if (!enabled) return;
        if (!dirty) return;

        File file = getLearnedFile();
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : learned.entrySet()) {
            cfg.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save learned_recipes.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        save();
    }

    // =====================================================================
    //  Queries
    // =====================================================================

    /**
     * Check if a player has learned a specific recipe.
     * Always returns true if the recipe-sharing system is disabled.
     */
    public boolean hasLearned(UUID uuid, String recipeId) {
        if (!enabled) return true;
        Set<String> set = learned.get(uuid);
        return set != null && set.contains(recipeId.toLowerCase());
    }

    /**
     * Get the set of learned recipe IDs for a player.
     */
    public Set<String> getLearned(UUID uuid) {
        if (!enabled) return Collections.emptySet();
        return Collections.unmodifiableSet(learned.getOrDefault(uuid, Collections.emptySet()));
    }

    public int getLearnedCount(UUID uuid) {
        if (!enabled) return 0;
        Set<String> set = learned.get(uuid);
        return set != null ? set.size() : 0;
    }

    /**
     * Check if a player can craft a recipe (considering learning).
     * Returns true if recipe-sharing is disabled, or if the player has learned it,
     * or if require-learning is false (cosmetic only).
     */
    public boolean canCraftRecipe(UUID uuid, CraftRecipe recipe) {
        if (!enabled) return true;
        if (!requireLearning) return true;
        return hasLearned(uuid, recipe.id());
    }

    // =====================================================================
    //  Learning
    // =====================================================================

    /**
     * Teach a recipe to a player. Returns false if already learned.
     */
    public boolean learnRecipe(UUID uuid, String recipeId) {
        if (!enabled) return false;
        recipeId = recipeId.toLowerCase();

        Set<String> set = learned.computeIfAbsent(uuid, k -> new HashSet<>());
        if (set.contains(recipeId)) {
            return false;
        }
        set.add(recipeId);
        dirty = true;
        save();
        return true;
    }

    /**
     * Teach a recipe to a player and notify them.
     */
    public void learnRecipeAndNotify(Player player, String recipeId) {
        if (!enabled) {
            player.sendMessage(messages.getMessage("recipe-sharing-disabled", true));
            return;
        }

        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            Map<String, String> ph = Map.of("%error%", "recipe not found");
            player.sendMessage(messages.getMessage("recipe-learn-failed", true, ph));
            return;
        }

        boolean learnedNow = learnRecipe(player.getUniqueId(), recipeId);
        if (learnedNow) {
            Map<String, String> ph = Map.of("%recipe%", recipeId);
            player.sendMessage(messages.getMessage("recipe-learned-success", true, ph));
            try {
                player.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
            } catch (Exception ignored) {
            }
        } else {
            player.sendMessage(messages.getMessage("recipe-already-learned", true));
        }
    }

    // =====================================================================
    //  Recipe Book Items
    // =====================================================================

    /**
     * Create a recipe book item for the given recipe.
     */
    public ItemStack createRecipeBook(String recipeId) {
        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) return null;

        String matName = plugin.getConfig().getString("recipe-sharing.book.material", "WRITTEN_BOOK");
        Material mat = Material.matchMaterial(matName.toUpperCase());
        if (mat == null) mat = Material.WRITTEN_BOOK;

        ItemStack book = new ItemStack(mat);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;

        // Display name with recipe name
        String nameFormat = plugin.getConfig().getString("recipe-sharing.book.name-format",
                "<gold>Recipe Book: <white>%recipe%");
        String recipeName = describeItem(recipe.resultItem());
        Map<String, String> namePh = Map.of("%recipe%", recipeName);
        String nameStr = replacePlaceholders(nameFormat, namePh);
        meta.displayName(MiniMessage.miniMessage().deserialize(nameStr));

        // Lore
        List<String> loreFormat = plugin.getConfig().getStringList("recipe-sharing.book.lore");
        if (loreFormat.isEmpty()) {
            loreFormat = List.of("<gray>Right-click to learn this recipe.",
                    "<dark_gray>Recipe: <white>%recipe_id%");
        }
        Map<String, String> lorePh = Map.of(
                "%recipe_id%", recipeId,
                "%recipe%", recipeName);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : loreFormat) {
            lore.add(MiniMessage.miniMessage().deserialize(replacePlaceholders(line, lorePh)));
        }
        meta.lore(lore);

        // Custom model data
        int cmd = plugin.getConfig().getInt("recipe-sharing.book.custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        // PDC key — store recipe ID
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, recipeId.toLowerCase());

        book.setItemMeta(meta);
        return book;
    }

    /**
     * Read the recipe ID from a recipe book item.
     * Returns null if the item is not a recipe book.
     */
    public String getRecipeIdFromBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
    }

    /**
     * Handle right-click of a recipe book item. Teaches the recipe if valid.
     */
    public void handleBookUse(Player player, ItemStack book) {
        if (!enabled) {
            player.sendMessage(messages.getMessage("recipe-sharing-disabled", true));
            return;
        }

        String recipeId = getRecipeIdFromBook(book);
        if (recipeId == null) {
            player.sendMessage(messages.getMessage("recipe-book-invalid", true));
            return;
        }

        CraftRecipe recipe = fishApi.getCraftingRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(messages.getMessage("recipe-book-invalid", true));
            return;
        }

        learnRecipeAndNotify(player, recipeId);

        // Consume the book item
        book.setAmount(book.getAmount() - 1);
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private String describeItem(ItemStack item) {
        if (item == null) return "???";
        if (item.hasItemMeta()) {
            ItemMeta m = item.getItemMeta();
            if (m != null && m.hasDisplayName()) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(m.displayName());
            }
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        return message;
    }
}
