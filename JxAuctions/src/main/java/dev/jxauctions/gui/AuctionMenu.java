package dev.jxauctions.gui;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.config.ItemBuilder;
import dev.jxauctions.currency.Currency;
import dev.jxauctions.model.*;
import dev.jxauctions.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AuctionMenu {

    private final JxAuctions plugin;

    public AuctionMenu(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void openMenuByName(Player player, String menuName) {
        openMenuByName(player, menuName, false);
    }

    public void openMenuByName(Player player, String menuName, boolean bypassPermission) {
        switch (menuName.toLowerCase(Locale.ROOT)) {
            case "category" -> {
                if (!bypassPermission && !player.hasPermission("jxauctions.open")) { player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission")); return; }
                openCategorySelector(player);
            }
            case "my-items" -> {
                if (!bypassPermission && !player.hasPermission("jxauctions.open")) { player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission")); return; }
                openMyItems(player, 1);
            }
            case "expired-items" -> {
                if (!bypassPermission && !player.hasPermission("jxauctions.open")) { player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission")); return; }
                openExpiredItems(player, 1);
            }
            case "history" -> {
                if (!bypassPermission && !player.hasPermission("jxauctions.history")) { player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission")); return; }
                openHistory(player, player.getUniqueId(), 1, false);
            }
            case "deleted" -> {
                if (!bypassPermission && !player.hasPermission("jxauctions.deleted")) { player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission")); return; }
                openDeleted(player, player.getUniqueId(), 1, false);
            }
            default -> {
                if (!bypassPermission && !player.hasPermission("jxauctions.open")) { player.sendMessage(plugin.getConfigManager().getPrefixedMessage("commands.no-permission")); return; }
                openMain(player, 1, null, null);
            }
        }
    }

    public void openMain(Player player, int page, String searchQuery, UUID filterSeller) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("main");
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);

        List<AuctionItem> items;
        if (filterSeller != null) {
            items = plugin.getAuctionManager().getPlayerItems(filterSeller, false, false);
        } else {
            items = plugin.getAuctionManager().getFilteredAuctions(
                    stats.getSelectedCategory(),
                    stats.getSelectedCurrency(),
                    searchQuery,
                    stats.getSelectedSorting()
            );
        }

        int pageSize = Math.max(1, gui.getInt("page-size", 36));
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / pageSize));
        int safePage = Math.max(1, Math.min(page, maxPage));

        Map<String, String> placeholders = buildPlaceholders(player, stats, safePage, maxPage, searchQuery);
        String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0Auction House"), placeholders));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));

        MenuHolder holder = new MenuHolder("main", safePage);
        holder.setSearchQuery(searchQuery);
        holder.setTargetUuid(filterSeller);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        populateStaticItems(inv, gui, holder, placeholders);

        int start = (safePage - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        int idx = 0;
        for (int slot = 0; slot < inv.getSize() && idx < (end - start); slot++) {
            if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) continue;
            AuctionItem auction = items.get(start + idx);
            inv.setItem(slot, buildAuctionDisplayItem(player, auction, false));
            holder.getAuctionSlots().put(slot, auction);
            idx++;
        }

        player.openInventory(inv);
        scheduleAutoUpdate(player, holder, gui.getLong("auto-update-ticks", -1L), () -> openMain(player, safePage, searchQuery, filterSeller));
    }

    public void openCategorySelector(Player player) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("category");
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
        Map<String, String> placeholders = buildPlaceholders(player, stats, 1, 1, null);

        String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0Category Selector"), placeholders));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 4)));

        MenuHolder holder = new MenuHolder("category", 1);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        populateStaticItems(inv, gui, holder, placeholders);
        for (String key : gui.getKeys(false)) {
            ConfigurationSection sec = gui.getConfigurationSection(key);
            if (sec != null && sec.contains("category")) {
                for (int slot : ItemBuilder.parseSlots(sec)) {
                    holder.getCategorySlots().put(slot, sec.getString("category"));
                }
            }
        }
        player.openInventory(inv);
    }

    public void openMyItems(Player player, int page) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("my-items");
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
        boolean separateExpired = plugin.getConfigManager().getConfig().getBoolean("separate-expired-items", false);

        List<AuctionItem> items = plugin.getAuctionManager().getPlayerItems(player.getUniqueId(), false, !separateExpired);
        int pageSize = Math.max(1, gui.getInt("page-size", 36));
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / pageSize));
        int safePage = Math.max(1, Math.min(page, maxPage));

        Map<String, String> placeholders = buildPlaceholders(player, stats, safePage, maxPage, null);
        String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0My Items"), placeholders));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));

        MenuHolder holder = new MenuHolder("my-items", safePage);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, placeholders);

        int start = (safePage - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        int idx = 0;
        for (int slot = 0; slot < inv.getSize() && idx < (end - start); slot++) {
            if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) continue;
            AuctionItem auction = items.get(start + idx);
            inv.setItem(slot, buildAuctionDisplayItem(player, auction, auction.getStatus() == AuctionItem.Status.EXPIRED));
            holder.getAuctionSlots().put(slot, auction);
            idx++;
        }

        player.openInventory(inv);
        scheduleAutoUpdate(player, holder, gui.getLong("auto-update-ticks", -1L), () -> openMyItems(player, safePage));
    }

    public void openExpiredItems(Player player, int page) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("expired-items");
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
        List<AuctionItem> items = plugin.getAuctionManager().getPlayerItems(player.getUniqueId(), true, false);

        int pageSize = Math.max(1, gui.getInt("page-size", 36));
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / pageSize));
        int safePage = Math.max(1, Math.min(page, maxPage));

        Map<String, String> placeholders = buildPlaceholders(player, stats, safePage, maxPage, null);
        String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0Expired Items"), placeholders));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));

        MenuHolder holder = new MenuHolder("expired-items", safePage);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, placeholders);

        int start = (safePage - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        int idx = 0;
        for (int slot = 0; slot < inv.getSize() && idx < (end - start); slot++) {
            if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) continue;
            AuctionItem auction = items.get(start + idx);
            inv.setItem(slot, buildAuctionDisplayItem(player, auction, true));
            holder.getAuctionSlots().put(slot, auction);
            idx++;
        }

        player.openInventory(inv);
        scheduleAutoUpdate(player, holder, gui.getLong("auto-update-ticks", -1L), () -> openExpiredItems(player, safePage));
    }

    public void openHistory(Player player, UUID targetUuid, int page, boolean adminMode) {
        boolean showGlobal = plugin.getConfigManager().getConfig().getBoolean("show-global-history", false);
        int maxLen = plugin.getConfigManager().getConfig().getInt("max-history-length", -1);
        UUID queryUuid = (adminMode && targetUuid == null) || showGlobal ? null : targetUuid;

        plugin.getDatabaseManager().loadHistory(queryUuid, maxLen).thenAccept(entries ->
                SchedulerUtil.runEntity(plugin, player, () -> {
                    FileConfiguration gui = plugin.getConfigManager().getGuiConfig("history");
                    PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);

                    int pageSize = Math.max(1, gui.getInt("page-size", 36));
                    int maxPage = Math.max(1, (int) Math.ceil((double) entries.size() / pageSize));
                    int safePage = Math.max(1, Math.min(page, maxPage));

                    Map<String, String> placeholders = buildPlaceholders(player, stats, safePage, maxPage, null);
                    String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0Auction History"), placeholders));
                    int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));

                    MenuHolder holder = new MenuHolder(adminMode ? "history-admin" : "history", safePage);
                    holder.setTargetUuid(targetUuid);
                    Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
                    holder.setInventory(inv);
                    populateStaticItems(inv, gui, holder, placeholders);

                    String datePattern = plugin.getAuctionManager().getDateFormat();
                    List<String> loreTemplate = plugin.getConfigManager().getLangConfig().getStringList(
                            adminMode && player.hasPermission("jxauctions.history.admin.take") ? "history-admin-item-lore" : "history-item-lore");

                    int start = (safePage - 1) * pageSize;
                    int end = Math.min(start + pageSize, entries.size());
                    int idx = 0;
                    for (int slot = 0; slot < inv.getSize() && idx < (end - start); slot++) {
                        if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) continue;
                        HistoryEntry entry = entries.get(start + idx);
                        ItemStack display = applyHistoryLore(entry, loreTemplate, datePattern);
                        inv.setItem(slot, display);
                        holder.getHistorySlots().put(slot, entry);
                        idx++;
                    }
                    player.openInventory(inv);
                })
        );
    }

    public void openDeleted(Player player, UUID targetUuid, int page, boolean adminMode) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("deleted");
        PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
        List<AuctionItem> items = plugin.getAuctionManager().getDeletedItems(adminMode ? targetUuid : player.getUniqueId());

        int pageSize = Math.max(1, gui.getInt("page-size", 36));
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / pageSize));
        int safePage = Math.max(1, Math.min(page, maxPage));

        Map<String, String> placeholders = buildPlaceholders(player, stats, safePage, maxPage, null);
        String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0Deleted Items"), placeholders));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));

        MenuHolder holder = new MenuHolder(adminMode ? "deleted-admin" : "deleted", safePage);
        holder.setTargetUuid(targetUuid);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, placeholders);

        String datePattern = plugin.getAuctionManager().getDateFormat();
        List<String> loreTemplate = plugin.getConfigManager().getLangConfig().getStringList(
                adminMode && player.hasPermission("jxauctions.deleted.admin.take") ? "deleted-gui-admin-item-lore" : "deleted-gui-item-lore");

        int start = (safePage - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        int idx = 0;
        for (int slot = 0; slot < inv.getSize() && idx < (end - start); slot++) {
            if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) continue;
            AuctionItem auction = items.get(start + idx);
            inv.setItem(slot, applyDeletedLore(auction, loreTemplate, datePattern));
            holder.getAuctionSlots().put(slot, auction);
            idx++;
        }
        player.openInventory(inv);
    }

    public void openLogs(Player player, UUID targetUuid, int page) {
        plugin.getDatabaseManager().loadLogs(targetUuid, 200).thenAccept(entries ->
                SchedulerUtil.runEntity(plugin, player, () -> {
                    FileConfiguration gui = plugin.getConfigManager().getGuiConfig("logs");
                    PlayerStats stats = plugin.getAuctionManager().getPlayerStats(player);
                    int pageSize = Math.max(1, gui.getInt("page-size", 36));
                    int maxPage = Math.max(1, (int) Math.ceil((double) entries.size() / pageSize));
                    int safePage = Math.max(1, Math.min(page, maxPage));

                    Map<String, String> placeholders = buildPlaceholders(player, stats, safePage, maxPage, null);
                    String title = ColorUtil.colorize(ItemBuilder.applyPlaceholders(gui.getString("title", "&0Admin Logs"), placeholders));
                    int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));

                    MenuHolder holder = new MenuHolder("logs", safePage);
                    holder.setTargetUuid(targetUuid);
                    Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
                    holder.setInventory(inv);
                    populateStaticItems(inv, gui, holder, placeholders);

                    String datePattern = plugin.getAuctionManager().getDateFormat();
                    int start = (safePage - 1) * pageSize;
                    int end = Math.min(start + pageSize, entries.size());
                    int idx = 0;
                    for (int slot = 0; slot < inv.getSize() && idx < (end - start); slot++) {
                        if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) continue;
                        LogEntry log = entries.get(start + idx);
                        ItemStack paper = new ItemStack(Material.PAPER);
                        ItemMeta meta = paper.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ColorUtil.colorize("&#00DDFF" + log.getAction() + " &7- &f" + log.getActorName()));
                            meta.setLore(ColorUtil.colorize(List.of(
                                    "&7Target: &f" + (log.getTargetName() != null ? log.getTargetName() : "N/A"),
                                    "&7Details: &f" + log.getDetails(),
                                    "&7Time: &f" + TimeUtil.formatDate(log.getTimestamp(), datePattern)
                            )));
                            paper.setItemMeta(meta);
                        }
                        inv.setItem(slot, paper);
                        holder.getLogSlots().put(slot, log);
                        idx++;
                    }
                    player.openInventory(inv);
                })
        );
    }

    public void openConfirmPurchase(Player player, AuctionItem auction) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("confirm-purchase");
        Currency currency = plugin.getCurrencyManager().getCurrency(auction.getCurrencyId());
        String priceFmt = currency != null ? currency.formatGui(auction.getPrice()) : String.valueOf(auction.getPrice());

        Map<String, String> placeholders = Map.of(
                "%price%", priceFmt,
                "%seller%", auction.getSellerName()
        );
        String title = ColorUtil.colorize(gui.getString("title", "&0Confirm Purchase"));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 3)));

        MenuHolder holder = new MenuHolder("confirm-purchase", 1);
        holder.setSelectedAuction(auction);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, placeholders);

        int itemSlot = gui.getInt("item-slot", 13);
        inv.setItem(itemSlot, auction.getItemStack());
        player.openInventory(inv);
    }


    public void openConfirmListing(Player player, ItemStack handItem, double price, int amount, Currency currency) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("confirm-listing");
        String priceFmt = currency.formatGui(price);
        Map<String, String> placeholders = Map.of(
                "%price%", priceFmt,
                "%tax%", String.valueOf(currency.getTaxPercent())
        );
        String title = ColorUtil.colorize(gui.getString("title", "&0Confirm Listing"));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 3)));

        MenuHolder holder = new MenuHolder("confirm-listing", 1);
        holder.setPendingSellItem(handItem.clone());
        holder.setPendingSellPrice(price);
        holder.setPendingSellAmount(amount);
        holder.setPendingSellCurrencyId(currency.getId());

        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, placeholders);

        ItemStack preview = handItem.clone();
        preview.setAmount(Math.min(amount, handItem.getAmount()));
        inv.setItem(gui.getInt("item-slot", 13), preview);
        player.openInventory(inv);
    }

    public void openCurrencySelector(Player player, ItemStack handItem, double price, int amount) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("currency-selector");
        String title = ColorUtil.colorize(gui.getString("title", "&0Select Currency"));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 3)));

        MenuHolder holder = new MenuHolder("currency-selector", 1);
        holder.setPendingSellItem(handItem.clone());
        holder.setPendingSellPrice(price);
        holder.setPendingSellAmount(amount);

        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, Map.of());

        Material fillerMat = Material.matchMaterial(gui.getString("filler.material", "LIGHT_BLUE_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT));
        int slot = 10;
        for (Currency currency : plugin.getCurrencyManager().getAllCurrencies()) {
            while (slot < inv.getSize() && (holder.getActionSlots().containsKey(slot)
                    || (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR
                    && inv.getItem(slot).getType() != fillerMat))) {
                slot++;
            }
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, currency.getSelectorItem());
            holder.getCurrencySlots().put(slot, currency.getId());
            slot++;
        }
        player.openInventory(inv);
    }

    public void openAdminRemoval(Player player, AuctionItem auction) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("admin-removal");
        String title = ColorUtil.colorize(gui.getString("title", "&0Admin Removal"));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 3)));

        MenuHolder holder = new MenuHolder("admin-removal", 1);
        holder.setSelectedAuction(auction);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, Map.of());
        inv.setItem(gui.getInt("item-slot", 13), auction.getItemStack());
        player.openInventory(inv);
    }

    public void openShulkerPreview(Player player, ItemStack containerItem, String returnMenu) {
        FileConfiguration gui = plugin.getConfigManager().getGuiConfig("shulker-preview");
        String title = ColorUtil.colorize(gui.getString("title", "&0Container Preview"));
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 4)));

        MenuHolder holder = new MenuHolder("shulker-preview", 1);
        holder.setReturnMenu(returnMenu);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        populateStaticItems(inv, gui, holder, Map.of());

        List<ItemStack> contents = ItemSerializer.getContainerContents(containerItem);
        for (int i = 0; i < Math.min(27, contents.size()); i++) {
            if (contents.get(i) != null) {
                inv.setItem(i, contents.get(i));
            }
        }
        player.openInventory(inv);
    }

    private void populateStaticItems(Inventory inv, FileConfiguration gui, MenuHolder holder, Map<String, String> placeholders) {
        for (String key : gui.getKeys(false)) {
            if (!gui.isConfigurationSection(key)) continue;
            ConfigurationSection sec = gui.getConfigurationSection(key);
            if (sec == null) continue;

            if (!checkConditions(sec.getStringList("conditions"), placeholders)) continue;

            ItemStack item = ItemBuilder.build(sec, placeholders);
            List<String> actions = sec.getStringList("actions");
            for (int slot : ItemBuilder.parseSlots(sec)) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, item);
                    if (!actions.isEmpty()) {
                        holder.getActionSlots().put(slot, actions);
                    }
                }
            }
        }
    }

    private boolean checkConditions(List<String> conditions, Map<String, String> placeholders) {
        for (String cond : conditions) {
            String resolved = ItemBuilder.applyPlaceholders(cond, placeholders).trim();
            if (resolved.contains("==")) {
                String[] parts = resolved.split("==");
                if (parts.length == 2 && !parts[0].trim().equalsIgnoreCase(parts[1].trim())) return false;
            } else if (resolved.contains(">")) {
                String[] parts = resolved.split(">");
                try {
                    if (Double.parseDouble(parts[0].trim()) <= Double.parseDouble(parts[1].trim())) return false;
                } catch (NumberFormatException e) { return false; }
            } else if (resolved.contains("<")) {
                String[] parts = resolved.split("<");
                try {
                    if (Double.parseDouble(parts[0].trim()) >= Double.parseDouble(parts[1].trim())) return false;
                } catch (NumberFormatException e) { return false; }
            }
        }
        return true;
    }

    private ItemStack buildAuctionDisplayItem(Player viewer, AuctionItem auction, boolean expired) {
        ItemStack stack = auction.getItemStack();
        if (stack == null) return new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String loreKey;
        if (expired) {
            loreKey = "item-lore-expired";
        } else if (auction.getSellerUuid().equals(viewer.getUniqueId())) {
            loreKey = "item-lore-own";
        } else if (viewer.hasPermission("jxauctions.admin.removal")) {
            loreKey = "item-lore-staff";
        } else {
            loreKey = "item-lore";
        }

        List<String> template = plugin.getConfigManager().getLangConfig().getStringList(loreKey);
        Currency currency = plugin.getCurrencyManager().getCurrency(auction.getCurrencyId());
        String priceFmt = currency != null ? currency.formatGui(auction.getPrice()) : String.valueOf(auction.getPrice());

        long now = System.currentTimeMillis();
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        FileConfiguration lang = plugin.getConfigManager().getLangConfig();
        int timerFormat = cfg.getInt("timer-format", 1);
        String expiresStr = TimeUtil.formatRemaining(auction.getExpiresAt() - now, timerFormat,
                lang.getString("time.second", "s"), lang.getString("time.minute", "m"),
                lang.getString("time.hour", "h"), lang.getString("time.day", "d"),
                lang.getString("expired", "Expired"));
        String deletionStr = TimeUtil.formatRemaining(auction.getDeletedAt() - now, timerFormat,
                lang.getString("time.second", "s"), lang.getString("time.minute", "m"),
                lang.getString("time.hour", "h"), lang.getString("time.day", "d"),
                lang.getString("expired", "Expired"));

        List<String> originalLore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();
        boolean isContainer = ItemSerializer.isContainer(stack);
        String containerLine = isContainer ? plugin.getConfigManager().getMessage("container") : "";

        List<String> finalLore = new ArrayList<>();
        for (String line : template) {
            if (line.equals("%lore%")) {
                finalLore.addAll(originalLore);
            } else if (line.equals("%container%")) {
                if (!containerLine.isEmpty()) finalLore.add(containerLine);
            } else {
                finalLore.add(ColorUtil.colorize(line
                        .replace("%price%", priceFmt)
                        .replace("%seller%", auction.getSellerName())
                        .replace("%expires%", expiresStr)
                        .replace("%deletion%", deletionStr)));
            }
        }
        meta.setLore(finalLore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack applyHistoryLore(HistoryEntry entry, List<String> template, String datePattern) {
        ItemStack stack = entry.getItemStack();
        if (stack == null) return new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        Currency currency = plugin.getCurrencyManager().getCurrency(entry.getCurrencyId());
        String priceFmt = currency != null ? currency.formatGui(entry.getPrice()) : String.valueOf(entry.getPrice());
        List<String> originalLore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();
        String containerLine = ItemSerializer.isContainer(stack) ? plugin.getConfigManager().getMessage("container") : "";

        List<String> finalLore = new ArrayList<>();
        for (String line : template) {
            if (line.equals("%lore%")) {
                finalLore.addAll(originalLore);
            } else if (line.equals("%container%")) {
                if (!containerLine.isEmpty()) finalLore.add(containerLine);
            } else {
                finalLore.add(ColorUtil.colorize(line
                        .replace("%seller%", entry.getSellerName())
                        .replace("%buyer%", entry.getBuyerName())
                        .replace("%price%", priceFmt)
                        .replace("%time%", TimeUtil.formatDate(entry.getSoldAt(), datePattern))));
            }
        }
        meta.setLore(finalLore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack applyDeletedLore(AuctionItem auction, List<String> template, String datePattern) {
        ItemStack stack = auction.getItemStack();
        if (stack == null) return new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        Currency currency = plugin.getCurrencyManager().getCurrency(auction.getCurrencyId());
        String priceFmt = currency != null ? currency.formatGui(auction.getPrice()) : String.valueOf(auction.getPrice());
        List<String> originalLore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();
        String containerLine = ItemSerializer.isContainer(stack) ? plugin.getConfigManager().getMessage("container") : "";

        List<String> finalLore = new ArrayList<>();
        for (String line : template) {
            if (line.equals("%lore%")) {
                finalLore.addAll(originalLore);
            } else if (line.equals("%container%")) {
                if (!containerLine.isEmpty()) finalLore.add(containerLine);
            } else {
                finalLore.add(ColorUtil.colorize(line
                        .replace("%seller%", auction.getSellerName())
                        .replace("%price%", priceFmt)
                        .replace("%time%", TimeUtil.formatDate(auction.getDeletedAt(), datePattern))));
            }
        }
        meta.setLore(finalLore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Map<String, String> buildPlaceholders(Player player, PlayerStats stats, int currentPage, int maxPage, String searchQuery) {
        Map<String, String> map = new HashMap<>();
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        int mode = cfg.getInt("number-formatting.mode", 0);
        String pattern = cfg.getString("number-formatting.formatted", "#,###.##");

        int limit = plugin.getAuctionManager().getSellLimit(player);
        long activeCount = plugin.getAuctionManager().getPlayerAuctionCount(player.getUniqueId(), AuctionItem.Status.ACTIVE);
        long expiredCount = plugin.getAuctionManager().getPlayerAuctionCount(player.getUniqueId(), AuctionItem.Status.EXPIRED);
        long totalActive = plugin.getAuctionManager().getTotalActiveCount();

        String limitStr = limit == Integer.MAX_VALUE
                ? plugin.getConfigManager().getLangConfig().getString("limit-bypass-icon", "∞")
                : String.valueOf(limit);

        Category cat = plugin.getConfigManager().getCategories().getOrDefault(stats.getSelectedCategory(),
                plugin.getConfigManager().getCategories().get("all"));
        String catName = cat != null ? cat.getDisplayName() : "All";

        Currency curr = stats.getSelectedCurrency() != null
                ? plugin.getCurrencyManager().getCurrency(stats.getSelectedCurrency())
                : null;
        String currName = curr != null ? curr.getDisplayRaw()
                : plugin.getConfigManager().getLangConfig().getString("currency.no-currency", "All");

        map.put("%current_page%", String.valueOf(currentPage));
        map.put("%max_page%", String.valueOf(maxPage));
        map.put("%total_active_count%", String.valueOf(totalActive));
        map.put("%sell_count%", String.valueOf(activeCount));
        map.put("%sell_limit%", limitStr);
        map.put("%expired_count%", String.valueOf(expiredCount));
        map.put("%items_sold%", NumberUtil.format(stats.getTotalItemsSold(), mode, pattern));
        map.put("%money_made%", NumberUtil.format(stats.getTotalMoneyMade(), mode, pattern));
        map.put("%items_purchased%", NumberUtil.format(stats.getTotalItemsPurchased(), mode, pattern));
        map.put("%money_spent%", NumberUtil.format(stats.getTotalMoneySpent(), mode, pattern));
        map.put("%selected_sorting_raw%", stats.getSelectedSorting().getId());
        map.put("%selected_sorting%", plugin.getConfigManager().getLangConfig().getString("sorting." + stats.getSelectedSorting().getId(), stats.getSelectedSorting().getId()));
        map.put("%selected_category%", catName);
        map.put("%selected_category_raw%", stats.getSelectedCategory());
        map.put("%selected_currency%", currName);
        map.put("%selected_currency_raw%", stats.getSelectedCurrency() != null ? stats.getSelectedCurrency() : "all");
        map.put("%categories_enabled%", String.valueOf(plugin.getConfigManager().isCategoriesEnabled()));
        map.put("%expired_items_enabled%", String.valueOf(cfg.getBoolean("separate-expired-items", false)));
        map.put("%search%", searchQuery != null && !searchQuery.isBlank() ? searchQuery : "None");
        return map;
    }

    private void scheduleAutoUpdate(Player player, MenuHolder holder, long ticks, Runnable reopen) {
        if (ticks <= 0) return;
        SchedulerUtil.runEntityLater(plugin, player, () -> {
            if (!player.isOnline()) return;
            if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
                reopen.run();
            }
        }, ticks);
    }
}
