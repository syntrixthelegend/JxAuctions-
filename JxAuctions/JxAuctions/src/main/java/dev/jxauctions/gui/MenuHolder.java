package dev.jxauctions.gui;

import dev.jxauctions.model.AuctionItem;
import dev.jxauctions.model.HistoryEntry;
import dev.jxauctions.model.LogEntry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MenuHolder implements InventoryHolder {

    private Inventory inventory;
    private final String menuType;
    private int page;
    private UUID targetUuid;
    private String searchQuery;
    private AuctionItem selectedAuction;
    private ItemStack pendingSellItem;
    private double pendingSellPrice;
    private int pendingSellAmount;
    private String pendingSellCurrencyId;
    private String returnMenu = "main";
    private boolean consumed = false;

    private final Map<Integer, AuctionItem> auctionSlots = new HashMap<>();
    private final Map<Integer, HistoryEntry> historySlots = new HashMap<>();
    private final Map<Integer, LogEntry> logSlots = new HashMap<>();
    private final Map<Integer, List<String>> actionSlots = new HashMap<>();
    private final Map<Integer, String> categorySlots = new HashMap<>();
    private final Map<Integer, String> currencySlots = new HashMap<>();

    public MenuHolder(String menuType, int page) {
        this.menuType = menuType;
        this.page = page;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public String getMenuType() { return menuType; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public UUID getTargetUuid() { return targetUuid; }
    public void setTargetUuid(UUID targetUuid) { this.targetUuid = targetUuid; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public AuctionItem getSelectedAuction() { return selectedAuction; }
    public void setSelectedAuction(AuctionItem selectedAuction) { this.selectedAuction = selectedAuction; }

    public ItemStack getPendingSellItem() { return pendingSellItem; }
    public void setPendingSellItem(ItemStack pendingSellItem) { this.pendingSellItem = pendingSellItem; }

    public double getPendingSellPrice() { return pendingSellPrice; }
    public void setPendingSellPrice(double pendingSellPrice) { this.pendingSellPrice = pendingSellPrice; }

    public int getPendingSellAmount() { return pendingSellAmount; }
    public void setPendingSellAmount(int pendingSellAmount) { this.pendingSellAmount = pendingSellAmount; }

    public String getPendingSellCurrencyId() { return pendingSellCurrencyId; }
    public void setPendingSellCurrencyId(String pendingSellCurrencyId) { this.pendingSellCurrencyId = pendingSellCurrencyId; }

    public String getReturnMenu() { return returnMenu; }
    public void setReturnMenu(String returnMenu) { this.returnMenu = returnMenu; }
    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean consumed) { this.consumed = consumed; }

    public Map<Integer, AuctionItem> getAuctionSlots() { return auctionSlots; }
    public Map<Integer, HistoryEntry> getHistorySlots() { return historySlots; }
    public Map<Integer, LogEntry> getLogSlots() { return logSlots; }
    public Map<Integer, List<String>> getActionSlots() { return actionSlots; }
    public Map<Integer, String> getCategorySlots() { return categorySlots; }
    public Map<Integer, String> getCurrencySlots() { return currencySlots; }
}
