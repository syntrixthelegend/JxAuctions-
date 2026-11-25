package dev.jxauctions.gui;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.config.ItemBuilder;
import dev.jxauctions.hook.FloodgateHook;
import dev.jxauctions.util.ColorUtil;
import dev.jxauctions.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchInputHandler implements Listener {

    private final JxAuctions plugin;
    private final Set<UUID> awaitingChatSearch = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> awaitingAnvilSearch = new ConcurrentHashMap<>();

    public SearchInputHandler(JxAuctions plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void promptSearch(Player player) {
        String mode = plugin.getConfigManager().getConfig().getString("search-mode", "anvil").toLowerCase();
        player.closeInventory();
        if (FloodgateHook.isBedrockPlayer(player) || (!mode.equals("anvil") && !mode.equals("sign"))) {
            promptChatFallback(player);
            return;
        }

        awaitingAnvilSearch.put(player.getUniqueId(), "");
        String title = ColorUtil.colorize(plugin.getConfigManager().getLangConfig().getString("input.anvil.title", "&0Search Here"));
        Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL, title);
        ItemStack paper = ItemBuilder.build(plugin.getConfigManager().getLangConfig().getConfigurationSection("input.anvil.item"), Map.of());
        anvil.setItem(0, paper);
        player.openInventory(anvil);
    }

    private void promptChatFallback(Player player) {
        awaitingChatSearch.add(player.getUniqueId());
        for (String line : plugin.getConfigManager().getMessageList("input.chat")) {
            player.sendMessage(line);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingChatSearch.remove(player.getUniqueId())) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();
        String query = msg.equalsIgnoreCase("cancel") ? null : msg;

        SchedulerUtil.runEntity(plugin, player, () ->
                plugin.getAuctionMenu().openMain(player, 1, query, null));
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getView().getPlayer() instanceof Player player && awaitingAnvilSearch.containsKey(player.getUniqueId())) {
            String text = event.getInventory().getRenameText();
            if (text != null) {
                awaitingAnvilSearch.put(player.getUniqueId(), text);
                ItemStack base = event.getInventory().getItem(0);
                if (base != null && !text.isBlank()) {
                    ItemStack result = base.clone();
                    var meta = result.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ColorUtil.colorize("&f" + text));
                        result.setItemMeta(meta);
                    }
                    event.setResult(result);
                }
            }
        }
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!awaitingAnvilSearch.containsKey(player.getUniqueId())) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        event.setCancelled(true);
        if (event.getRawSlot() == 2) {
            String typed = awaitingAnvilSearch.remove(player.getUniqueId());
            if (typed == null || typed.isBlank()) {
                ItemStack result = event.getCurrentItem();
                if (result != null && result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                    typed = ColorUtil.stripColor(result.getItemMeta().getDisplayName());
                }
            }
            String finalQuery = (typed != null && !typed.isBlank()) ? typed.trim() : null;
            event.getInventory().clear();
            player.closeInventory();
            SchedulerUtil.runEntity(plugin, player, () ->
                    plugin.getAuctionMenu().openMain(player, 1, finalQuery, null));
        }
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getType() == InventoryType.ANVIL) {
            if (awaitingAnvilSearch.remove(player.getUniqueId()) != null) {
                event.getInventory().clear();
            }
        }
    }

    @EventHandler
    public void onAnvilDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && awaitingAnvilSearch.containsKey(player.getUniqueId())
                && event.getInventory().getType() == InventoryType.ANVIL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        awaitingChatSearch.remove(uuid);
        awaitingAnvilSearch.remove(uuid);
    }
}
