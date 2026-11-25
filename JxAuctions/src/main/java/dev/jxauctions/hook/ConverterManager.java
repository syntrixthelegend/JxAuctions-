package dev.jxauctions.hook;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.model.AuctionItem;
import dev.jxauctions.util.ColorUtil;
import dev.jxauctions.util.ItemSerializer;
import dev.jxauctions.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ConverterManager {

    private final JxAuctions plugin;

    public ConverterManager(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void convert(String sourcePluginName, CommandSender sender) {
        String key = sourcePluginName.toLowerCase(Locale.ROOT);
        SchedulerUtil.runAsync(plugin, () -> {
            List<CompletableFuture<Boolean>> pendingFutures = new ArrayList<>();
            int imported = 0;
            String defaultCurrency = plugin.getCurrencyManager().getDefaultCurrency() != null
                    ? plugin.getCurrencyManager().getDefaultCurrency().getId()
                    : "Vault";
            long now = System.currentTimeMillis();
            long expireMs = plugin.getConfigManager().getConfig().getLong("item-expire-time", 86400L) * 1000L;

            try {
                switch (key) {
                    case "crazyauctions" -> imported = convertCrazyAuctions(defaultCurrency, now, expireMs, pendingFutures);
                    case "playerauctions" -> imported = convertPlayerAuctions(defaultCurrency, now, expireMs, pendingFutures);
                    case "auctionhouse" -> imported = convertAuctionHouse(defaultCurrency, now, expireMs, pendingFutures);
                    case "zauctionhousev3", "zauctionhouse" -> imported = convertZAuctionHouse(defaultCurrency, now, expireMs, pendingFutures);
                    default -> {
                        sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getPrefix()
                                + "&#FF3333Unsupported converter: " + sourcePluginName
                                + " (Supported: AuctionHouse, zAuctionHouseV3, CrazyAuctions, PlayerAuctions)"));
                        return;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Converter] Error converting from " + sourcePluginName + ": " + e.getMessage());
                e.printStackTrace();
            }

            if (!pendingFutures.isEmpty()) {
                CompletableFuture.allOf(pendingFutures.toArray(new CompletableFuture[0])).join();
                pendingFutures.clear();
            }
            plugin.getAuctionManager().reloadCacheFromDatabase();
            sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getPrefix()
                    + "&#33FF33Converted &f" + imported + " &#33FF33listings from &f" + sourcePluginName + "&#33FF33!"));
        });
    }

    private int convertCrazyAuctions(String currencyId, long now, long expireMs, List<CompletableFuture<Boolean>> pendingFutures) {
        File dataFile = new File(plugin.getDataFolder().getParentFile(), "CrazyAuctions/data.yml");
        if (!dataFile.exists()) dataFile = new File(plugin.getDataFolder().getParentFile(), "CrazyAuctions/Data.yml");
        if (!dataFile.exists()) return 0;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection items = yaml.getConfigurationSection("Items");
        if (items == null) return 0;

        int count = 0;
        for (String id : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(id);
            if (sec == null) continue;
            ItemStack item = sec.getItemStack("Item");
            if (item == null) continue;
            String sellerName = sec.getString("Seller", "Unknown");
            UUID sellerUuid = parseUuidOrLookup(sec.getString("SellerUUID"), sellerName);
            double price = sec.getDouble("Price", 100.0);
            long expiresAt = sec.getLong("TimeTillExpire", now + expireMs);

            insertConvertedItem(sellerUuid, sellerName, item, price, currencyId, now, expiresAt, pendingFutures);
            count++;
        }
        return count;
    }

    private int convertPlayerAuctions(String currencyId, long now, long expireMs, List<CompletableFuture<Boolean>> pendingFutures) throws SQLException {
        File dbFile = new File(plugin.getDataFolder().getParentFile(), "PlayerAuctions/playerauctions.db");
        if (!dbFile.exists()) return 0;

        int count = 0;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM playerauctions_auctions")) {
            while (rs.next()) {
                try {
                    UUID sellerUuid = UUID.fromString(rs.getString("uuid"));
                    String sellerName = Bukkit.getOfflinePlayer(sellerUuid).getName();
                    if (sellerName == null) sellerName = "Unknown";
                    ItemStack item = ItemSerializer.fromBase64(rs.getString("item"));
                    if (item == null) continue;
                    double price = rs.getDouble("price");
                    long expireAt = rs.getLong("expire_time");
                    insertConvertedItem(sellerUuid, sellerName, item, price, currencyId, now, expireAt > now ? expireAt : now + expireMs, pendingFutures);
                    count++;
                } catch (Exception ignored) {}
            }
        }
        return count;
    }

    private int convertAuctionHouse(String currencyId, long now, long expireMs, List<CompletableFuture<Boolean>> pendingFutures) throws SQLException {
        File dbFile = new File(plugin.getDataFolder().getParentFile(), "AuctionHouse/database.db");
        if (!dbFile.exists()) return 0;

        int count = 0;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM listings")) {
            while (rs.next()) {
                try {
                    UUID sellerUuid = UUID.fromString(rs.getString("seller"));
                    String sellerName = Bukkit.getOfflinePlayer(sellerUuid).getName();
                    if (sellerName == null) sellerName = "Unknown";
                    ItemStack item = ItemSerializer.fromBase64(rs.getString("item"));
                    if (item == null) continue;
                    double price = rs.getDouble("price");
                    long expiresAt = rs.getLong("expiration");
                    insertConvertedItem(sellerUuid, sellerName, item, price, currencyId, now, expiresAt > now ? expiresAt : now + expireMs, pendingFutures);
                    count++;
                } catch (Exception ignored) {}
            }
        }
        return count;
    }

    private int convertZAuctionHouse(String currencyId, long now, long expireMs, List<CompletableFuture<Boolean>> pendingFutures) throws SQLException {
        File dbFile = new File(plugin.getDataFolder().getParentFile(), "zAuctionHouseV3/storage.db");
        if (!dbFile.exists()) return 0;

        int count = 0;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM zauctionhouse_items")) {
            while (rs.next()) {
                try {
                    UUID sellerUuid = UUID.fromString(rs.getString("seller"));
                    String sellerName = Bukkit.getOfflinePlayer(sellerUuid).getName();
                    if (sellerName == null) sellerName = "Unknown";
                    ItemStack item = ItemSerializer.fromBase64(rs.getString("itemstack"));
                    if (item == null) continue;
                    double price = rs.getDouble("price");
                    long expiresAt = rs.getLong("expire_at");
                    insertConvertedItem(sellerUuid, sellerName, item, price, currencyId, now, expiresAt > now ? expiresAt : now + expireMs, pendingFutures);
                    count++;
                } catch (Exception ignored) {}
            }
        }
        return count;
    }

    private void insertConvertedItem(UUID sellerUuid, String sellerName, ItemStack item, double price, String currencyId, long now, long expiresAt, List<CompletableFuture<Boolean>> pendingFutures) {
        AuctionItem auction = new AuctionItem(
                -1, UUID.randomUUID(), sellerUuid, sellerName,
                ItemSerializer.toBase64(item), price, currencyId,
                now, expiresAt, 0L, AuctionItem.Status.ACTIVE,
                plugin.getMultiServerSync().getServerId()
        );
        pendingFutures.add(plugin.getDatabaseManager().insertAuction(auction));
    }

    private UUID parseUuidOrLookup(String rawUuid, String name) {
        if (rawUuid != null && !rawUuid.isBlank()) {
            try { return UUID.fromString(rawUuid); } catch (IllegalArgumentException ignored) {}
        }
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }
}
