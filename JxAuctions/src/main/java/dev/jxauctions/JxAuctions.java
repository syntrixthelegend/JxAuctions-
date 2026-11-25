package dev.jxauctions;

import dev.jxauctions.command.AdminCommand;
import dev.jxauctions.command.AuctionCommand;
import dev.jxauctions.config.ConfigManager;
import dev.jxauctions.currency.CurrencyManager;
import dev.jxauctions.database.DatabaseManager;
import dev.jxauctions.discord.DiscordWebhookManager;
import dev.jxauctions.gui.AuctionMenu;
import dev.jxauctions.gui.MenuListener;
import dev.jxauctions.gui.SearchInputHandler;
import dev.jxauctions.hook.ConverterManager;
import dev.jxauctions.hook.JxPlaceholderExpansion;
import dev.jxauctions.manager.AuctionManager;
import dev.jxauctions.sync.MultiServerSync;
import dev.jxauctions.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class JxAuctions extends JavaPlugin {

    private static JxAuctions instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CurrencyManager currencyManager;
    private MultiServerSync multiServerSync;
    private DiscordWebhookManager discordWebhookManager;
    private AuctionManager auctionManager;
    private AuctionMenu auctionMenu;
    private SearchInputHandler searchInputHandler;
    private ConverterManager converterManager;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        this.configManager = new ConfigManager(this);
        if (!configManager.loadAll()) {
            getLogger().severe("Failed to load configuration files! Disabling JxAuctions.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling JxAuctions.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.currencyManager = new CurrencyManager(this);
        currencyManager.loadCurrencies();

        this.multiServerSync = new MultiServerSync(this);
        multiServerSync.initialize();

        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.auctionManager = new AuctionManager(this);
        auctionManager.initialize();

        this.auctionMenu = new AuctionMenu(this);
        this.searchInputHandler = new SearchInputHandler(this);
        new MenuListener(this);

        this.converterManager = new ConverterManager(this);

        PluginCommand ahCmd = getCommand("ah");
        if (ahCmd != null) {
            var ahAliases = configManager.getConfig().getStringList("command-aliases.auction");
            if (!ahAliases.isEmpty()) ahCmd.setAliases(ahAliases);
            AuctionCommand auctionCommand = new AuctionCommand(this);
            ahCmd.setExecutor(auctionCommand);
            ahCmd.setTabCompleter(auctionCommand);
        }

        PluginCommand adminCmd = getCommand("ahadmin");
        if (adminCmd != null) {
            var adminAliases = configManager.getConfig().getStringList("command-aliases.admin");
            if (!adminAliases.isEmpty()) adminCmd.setAliases(adminAliases);
            AdminCommand adminCommand = new AdminCommand(this);
            adminCmd.setExecutor(adminCommand);
            adminCmd.setTabCompleter(adminCommand);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new JxPlaceholderExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI!");
        }

        new UpdateChecker(this).check();
        getLogger().info("JxAuctions v" + getDescription().getVersion() + " enabled in " + (System.currentTimeMillis() - start) + "ms!");
    }

    @Override
    public void onDisable() {
        if (multiServerSync != null) {
            multiServerSync.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("JxAuctions disabled.");
    }

    public static JxAuctions getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public CurrencyManager getCurrencyManager() { return currencyManager; }
    public MultiServerSync getMultiServerSync() { return multiServerSync; }
    public DiscordWebhookManager getDiscordWebhookManager() { return discordWebhookManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public AuctionMenu getAuctionMenu() { return auctionMenu; }
    public SearchInputHandler getSearchInputHandler() { return searchInputHandler; }
    public ConverterManager getConverterManager() { return converterManager; }
}
