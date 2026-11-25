package dev.jxauctions.api.events;

import dev.jxauctions.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AuctionCancelEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player actor;
    private final AuctionItem auction;
    private final boolean adminAction;

    public AuctionCancelEvent(Player actor, AuctionItem auction, boolean adminAction) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.actor = actor;
        this.auction = auction;
        this.adminAction = adminAction;
    }

    public Player getActor() { return actor; }
    public AuctionItem getAuction() { return auction; }
    public boolean isAdminAction() { return adminAction; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
