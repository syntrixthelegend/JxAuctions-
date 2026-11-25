package dev.jxauctions.command;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.currency.Currency;
import dev.jxauctions.util.NumberUtil;
import dev.jxauctions.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final JxAuctions plugin;

    public AuctionCommand(JxAuctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.player-only"));
            return true;
        }

        if (!player.hasPermission("jxauctions.use")) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("jxauctions.open")) {
                player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                return true;
            }
            String defaultMenu = plugin.getConfigManager().getConfig().getString("default-menu", "main");
            plugin.getAuctionMenu().openMenuByName(player, defaultMenu);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open" -> {
                if (!player.hasPermission("jxauctions.open")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                String menu = args.length >= 2 ? args[1] : plugin.getConfigManager().getConfig().getString("default-menu", "main");
                plugin.getAuctionMenu().openMenuByName(player, menu);
            }
            case "sell" -> {
                if (!player.hasPermission("jxauctions.sell")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.missing-argument").replace("%value%", "price"));
                    return true;
                }
                double price = NumberUtil.parseAmount(args[1]);
                if (price <= 0) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.invalid-price"));
                    return true;
                }

                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.no-item"));
                    return true;
                }

                int amount = hand.getAmount();
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.invalid-value").replace("%value%", args[2]));
                        return true;
                    }
                }

                Currency currency = null;
                if (args.length >= 4) {
                    currency = plugin.getCurrencyManager().getCurrency(args[3]);
                    if (currency == null) {
                        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.invalid-value").replace("%value%", args[3]));
                        return true;
                    }
                }

                if (currency == null) {
                    var all = plugin.getCurrencyManager().getAllCurrencies();
                    boolean forceSelector = plugin.getConfigManager().getConfig().getBoolean("force-currency-selector", false);
                    boolean enableSelector = plugin.getConfigManager().getConfig().getBoolean("enable-currency-selector", true);
                    if (enableSelector && (all.size() > 1 || forceSelector)) {
                        plugin.getAuctionMenu().openCurrencySelector(player, hand, price, amount);
                        return true;
                    }
                    currency = plugin.getCurrencyManager().getDefaultCurrency();
                    if (currency == null) {
                        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("sell.no-currency"));
                        return true;
                    }
                }

                if (plugin.getConfigManager().getConfig().getBoolean("confirm-listing", false)) {
                    plugin.getAuctionMenu().openConfirmListing(player, hand, price, amount, currency);
                } else {
                    plugin.getAuctionManager().listItem(player, hand, price, amount, currency);
                }
            }
            case "search" -> {
                if (!player.hasPermission("jxauctions.search")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length >= 2) {
                    String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    plugin.getAuctionMenu().openMain(player, 1, query, null);
                } else {
                    plugin.getSearchInputHandler().promptSearch(player);
                }
            }
            case "view" -> {
                if (!player.hasPermission("jxauctions.view")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    plugin.getAuctionMenu().openMyItems(player, 1);
                } else {
                    Player online = Bukkit.getPlayerExact(args[1]);
                    if (online != null) {
                        plugin.getAuctionMenu().openMain(player, 1, null, online.getUniqueId());
                    } else {
                        String targetName = args[1];
                        SchedulerUtil.runAsync(plugin, () -> {
                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                            SchedulerUtil.runEntity(plugin, player, () ->
                                    plugin.getAuctionMenu().openMain(player, 1, null, target.getUniqueId()));
                        });
                    }
                }
            }
            case "history" -> {
                if (!player.hasPermission("jxauctions.history")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                plugin.getAuctionMenu().openHistory(player, player.getUniqueId(), 1, false);
            }
            case "deleted" -> {
                if (!player.hasPermission("jxauctions.deleted")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                plugin.getAuctionMenu().openDeleted(player, player.getUniqueId(), 1, false);
            }
            default -> {
                for (String line : plugin.getConfigManager().getMessageList("help")) {
                    player.sendMessage(line);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("open", "sell", "search", "view", "history", "deleted", "help"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "open" -> completions.addAll(List.of("main", "category", "my-items", "expired-items", "history", "deleted"));
                case "sell" -> completions.addAll(List.of("100", "1k", "10k", "100k", "1m"));
                case "view" -> Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("sell")) {
            plugin.getCurrencyManager().getAllCurrencies().forEach(c -> completions.add(c.getId()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
