package com.phoenix.craft.crafting;

import com.phoenix.craft.PhoenixCraft;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Visual crafting animation (F14). On a successful craft, briefly display
 * the result item floating above the player's head for 1.5 seconds before
 * it's added to the inventory (which already happens via PhoenixFish).
 *
 * <p>Implementation: spawn an invisible {@link ArmorStand} at the player's
 * head location + 1.0, set the result item as its helmet, then schedule a
 * task 30 ticks later to remove the stand. A gentle upward velocity makes
 * the item appear to "rise" out of the crafting table.</p>
 *
 * @since 1.0.7 (feature #14)
 */
public class CraftingAnimationManager {

    private final PhoenixCraft plugin;
    /** Track active animations per player so we can cancel if they quit. */
    private final ConcurrentHashMap<UUID, ArmorStand> active = new ConcurrentHashMap<>();

    public CraftingAnimationManager(PhoenixCraft plugin) {
        this.plugin = plugin;
    }

    public void playCraftAnimation(Player player, ItemStack resultItem) {
        if (player == null || !player.isOnline()) return;
        if (resultItem == null || resultItem.getType() == Material.AIR) return;
        if (!plugin.getConfig().getBoolean("features.crafting-animation.enabled", true)) return;

        // Cancel any in-flight animation for this player.
        cancelFor(player.getUniqueId());

        try {
            Location loc = player.getLocation().add(0, 2.2, 0);
            if (loc.getWorld() == null) return;

            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
                s.setVisible(false);
                s.setGravity(false);
                s.setMarker(true);
                s.setSmall(true);
                s.setSilent(true);
                s.setCollidable(false);
                s.setHelmet(resultItem.clone());
            });

            active.put(player.getUniqueId(), stand);

            // Schedule removal after 30 ticks (1.5s).
            final UUID uuid = player.getUniqueId();
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ArmorStand s = active.remove(uuid);
                if (s != null) {
                    try { s.remove(); } catch (Throwable ignored) {}
                }
            }, 30L);
        } catch (Throwable t) {
            plugin.getLogger().warning("Crafting animation failed for "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    public void cancelFor(UUID uuid) {
        ArmorStand s = active.remove(uuid);
        if (s != null) {
            try { s.remove(); } catch (Throwable ignored) {}
        }
    }

    public void cancelAll() {
        for (ArmorStand s : active.values()) {
            try { s.remove(); } catch (Throwable ignored) {}
        }
        active.clear();
    }
}
