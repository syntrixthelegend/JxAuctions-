package dev.jxauctions.command;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.model.PlayerStats;
import dev.jxauctions.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final JxAuctions plugin;

    public AdminCommand(JxAuctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("jxauctions.admin")) {
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
            return true;
        }

        if (args.length == 0) {
            for (String line : plugin.getConfigManager().getMessageList("admin-help")) {
                sender.sendMessage(line);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("jxauctions.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (plugin.getConfigManager().loadAll()) {
                    plugin.getCurrencyManager().loadCurrencies();
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("reload.success"));
                } else {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("reload.failed"));
                }
            }
            case "forceopen" -> {
                if (!sender.hasPermission("jxauctions.forceopen")) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.missing-argument").replace("%value%", "player"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.invalid-player").replace("%player%", args[1]));
                    return true;
                }
                String menu = args.length >= 3 ? args[2] : "main";
                plugin.getAuctionMenu().openMenuByName(target, menu, true);
            }
            case "history" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.player-only"));
                    return true;
                }
                if (!player.hasPermission("jxauctions.history.admin")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    plugin.getAuctionMenu().openHistory(player, null, 1, true);
                } else {
                    Player online = Bukkit.getPlayerExact(args[1]);
                    if (online != null) {
                        plugin.getAuctionMenu().openHistory(player, online.getUniqueId(), 1, true);
                    } else {
                        String targetName = args[1];
                        SchedulerUtil.runAsync(plugin, () -> {
                            UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                            SchedulerUtil.runEntity(plugin, player, () ->
                                    plugin.getAuctionMenu().openHistory(player, targetUuid, 1, true));
                        });
                    }
                }
            }
            case "deleted" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.player-only"));
                    return true;
                }
                if (!player.hasPermission("jxauctions.deleted.admin")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    plugin.getAuctionMenu().openDeleted(player, null, 1, true);
                } else {
                    Player online = Bukkit.getPlayerExact(args[1]);
                    if (online != null) {
                        plugin.getAuctionMenu().openDeleted(player, online.getUniqueId(), 1, true);
                    } else {
                        String targetName = args[1];
                        SchedulerUtil.runAsync(plugin, () -> {
                            UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                            SchedulerUtil.runEntity(plugin, player, () ->
                                    plugin.getAuctionMenu().openDeleted(player, targetUuid, 1, true));
                        });
                    }
                }
            }
            case "logs" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.player-only"));
                    return true;
                }
                if (!player.hasPermission("jxauctions.logs.admin")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    plugin.getAuctionMenu().openLogs(player, null, 1);
                } else {
                    Player online = Bukkit.getPlayerExact(args[1]);
                    if (online != null) {
                        plugin.getAuctionMenu().openLogs(player, online.getUniqueId(), 1);
                    } else {
                        String targetName = args[1];
                        SchedulerUtil.runAsync(plugin, () -> {
                            UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                            SchedulerUtil.runEntity(plugin, player, () ->
                                    plugin.getAuctionMenu().openLogs(player, targetUuid, 1));
                        });
                    }
                }
            }
            case "limit" -> {
                if (!sender.hasPermission("jxauctions.limit")) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.missing-argument").replace("%value%", "action/player"));
                    return true;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                String targetName = args[2];
                int parsedAmount = 0;
                if (!action.equals("reset")) {
                    if (!action.equals("set") && !action.equals("give") && !action.equals("take")) {
                        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.invalid-value").replace("%value%", action));
                        return true;
                    }
                    if (args.length < 4) {
                        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.missing-argument").replace("%value%", "amount"));
                        return true;
                    }
                    try {
                        parsedAmount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.invalid-value").replace("%value%", args[3]));
                        return true;
                    }
                }
                final int amount = parsedAmount;
                SchedulerUtil.runAsync(plugin, () -> {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    PlayerStats stats = plugin.getDatabaseManager().loadPlayerStats(target.getUniqueId(),
                            target.getName() != null ? target.getName() : targetName);
                    switch (action) {
                        case "reset" -> stats.setDbLimitOffset(0);
                        case "set" -> stats.setDbLimitOffset(amount);
                        case "give" -> stats.setDbLimitOffset(stats.getDbLimitOffset() + amount);
                        case "take" -> stats.setDbLimitOffset(Math.max(0, stats.getDbLimitOffset() - amount));
                    }
                    plugin.getDatabaseManager().savePlayerData(stats);
                    int updatedOffset = stats.getDbLimitOffset();
                    SchedulerUtil.runSync(plugin, () -> {
                        Player online = Bukkit.getPlayer(target.getUniqueId());
                        if (online != null && online.isOnline()) {
                            plugin.getAuctionManager().getPlayerStats(online).setDbLimitOffset(updatedOffset);
                        }
                        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("limit-changed")
                                .replace("%player%", targetName)
                                .replace("%amount%", String.valueOf(updatedOffset)));
                    });
                });
            }
            case "convert" -> {
                if (!sender.hasPermission("jxauctions.convert")) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.missing-argument").replace("%value%", "plugin"));
                    return true;
                }
                sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("converting"));
                plugin.getConverterManager().convert(args[1], sender);
            }
            default -> {
                for (String line : plugin.getConfigManager().getMessageList("admin-help")) {
                    sender.sendMessage(line);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.addAll(List.of("reload", "forceopen", "history", "deleted", "logs", "limit", "convert"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "limit" -> list.addAll(List.of("set", "give", "take", "reset"));
                case "convert" -> list.addAll(List.of("AuctionHouse", "zAuctionHouseV3", "CrazyAuctions", "PlayerAuctions"));
                case "forceopen", "history", "deleted", "logs" -> Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("forceopen")) {
                list.addAll(List.of("main", "category", "my-items", "expired-items", "history", "deleted"));
            } else if (args[0].equalsIgnoreCase("limit")) {
                Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
