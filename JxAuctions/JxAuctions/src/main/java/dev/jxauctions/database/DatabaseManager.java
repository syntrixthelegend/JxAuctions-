package dev.jxauctions.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.jxauctions.JxAuctions;
import dev.jxauctions.model.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class DatabaseManager {

    public enum ClaimResult {
        CLAIMED,
        NOT_FOUND,
        ERROR
    }

    public record SyncMessage(long id, String payload) {}
    public record GlobalStats(long itemsSold, long itemsPurchased, double moneyMade, double moneySpent) {}

    private final JxAuctions plugin;
    private HikariDataSource dataSource;
    private String prefix;
    private String dbType;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JxAuctions-DB-Writer");
        t.setDaemon(false);
        return t;
    });

    public DatabaseManager(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        this.dbType = cfg.getString("database.type", "h2").toLowerCase(Locale.ROOT);
        this.prefix = cfg.getString("database.prefix", "jxauctions") + "_";

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("JxAuctions-Pool");
        hikari.setMaximumPoolSize(cfg.getInt("database.pool.maximum-pool-size", 10));
        hikari.setMinimumIdle(cfg.getInt("database.pool.minimum-idle", 5));
        hikari.setMaxLifetime(cfg.getLong("database.pool.maximum-lifetime", 1800000));
        long keepalive = cfg.getLong("database.pool.keepalive-time", 0);
        if (keepalive > 0) {
            hikari.setKeepaliveTime(keepalive);
        }
        hikari.setConnectionTimeout(cfg.getLong("database.pool.connection-timeout", 5000));

        switch (dbType) {
            case "mysql" -> {
                String host = cfg.getString("database.address", "127.0.0.1");
                int port = cfg.getInt("database.port", 3306);
                String database = cfg.getString("database.database", "minecraft");
                hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
                hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");
                hikari.setUsername(cfg.getString("database.username", "root"));
                hikari.setPassword(cfg.getString("database.password", ""));
            }
            case "sqlite" -> {
                File dbFile = new File(plugin.getDataFolder(), "database.db");
                hikari.setDriverClassName("org.sqlite.JDBC");
                hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikari.setMaximumPoolSize(1);
                hikari.setMinimumIdle(1);
            }
            default -> {
                File dbFile = new File(plugin.getDataFolder(), "database");
                hikari.setDriverClassName("org.h2.Driver");
                hikari.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL");
            }
        }

        try {
            this.dataSource = new HikariDataSource(hikari);
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void createTables() throws SQLException {
        String autoInc = dbType.equals("sqlite") ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INT AUTO_INCREMENT PRIMARY KEY";
        String charsetSuffix = dbType.equals("mysql") ? " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" : "";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %sauctions (
                    id %s,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(32) NOT NULL,
                    item_data TEXT NOT NULL,
                    price DOUBLE NOT NULL,
                    currency VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    deleted_at BIGINT NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL,
                    server_name VARCHAR(64) NOT NULL
                )%s
            """.formatted(prefix, autoInc, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %shistory (
                    id %s,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(32) NOT NULL,
                    buyer_uuid VARCHAR(36) NOT NULL,
                    buyer_name VARCHAR(32) NOT NULL,
                    item_data TEXT NOT NULL,
                    price DOUBLE NOT NULL,
                    currency VARCHAR(64) NOT NULL,
                    sold_at BIGINT NOT NULL
                )%s
            """.formatted(prefix, autoInc, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %slogs (
                    id %s,
                    actor_uuid VARCHAR(36),
                    actor_name VARCHAR(32) NOT NULL,
                    target_uuid VARCHAR(36),
                    target_name VARCHAR(32),
                    action VARCHAR(32) NOT NULL,
                    details TEXT,
                    timestamp BIGINT NOT NULL
                )%s
            """.formatted(prefix, autoInc, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %splayer_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    last_name VARCHAR(32) NOT NULL,
                    limit_offset INT NOT NULL DEFAULT 0,
                    sorting VARCHAR(16) NOT NULL DEFAULT 'newest',
                    category VARCHAR(64) NOT NULL DEFAULT 'all',
                    currency VARCHAR(64)
                )%s
            """.formatted(prefix, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %splayer_stats (
                    uuid VARCHAR(36) NOT NULL,
                    currency VARCHAR(64) NOT NULL,
                    items_sold INT NOT NULL DEFAULT 0,
                    items_purchased INT NOT NULL DEFAULT 0,
                    money_made DOUBLE NOT NULL DEFAULT 0,
                    money_spent DOUBLE NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, currency)
                )%s
            """.formatted(prefix, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %spending_payouts (
                    id %s,
                    seller_uuid VARCHAR(36) NOT NULL,
                    buyer_name VARCHAR(32) NOT NULL,
                    item_name VARCHAR(128) NOT NULL,
                    item_amount INT NOT NULL,
                    currency VARCHAR(64) NOT NULL,
                    gross_amount DOUBLE NOT NULL,
                    net_amount DOUBLE NOT NULL,
                    tax_percent DOUBLE NOT NULL,
                    created_at BIGINT NOT NULL,
                    claimed TINYINT NOT NULL DEFAULT 0
                )%s
            """.formatted(prefix, autoInc, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %spending_items (
                    id %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_data TEXT NOT NULL,
                    reason VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL,
                    claimed TINYINT NOT NULL DEFAULT 0
                )%s
            """.formatted(prefix, autoInc, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %sglobal_stats (
                    id INT PRIMARY KEY,
                    items_sold BIGINT NOT NULL DEFAULT 0,
                    items_purchased BIGINT NOT NULL DEFAULT 0,
                    money_made DOUBLE NOT NULL DEFAULT 0,
                    money_spent DOUBLE NOT NULL DEFAULT 0
                )%s
            """.formatted(prefix, charsetSuffix));

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %ssync_messages (
                    id %s,
                    server_id VARCHAR(64) NOT NULL,
                    payload TEXT NOT NULL,
                    created_at BIGINT NOT NULL
                )%s
            """.formatted(prefix, autoInc, charsetSuffix));
        }
    }

    public List<AuctionItem> loadAllAuctions() {
        List<AuctionItem> list = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "auctions";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    list.add(new AuctionItem(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            UUID.fromString(rs.getString("seller_uuid")),
                            rs.getString("seller_name"),
                            rs.getString("item_data"),
                            rs.getDouble("price"),
                            rs.getString("currency"),
                            rs.getLong("created_at"),
                            rs.getLong("expires_at"),
                            rs.getLong("deleted_at"),
                            AuctionItem.Status.valueOf(rs.getString("status")),
                            rs.getString("server_name")
                    ));
                } catch (Exception rowErr) {
                    plugin.getLogger().warning("Skipping corrupted auction row id=" + rs.getInt("id") + ": " + rowErr.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load auctions: " + e.getMessage());
        }
        return list;
    }

    public CompletableFuture<Boolean> insertAuction(AuctionItem item) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + prefix + "auctions (uuid, seller_uuid, seller_name, item_data, price, currency, created_at, expires_at, deleted_at, status, server_name) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, item.getUuid().toString());
                ps.setString(2, item.getSellerUuid().toString());
                ps.setString(3, item.getSellerName());
                ps.setString(4, item.getSerializedItem());
                ps.setDouble(5, item.getPrice());
                ps.setString(6, item.getCurrencyId());
                ps.setLong(7, item.getCreatedAt());
                ps.setLong(8, item.getExpiresAt());
                ps.setLong(9, item.getDeletedAt());
                ps.setString(10, item.getStatus().name());
                ps.setString(11, item.getServerName());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        item.setId(keys.getInt(1));
                    }
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert auction: " + e.getMessage());
                return false;
            }
        }, writeExecutor);
    }

    public ClaimResult tryClaimActiveAuction(UUID auctionUuid) {
        return claimByStatus(auctionUuid, "ACTIVE");
    }

    public ClaimResult tryClaimExpiredAuction(UUID auctionUuid) {
        return claimByStatus(auctionUuid, "EXPIRED");
    }

    public ClaimResult tryClaimDeletedAuction(UUID auctionUuid) {
        return claimByStatus(auctionUuid, "DELETED");
    }

    private ClaimResult claimByStatus(UUID auctionUuid, String status) {
        String sql = "DELETE FROM " + prefix + "auctions WHERE uuid = ? AND status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionUuid.toString());
            ps.setString(2, status);
            return ps.executeUpdate() > 0 ? ClaimResult.CLAIMED : ClaimResult.NOT_FOUND;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to claim " + status + " auction: " + e.getMessage());
            return ClaimResult.ERROR;
        }
    }

    public boolean tryClaimHistoryEntry(int historyId) {
        String sql = "DELETE FROM " + prefix + "history WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, historyId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to claim history entry: " + e.getMessage());
            return false;
        }
    }

    public boolean tryTransitionStatus(UUID auctionUuid, AuctionItem.Status expected, AuctionItem.Status next, long deletedAt) {
        String sql = "UPDATE " + prefix + "auctions SET status = ?, deleted_at = ? WHERE uuid = ? AND status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, next.name());
            ps.setLong(2, deletedAt);
            ps.setString(3, auctionUuid.toString());
            ps.setString(4, expected.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to transition auction status: " + e.getMessage());
            return false;
        }
    }

    public CompletableFuture<Void> insertHistory(HistoryEntry entry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + prefix + "history (seller_uuid, seller_name, buyer_uuid, buyer_name, item_data, price, currency, sold_at) VALUES (?,?,?,?,?,?,?,?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entry.getSellerUuid().toString());
                ps.setString(2, entry.getSellerName());
                ps.setString(3, entry.getBuyerUuid().toString());
                ps.setString(4, entry.getBuyerName());
                ps.setString(5, entry.getSerializedItem());
                ps.setDouble(6, entry.getPrice());
                ps.setString(7, entry.getCurrencyId());
                ps.setLong(8, entry.getSoldAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert history: " + e.getMessage());
            }
        }, writeExecutor);
    }

    public GlobalStats loadGlobalStats() {
        String sql = "SELECT items_sold, items_purchased, money_made, money_spent FROM " + prefix + "global_stats WHERE id = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new GlobalStats(rs.getLong("items_sold"), rs.getLong("items_purchased"),
                        rs.getDouble("money_made"), rs.getDouble("money_spent"));
            }
        } catch (SQLException ignored) {}
        return new GlobalStats(0, 0, 0, 0);
    }

    public void incrementGlobalStats(long addSold, long addPurchased, double addMade, double addSpent) {
        writeExecutor.execute(() -> {
            String upd = "UPDATE " + prefix + "global_stats SET items_sold=items_sold+?, items_purchased=items_purchased+?, money_made=money_made+?, money_spent=money_spent+? WHERE id=1";
            String ins = "INSERT INTO " + prefix + "global_stats (id, items_sold, items_purchased, money_made, money_spent) VALUES (1,?,?,?,?)";
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setLong(1, addSold);
                    ps.setLong(2, addPurchased);
                    ps.setDouble(3, addMade);
                    ps.setDouble(4, addSpent);
                    if (ps.executeUpdate() > 0) return;
                }
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    ps.setLong(1, addSold);
                    ps.setLong(2, addPurchased);
                    ps.setDouble(3, addMade);
                    ps.setDouble(4, addSpent);
                    ps.executeUpdate();
                }
            } catch (SQLException ignored) {}
        });
    }

    public CompletableFuture<List<HistoryEntry>> loadHistory(UUID playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<HistoryEntry> list = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(prefix).append("history");
            if (playerUuid != null) {
                sql.append(" WHERE seller_uuid = ? OR buyer_uuid = ?");
            }
            sql.append(" ORDER BY sold_at DESC");
            if (limit > 0) {
                sql.append(" LIMIT ").append(limit);
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (playerUuid != null) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, playerUuid.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new HistoryEntry(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("seller_uuid")),
                                rs.getString("seller_name"),
                                UUID.fromString(rs.getString("buyer_uuid")),
                                rs.getString("buyer_name"),
                                rs.getString("item_data"),
                                rs.getDouble("price"),
                                rs.getString("currency"),
                                rs.getLong("sold_at")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load history: " + e.getMessage());
            }
            return list;
        });
    }

    public CompletableFuture<Void> insertLog(LogEntry log) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + prefix + "logs (actor_uuid, actor_name, target_uuid, target_name, action, details, timestamp) VALUES (?,?,?,?,?,?,?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, log.getActorUuid() != null ? log.getActorUuid().toString() : null);
                ps.setString(2, log.getActorName());
                ps.setString(3, log.getTargetUuid() != null ? log.getTargetUuid().toString() : null);
                ps.setString(4, log.getTargetName());
                ps.setString(5, log.getAction());
                ps.setString(6, log.getDetails());
                ps.setLong(7, log.getTimestamp());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert log: " + e.getMessage());
            }
        }, writeExecutor);
    }

    public CompletableFuture<List<LogEntry>> loadLogs(UUID playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LogEntry> list = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(prefix).append("logs");
            if (playerUuid != null) {
                sql.append(" WHERE actor_uuid = ? OR target_uuid = ?");
            }
            sql.append(" ORDER BY timestamp DESC");
            if (limit > 0) {
                sql.append(" LIMIT ").append(limit);
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (playerUuid != null) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, playerUuid.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String aUuid = rs.getString("actor_uuid");
                        String tUuid = rs.getString("target_uuid");
                        list.add(new LogEntry(
                                rs.getInt("id"),
                                aUuid != null ? UUID.fromString(aUuid) : null,
                                rs.getString("actor_name"),
                                tUuid != null ? UUID.fromString(tUuid) : null,
                                rs.getString("target_name"),
                                rs.getString("action"),
                                rs.getString("details"),
                                rs.getLong("timestamp")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load logs: " + e.getMessage());
            }
            return list;
        });
    }

    public PlayerStats loadPlayerStats(UUID uuid, String name) {
        PlayerStats stats = new PlayerStats(uuid, name);
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + prefix + "player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.setDbLimitOffset(rs.getInt("limit_offset"));
                        stats.setSelectedSorting(SortType.fromString(rs.getString("sorting")));
                        stats.setSelectedCategory(rs.getString("category"));
                        stats.setSelectedCurrency(rs.getString("currency"));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + prefix + "player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stats.setStat(
                                rs.getString("currency"),
                                rs.getInt("items_sold"),
                                rs.getInt("items_purchased"),
                                rs.getDouble("money_made"),
                                rs.getDouble("money_spent")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player stats: " + e.getMessage());
        }
        return stats;
    }

    public CompletableFuture<Void> savePlayerData(PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            String upsert = dbType.equals("mysql")
                    ? "INSERT INTO " + prefix + "player_data (uuid, last_name, limit_offset, sorting, category, currency) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE last_name=VALUES(last_name), limit_offset=VALUES(limit_offset), sorting=VALUES(sorting), category=VALUES(category), currency=VALUES(currency)"
                    : "MERGE INTO " + prefix + "player_data (uuid, last_name, limit_offset, sorting, category, currency) KEY(uuid) VALUES (?,?,?,?,?,?)";
            if (dbType.equals("sqlite")) {
                upsert = "INSERT OR REPLACE INTO " + prefix + "player_data (uuid, last_name, limit_offset, sorting, category, currency) VALUES (?,?,?,?,?,?)";
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, stats.getUuid().toString());
                ps.setString(2, stats.getLastKnownName());
                ps.setInt(3, stats.getDbLimitOffset());
                ps.setString(4, stats.getSelectedSorting().getId());
                ps.setString(5, stats.getSelectedCategory());
                ps.setString(6, stats.getSelectedCurrency());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save player data: " + e.getMessage());
            }
        }, writeExecutor);
    }

    public CompletableFuture<Void> incrementPlayerCurrencyStat(UUID uuid, String currencyId, int addSold, int addPurchased, double addMade, double addSpent) {
        return CompletableFuture.runAsync(() -> {
            String upd = "UPDATE " + prefix + "player_stats SET items_sold=items_sold+?, items_purchased=items_purchased+?, money_made=money_made+?, money_spent=money_spent+? WHERE uuid=? AND currency=?";
            String ins = "INSERT INTO " + prefix + "player_stats (uuid, currency, items_sold, items_purchased, money_made, money_spent) VALUES (?,?,?,?,?,?)";
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setInt(1, addSold);
                    ps.setInt(2, addPurchased);
                    ps.setDouble(3, addMade);
                    ps.setDouble(4, addSpent);
                    ps.setString(5, uuid.toString());
                    ps.setString(6, currencyId);
                    if (ps.executeUpdate() > 0) return;
                }
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, currencyId);
                    ps.setInt(3, addSold);
                    ps.setInt(4, addPurchased);
                    ps.setDouble(5, addMade);
                    ps.setDouble(6, addSpent);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to increment player currency stat: " + e.getMessage());
            }
        }, writeExecutor);
    }

    public CompletableFuture<Void> insertPendingPayout(PendingPayout payout) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + prefix + "pending_payouts (seller_uuid, buyer_name, item_name, item_amount, currency, gross_amount, net_amount, tax_percent, created_at) VALUES (?,?,?,?,?,?,?,?,?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, payout.sellerUuid().toString());
                ps.setString(2, payout.buyerName());
                ps.setString(3, payout.itemName());
                ps.setInt(4, payout.itemAmount());
                ps.setString(5, payout.currencyId());
                ps.setDouble(6, payout.grossAmount());
                ps.setDouble(7, payout.netAmount());
                ps.setDouble(8, payout.taxPercent());
                ps.setLong(9, payout.createdAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert pending payout: " + e.getMessage());
            }
        }, writeExecutor);
    }

    public List<PendingPayout> loadPendingPayouts(UUID sellerUuid) {
        List<PendingPayout> payouts = new ArrayList<>();
        String selectSql = "SELECT * FROM " + prefix + "pending_payouts WHERE seller_uuid = ? AND claimed = 0 ORDER BY id ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, sellerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.add(new PendingPayout(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            rs.getString("buyer_name"),
                            rs.getString("item_name"),
                            rs.getInt("item_amount"),
                            rs.getString("currency"),
                            rs.getDouble("gross_amount"),
                            rs.getDouble("net_amount"),
                            rs.getDouble("tax_percent"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load pending payouts: " + e.getMessage());
        }
        return payouts;
    }


    public record PendingItem(int id, UUID playerUuid, String serializedItem, String reason, long createdAt) {}

    public boolean tryClaimPendingPayout(int payoutId) {
        String sql = "UPDATE " + prefix + "pending_payouts SET claimed = 1 WHERE id = ? AND claimed = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, payoutId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public void unclaimPendingPayout(int payoutId) {
        writeExecutor.execute(() -> {
            String sql = "UPDATE " + prefix + "pending_payouts SET claimed = 0 WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, payoutId);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public CompletableFuture<Void> insertPendingItem(UUID playerUuid, String serializedItem, String reason) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + prefix + "pending_items (player_uuid, item_data, reason, created_at, claimed) VALUES (?,?,?,?,0)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, serializedItem);
                ps.setString(3, reason);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert pending item: " + e.getMessage());
            }
        }, writeExecutor);
    }

    public List<PendingItem> loadPendingItems(UUID playerUuid) {
        List<PendingItem> items = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "pending_items WHERE player_uuid = ? AND claimed = 0 ORDER BY id ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new PendingItem(rs.getInt("id"), UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("item_data"), rs.getString("reason"), rs.getLong("created_at")));
                }
            }
        } catch (SQLException ignored) {}
        return items;
    }

    public boolean tryClaimPendingItem(int itemId) {
        String sql = "UPDATE " + prefix + "pending_items SET claimed = 1 WHERE id = ? AND claimed = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public void unclaimPendingItem(int itemId) {
        writeExecutor.execute(() -> {
            String sql = "UPDATE " + prefix + "pending_items SET claimed = 0 WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public void deletePendingItemById(int itemId) {
        writeExecutor.execute(() -> {
            String sql = "DELETE FROM " + prefix + "pending_items WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public CompletableFuture<Void> deletePendingPayoutById(int payoutId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + prefix + "pending_payouts WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, payoutId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete pending payout id=" + payoutId + ": " + e.getMessage());
            }
        }, writeExecutor);
    }

    public void pushSyncMessage(String serverId, String payload) {
        writeExecutor.execute(() -> {
            String sql = "INSERT INTO " + prefix + "sync_messages (server_id, payload, created_at) VALUES (?,?,?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, serverId);
                ps.setString(2, payload);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to push SQL sync message: " + e.getMessage());
            }
        });
    }

    public long getMaxSyncId() {
        String sql = "SELECT COALESCE(MAX(id), 0) FROM " + prefix + "sync_messages";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException ignored) {}
        return 0L;
    }

    public List<SyncMessage> pollSyncMessages(String selfServerId, long afterId) {
        List<SyncMessage> messages = new ArrayList<>();
        String sql = "SELECT id, payload FROM " + prefix + "sync_messages WHERE id > ? AND server_id <> ? ORDER BY id ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterId);
            ps.setString(2, selfServerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new SyncMessage(rs.getLong("id"), rs.getString("payload")));
                }
            }
        } catch (SQLException ignored) {}
        return messages;
    }

    public void pruneSyncMessages(long olderThanEpochMs) {
        writeExecutor.execute(() -> {
            String sql = "DELETE FROM " + prefix + "sync_messages WHERE created_at < ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, olderThanEpochMs);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public int purgeOldDeletedAuctions(long olderThanEpochMs) {
        String sql = "DELETE FROM " + prefix + "auctions WHERE status = 'DELETED' AND deleted_at > 0 AND deleted_at < ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, olderThanEpochMs);
            return ps.executeUpdate();
        } catch (SQLException ignored) {
            return 0;
        }
    }

    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
