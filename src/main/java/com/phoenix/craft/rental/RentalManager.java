package com.phoenix.craft.rental;

import com.phoenix.craft.PhoenixCraft;
import com.phoenix.craft.util.MessageManager;
import com.phoenix.fish.api.PhoenixFishAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages recipe rentals between players.
 *
 * <p>Players who have learned a recipe can offer it for rent at a price
 * and duration. Other players can accept the offer, paying the price
 * from their PhoenixFish balance. The renter gains temporary access
 * to the recipe until the rental expires.</p>
 *
 * <p>Storage: {@code plugins/PhoenixCraft/rentals.yml}</p>
 *
 * @since 1.0.0
 */
public class RentalManager {

    private final PhoenixCraft plugin;
    private final PhoenixFishAPI fishApi;
    private final MessageManager messages;
    /** Cached for the constructor's initial boot decision only — reload picks
     *  up the live value via {@link #isEnabled()}. */
    private final boolean enabledAtBoot;

    private final Map<UUID, RentalOffer> offers = new ConcurrentHashMap<>();
    private final Map<UUID, List<Rental>> rentals = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public RentalManager(PhoenixCraft plugin, PhoenixFishAPI fishApi,
                          MessageManager messages) {
        this.plugin = plugin;
        this.fishApi = fishApi;
        this.messages = messages;
        this.enabledAtBoot = plugin.getConfig().getBoolean("recipe-rental.enabled", false);

        if (enabledAtBoot) {
            load();
            startExpiryChecker();
            plugin.getLogger().info("RentalManager enabled — "
                    + offers.size() + " offers, "
                    + rentals.size() + " active rentals.");
        } else {
            plugin.getLogger().info("RentalManager disabled (recipe-rental.enabled=false).");
        }
    }

