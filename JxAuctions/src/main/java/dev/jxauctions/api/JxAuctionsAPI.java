package dev.jxauctions.api;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.currency.Currency;
import dev.jxauctions.model.AuctionItem;
import dev.jxauctions.model.PlayerStats;
import dev.jxauctions.model.SortType;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class JxAuctionsAPI {

    private JxAuctionsAPI() {}

    public static List<AuctionItem> getActiveAuctions(String categoryId, String currencyId, String search, SortType sortType) {
        return JxAuctions.getInstance().getAuctionManager().getFilteredAuctions(categoryId, currencyId, search, sortType);
    }

    public static List<AuctionItem> getPlayerAuctions(UUID playerUuid, boolean expiredOnly) {
        return JxAuctions.getInstance().getAuctionManager().getPlayerItems(playerUuid, expiredOnly, false);
    }

    public static PlayerStats getPlayerStats(Player player) {
        return JxAuctions.getInstance().getAuctionManager().getPlayerStats(player);
    }

    public static int getSellLimit(Player player) {
        return JxAuctions.getInstance().getAuctionManager().getSellLimit(player);
    }

    public static Collection<Currency> getRegisteredCurrencies() {
        return JxAuctions.getInstance().getCurrencyManager().getAllCurrencies();
    }

    public static Currency getCurrency(String id) {
        return JxAuctions.getInstance().getCurrencyManager().getCurrency(id);
    }
}
