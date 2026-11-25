package dev.jxauctions.api.events;

import dev.jxauctions.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AuctionBuyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player buyer;
    private final AuctionItem auction;
    private boolean cancelled;

    public AuctionBuyEvent(Player buyer, AuctionItem auction) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.buyer = buyer;
        this.auction = auction;
    }

    public Player getBuyer() { return buyer; }
    public AuctionItem getAuction() { return auction; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
