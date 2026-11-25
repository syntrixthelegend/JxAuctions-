package dev.jxauctions.model;

import dev.jxauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class HistoryEntry {

    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final UUID buyerUuid;
    private final String buyerName;
    private final String serializedItem;
    private transient ItemStack cachedItem;
    private final double price;
    private final String currencyId;
    private final long soldAt;

    public HistoryEntry(int id, UUID sellerUuid, String sellerName, UUID buyerUuid, String buyerName,
                        String serializedItem, double price, String currencyId, long soldAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.serializedItem = serializedItem;
        this.price = price;
        this.currencyId = currencyId;
        this.soldAt = soldAt;
    }

    public int getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public UUID getBuyerUuid() { return buyerUuid; }
    public String getBuyerName() { return buyerName; }
    public String getSerializedItem() { return serializedItem; }

    public ItemStack getItemStack() {
        if (cachedItem == null) {
            cachedItem = ItemSerializer.fromBase64(serializedItem);
        }
        return cachedItem != null ? cachedItem.clone() : null;
    }

    public double getPrice() { return price; }
    public String getCurrencyId() { return currencyId; }
    public long getSoldAt() { return soldAt; }
}
