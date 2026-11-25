package dev.jxauctions.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStats {

    private final UUID uuid;
    private String lastKnownName;
    private volatile boolean loaded = false;
    private int dbLimitOffset;
    private SortType selectedSorting = SortType.NEWEST;
    private String selectedCategory = "all";
    private String selectedCurrency = null;

    private final Map<String, Integer> itemsSoldByCurrency = new ConcurrentHashMap<>();
    private final Map<String, Integer> itemsPurchasedByCurrency = new ConcurrentHashMap<>();
    private final Map<String, Double> moneyMadeByCurrency = new ConcurrentHashMap<>();
    private final Map<String, Double> moneySpentByCurrency = new ConcurrentHashMap<>();

    public PlayerStats(UUID uuid, String lastKnownName) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
    }

    public UUID getUuid() { return uuid; }
    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String lastKnownName) { this.lastKnownName = lastKnownName; }

    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }

    public int getDbLimitOffset() { return dbLimitOffset; }
    public void setDbLimitOffset(int dbLimitOffset) { this.dbLimitOffset = dbLimitOffset; }

    public SortType getSelectedSorting() { return selectedSorting; }
    public void setSelectedSorting(SortType selectedSorting) { this.selectedSorting = selectedSorting; }

    public String getSelectedCategory() { return selectedCategory; }
    public void setSelectedCategory(String selectedCategory) { this.selectedCategory = selectedCategory; }

    public String getSelectedCurrency() { return selectedCurrency; }
    public void setSelectedCurrency(String selectedCurrency) { this.selectedCurrency = selectedCurrency; }

    public void addSale(String currencyId, double amount) {
        itemsSoldByCurrency.merge(currencyId, 1, Integer::sum);
        moneyMadeByCurrency.merge(currencyId, amount, Double::sum);
    }

    public void addPurchase(String currencyId, double amount) {
        itemsPurchasedByCurrency.merge(currencyId, 1, Integer::sum);
        moneySpentByCurrency.merge(currencyId, amount, Double::sum);
    }

    public void setStat(String currencyId, int sold, int purchased, double made, double spent) {
        itemsSoldByCurrency.put(currencyId, sold);
        itemsPurchasedByCurrency.put(currencyId, purchased);
        moneyMadeByCurrency.put(currencyId, made);
        moneySpentByCurrency.put(currencyId, spent);
    }

    public int getTotalItemsSold() { return itemsSoldByCurrency.values().stream().mapToInt(Integer::intValue).sum(); }
    public int getItemsSold(String currencyId) { return itemsSoldByCurrency.getOrDefault(currencyId, 0); }
    public int getTotalItemsPurchased() { return itemsPurchasedByCurrency.values().stream().mapToInt(Integer::intValue).sum(); }
    public int getItemsPurchased(String currencyId) { return itemsPurchasedByCurrency.getOrDefault(currencyId, 0); }
    public double getTotalMoneyMade() { return moneyMadeByCurrency.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public double getMoneyMade(String currencyId) { return moneyMadeByCurrency.getOrDefault(currencyId, 0.0); }
    public double getTotalMoneySpent() { return moneySpentByCurrency.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public double getMoneySpent(String currencyId) { return moneySpentByCurrency.getOrDefault(currencyId, 0.0); }
}
