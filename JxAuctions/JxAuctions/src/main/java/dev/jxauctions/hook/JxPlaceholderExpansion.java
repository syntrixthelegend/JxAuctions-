package dev.jxauctions.hook;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.currency.Currency;
import dev.jxauctions.model.AuctionItem;
import dev.jxauctions.model.Category;
import dev.jxauctions.model.PlayerStats;
import dev.jxauctions.util.NumberUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class JxPlaceholderExpansion extends PlaceholderExpansion {

    private final JxAuctions plugin;

    public JxPlaceholderExpansion(JxAuctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "jxauctions"; }

    @Override
    public String getAuthor() { return "JxAuctions"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline() || offlinePlayer.getPlayer() == null) {
            return "";
        }
        Player player = offlinePlayer.getPlayer();
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        int mode = cfg.getInt("number-formatting.mode", 0);
        String pattern = cfg.getString("number-formatting.formatted", "#,###.##");

        return switch (params.toLowerCase()) {
            case "expired_count" -> NumberUtil.format(plugin.getAuctionManager().getPlayerAuctionCount(player.getUniqueId(), AuctionItem.Status.EXPIRED), mode, pattern);
            case "expired_count_raw" -> String.valueOf(plugin.getAuctionManager().getPlayerAuctionCount(player.getUniqueId(), AuctionItem.Status.EXPIRED));
            case "active_count", "sell_count", "purchasable_count" -> NumberUtil.format(plugin.getAuctionManager().getPlayerAuctionCount(player.getUniqueId(), AuctionItem.Status.ACTIVE), mode, pattern);
            case "active_count_raw", "sell_count_raw", "purchasable_count_raw" -> String.valueOf(plugin.getAuctionManager().getPlayerAuctionCount(player.getUniqueId(), AuctionItem.Status.ACTIVE));
            case "total_active_count" -> NumberUtil.format(plugin.getAuctionManager().getTotalActiveCount(), mode, pattern);
            case "total_active_count_raw" -> String.valueOf(plugin.getAuctionManager().getTotalActiveCount());
            case "sell_limit" -> {
                int lim = plugin.getAuctionManager().getSellLimit(player);
                yield lim == Integer.MAX_VALUE ? plugin.getConfigManager().getLangConfig().getString("limit-bypass-icon", "∞") : NumberUtil.format(lim, mode, pattern);
            }
            case "sell_limit_raw" -> String.valueOf(plugin.getAuctionManager().getSellLimit(player));
            case "selected_sorting" -> plugin.getConfigManager().getLangConfig().getString("sorting." + stats.getSelectedSorting().getId(), stats.getSelectedSorting().getId());
            case "selected_sorting_raw" -> stats.getSelectedSorting().getId();
            case "selected_category" -> {
                Category c = plugin.getConfigManager().getCategories().get(stats.getSelectedCategory());
                yield c != null ? c.getDisplayName() : "All";
            }
            case "selected_category_raw" -> stats.getSelectedCategory();
            case "selected_currency" -> {
                Currency c = stats.getSelectedCurrency() != null ? plugin.getCurrencyManager().getCurrency(stats.getSelectedCurrency()) : null;
                yield c != null ? c.getDisplayRaw() : plugin.getConfigManager().getLangConfig().getString("currency.no-currency", "All");
            }
            case "selected_currency_raw" -> stats.getSelectedCurrency() != null ? stats.getSelectedCurrency() : "all";
            case "categories_enabled" -> String.valueOf(plugin.getConfigManager().isCategoriesEnabled());
            case "expired_items_enabled" -> String.valueOf(cfg.getBoolean("separate-expired-items", false));
            case "auction_listing_confirmation_enabled" -> String.valueOf(cfg.getBoolean("confirm-listing", false));
            case "items_sold" -> NumberUtil.format(stats.getTotalItemsSold(), mode, pattern);
            case "items_sold_raw" -> String.valueOf(stats.getTotalItemsSold());
            case "items_purchased" -> NumberUtil.format(stats.getTotalItemsPurchased(), mode, pattern);
            case "items_purchased_raw" -> String.valueOf(stats.getTotalItemsPurchased());
            case "money_made" -> NumberUtil.format(stats.getTotalMoneyMade(), mode, pattern);
            case "money_made_raw" -> String.valueOf(stats.getTotalMoneyMade());
            case "money_spent" -> NumberUtil.format(stats.getTotalMoneySpent(), mode, pattern);
            case "money_spent_raw" -> String.valueOf(stats.getTotalMoneySpent());
            case "total_items_sold" -> NumberUtil.format(plugin.getAuctionManager().getTotalItemsSoldGlobal(), mode, pattern);
            case "total_items_sold_raw" -> String.valueOf(plugin.getAuctionManager().getTotalItemsSoldGlobal());
            case "total_items_purchased" -> NumberUtil.format(plugin.getAuctionManager().getTotalItemsPurchasedGlobal(), mode, pattern);
            case "total_items_purchased_raw" -> String.valueOf(plugin.getAuctionManager().getTotalItemsPurchasedGlobal());
            case "total_money_made" -> NumberUtil.format(plugin.getAuctionManager().getTotalMoneyMadeGlobal(), mode, pattern);
            case "total_money_made_raw" -> String.valueOf(plugin.getAuctionManager().getTotalMoneyMadeGlobal());
            case "total_money_spent" -> NumberUtil.format(plugin.getAuctionManager().getTotalMoneySpentGlobal(), mode, pattern);
            case "total_money_spent_raw" -> String.valueOf(plugin.getAuctionManager().getTotalMoneySpentGlobal());
            default -> handleCurrencySpecific(params, stats, mode, pattern);
        };
    }

    private String handleCurrencySpecific(String params, PlayerStats stats, int mode, String pattern) {
        if (params.startsWith("items_sold_")) {
            boolean raw = params.endsWith("_raw");
            String curr = params.substring("items_sold_".length(), raw ? params.length() - 4 : params.length());
            int val = stats.getItemsSold(curr);
            return raw ? String.valueOf(val) : NumberUtil.format(val, mode, pattern);
        }
        if (params.startsWith("items_purchased_")) {
            boolean raw = params.endsWith("_raw");
            String curr = params.substring("items_purchased_".length(), raw ? params.length() - 4 : params.length());
            int val = stats.getItemsPurchased(curr);
            return raw ? String.valueOf(val) : NumberUtil.format(val, mode, pattern);
        }
        if (params.startsWith("money_made_")) {
            boolean raw = params.endsWith("_raw");
            String curr = params.substring("money_made_".length(), raw ? params.length() - 4 : params.length());
            double val = stats.getMoneyMade(curr);
            return raw ? String.valueOf(val) : NumberUtil.format(val, mode, pattern);
        }
        if (params.startsWith("money_spent_")) {
            boolean raw = params.endsWith("_raw");
            String curr = params.substring("money_spent_".length(), raw ? params.length() - 4 : params.length());
            double val = stats.getMoneySpent(curr);
            return raw ? String.valueOf(val) : NumberUtil.format(val, mode, pattern);
        }
        return null;
    }
}
