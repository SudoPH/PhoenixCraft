package com.phoenix.craft.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * DesktopLayouts — PhoenixCraft dashboard layout engine (v1.1.5).
 *
 * <p>Single source of truth for every 54-slot menu canvas. All screen real
 * estate is split into four zones, and no menu may ever paint outside its
 * zone contract:</p>
 *
 * <pre>
 *  ┌──────────────────────────────────────────────────────────────────┐
 *  │ Row 1 |  0– 8 │ FRAME — outer ring, gray panes, never content    │
 *  │ Row 2 |  9–17 │ FRAME │ CONTENT 10–16                      │FRAME│
 *  │ Row 3 | 18–26 │ FRAME │ CONTENT 19–25                      │FRAME│
 *  │ Row 4 | 27–35 │ FRAME │ CONTENT 28–34                      │FRAME│
 *  │ Row 5 | 36–44 │ FRAME │ CONTENT 37–43                      │FRAME│
 *  │ Row 6 | 45–53 │ ACTION BAR — 46 prev · 49 back · 52 next    │FRAME│
 *  └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Zones</b></p>
 * <ul>
 *   <li><b>FRAME</b> — the outer ring (rows 1 and 6, columns 1 and 9).
 *       Painted solid by {@link #fillMenuFrame}. Content is forbidden here;
 *       this is what makes stray "floating" border items structurally
 *       impossible.</li>
 *   <li><b>CONTENT</b> — the dense list grid, every non-frame slot between
 *       rows 2 and 5 (slots 10–43, 7 columns × 4 rows = {@value #PAGE_SIZE}
 *       slots per page). Used by all drill-down list menus (rods, baits,
 *       recipes, achievements, black market, search results).</li>
 *   <li><b>CORE</b> — the sparse hub grid (rows 3–4, columns 3–7). Used by
 *       the main menu dashboard only. Features are placed symmetrically via
 *       {@link #spacedSlots(int, int[])}.</li>
 *   <li><b>ACTION BAR</b> — row 6. Anchored at slots
 *       {@value #SLOT_PREVIOUS_PAGE} / {@value #SLOT_BACK} /
 *       {@value #SLOT_NEXT_PAGE}; utility buttons (search / filter / sort)
 *       sit between the anchors. Slots without a button keep the frame pane.</li>
 * </ul>
 *
 * <p><b>Pagination contract</b> — one page holds at most
 * {@value #PAGE_SIZE} items. Anything beyond that is split across pages by
 * {@link #pageCount}, {@link #clampPage} and {@link #slicePage}. Page
 * indices are 0-based everywhere internally; 1-based only in user-facing
 * titles and lore.</p>
 */
public final class DesktopLayouts {

    /** Standard menu canvas size (6 rows × 9 columns). */
    public static final int INVENTORY_SIZE = 54;

    /**
     * Dense content grid — rows 2–5, columns 2–8 (slots 10–43).
     * This is the maximum number of list items one page can hold.
     */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Maximum items per page — derived from the content grid, never hardcoded elsewhere. */
    public static final int PAGE_SIZE = CONTENT_SLOTS.length;

    /** Sparse hub grid — rows 3–4, columns 3–7 (main menu dashboard core). */
    public static final int[] CORE_ROW_A = {20, 21, 22, 23, 24};
    public static final int[] CORE_ROW_B = {29, 30, 31, 32, 33};

    // ── Action bar anchors (row 6) ──────────────────────────────────
    /** Previous-page arrow. Only rendered when a previous page exists. */
    public static final int SLOT_PREVIOUS_PAGE = 46;
    /** Search button (list menus). */
    public static final int SLOT_SEARCH = 47;
    /** Filter button (category menus). */
    public static final int SLOT_FILTER = 48;
    /** Close / back button — always BARRIER, always present. */
    public static final int SLOT_BACK = 49;
    /** Sort button (category menus). */
    public static final int SLOT_SORT = 50;
    /** Next-page arrow. Only rendered when a next page exists. */
    public static final int SLOT_NEXT_PAGE = 52;

    // ── Main menu auxiliary anchors ─────────────────────────────────
    /** Left auxiliary slot on the hub action bar (row 6). */
    public static final int SLOT_AUX_LEFT = 47;
    /** Center hub action bar slot — reserved for the close BARRIER. */
    public static final int SLOT_CLOSE = 49;
    /** Right auxiliary slot on the hub action bar (row 6). */
    public static final int SLOT_AUX_RIGHT = 51;

    /** Nameless filler panes, cached per material. */
    private static final Map<Material, ItemStack> PANE_CACHE = new EnumMap<>(Material.class);

    private DesktopLayouts() {
    }

    // =====================================================================
    //  Frame painting
    // =====================================================================

    /**
     * True if the slot belongs to the outer frame ring
     * (rows 1 and 6, or columns 1 and 9).
     */
    public static boolean isFrameSlot(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return true; // out of canvas — treat as frame so callers skip it
        }
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == 5 || col == 0 || col == 8;
    }

    /**
     * True if the slot belongs to the dense content grid (rows 2–5,
     * columns 2–8). Complement of {@link #isFrameSlot} within rows 2–5.
     */
    public static boolean isContentSlot(int slot) {
        return !isFrameSlot(slot);
    }

    /**
     * Paint the full 54-slot canvas: solid outer frame ring plus solid
     * interior background.
     *
     * <p>Every single slot is written explicitly, which is what guarantees
     * the frame can never contain stray or floating items — anything that
     * was there before is overwritten, and content methods
     * ({@link #placeContent}) only ever paint inside the grid.</p>
     *
     * @param inv        the inventory to paint (54 slots)
     * @param frame      material for the outer ring (e.g. gray stained glass)
     * @param background material for the interior (e.g. black stained glass)
     */
    public static void fillMenuFrame(Inventory inv, Material frame, Material background) {
        ItemStack framePane = pane(frame);
        ItemStack backgroundPane = pane(background);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inv.setItem(slot, isFrameSlot(slot) ? framePane : backgroundPane);
        }
    }

    /** Get a cached nameless pane of the given material. */
    private static ItemStack pane(Material material) {
        return PANE_CACHE.computeIfAbsent(material, m -> {
            ItemStack pane = new ItemStack(m);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.empty());
                pane.setItemMeta(meta);
            }
            return pane;
        });
    }

    // =====================================================================
    //  Content placement
    // =====================================================================

    /**
     * Place page items into the dense content grid, in order.
     * Never writes outside {@link #CONTENT_SLOTS} — anything beyond the
     * grid capacity is silently dropped (callers must paginate, not stuff).
     *
     * @return the number of items actually placed
     */
    public static int placeContent(Inventory inv, List<ItemStack> icons) {
        int placed = 0;
        for (int i = 0; i < icons.size() && i < PAGE_SIZE; i++) {
            ItemStack icon = icons.get(i);
            if (icon == null) {
                continue;
            }
            inv.setItem(CONTENT_SLOTS[i], icon);
            placed++;
        }
        return placed;
    }

    /**
     * Symmetric slot selection on a hub row (the v1.1.4 dashboard spacing
     * contract). Items keep one empty slot between neighbours and stay
     * perfectly centered:
     *
     * <pre>
     *   1 item  →  center              (22)
     *   2 items →  center ± 1          (21, 23)
     *   3 items →  center ± 2          (20, 22, 24)
     *   4+      →  even distribution across the whole row
     * </pre>
     *
     * @param count    how many features will be placed
     * @param rowSlots the candidate slots of one hub row (odd length,
     *                 ordered low → high)
     * @return the exact slots to use, low → high
     */
    public static int[] spacedSlots(int count, int[] rowSlots) {
        if (count <= 0 || rowSlots == null || rowSlots.length == 0) {
            return new int[0];
        }
        if (count >= rowSlots.length) {
            return rowSlots.clone();
        }

        int len = rowSlots.length;
        int center = len / 2; // row lengths are odd → exact middle index

        // stride-2 centered pattern: one empty slot between neighbours
        int spread = count - 1;               // offset of the outermost item
        if (len % 2 == 1 && center - spread >= 0 && center + spread < len) {
            int[] out = new int[count];
            for (int i = 0; i < count; i++) {
                out[i] = rowSlots[center - spread + 2 * i];
            }
            return out;
        }

        // fallback: even distribution (also covers even-length rows)
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int index = (int) Math.round(i * (len - 1) / (double) (count - 1));
            out[i] = rowSlots[index];
        }
        return out;
    }

    /**
     * Place features symmetrically on a hub row using
     * {@link #spacedSlots(int, int[])}.
     */
    public static void placeSpaced(Inventory inv, List<ItemStack> items, int[] rowSlots) {
        int[] slots = spacedSlots(items.size(), rowSlots);
        for (int i = 0; i < slots.length && i < items.size(); i++) {
            inv.setItem(slots[i], items.get(i));
        }
    }

    // =====================================================================
    //  Pagination math
    // =====================================================================

    /**
     * Total number of pages needed for {@code total} items at
     * {@code perPage} items per page. Never returns 0 — an empty list
     * still counts as exactly one (empty) page.
     */
    public static int pageCount(int total, int perPage) {
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(total / (double) perPage));
    }

    /**
     * Clamp a requested 0-based page index into the valid range
     * {@code [0, totalPages - 1]}. Out-of-range requests (stale page
     * numbers after a list shrank, negative values, overflow) always
     * resolve to a safe page instead of throwing or rendering empty.
     */
    public static int clampPage(int page, int totalPages) {
        int last = Math.max(0, totalPages - 1);
        return Math.min(Math.max(0, page), last);
    }

    /**
     * Robust page slicing — the exact items that belong on {@code page}.
     *
     * <p>Never throws and never returns out-of-bounds data: the page index
     * is clamped internally, an empty/null list yields an empty page, and
     * the last page simply contains whatever is left over.</p>
     *
     * @param items   the full, already-filtered and sorted list
     *                (stable order is the caller's responsibility —
     *                unstable ordering makes items jump between pages)
     * @param page    requested 0-based page index (clamped)
     * @param perPage page capacity (≤ {@link #PAGE_SIZE})
     * @return a defensive copy of the page slice, never null
     */
    public static <T> List<T> slicePage(List<T> items, int page, int perPage) {
        if (items == null || items.isEmpty() || perPage <= 0) {
            return List.of();
        }
        int totalPages = pageCount(items.size(), perPage);
        int safePage = clampPage(page, totalPages);
        int from = safePage * perPage;
        int to = Math.min(from + perPage, items.size());
        if (from >= to) {
            return List.of();
        }
        return new ArrayList<>(items.subList(from, to));
    }
}
