package com.phoenix.craft.gui;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Per-player GUI state for filter and sort modes.
 *
 * <p>Each player has their own filter/sort selection that persists across
 * category menu reopens (until they log out or change it).</p>
 *
 * @since 0.4.0
 */
public class PlayerGUIState {

    public enum FilterMode {
        ALL,
        CRAFTABLE,
        LOCKED
    }

    public enum SortMode {
        ID,
        LEVEL,
        XP,
        TIER
    }

    private final Map<UUID, FilterMode> filterModes = new ConcurrentHashMap<>();
    private final Map<UUID, SortMode> sortModes = new ConcurrentHashMap<>();

    /** Players currently in chat-search mode (waiting for chat input). */
    private final Map<UUID, Long> pendingSearchPlayers = new ConcurrentHashMap<>();

    public FilterMode getFilterMode(UUID uuid) {
        return filterModes.getOrDefault(uuid, FilterMode.ALL);
    }

    public void setFilterMode(UUID uuid, FilterMode mode) {
        filterModes.put(uuid, mode);
    }

    public FilterMode cycleFilterMode(UUID uuid, java.util.List<FilterMode> availableModes) {
        FilterMode current = getFilterMode(uuid);
        int idx = availableModes.indexOf(current);
        int next = (idx + 1) % availableModes.size();
        FilterMode nextMode = availableModes.get(next);
        setFilterMode(uuid, nextMode);
        return nextMode;
    }

    public SortMode getSortMode(UUID uuid) {
        return sortModes.getOrDefault(uuid, SortMode.ID);
    }

    public void setSortMode(UUID uuid, SortMode mode) {
        sortModes.put(uuid, mode);
    }

    public SortMode cycleSortMode(UUID uuid, java.util.List<SortMode> availableModes) {
        SortMode current = getSortMode(uuid);
        int idx = availableModes.indexOf(current);
        int next = (idx + 1) % availableModes.size();
        SortMode nextMode = availableModes.get(next);
        setSortMode(uuid, nextMode);
        return nextMode;
    }

    /**
     * Mark a player as waiting for chat search input. They have until
     * the timeout (System.currentTimeMillis() + ms) to type.
     */
    public void startSearchInput(UUID uuid, long timeoutMs) {
        pendingSearchPlayers.put(uuid, System.currentTimeMillis() + timeoutMs);
    }

    /**
     * Check if a player is currently waiting for chat search input.
     * Returns false if the timeout has expired (and removes the entry).
     */
    public boolean isPendingSearch(UUID uuid) {
        Long deadline = pendingSearchPlayers.get(uuid);
        if (deadline == null) return false;
        if (System.currentTimeMillis() > deadline) {
            pendingSearchPlayers.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Consume (remove) the pending search state. Called when the player
     * types a message or cancels.
     */
    public void clearSearchInput(UUID uuid) {
        pendingSearchPlayers.remove(uuid);
    }

    /**
     * Cleanup on player quit.
     */
    public void clear(UUID uuid) {
        filterModes.remove(uuid);
        sortModes.remove(uuid);
        pendingSearchPlayers.remove(uuid);
    }
}