    /** Live config read — picks up /pcraft reload changes immediately. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("recipe-rental.enabled", false);
    }

    public static class RentalOffer {
        public final UUID offerId;
        public final UUID ownerUuid;
        public final String ownerName;
        public final String recipeId;
        public final double price;
        public final int durationMinutes;
        public final long createdAt;

        public RentalOffer(UUID offerId, UUID ownerUuid, String ownerName,
                            String recipeId, double price, int durationMinutes) {
            this.offerId = offerId;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.recipeId = recipeId;
            this.price = price;
            this.durationMinutes = durationMinutes;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static class Rental {
        public final String recipeId;
        public final UUID ownerUuid;
        public final String ownerName;
        public final double pricePaid;
        public final long expiresAt;

        public Rental(String recipeId, UUID ownerUuid, String ownerName,
                       double pricePaid, long expiresAt) {
            this.recipeId = recipeId;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.pricePaid = pricePaid;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    private File getFile() {
        return new File(plugin.getDataFolder(), "rentals.yml");
    }

    @SuppressWarnings("unchecked")
    private void load() {
        File file = getFile();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection offersSec = cfg.getConfigurationSection("offers");
        if (offersSec != null) {
            for (String idStr : offersSec.getKeys(false)) {
                try {
                    UUID offerId = UUID.fromString(idStr);
                    ConfigurationSection sec = offersSec.getConfigurationSection(idStr);
                    if (sec == null) continue;
                    offers.put(offerId, new RentalOffer(
                            offerId,
                            UUID.fromString(sec.getString("owner-uuid")),
                            sec.getString("owner-name", "???"),
                            sec.getString("recipe-id", ""),
                            sec.getDouble("price", 0),
                            sec.getInt("duration-minutes", 60)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection rentalsSec = cfg.getConfigurationSection("rentals");
        if (rentalsSec != null) {
            for (String uuidStr : rentalsSec.getKeys(false)) {
                try {
                    UUID renterUuid = UUID.fromString(uuidStr);
                    List<Rental> list = new ArrayList<>();
                    for (Map<?, ?> entry : rentalsSec.getMapList(uuidStr)) {
                        try {
                            Rental r = new Rental(
                                    (String) entry.get("recipe-id"),
                                    UUID.fromString((String) entry.get("owner-uuid")),
                                    (String) entry.get("owner-name"),
                                    ((Number) entry.get("price-paid")).doubleValue(),
                                    ((Number) entry.get("expires-at")).longValue());
                            if (!r.isExpired()) list.add(r);
                        } catch (Exception ignored) {
                        }
                    }
                    if (!list.isEmpty()) rentals.put(renterUuid, list);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public synchronized void save() {
        if (!isEnabled() || !dirty) return;
        File file = getFile();
        FileConfiguration cfg = new YamlConfiguration();

        for (RentalOffer offer : offers.values()) {
            String p = "offers." + offer.offerId;
            cfg.set(p + ".owner-uuid", offer.ownerUuid.toString());
            cfg.set(p + ".owner-name", offer.ownerName);
            cfg.set(p + ".recipe-id", offer.recipeId);
            cfg.set(p + ".price", offer.price);
            cfg.set(p + ".duration-minutes", offer.durationMinutes);
        }

        for (Map.Entry<UUID, List<Rental>> entry : rentals.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Rental r : entry.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("recipe-id", r.recipeId);
                m.put("owner-uuid", r.ownerUuid.toString());
                m.put("owner-name", r.ownerName);
                m.put("price-paid", r.pricePaid);
                m.put("expires-at", r.expiresAt);
                list.add(m);
            }
            cfg.set("rentals." + entry.getKey(), list);
        }

        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save rentals.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        save();
    }

    // =====================================================================
    //  Offer Management
    // =====================================================================

    public String createOffer(Player owner, String recipeId, double price, int durationMinutes) {
        if (!isEnabled()) return "rental-disabled";

        int maxDur = plugin.getConfig().getInt("recipe-rental.max-duration-minutes", 60);
        int minDur = plugin.getConfig().getInt("recipe-rental.min-duration-minutes", 5);
        if (maxDur > 0 && durationMinutes > maxDur) return "rental-offer-invalid-duration";
        if (durationMinutes < minDur) return "rental-offer-invalid-duration";

        double minPrice = plugin.getConfig().getDouble("recipe-rental.min-price", 10);
        double maxPrice = plugin.getConfig().getDouble("recipe-rental.max-price", 0);
        if (price < minPrice) return "rental-offer-invalid-price";
        if (maxPrice > 0 && price > maxPrice) return "rental-offer-invalid-price";

        int maxOffers = plugin.getConfig().getInt("recipe-rental.max-offers-per-player", 10);
        if (maxOffers > 0) {
            long count = offers.values().stream()
                    .filter(o -> o.ownerUuid.equals(owner.getUniqueId())).count();
            if (count >= maxOffers) return "rental-offer-too-many";
        }

        if (fishApi.getCraftingRecipe(recipeId) == null) return "craft-recipe-unknown";

        UUID offerId = UUID.randomUUID();
        offers.put(offerId, new RentalOffer(offerId, owner.getUniqueId(),
                owner.getName(), recipeId.toLowerCase(), price, durationMinutes));
        dirty = true;
        save();
        return null;
    }

    public String acceptOffer(Player renter, UUID ownerUuid, String recipeId) {
        if (!isEnabled()) return "rental-disabled";

        RentalOffer found = null;
        for (RentalOffer o : offers.values()) {
            if (o.ownerUuid.equals(ownerUuid) && o.recipeId.equalsIgnoreCase(recipeId)) {
                found = o;
                break;
            }
        }
        if (found == null) return "rental-accept-not-found";

        double balance = fishApi.getBalance(renter.getUniqueId());
        if (balance < found.price) return "rental-accept-insufficient-funds";

        int maxRentals = plugin.getConfig().getInt("recipe-rental.max-active-rentals-per-player", 5);
        if (maxRentals > 0 && getActiveRentals(renter.getUniqueId()).size() >= maxRentals)
            return "rental-accept-too-many";

        fishApi.withdrawPlayer(renter.getUniqueId(), found.price);

        double feePct = plugin.getConfig().getDouble("recipe-rental.transaction-fee-percent", 0);
        double payout = found.price;
        if (feePct > 0) payout = found.price * (1 - feePct / 100.0);
        if (payout > 0) fishApi.depositPlayer(found.ownerUuid, payout);

        long expiresAt = System.currentTimeMillis() + (found.durationMinutes * 60_000L);
        rentals.computeIfAbsent(renter.getUniqueId(), k -> new ArrayList<>())
                .add(new Rental(found.recipeId, found.ownerUuid, found.ownerName,
                        found.price, expiresAt));

        offers.remove(found.offerId);
        dirty = true;
        save();

        Player ownerOnline = Bukkit.getPlayer(found.ownerUuid);
        if (ownerOnline != null && ownerOnline.isOnline()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%player%", renter.getName());
            ph.put("%recipe%", found.recipeId);
            ph.put("%price%", String.format("%.2f", found.price));
            ownerOnline.sendMessage(messages.getMessage("rental-accept-owner-notify", true, ph));
        }

        return null;
    }

    public String cancelOffer(Player owner, String recipeId) {
        if (!isEnabled()) return "rental-disabled";
        UUID toRemove = null;
        for (RentalOffer o : offers.values()) {
            if (o.ownerUuid.equals(owner.getUniqueId())
                    && o.recipeId.equalsIgnoreCase(recipeId)) {
                toRemove = o.offerId;
                break;
            }
        }
        if (toRemove == null) return "rental-cancel-not-found";
        offers.remove(toRemove);
        dirty = true;
        save();
        return null;
    }

    public List<RentalOffer> getAllOffers() {
        return new ArrayList<>(offers.values());
    }

    // =====================================================================
    //  Rental Queries
    // =====================================================================

    public List<Rental> getActiveRentals(UUID uuid) {
        List<Rental> all = rentals.get(uuid);
        if (all == null) return Collections.emptyList();
        List<Rental> active = new ArrayList<>();
        for (Rental r : all) if (!r.isExpired()) active.add(r);
        return active;
    }

    public boolean hasActiveRental(UUID uuid, String recipeId) {
        for (Rental r : getActiveRentals(uuid)) {
            if (r.recipeId.equalsIgnoreCase(recipeId)) return true;
        }
        return false;
    }

    // =====================================================================
    //  Expiry Checker
    // =====================================================================

    private void startExpiryChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiry, 600L, 600L);
    }

    private void checkExpiry() {
        boolean changed = false;
        for (Map.Entry<UUID, List<Rental>> entry : rentals.entrySet()) {
            Iterator<Rental> it = entry.getValue().iterator();
            while (it.hasNext()) {
                Rental r = it.next();
                if (r.isExpired()) {
                    it.remove();
                    changed = true;
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("%recipe%", r.recipeId);
                        p.sendMessage(messages.getMessage("rental-expired", true, ph));
                    }
                }
            }
        }
        if (changed) { dirty = true; save(); }
    }
}
