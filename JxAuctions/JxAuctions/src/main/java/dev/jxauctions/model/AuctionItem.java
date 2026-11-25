package dev.jxauctions.model;

import dev.jxauctions.util.ColorUtil;
import dev.jxauctions.util.ItemSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.UUID;

public class AuctionItem {

    private int id;
    private final UUID uuid;
    private final UUID sellerUuid;
    private final String sellerName;
    private final String serializedItem;
    private transient ItemStack cachedItem;
    private transient String searchIndex;
    private final double price;
    private final String currencyId;
    private final long createdAt;
    private long expiresAt;
    private long deletedAt;
    private volatile Status status;
    private final String serverName;

    public enum Status {
        ACTIVE,
        EXPIRED,
        DELETED
    }

    public AuctionItem(int id, UUID uuid, UUID sellerUuid, String sellerName, String serializedItem,
                       double price, String currencyId, long createdAt, long expiresAt, long deletedAt,
                       Status status, String serverName) {
        this.id = id;
        this.uuid = uuid;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.serializedItem = serializedItem;
        this.price = price;
        this.currencyId = currencyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.deletedAt = deletedAt;
        this.status = status;
        this.serverName = serverName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public UUID getUuid() { return uuid; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public String getSerializedItem() { return serializedItem; }

    public ItemStack getItemStack() {
        if (cachedItem == null) {
            cachedItem = ItemSerializer.fromBase64(serializedItem);
        }
        return cachedItem != null ? cachedItem.clone() : null;
    }

    public String getSearchIndex() {
        if (searchIndex == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(sellerName.toLowerCase(Locale.ROOT)).append('\n');
            appendItemToIndex(sb, getItemStack(), 0);
            searchIndex = sb.toString();
        }
        return searchIndex;
    }

    private void appendItemToIndex(StringBuilder sb, ItemStack stack, int depth) {
        if (stack == null || depth > 2) return;
        sb.append(stack.getType().name().toLowerCase(Locale.ROOT)).append('\n');
        sb.append(ItemSerializer.getDisplayName(stack).toLowerCase(Locale.ROOT)).append('\n');
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    sb.append(ColorUtil.stripColor(line).toLowerCase(Locale.ROOT)).append('\n');
                }
            }
            for (Enchantment ench : meta.getEnchants().keySet()) {
                sb.append(ench.getKey().getKey().toLowerCase(Locale.ROOT)).append('\n');
            }
        }
        if (ItemSerializer.isContainer(stack)) {
            for (ItemStack inner : ItemSerializer.getContainerContents(stack)) {
                appendItemToIndex(sb, inner, depth + 1);
            }
        }
    }

    public double getPrice() { return price; }
    public String getCurrencyId() { return currencyId; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getServerName() { return serverName; }

    public boolean isExpired(long now) {
        return status == Status.EXPIRED || (status == Status.ACTIVE && now >= expiresAt);
    }
}
