package com.phoenix.craft.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker InventoryHolder for all PhoenixCraft GUIs.
 *
 * <p>Tek bir holder tüm crafting ekranları için kullanılır; ekran türü
 * butonlardaki PDC action/value ile ayırt edilir.</p>
 */
public final class CraftingGUIHolder implements InventoryHolder {

    public enum ScreenType {
        MAIN,
        CATEGORY,
        DETAIL
    }

    private final ScreenType screenType;
    private final String category;
    private final int page;
    private final String recipeId;

    public CraftingGUIHolder(ScreenType screenType) {
        this(screenType, null, 0, null);
    }

    public CraftingGUIHolder(ScreenType screenType, String category, int page) {
        this(screenType, category, page, null);
    }

    public CraftingGUIHolder(ScreenType screenType, String category, int page, String recipeId) {
        this.screenType = screenType;
        this.category = category;
        this.page = page;
        this.recipeId = recipeId;
    }

    public ScreenType getScreenType() {
        return screenType;
    }

    public String getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public String getRecipeId() {
        return recipeId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
