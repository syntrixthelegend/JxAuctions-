package dev.jxauctions.api.events;

import dev.jxauctions.currency.Currency;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class AuctionListEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player seller;
    private final ItemStack item;
    private double price;
    private final Currency currency;
    private boolean cancelled;

    public AuctionListEvent(Player seller, ItemStack item, double price, Currency currency) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.seller = seller;
        this.item = item;
        this.price = price;
        this.currency = currency;
    }

    public Player getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public Currency getCurrency() { return currency; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
