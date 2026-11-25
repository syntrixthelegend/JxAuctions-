package dev.jxauctions.manager;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.api.events.AuctionBuyEvent;
import dev.jxauctions.api.events.AuctionCancelEvent;
import dev.jxauctions.api.events.AuctionExpireEvent;
import dev.jxauctions.api.events.AuctionListEvent;
import dev.jxauctions.currency.Currency;
import dev.jxauctions.database.DatabaseManager;
import dev.jxauctions.model.*;
import dev.jxauctions.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

public class AuctionManager implements Listener {

    private final JxAuctions plugin;
    private volatile Map<UUID, AuctionItem> auctions = new ConcurrentHashMap<>();
    private final Set<UUID> inFlightInserts = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private final Set<UUID> purchaseLocks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> payoutLocks = ConcurrentHashMap.newKeySet();

    private final AtomicLong totalItemsSoldGlobal = new AtomicLong(0);
    private final AtomicLong totalItemsPurchasedGlobal = new AtomicLong(0);
    private final DoubleAdder totalMoneyMadeGlobal = new DoubleAdder();
    private final DoubleAdder totalMoneySpentGlobal = new DoubleAdder();
    private final AtomicInteger sweepCounter = new AtomicInteger(0);

    public AuctionManager(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reloadCacheFromDatabase();
        DatabaseManager.GlobalStats gs = plugin.getDatabaseManager().loadGlobalStats();
        totalItemsSoldGlobal.set(gs.itemsSold());
        totalItemsPurchasedGlobal.set(gs.itemsPurchased());
        totalMoneyMadeGlobal.reset();
        totalMoneyMadeGlobal.add(gs.moneyMade());
        totalMoneySpentGlobal.reset();
        totalMoneySpentGlobal.add(gs.moneySpent());

        startExpirationTask();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public synchronized void reloadCacheFromDatabase() {
        List<AuctionItem> loaded = plugin.getDatabaseManager().loadAllAuctions();
        Map<UUID, AuctionItem> fresh = new ConcurrentHashMap<>();
        for (AuctionItem item : loaded) {
            fresh.put(item.getUuid(), item);
        }
        for (UUID pendingUuid : inFlightInserts) {
            AuctionItem local = auctions.get(pendingUuid);
            if (local != null) {
                fresh.putIfAbsent(pendingUuid, local);
            }
        }
        this.auctions = fresh;
    }

    private void startExpirationTask() {
        SchedulerUtil.runAsyncTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long deletionDurationMs = plugin.getConfigManager().getConfig().getLong("item-deletion-time", 604800L) * 1000L;
            String datePattern = getDateFormat();
            boolean lostCasRace = false;

            for (AuctionItem item : auctions.values()) {
                if (item.getStatus() == AuctionItem.Status.ACTIVE && now >= item.getExpiresAt()) {
                    long deletedAt = now + deletionDurationMs;
                    if (!plugin.getDatabaseManager().tryTransitionStatus(item.getUuid(), AuctionItem.Status.ACTIVE, AuctionItem.Status.EXPIRED, deletedAt)) {
                        lostCasRace = true;
                        continue;
                    }
                    item.setStatus(AuctionItem.Status.EXPIRED);
                    item.setDeletedAt(deletedAt);
                    plugin.getMultiServerSync().broadcastSync("EXPIRE", item.getUuid().toString());
                    Bukkit.getPluginManager().callEvent(new AuctionExpireEvent(item));
                    logAction(item.getSellerUuid(), item.getSellerName(), null, null, "EXPIRE",
                            "Expired " + item.getItemStack().getAmount() + "x " + ItemSerializer.getDisplayName(item.getItemStack()));

                    Player seller = Bukkit.getPlayer(item.getSellerUuid());
                    if (seller != null && seller.isOnline()) {
                        ItemStack stack = item.getItemStack();
                        String msg = plugin.getConfigManager().getPrefixedMessage("my-items.expired")
                                .replace("%amount%", String.valueOf(stack.getAmount()))
                                .replace("%item%", ItemSerializer.getDisplayName(stack))
                                .replace("%date%", TimeUtil.formatDate(now, datePattern));
                        SchedulerUtil.runEntity(plugin, seller, () -> seller.sendMessage(msg));
                    }
                } else if (item.getStatus() == AuctionItem.Status.EXPIRED && item.getDeletedAt() > 0 && now >= item.getDeletedAt()) {
                    if (!plugin.getDatabaseManager().tryTransitionStatus(item.getUuid(), AuctionItem.Status.EXPIRED, AuctionItem.Status.DELETED, now)) {
                        lostCasRace = true;
                        continue;
                    }
                    item.setStatus(AuctionItem.Status.DELETED);
                    item.setDeletedAt(now);
                    plugin.getMultiServerSync().broadcastSync("DELETE", item.getUuid().toString());
                    logAction(item.getSellerUuid(), item.getSellerName(), null, null, "DELETE",
                            "Auto-deleted expired item " + ItemSerializer.getDisplayName(item.getItemStack()));

                    Player seller = Bukkit.getPlayer(item.getSellerUuid());
                    if (seller != null && seller.isOnline()) {
                        ItemStack stack = item.getItemStack();
                        String msg = plugin.getConfigManager().getPrefixedMessage("my-items.deleted")
                                .replace("%amount%", String.valueOf(stack.getAmount()))
                                .replace("%item%", ItemSerializer.getDisplayName(stack))
                                .replace("%date%", TimeUtil.formatDate(now, datePattern));
                        SchedulerUtil.runEntity(plugin, seller, () -> seller.sendMessage(msg));
                    }
                }
            }

            if (lostCasRace) {
                reloadCacheFromDatabase();
            }

            // Purge old DELETED rows once per hour (360 * 10s = 3600s)
            if (sweepCounter.incrementAndGet() % 360 == 0) {
                long purgeDays = plugin.getConfigManager().getConfig().getLong("deleted-purge-days", 30L);
                if (purgeDays > 0) {
                    int purged = plugin.getDatabaseManager().purgeOldDeletedAuctions(now - (purgeDays * 86_400_000L));
                    if (purged > 0) {
                        plugin.getLogger().info("[Purge] Permanently purged " + purged + " deleted auction(s) older than " + purgeDays + " days.");
                        logAction(null, "SYSTEM", null, null, "PURGE", "Purged " + purged + " deleted auctions");
                    }
                }
            }
        }, 20L * 10, 20L * 10);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        SchedulerUtil.runAsync(plugin, () -> {
            PlayerStats stats = plugin.getDatabaseManager().loadPlayerStats(uuid, player.getName());
            stats.setLastKnownName(player.getName());
            stats.setLoaded(true);
            playerStats.put(uuid, stats);

            long delayTicks = Math.max(1, plugin.getConfigManager().getConfig().getLong("delay-money-give-seconds", 3L)) * 20L;
            List<PendingPayout> payouts = plugin.getDatabaseManager().loadPendingPayouts(uuid);
            List<DatabaseManager.PendingItem> pendingItems = plugin.getDatabaseManager().loadPendingItems(uuid);

            if (!payouts.isEmpty() || !pendingItems.isEmpty()) {
                SchedulerUtil.runEntityLater(plugin, player, () -> {
                    if (!player.isOnline()) return;

                    if (payoutLocks.add(uuid)) {
                        try {
                            for (PendingPayout payout : payouts) {
                                Currency currency = plugin.getCurrencyManager().getCurrency(payout.currencyId());
                                if (currency == null) continue;
                                if (!plugin.getDatabaseManager().tryClaimPendingPayout(payout.id())) continue;

                                if (currency.deposit(player, payout.netAmount())) {
                                    plugin.getDatabaseManager().deletePendingPayoutById(payout.id());
                                    String grossFmt = currency.formatGui(payout.grossAmount());
                                    String netFmt = currency.formatGui(payout.netAmount());
                                    String msgKey = payout.taxPercent() > 0 ? "buy.notify-seller-tax" : "buy.notify-seller";
                                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage(msgKey)
                                            .replace("%player%", payout.buyerName())
                                            .replace("%amount%", String.valueOf(payout.itemAmount()))
                                            .replace("%item%", payout.itemName())
                                            .replace("%price%", grossFmt)
                                            .replace("%tax-amount%", netFmt)
                                            .replace("%tax-percent%", String.valueOf(payout.taxPercent()))
                                            .replace("%tax-fee%", currency.formatGui(payout.grossAmount() - payout.netAmount())));
                                } else {
                                    plugin.getDatabaseManager().unclaimPendingPayout(payout.id());
                                }
                            }
                        } finally {
                            payoutLocks.remove(uuid);
                        }
                    }

                    for (DatabaseManager.PendingItem pItem : pendingItems) {
                        if (!plugin.getDatabaseManager().tryClaimPendingItem(pItem.id())) continue;
                        ItemStack stack = ItemSerializer.fromBase64(pItem.serializedItem());
                        if (stack == null) {
                            plugin.getDatabaseManager().deletePendingItemById(pItem.id());
                            continue;
                        }
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
                        if (leftovers.isEmpty()) {
                            plugin.getDatabaseManager().deletePendingItemById(pItem.id());
                        } else {
                            plugin.getDatabaseManager().deletePendingItemById(pItem.id());
                            for (ItemStack rem : leftovers.values()) {
                                plugin.getDatabaseManager().insertPendingItem(uuid, ItemSerializer.toBase64(rem), pItem.reason());
                            }
                        }
                    }
                }, delayTicks);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerStats stats = playerStats.remove(event.getPlayer().getUniqueId());
        if (stats != null && stats.isLoaded()) {
            plugin.getDatabaseManager().savePlayerData(stats);
        }
    }

    public PlayerStats getPlayerStats(Player player) {
        PlayerStats existing = playerStats.get(player.getUniqueId());
        if (existing != null) return existing;
        PlayerStats fallback = new PlayerStats(player.getUniqueId(), player.getName());
        playerStats.putIfAbsent(player.getUniqueId(), fallback);
        SchedulerUtil.runAsync(plugin, () -> {
            PlayerStats loaded = plugin.getDatabaseManager().loadPlayerStats(player.getUniqueId(), player.getName());
            loaded.setLoaded(true);
            playerStats.put(player.getUniqueId(), loaded);
        });
        return playerStats.get(player.getUniqueId());
    }

    public int getSellLimit(Player player) {
        if (player.hasPermission("jxauctions.limit.bypass")) {
            return Integer.MAX_VALUE;
        }
        int permLimit = plugin.getConfigManager().getConfig().getInt("default-sell-limit", 3);
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission();
            if (perm.startsWith("jxauctions.limit.") || perm.startsWith("axauctions.limit.")) {
                String suffix = perm.substring(perm.lastIndexOf('.') + 1);
                try {
                    permLimit = Math.max(permLimit, Integer.parseInt(suffix));
                } catch (NumberFormatException ignored) {}
            }
        }
        int offset = getPlayerStats(player).getDbLimitOffset();
        return Math.max(0, permLimit + offset);
    }

