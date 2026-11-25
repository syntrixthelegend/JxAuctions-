package dev.jxauctions.api.events;

import dev.jxauctions.model.AuctionItem;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AuctionExpireEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final AuctionItem auction;

    public AuctionExpireEvent(AuctionItem auction) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.auction = auction;
    }

    public AuctionItem getAuction() { return auction; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
