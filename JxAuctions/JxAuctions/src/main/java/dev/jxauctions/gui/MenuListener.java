package dev.jxauctions.gui;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.currency.Currency;
import dev.jxauctions.model.AuctionItem;
import dev.jxauctions.model.HistoryEntry;
import dev.jxauctions.model.PlayerStats;
import dev.jxauctions.model.SortType;
import dev.jxauctions.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MenuListener implements Listener {

    private final JxAuctions plugin;
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();

    public MenuListener(JxAuctions plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfigManager().getConfig().getLong("gui-refresh-cooldown-milliseconds", 150L);
        if (now - clickCooldowns.getOrDefault(player.getUniqueId(), 0L) < cooldown) {
            return;
        }
        clickCooldowns.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        String menuType = holder.getMenuType();

        switch (menuType) {
            case "confirm-purchase" -> {
                if (holder.isConsumed()) return;
                if (slot == plugin.getConfigManager().getGuiConfig("confirm-purchase").getInt("confirm.slot", 11)) {
                    holder.setConsumed(true);
                    AuctionItem target = holder.getSelectedAuction();
                    player.closeInventory();
                    plugin.getAuctionManager().purchaseItem(player, target);
                } else if (slot == plugin.getConfigManager().getGuiConfig("confirm-purchase").getInt("cancel.slot", 15)) {
                    holder.setConsumed(true);
                    plugin.getAuctionMenu().openMain(player, 1, null, null);
                }
                return;
            }
            case "confirm-listing" -> {
                if (holder.isConsumed()) return;
                var gui = plugin.getConfigManager().getGuiConfig("confirm-listing");
                if (slot == gui.getInt("confirm.slot", 11)) {
                    holder.setConsumed(true);
                    Currency currency = plugin.getCurrencyManager().getCurrency(holder.getPendingSellCurrencyId());
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    ItemStack pending = holder.getPendingSellItem();
                    player.closeInventory();
                    if (currency != null && pending != null && hand.isSimilar(pending) && hand.getAmount() >= holder.getPendingSellAmount()) {
                        plugin.getAuctionManager().listItem(player, hand,
                                holder.getPendingSellPrice(), holder.getPendingSellAmount(), currency);
                    } else {
                        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.no-item"));
                    }
                } else if (slot == gui.getInt("cancel.slot", 15)) {
                    holder.setConsumed(true);
                    player.closeInventory();
                }
                return;
            }
            case "currency-selector" -> {
                if (holder.isConsumed()) return;
                String currId = holder.getCurrencySlots().get(slot);
                if (currId != null) {
                    holder.setConsumed(true);
                    Currency currency = plugin.getCurrencyManager().getCurrency(currId);
                    if (currency != null) {
                        if (plugin.getConfigManager().getConfig().getBoolean("confirm-listing", false)) {
                            plugin.getAuctionMenu().openConfirmListing(player, holder.getPendingSellItem(),
                                    holder.getPendingSellPrice(), holder.getPendingSellAmount(), currency);
                        } else {
                            ItemStack hand = player.getInventory().getItemInMainHand();
                            ItemStack pending = holder.getPendingSellItem();
                            player.closeInventory();
                            if (pending != null && hand.isSimilar(pending) && hand.getAmount() >= holder.getPendingSellAmount()) {
                                plugin.getAuctionManager().listItem(player, hand,
                                        holder.getPendingSellPrice(), holder.getPendingSellAmount(), currency);
                            } else {
                                player.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.no-item"));
                            }
                        }
                    } else {
                        player.closeInventory();
                    }
                    return;
                }
            }
            case "admin-removal" -> {
                var gui = plugin.getConfigManager().getGuiConfig("admin-removal");
                AuctionItem target = holder.getSelectedAuction();
                if (slot == gui.getInt("cancel-return.slot", 11)) {
                    plugin.getAuctionManager().adminCancelAndReturn(player, target,
                            () -> plugin.getAuctionMenu().openMain(player, 1, null, null));
                } else if (slot == gui.getInt("delete-permanent.slot", 15)) {
                    plugin.getAuctionManager().adminDelete(player, target,
                            () -> plugin.getAuctionMenu().openMain(player, 1, null, null));
                } else if (slot == gui.getInt("back.slot", 22)) {
                    plugin.getAuctionMenu().openMain(player, 1, null, null);
                }
                return;
            }
            case "shulker-preview" -> {
                var gui = plugin.getConfigManager().getGuiConfig("shulker-preview");
                if (slot == gui.getInt("back.slot", 31)) {
                    plugin.getAuctionMenu().openMenuByName(player, holder.getReturnMenu());
                }
                return;
            }
            case "category" -> {
                String catId = holder.getCategorySlots().get(slot);
                if (catId != null) {
                    PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
                    stats.setSelectedCategory(catId);
                    plugin.getAuctionMenu().openMain(player, 1, null, null);
                    return;
                }
            }
        }

        AuctionItem auction = holder.getAuctionSlots().get(slot);
        if (auction != null) {
            if (event.getClick() == ClickType.RIGHT && ItemSerializer.isContainer(auction.getItemStack())) {
                plugin.getAuctionMenu().openShulkerPreview(player, auction.getItemStack(), menuType);
                return;
            }

            if (menuType.equals("main")) {
                if (event.getClick().isShiftClick() && player.hasPermission("jxauctions.admin.removal")) {
                    plugin.getAuctionMenu().openAdminRemoval(player, auction);
                } else if (auction.getSellerUuid().equals(player.getUniqueId())) {
                    plugin.getAuctionManager().cancelOwnAuction(player, auction,
                            () -> plugin.getAuctionMenu().openMain(player, holder.getPage(), holder.getSearchQuery(), holder.getTargetUuid()));
                } else {
                    plugin.getAuctionMenu().openConfirmPurchase(player, auction);
                }
                return;
            }

            if (menuType.equals("my-items") || menuType.equals("expired-items")) {
                Runnable reopen = menuType.equals("my-items")
                        ? () -> plugin.getAuctionMenu().openMyItems(player, holder.getPage())
                        : () -> plugin.getAuctionMenu().openExpiredItems(player, holder.getPage());
                if (auction.getStatus() == AuctionItem.Status.EXPIRED) {
                    plugin.getAuctionManager().claimExpiredItem(player, auction, reopen);
                } else {
                    plugin.getAuctionManager().cancelOwnAuction(player, auction, reopen);
                }
                return;
            }

            if (menuType.equals("deleted-admin") && player.hasPermission("jxauctions.deleted.admin.take")) {
                plugin.getAuctionManager().adminRecoverDeleted(player, auction,
                        () -> plugin.getAuctionMenu().openDeleted(player, holder.getTargetUuid(), holder.getPage(), true));
                return;
            }
        }

        HistoryEntry history = holder.getHistorySlots().get(slot);
        if (history != null) {
            if (event.getClick() == ClickType.RIGHT && ItemSerializer.isContainer(history.getItemStack())) {
                plugin.getAuctionMenu().openShulkerPreview(player, history.getItemStack(), "history");
                return;
            }
            if (menuType.equals("history-admin") && player.hasPermission("jxauctions.history.admin.take")) {
                plugin.getAuctionManager().adminRecoverHistory(player, history,
                        () -> plugin.getAuctionMenu().openHistory(player, holder.getTargetUuid(), holder.getPage(), true));
                return;
            }
        }

        List<String> actions = holder.getActionSlots().get(slot);
        if (actions != null) {
            executeActions(player, holder, actions, event.getClick().isShiftClick());
        }
    }

    private void executeActions(Player player, MenuHolder holder, List<String> actions, boolean isShift) {
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);

        for (String action : actions) {
            if (action.startsWith("[SOUND] ")) {
                String[] parts = action.substring(8).split("\\|");
                try {
                    float vol = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    player.playSound(player.getLocation(), parts[0], vol, pitch);
                } catch (Exception ignored) {}
            } else if (action.equals("[CLOSE]")) {
                player.closeInventory();
            } else if (action.startsWith("[MENU] ")) {
                plugin.getAuctionMenu().openMenuByName(player, action.substring(7).trim());
            } else if (action.equals("[REFRESH]")) {
                reopenCurrent(player, holder);
            } else if (action.startsWith("[PAGE] ")) {
                String dir = action.substring(7).trim();
                int nextPage = dir.equalsIgnoreCase("next") ? holder.getPage() + 1 : Math.max(1, holder.getPage() - 1);
                holder.setPage(nextPage);
                reopenCurrent(player, holder);
            } else if (action.startsWith("[SORTING]")) {
                String arg = action.length() > 9 ? action.substring(9).trim() : "";
                if (!arg.isEmpty()) {
                    String[] parts = arg.split("\\|");
                    if (isShift && parts.length > 1) {
                        stats.setSelectedSorting(SortType.fromString(parts[1]));
                    } else if (!isShift) {
                        stats.setSelectedSorting(SortType.fromString(parts[0]));
                    } else {
                        stats.setSelectedSorting(SortType.NEWEST);
                    }
                } else if (isShift) {
                    stats.setSelectedSorting(SortType.NEWEST);
                } else {
                    stats.setSelectedSorting(stats.getSelectedSorting().next());
                }
                plugin.getAuctionMenu().openMain(player, 1, holder.getSearchQuery(), holder.getTargetUuid());
            } else if (action.equals("[CATEGORY]")) {
                if (isShift) {
                    stats.setSelectedCategory("all");
                } else {
                    List<String> keys = new ArrayList<>(plugin.getConfigManager().getCategories().keySet());
                    if (!keys.isEmpty()) {
                        int idx = keys.indexOf(stats.getSelectedCategory());
                        stats.setSelectedCategory(keys.get((idx + 1) % keys.size()));
                    }
                }
                plugin.getAuctionMenu().openMain(player, 1, holder.getSearchQuery(), holder.getTargetUuid());
            } else if (action.equals("[CURRENCY]")) {
                if (isShift) {
                    stats.setSelectedCurrency(null);
                } else {
                    List<String> ids = new ArrayList<>();
                    ids.add(null);
                    plugin.getCurrencyManager().getAllCurrencies().forEach(c -> ids.add(c.getId()));
                    int idx = ids.indexOf(stats.getSelectedCurrency());
                    stats.setSelectedCurrency(ids.get((idx + 1) % ids.size()));
                }
                plugin.getAuctionMenu().openMain(player, 1, holder.getSearchQuery(), holder.getTargetUuid());
            } else if (action.equals("[SEARCH]")) {
                if (isShift) {
                    plugin.getAuctionMenu().openMain(player, 1, null, holder.getTargetUuid());
                } else {
                    plugin.getSearchInputHandler().promptSearch(player);
                }
            }
        }
    }

    private void reopenCurrent(Player player, MenuHolder holder) {
        switch (holder.getMenuType()) {
            case "my-items" -> plugin.getAuctionMenu().openMyItems(player, holder.getPage());
            case "expired-items" -> plugin.getAuctionMenu().openExpiredItems(player, holder.getPage());
            case "history", "history-admin" -> plugin.getAuctionMenu().openHistory(player, holder.getTargetUuid(), holder.getPage(), holder.getMenuType().equals("history-admin"));
            case "deleted", "deleted-admin" -> plugin.getAuctionMenu().openDeleted(player, holder.getTargetUuid(), holder.getPage(), holder.getMenuType().equals("deleted-admin"));
            case "logs" -> plugin.getAuctionMenu().openLogs(player, holder.getTargetUuid(), holder.getPage());
            default -> plugin.getAuctionMenu().openMain(player, holder.getPage(), holder.getSearchQuery(), holder.getTargetUuid());
        }
    }
}