    public long getPlayerAuctionCount(UUID uuid, AuctionItem.Status status) {
        return auctions.values().stream()
                .filter(a -> a.getSellerUuid().equals(uuid) && a.getStatus() == status)
                .count();
    }

    public long getTotalActiveCount() {
        long now = System.currentTimeMillis();
        return auctions.values().stream()
                .filter(a -> a.getStatus() == AuctionItem.Status.ACTIVE && !a.isExpired(now))
                .count();
    }

    public boolean isBlacklisted(ItemStack item) {
        for (Category.Rule rule : plugin.getConfigManager().getBlacklistRules()) {
            if (rule.matches(item)) return true;
        }
        return false;
    }

    public boolean isOversized(ItemStack item) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        int maxBytes = cfg.getInt("content-limit-bytes.item", 5000);
        String name = item.getType().name();
        if (name.contains("SHULKER_BOX")) {
            maxBytes = cfg.getInt("content-limit-bytes.shulker", 20000);
        } else if (name.equals("BUNDLE")) {
            maxBytes = cfg.getInt("content-limit-bytes.bundle", 20000);
        }
        if (maxBytes <= 0) return false;
        int size = ItemSerializer.getSerializedByteSize(item);
        return size > maxBytes;
    }

    private void deliverItemOrStorePending(Player player, UUID playerUuid, ItemStack stack, String reason, Runnable onDelivered) {
        String serialized = ItemSerializer.toBase64(stack);
        SchedulerUtil.runEntity(plugin, player, () -> {
            if (!player.isOnline()) {
                plugin.getDatabaseManager().insertPendingItem(playerUuid, serialized, reason);
                return;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            if (!leftovers.isEmpty()) {
                for (ItemStack rem : leftovers.values()) {
                    plugin.getDatabaseManager().insertPendingItem(playerUuid, ItemSerializer.toBase64(rem), reason);
                }
            }
            if (onDelivered != null) onDelivered.run();
        }, () -> plugin.getDatabaseManager().insertPendingItem(playerUuid, serialized, reason));
    }

    private void refundOrStorePending(Player buyer, UUID buyerUuid, Currency currency, double amount, String itemName) {
        SchedulerUtil.runEntity(plugin, buyer, () -> {
            if (buyer.isOnline() && currency.deposit(buyer, amount)) {
                buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.item-not-found"));
            } else {
                plugin.getDatabaseManager().insertPendingPayout(new PendingPayout(
                        -1, buyerUuid, "REFUND", itemName, 1, currency.getId(), amount, amount, 0.0, System.currentTimeMillis()
                ));
            }
        }, () -> plugin.getDatabaseManager().insertPendingPayout(new PendingPayout(
                -1, buyerUuid, "REFUND", itemName, 1, currency.getId(), amount, amount, 0.0, System.currentTimeMillis()
        )));
    }

    public boolean listItem(Player seller, ItemStack handItem, double price, int amount, Currency currency) {
        if (seller == null || !seller.isOnline() || seller.isDead()) return false;
        if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0 || amount <= 0) return false;
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        if (handItem == null || handItem.getType() == Material.AIR) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.no-item"));
            return false;
        }

        int limit = getSellLimit(seller);
        long currentCount = getPlayerAuctionCount(seller.getUniqueId(), AuctionItem.Status.ACTIVE);
        if (currentCount >= limit) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.limit-reached")
                    .replace("%current%", String.valueOf(currentCount))
                    .replace("%limit%", String.valueOf(limit)));
            return false;
        }

        if (isBlacklisted(handItem)) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("blacklisted"));
            return false;
        }

        if (isOversized(handItem)) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("oversized"));
            return false;
        }

        double minPrice = Math.max(0.01, cfg.getDouble("min-price", 10.0));
        double maxPrice = cfg.getDouble("max-price", 1_000_000_000_000.0);
        if (price < minPrice) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.price-too-low")
                    .replace("%amount%", currency.formatGui(minPrice)));
            return false;
        }
        if (maxPrice > 0 && price > maxPrice) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.price-too-high")
                    .replace("%amount%", currency.formatGui(maxPrice)));
            return false;
        }

        int sellAmount = Math.min(amount, handItem.getAmount());
        ItemStack listedStack = handItem.clone();
        listedStack.setAmount(sellAmount);

        AuctionListEvent listEvent = new AuctionListEvent(seller, listedStack, price, currency);
        Bukkit.getPluginManager().callEvent(listEvent);
        if (listEvent.isCancelled()) return false;
        double finalPrice = listEvent.getPrice();

        String base64;
        try {
            base64 = ItemSerializer.toBase64(listedStack);
        } catch (Exception e) {
            seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("oversized"));
            return false;
        }

        if (handItem.getAmount() > sellAmount) {
            handItem.setAmount(handItem.getAmount() - sellAmount);
        } else {
            seller.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        long now = System.currentTimeMillis();
        long expireTimeMs = cfg.getLong("item-expire-time", 86400L) * 1000L;
        AuctionItem auction = new AuctionItem(
                -1, UUID.randomUUID(), seller.getUniqueId(), seller.getName(),
                base64, finalPrice, currency.getId(),
                now, now + expireTimeMs, 0L,
                AuctionItem.Status.ACTIVE, plugin.getMultiServerSync().getServerId()
        );

        inFlightInserts.add(auction.getUuid());
        auctions.put(auction.getUuid(), auction);

        plugin.getDatabaseManager().insertAuction(auction).thenAccept(success -> {
            inFlightInserts.remove(auction.getUuid());
            if (!success) {
                auctions.remove(auction.getUuid());
                deliverItemOrStorePending(seller, seller.getUniqueId(), listedStack, "LIST_ROLLBACK", () ->
                        seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.failed")));
                return;
            }

            plugin.getMultiServerSync().broadcastSync("LIST", auction.getUuid().toString());
            String itemName = ItemSerializer.getDisplayName(listedStack);
            String formattedPrice = currency.formatGui(finalPrice);

            logAction(seller.getUniqueId(), seller.getName(), null, null, "LIST",
                    "Listed " + sellAmount + "x " + itemName + " for " + ColorUtil.stripColor(formattedPrice));

            SchedulerUtil.runEntity(plugin, seller, () -> {
                if (seller.isOnline()) {
                    seller.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.success")
                            .replace("%amount%", String.valueOf(sellAmount))
                            .replace("%item%", itemName)
                            .replace("%price%", formattedPrice));
                }
            });

            String actionBar = plugin.getConfigManager().getMessage("sell.action-bar-broadcast");
            if (!actionBar.isBlank()) {
                String msg = actionBar.replace("%player%", seller.getName())
                        .replace("%amount%", String.valueOf(sellAmount))
                        .replace("%item%", itemName)
                        .replace("%price%", formattedPrice);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    SchedulerUtil.runEntity(plugin, p, () ->
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg)));
                }
            }

            String chatBc = plugin.getConfigManager().getMessage("sell.chat-broadcast");
            if (!chatBc.isBlank()) {
                String msg = chatBc.replace("%player%", seller.getName())
                        .replace("%amount%", String.valueOf(sellAmount))
                        .replace("%item%", itemName)
                        .replace("%price%", formattedPrice);
                SchedulerUtil.runSync(plugin, () -> Bukkit.broadcastMessage(msg));
            }

            plugin.getDiscordWebhookManager().sendAuctionStart(seller.getName(), itemName, sellAmount, ColorUtil.stripColor(formattedPrice));
        });
        return true;
    }

    public void purchaseItem(Player buyer, AuctionItem auction) {
        if (buyer == null || !buyer.isOnline() || buyer.isDead()) return;
        if (auction == null || auction.getStatus() != AuctionItem.Status.ACTIVE || auction.isExpired(System.currentTimeMillis())) {
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.item-not-found"));
            return;
        }
        if (auction.getSellerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.own-item"));
            return;
        }
        if (buyer.getInventory().firstEmpty() == -1) {
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.inventory-full"));
            return;
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(auction.getCurrencyId());
        if (currency == null) {
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.item-not-found"));
            return;
        }
        if (!currency.has(buyer, auction.getPrice())) {
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.not-enough-money"));
            return;
        }

        AuctionBuyEvent buyEvent = new AuctionBuyEvent(buyer, auction);
        Bukkit.getPluginManager().callEvent(buyEvent);
        if (buyEvent.isCancelled()) return;

        if (!purchaseLocks.add(auction.getUuid())) {
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.item-not-found"));
            return;
        }

        if (!currency.withdraw(buyer, auction.getPrice())) {
            purchaseLocks.remove(auction.getUuid());
            buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.not-enough-money"));
            return;
        }

        UUID buyerUuid = buyer.getUniqueId();
        String buyerName = buyer.getName();

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                DatabaseManager.ClaimResult claim = plugin.getDatabaseManager().tryClaimActiveAuction(auction.getUuid());
                if (claim != DatabaseManager.ClaimResult.CLAIMED) {
                    if (claim == DatabaseManager.ClaimResult.NOT_FOUND) {
                        auctions.remove(auction.getUuid());
                    }
                    refundOrStorePending(buyer, buyerUuid, currency, auction.getPrice(),
                            ItemSerializer.getDisplayName(auction.getItemStack()));
                    return;
                }

                auctions.remove(auction.getUuid());
                plugin.getMultiServerSync().broadcastSync("BUY", auction.getUuid().toString());

                ItemStack boughtStack = auction.getItemStack();
                double gross = auction.getPrice();
                double taxPercent = Math.max(0.0, Math.min(100.0, currency.getTaxPercent()));
                double net = gross * (1.0 - (taxPercent / 100.0));

                String itemName = ItemSerializer.getDisplayName(boughtStack);
                String grossFmt = currency.formatGui(gross);
                String netFmt = currency.formatGui(net);

                deliverItemOrStorePending(buyer, buyerUuid, boughtStack, "PURCHASE_DELIVERY", () ->
                        buyer.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.success")
                                .replace("%amount%", String.valueOf(boughtStack.getAmount()))
                                .replace("%item%", itemName)
                                .replace("%price%", grossFmt)));

                boolean onlyOnJoin = plugin.getConfigManager().getConfig().getBoolean("only-give-money-on-join", false);
                OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerUuid());
                Player sellerOnline = sellerOffline.getPlayer();

                if (sellerOnline != null && sellerOnline.isOnline()) {
                    SchedulerUtil.runEntity(plugin, sellerOnline, () -> {
                        if (sellerOnline.isOnline() && currency.deposit(sellerOnline, net)) {
                            String msgKey = taxPercent > 0 ? "buy.notify-seller-tax" : "buy.notify-seller";
                            sellerOnline.sendMessage(plugin.getConfigManager().getPrefixedMessage(msgKey)
                                    .replace("%player%", buyerName)
                                    .replace("%amount%", String.valueOf(boughtStack.getAmount()))
                                    .replace("%item%", itemName)
                                    .replace("%price%", grossFmt)
                                    .replace("%tax-amount%", netFmt)
                                    .replace("%tax-percent%", String.valueOf(taxPercent))
                                    .replace("%tax-fee%", currency.formatGui(gross - net)));
                        } else {
                            plugin.getDatabaseManager().insertPendingPayout(new PendingPayout(
                                    -1, auction.getSellerUuid(), buyerName, itemName,
                                    boughtStack.getAmount(), currency.getId(), gross, net, taxPercent, System.currentTimeMillis()
                            ));
                        }
                    }, () -> plugin.getDatabaseManager().insertPendingPayout(new PendingPayout(
                            -1, auction.getSellerUuid(), buyerName, itemName,
                            boughtStack.getAmount(), currency.getId(), gross, net, taxPercent, System.currentTimeMillis()
                    )));
                } else if (!onlyOnJoin && currency.worksOffline() && currency.deposit(sellerOffline, net)) {
                    // Offline deposit succeeded
                } else {
                    plugin.getDatabaseManager().insertPendingPayout(new PendingPayout(
                            -1, auction.getSellerUuid(), buyerName, itemName,
                            boughtStack.getAmount(), currency.getId(), gross, net, taxPercent, System.currentTimeMillis()
                    ));
                }

                long now = System.currentTimeMillis();
                plugin.getDatabaseManager().insertHistory(new HistoryEntry(
                        -1, auction.getSellerUuid(), auction.getSellerName(),
                        buyerUuid, buyerName,
                        auction.getSerializedItem(), gross, currency.getId(), now
                ));

                PlayerStats buyerStats = getPlayerStats(buyer);
                buyerStats.addPurchase(currency.getId(), gross);
                plugin.getDatabaseManager().incrementPlayerCurrencyStat(buyerUuid, currency.getId(), 0, 1, 0.0, gross);

                PlayerStats cachedSeller = playerStats.get(auction.getSellerUuid());
                if (cachedSeller != null) {
                    cachedSeller.addSale(currency.getId(), net);
                }
                plugin.getDatabaseManager().incrementPlayerCurrencyStat(auction.getSellerUuid(), currency.getId(), 1, 0, net, 0.0);

                totalItemsSoldGlobal.incrementAndGet();
                totalItemsPurchasedGlobal.incrementAndGet();
                totalMoneyMadeGlobal.add(net);
                totalMoneySpentGlobal.add(gross);
                plugin.getDatabaseManager().incrementGlobalStats(1, 1, net, gross);

                logAction(buyerUuid, buyerName, auction.getSellerUuid(), auction.getSellerName(), "BUY",
                        "Bought " + boughtStack.getAmount() + "x " + itemName + " for " + ColorUtil.stripColor(grossFmt));

                String actionBar = plugin.getConfigManager().getMessage("buy.action-bar-purchase");
                if (!actionBar.isBlank()) {
                    String msg = actionBar.replace("%player%", buyerName)
                            .replace("%seller%", auction.getSellerName())
                            .replace("%amount%", String.valueOf(boughtStack.getAmount()))
                            .replace("%item%", itemName)
                            .replace("%price%", grossFmt);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        SchedulerUtil.runEntity(plugin, p, () ->
                                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg)));
                    }
                }

                plugin.getDiscordWebhookManager().sendAuctionBuy(buyerName, auction.getSellerName(),
                        itemName, boughtStack.getAmount(), ColorUtil.stripColor(grossFmt));
            } finally {
                purchaseLocks.remove(auction.getUuid());
            }
        });
    }

    public void cancelOwnAuction(Player player, AuctionItem auction) {
        cancelOwnAuction(player, auction, null);
    }

    public void cancelOwnAuction(Player player, AuctionItem auction, Runnable onComplete) {
        if (auction == null || !auction.getSellerUuid().equals(player.getUniqueId())) return;
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.inventory-full"));
            return;
        }
        if (!purchaseLocks.add(auction.getUuid())) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.failed"));
            return;
        }

        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                DatabaseManager.ClaimResult claim = plugin.getDatabaseManager().tryClaimActiveAuction(auction.getUuid());
                if (claim != DatabaseManager.ClaimResult.CLAIMED) {
                    if (claim == DatabaseManager.ClaimResult.NOT_FOUND) {
                        auctions.remove(auction.getUuid());
                    }
                    SchedulerUtil.runEntity(plugin, player, () ->
                            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.failed")));
                    return;
                }
                auctions.remove(auction.getUuid());
                Bukkit.getPluginManager().callEvent(new AuctionCancelEvent(player, auction, false));
                plugin.getMultiServerSync().broadcastSync("CANCEL", auction.getUuid().toString());

                ItemStack stack = auction.getItemStack();
                Currency currency = plugin.getCurrencyManager().getCurrency(auction.getCurrencyId());
                String priceFmt = currency != null ? ColorUtil.stripColor(currency.formatGui(auction.getPrice())) : String.valueOf(auction.getPrice());
                plugin.getDiscordWebhookManager().sendAuctionCancel(playerName, ItemSerializer.getDisplayName(stack), stack.getAmount(), priceFmt);

                logAction(playerUuid, playerName, null, null, "CANCEL",
                        "Cancelled " + ItemSerializer.getDisplayName(stack));

                deliverItemOrStorePending(player, playerUuid, stack, "CANCEL_RETURN", () ->
                        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.removed")));
            } finally {
                purchaseLocks.remove(auction.getUuid());
                if (onComplete != null) SchedulerUtil.runEntity(plugin, player, onComplete);
            }
        });
    }

    public void claimExpiredItem(Player player, AuctionItem auction) {
        claimExpiredItem(player, auction, null);
    }

    public void claimExpiredItem(Player player, AuctionItem auction, Runnable onComplete) {
        if (auction == null || !auction.getSellerUuid().equals(player.getUniqueId()) || auction.getStatus() != AuctionItem.Status.EXPIRED) {
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.inventory-full"));
            return;
        }
        if (!purchaseLocks.add(auction.getUuid())) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.failed"));
            return;
        }

        UUID playerUuid = player.getUniqueId();
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                DatabaseManager.ClaimResult claim = plugin.getDatabaseManager().tryClaimExpiredAuction(auction.getUuid());
                if (claim != DatabaseManager.ClaimResult.CLAIMED) {
                    if (claim == DatabaseManager.ClaimResult.NOT_FOUND) {
                        auctions.remove(auction.getUuid());
                    }
                    SchedulerUtil.runEntity(plugin, player, () ->
                            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.failed")));
                    return;
                }
                auctions.remove(auction.getUuid());
                plugin.getMultiServerSync().broadcastSync("CLAIM_EXPIRED", auction.getUuid().toString());
                ItemStack stack = auction.getItemStack();
                deliverItemOrStorePending(player, playerUuid, stack, "EXPIRED_CLAIM", () ->
                        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("my-items.removed")));
            } finally {
                purchaseLocks.remove(auction.getUuid());
                if (onComplete != null) SchedulerUtil.runEntity(plugin, player, onComplete);
            }
        });
    }

    public void adminCancelAndReturn(Player staff, AuctionItem auction) {
        adminCancelAndReturn(staff, auction, null);
    }

    public void adminCancelAndReturn(Player staff, AuctionItem auction, Runnable onComplete) {
        if (auction == null || auction.getStatus() != AuctionItem.Status.ACTIVE) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }
        if (!purchaseLocks.add(auction.getUuid())) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                long now = System.currentTimeMillis();
                long deletionMs = plugin.getConfigManager().getConfig().getLong("item-deletion-time", 604800L) * 1000L;
                long deletedAt = now + deletionMs;

                if (!plugin.getDatabaseManager().tryTransitionStatus(auction.getUuid(), AuctionItem.Status.ACTIVE, AuctionItem.Status.EXPIRED, deletedAt)) {
                    SchedulerUtil.runEntity(plugin, staff, () ->
                            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed")));
                    return;
                }
                auction.setStatus(AuctionItem.Status.EXPIRED);
                auction.setDeletedAt(deletedAt);
                Bukkit.getPluginManager().callEvent(new AuctionCancelEvent(staff, auction, true));
                plugin.getMultiServerSync().broadcastSync("ADMIN_CANCEL", auction.getUuid().toString());

                logAction(staff.getUniqueId(), staff.getName(), auction.getSellerUuid(), auction.getSellerName(),
                        "ADMIN_CANCEL", "Staff cancelled " + ItemSerializer.getDisplayName(auction.getItemStack()));

                SchedulerUtil.runEntity(plugin, staff, () ->
                        staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.cancelled-info")));

                Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    ItemStack stack = auction.getItemStack();
                    String msg = plugin.getConfigManager().getPrefixedMessage("admin-action.cancelled")
                            .replace("%staff%", staff.getName())
                            .replace("%amount%", String.valueOf(stack.getAmount()))
                            .replace("%item%", ItemSerializer.getDisplayName(stack))
                            .replace("%date%", TimeUtil.formatDate(now, getDateFormat()));
                    SchedulerUtil.runEntity(plugin, seller, () -> seller.sendMessage(msg));
                }
            } finally {
                purchaseLocks.remove(auction.getUuid());
                if (onComplete != null) SchedulerUtil.runEntity(plugin, staff, onComplete);
            }
        });
    }

    public void adminDelete(Player staff, AuctionItem auction) {
        adminDelete(staff, auction, null);
    }

    public void adminDelete(Player staff, AuctionItem auction, Runnable onComplete) {
        if (auction == null || auction.getStatus() == AuctionItem.Status.DELETED) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }
        if (!purchaseLocks.add(auction.getUuid())) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                long now = System.currentTimeMillis();
                AuctionItem.Status previousStatus = auction.getStatus();
                if (!plugin.getDatabaseManager().tryTransitionStatus(auction.getUuid(), previousStatus, AuctionItem.Status.DELETED, now)) {
                    SchedulerUtil.runEntity(plugin, staff, () ->
                            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed")));
                    return;
                }
                auction.setStatus(AuctionItem.Status.DELETED);
                auction.setDeletedAt(now);
                Bukkit.getPluginManager().callEvent(new AuctionCancelEvent(staff, auction, true));
                plugin.getMultiServerSync().broadcastSync("ADMIN_DELETE", auction.getUuid().toString());

                logAction(staff.getUniqueId(), staff.getName(), auction.getSellerUuid(), auction.getSellerName(),
                        "ADMIN_REMOVE", "Staff deleted " + ItemSerializer.getDisplayName(auction.getItemStack()));

                SchedulerUtil.runEntity(plugin, staff, () ->
                        staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.removed-info")));

                Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    ItemStack stack = auction.getItemStack();
                    String msg = plugin.getConfigManager().getPrefixedMessage("admin-action.removed")
                            .replace("%staff%", staff.getName())
                            .replace("%amount%", String.valueOf(stack.getAmount()))
                            .replace("%item%", ItemSerializer.getDisplayName(stack))
                            .replace("%date%", TimeUtil.formatDate(now, getDateFormat()));
                    SchedulerUtil.runEntity(plugin, seller, () -> seller.sendMessage(msg));
                }
            } finally {
                purchaseLocks.remove(auction.getUuid());
                if (onComplete != null) SchedulerUtil.runEntity(plugin, staff, onComplete);
            }
        });
    }

    public void adminRecoverDeleted(Player staff, AuctionItem auction) {
        adminRecoverDeleted(staff, auction, null);
    }

    public void adminRecoverDeleted(Player staff, AuctionItem auction, Runnable onComplete) {
        if (auction == null || auction.getStatus() != AuctionItem.Status.DELETED) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }
        if (staff.getInventory().firstEmpty() == -1) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.inventory-full"));
            return;
        }
        if (!purchaseLocks.add(auction.getUuid())) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }

        UUID staffUuid = staff.getUniqueId();
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                DatabaseManager.ClaimResult claim = plugin.getDatabaseManager().tryClaimDeletedAuction(auction.getUuid());
                if (claim != DatabaseManager.ClaimResult.CLAIMED) {
                    if (claim == DatabaseManager.ClaimResult.NOT_FOUND) {
                        auctions.remove(auction.getUuid());
                    }
                    SchedulerUtil.runEntity(plugin, staff, () ->
                            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed")));
                    return;
                }
                auctions.remove(auction.getUuid());
                plugin.getMultiServerSync().broadcastSync("ADMIN_RECOVER", auction.getUuid().toString());
                ItemStack stack = auction.getItemStack();
                logAction(staffUuid, staff.getName(), auction.getSellerUuid(),
                        auction.getSellerName(), "ADMIN_RECOVER", "Recovered deleted item");
                deliverItemOrStorePending(staff, staffUuid, stack, "ADMIN_RECOVER", () ->
                        staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.take-item")));
            } finally {
                purchaseLocks.remove(auction.getUuid());
                if (onComplete != null) SchedulerUtil.runEntity(plugin, staff, onComplete);
            }
        });
    }

    public void adminRecoverHistory(Player staff, HistoryEntry history) {
        adminRecoverHistory(staff, history, null);
    }

    public void adminRecoverHistory(Player staff, HistoryEntry history, Runnable onComplete) {
        if (history == null) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed"));
            return;
        }
        if (staff.getInventory().firstEmpty() == -1) {
            staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("buy.inventory-full"));
            return;
        }

        UUID staffUuid = staff.getUniqueId();
        SchedulerUtil.runAsync(plugin, () -> {
            if (!plugin.getDatabaseManager().tryClaimHistoryEntry(history.getId())) {
                SchedulerUtil.runEntity(plugin, staff, () ->
                        staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.failed")));
                return;
            }
            ItemStack stack = history.getItemStack();
            logAction(staffUuid, staff.getName(), history.getSellerUuid(),
                    history.getSellerName(), "ADMIN_RECOVER", "Recovered item from history");
            deliverItemOrStorePending(staff, staffUuid, stack, "ADMIN_RECOVER", () ->
                    staff.sendMessage(plugin.getConfigManager().getPrefixedMessage("admin-action.take-item")));
            if (onComplete != null) SchedulerUtil.runEntity(plugin, staff, onComplete);
        });
    }

    public List<AuctionItem> getFilteredAuctions(String categoryId, String currencyFilter, String searchQuery, SortType sortType) {
        long now = System.currentTimeMillis();
        Category category = plugin.getConfigManager().getCategories().get(categoryId);
        String queryLower = searchQuery != null && !searchQuery.isBlank() ? searchQuery.toLowerCase(Locale.ROOT) : null;

        Comparator<AuctionItem> comparator = switch (sortType) {
            case OLDEST -> Comparator.comparingLong(AuctionItem::getCreatedAt);
            case CHEAPEST -> Comparator.comparingDouble(AuctionItem::getPrice);
            case PRICIEST -> Comparator.comparingDouble(AuctionItem::getPrice).reversed();
            default -> Comparator.comparingLong(AuctionItem::getCreatedAt).reversed();
        };

        return auctions.values().stream()
                .filter(a -> a.getStatus() == AuctionItem.Status.ACTIVE && !a.isExpired(now))
                .filter(a -> currencyFilter == null || currencyFilter.isBlank() || a.getCurrencyId().equalsIgnoreCase(currencyFilter))
                .filter(a -> {
                    if (category == null || categoryId == null || categoryId.equalsIgnoreCase("all")) return true;
                    return category.matches(a.getItemStack());
                })
                .filter(a -> queryLower == null || a.getSearchIndex().contains(queryLower))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    public List<AuctionItem> getPlayerItems(UUID playerUuid, boolean expiredOnly, boolean includeExpired) {
        return auctions.values().stream()
                .filter(a -> a.getSellerUuid().equals(playerUuid))
                .filter(a -> {
                    if (expiredOnly) return a.getStatus() == AuctionItem.Status.EXPIRED;
                    if (includeExpired) return a.getStatus() == AuctionItem.Status.ACTIVE || a.getStatus() == AuctionItem.Status.EXPIRED;
                    return a.getStatus() == AuctionItem.Status.ACTIVE;
                })
                .sorted(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<AuctionItem> getDeletedItems(UUID playerUuid) {
        return auctions.values().stream()
                .filter(a -> a.getStatus() == AuctionItem.Status.DELETED)
                .filter(a -> playerUuid == null || a.getSellerUuid().equals(playerUuid))
                .sorted(Comparator.comparingLong(AuctionItem::getDeletedAt).reversed())
                .collect(Collectors.toList());
    }

    public String getDateFormat() {
        return plugin.getConfigManager().getConfig().getString("date-format",
                plugin.getConfigManager().getLangConfig().getString("date-format", "yyyy/MM/dd HH:mm:ss"));
    }

    public void logAction(UUID actorUuid, String actorName, UUID targetUuid, String targetName, String action, String details) {
        plugin.getDatabaseManager().insertLog(new LogEntry(
                -1, actorUuid, actorName, targetUuid, targetName, action, details, System.currentTimeMillis()
        ));
    }

    public long getTotalItemsSoldGlobal() { return totalItemsSoldGlobal.get(); }
    public long getTotalItemsPurchasedGlobal() { return totalItemsPurchasedGlobal.get(); }
    public double getTotalMoneyMadeGlobal() { return totalMoneyMadeGlobal.sum(); }
    public double getTotalMoneySpentGlobal() { return totalMoneySpentGlobal.sum(); }
}
