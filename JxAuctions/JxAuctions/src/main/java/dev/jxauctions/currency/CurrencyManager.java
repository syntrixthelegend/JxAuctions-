package dev.jxauctions.currency;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.config.ItemBuilder;
import dev.jxauctions.util.ColorUtil;
import dev.jxauctions.util.NumberUtil;
import dev.jxauctions.util.SchedulerUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CurrencyManager {

    private final JxAuctions plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();

    public CurrencyManager(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void loadCurrencies() {
        currencies.clear();
        FileConfiguration cfg = plugin.getConfigManager().getCurrenciesConfig();

        ConfigurationSection builtIn = cfg.getConfigurationSection("currencies");
        if (builtIn != null) {
            registerVault(builtIn.getConfigurationSection("Vault"));
            registerExperience(builtIn.getConfigurationSection("Experience"));
            registerPlayerPoints(builtIn.getConfigurationSection("PlayerPoints"));
            registerCoinsEngine(builtIn.getConfigurationSection("CoinsEngine"));
            registerExcellentEconomy(builtIn.getConfigurationSection("ExcellentEconomy"));
            registerEcoBits(builtIn.getConfigurationSection("EcoBits"));
            registerRedisEconomy(builtIn.getConfigurationSection("RedisEconomy"));
            registerUltraEconomy(builtIn.getConfigurationSection("UltraEconomy"));
            registerTokenManager(builtIn.getConfigurationSection("TokenManager"));
            registerRoyaleEconomy(builtIn.getConfigurationSection("RoyaleEconomy"));
            registerBeastTokens(builtIn.getConfigurationSection("BeastTokens"));
            registerSuperMobCoins(builtIn.getConfigurationSection("SuperMobCoins"));
            registerTheOnlyMobCoins(builtIn.getConfigurationSection("TheOnly-MobCoins"));
            registerRivalCredits(builtIn.getConfigurationSection("RivalCredits"));
            registerKingdomsX(builtIn.getConfigurationSection("KingdomsX"));
            registerRivalHarvesterHoes(builtIn.getConfigurationSection("RivalHarvesterHoes"));
        }

        ConfigurationSection placeholderCurrencies = cfg.getConfigurationSection("placeholder-currencies");
        if (placeholderCurrencies != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            for (String key : placeholderCurrencies.getKeys(false)) {
                ConfigurationSection sec = placeholderCurrencies.getConfigurationSection(key);
                if (sec != null && sec.getBoolean("register", false)) {
                    registerPlaceholderCurrency(key, sec);
                }
            }
        }

        plugin.getLogger().info("Loaded " + currencies.size() + " currency provider(s): " + String.join(", ", currencies.keySet()));
    }

    private void registerVault(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return;
        Economy econ = rsp.getProvider();

        currencies.put("Vault", new BaseCurrency("Vault", sec, true, true) {
            @Override public double getBalance(OfflinePlayer p) { return econ.getBalance(p); }
            @Override public boolean has(OfflinePlayer p, double amt) { return econ.has(p, amt); }
            @Override public boolean withdraw(OfflinePlayer p, double amt) { return econ.withdrawPlayer(p, amt).transactionSuccess(); }
            @Override public boolean deposit(OfflinePlayer p, double amt) { return econ.depositPlayer(p, amt).transactionSuccess(); }
        });
    }

    private void registerExperience(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        currencies.put("Experience", new BaseCurrency("Experience", sec, false, false) {
            @Override
            public double getBalance(OfflinePlayer player) {
                Player online = player.getPlayer();
                return online != null ? online.getTotalExperience() : 0;
            }
            @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
            @Override
            public boolean withdraw(OfflinePlayer player, double amount) {
                Player online = player.getPlayer();
                if (online == null || !has(player, amount)) return false;
                int remaining = (int) Math.max(0, online.getTotalExperience() - Math.round(amount));
                online.setTotalExperience(0);
                online.setLevel(0);
                online.setExp(0);
                online.giveExp(remaining);
                return true;
            }
            @Override
            public boolean deposit(OfflinePlayer player, double amount) {
                Player online = player.getPlayer();
                if (online == null) return false;
                online.giveExp((int) Math.round(amount));
                return true;
            }
        });
    }

    private void registerPlayerPoints(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) return;
        try {
            Class<?> apiClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            Object api = apiClass.getMethod("getAPI").invoke(instance);
            Method look = api.getClass().getMethod("look", UUID.class);
            Method take = api.getClass().getMethod("take", UUID.class, int.class);
            Method give = api.getClass().getMethod("give", UUID.class, int.class);

            currencies.put("PlayerPoints", new BaseCurrency("PlayerPoints", sec, true, false) {
                @Override public double getBalance(OfflinePlayer p) {
                    try { return ((Number) look.invoke(api, p.getUniqueId())).doubleValue(); } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    try { return (boolean) take.invoke(api, p.getUniqueId(), (int) Math.round(amt)); } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { return (boolean) give.invoke(api, p.getUniqueId(), (int) Math.round(amt)); } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerCoinsEngine(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("CoinsEngine")) return;
        try {
            Class<?> api = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            for (String key : sec.getKeys(false)) {
                if (key.equals("register")) continue;
                ConfigurationSection sub = sec.getConfigurationSection(key);
                if (sub == null) continue;
                Object coin = api.getMethod("getCurrency", String.class).invoke(null, key);
                if (coin == null) continue;
                String id = "CoinsEngine-" + key;
                currencies.put(id, new BaseCurrency(id, sub, true, true) {
                    @Override public double getBalance(OfflinePlayer p) {
                        try { return (double) api.getMethod("getBalance", UUID.class, coin.getClass()).invoke(null, p.getUniqueId(), coin); } catch (Exception e) { return 0; }
                    }
                    @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                    @Override public boolean withdraw(OfflinePlayer p, double amt) {
                        if (amt < 0 || !has(p, amt)) return false;
                        try {
                            Object res = api.getMethod("removeBalance", UUID.class, coin.getClass(), double.class).invoke(null, p.getUniqueId(), coin, amt);
                            return !(res instanceof Boolean b) || b;
                        } catch (Exception e) { return false; }
                    }
                    @Override public boolean deposit(OfflinePlayer p, double amt) {
                        try { api.getMethod("addBalance", UUID.class, coin.getClass(), double.class).invoke(null, p.getUniqueId(), coin, amt); return true; } catch (Exception e) { return false; }
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void registerExcellentEconomy(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("ExcellentEconomy")) return;
        try {
            Class<?> api = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
            for (String key : sec.getKeys(false)) {
                if (key.equals("register")) continue;
                ConfigurationSection sub = sec.getConfigurationSection(key);
                if (sub == null) continue;
                String id = "ExcellentEconomy-" + key;
                currencies.put(id, new BaseCurrency(id, sub, true, true) {
                    @Override public double getBalance(OfflinePlayer p) {
                        try { return (double) api.getMethod("getBalance", UUID.class, String.class).invoke(null, p.getUniqueId(), key); } catch (Exception e) { return 0; }
                    }
                    @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                    @Override public boolean withdraw(OfflinePlayer p, double amt) {
                        if (amt < 0 || !has(p, amt)) return false;
                        try {
                            Object res = api.getMethod("withdraw", UUID.class, String.class, double.class).invoke(null, p.getUniqueId(), key, amt);
                            return !(res instanceof Boolean b) || b;
                        } catch (Exception e) { return false; }
                    }
                    @Override public boolean deposit(OfflinePlayer p, double amt) {
                        try { api.getMethod("deposit", UUID.class, String.class, double.class).invoke(null, p.getUniqueId(), key, amt); return true; } catch (Exception e) { return false; }
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void registerEcoBits(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("EcoBits")) return;
        try {
            Class<?> currenciesClass = Class.forName("com.willfp.ecobits.currencies.Currencies");
            Class<?> economyClass = Class.forName("com.willfp.ecobits.currencies.CurrencyUtils");
            for (String key : sec.getKeys(false)) {
                if (key.equals("register")) continue;
                ConfigurationSection sub = sec.getConfigurationSection(key);
                if (sub == null) continue;
                Object ecoCur = currenciesClass.getMethod("getByID", String.class).invoke(null, key);
                if (ecoCur == null) continue;
                String id = "EcoBits-" + key;
                currencies.put(id, new BaseCurrency(id, sub, true, true) {
                    @Override public double getBalance(OfflinePlayer p) {
                        try { return ((BigDecimal) economyClass.getMethod("getBalance", OfflinePlayer.class, ecoCur.getClass()).invoke(null, p, ecoCur)).doubleValue(); } catch (Exception e) { return 0; }
                    }
                    @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                    @Override public boolean withdraw(OfflinePlayer p, double amt) {
                        if (amt < 0 || !has(p, amt)) return false;
                        try { economyClass.getMethod("adjustBalance", OfflinePlayer.class, ecoCur.getClass(), BigDecimal.class).invoke(null, p, ecoCur, BigDecimal.valueOf(-amt)); return true; } catch (Exception e) { return false; }
                    }
                    @Override public boolean deposit(OfflinePlayer p, double amt) {
                        try { economyClass.getMethod("adjustBalance", OfflinePlayer.class, ecoCur.getClass(), BigDecimal.class).invoke(null, p, ecoCur, BigDecimal.valueOf(amt)); return true; } catch (Exception e) { return false; }
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void registerRedisEconomy(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("RedisEconomy")) return;
        try {
            Class<?> apiClass = Class.forName("dev.unnm3d.rediseconomy.api.RedisEconomyAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            for (String key : sec.getKeys(false)) {
                if (key.equals("register")) continue;
                ConfigurationSection sub = sec.getConfigurationSection(key);
                if (sub == null) continue;
                Object curr = apiClass.getMethod("getCurrencyByName", String.class).invoke(api, key);
                if (curr == null) continue;
                String id = "RedisEconomy-" + key;
                currencies.put(id, new BaseCurrency(id, sub, true, true) {
                    @Override public double getBalance(OfflinePlayer p) {
                        try { return (double) curr.getClass().getMethod("getBalance", UUID.class).invoke(curr, p.getUniqueId()); } catch (Exception e) { return 0; }
                    }
                    @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                    @Override public boolean withdraw(OfflinePlayer p, double amt) {
                        if (amt < 0 || !has(p, amt)) return false;
                        try {
                            Object res = curr.getClass().getMethod("withdrawPlayer", UUID.class, double.class).invoke(curr, p.getUniqueId(), amt);
                            return !(res instanceof Boolean b) || b;
                        } catch (Exception e) { return false; }
                    }
                    @Override public boolean deposit(OfflinePlayer p, double amt) {
                        try { curr.getClass().getMethod("depositPlayer", UUID.class, double.class).invoke(curr, p.getUniqueId(), amt); return true; } catch (Exception e) { return false; }
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void registerUltraEconomy(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("UltraEconomy")) return;
        try {
            Class<?> apiClass = Class.forName("me.TechsCode.UltraEconomy.UltraEconomy");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            for (String key : sec.getKeys(false)) {
                if (key.equals("register")) continue;
                ConfigurationSection sub = sec.getConfigurationSection(key);
                if (sub == null) continue;
                String id = "UltraEconomy-" + key;
                currencies.put(id, new BaseCurrency(id, sub, true, true) {
                    @Override public double getBalance(OfflinePlayer p) {
                        try {
                            Optional<?> acc = (Optional<?>) api.getClass().getMethod("getAccounts").invoke(api)
                                    .getClass().getMethod("uuid", UUID.class).invoke(api.getClass().getMethod("getAccounts").invoke(api), p.getUniqueId());
                            if (acc.isPresent()) {
                                Object acct = acc.get();
                                Object cur = api.getClass().getMethod("getCurrencies").invoke(api)
                                        .getClass().getMethod("name", String.class).invoke(api.getClass().getMethod("getCurrencies").invoke(api), key);
                                return ((Number) acct.getClass().getMethod("getBalance", cur.getClass()).invoke(acct, cur)).doubleValue();
                            }
                        } catch (Exception ignored) {}
                        return 0;
                    }
                    @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                    @Override public boolean withdraw(OfflinePlayer p, double amt) {
                        if (amt < 0 || !has(p, amt)) return false;
                        try {
                            Object accounts = api.getClass().getMethod("getAccounts").invoke(api);
                            Optional<?> acc = (Optional<?>) accounts.getClass().getMethod("uuid", UUID.class).invoke(accounts, p.getUniqueId());
                            if (acc.isPresent()) {
                                Object acct = acc.get();
                                Object currenciesMgr = api.getClass().getMethod("getCurrencies").invoke(api);
                                Object cur = currenciesMgr.getClass().getMethod("name", String.class).invoke(currenciesMgr, key);
                                acct.getClass().getMethod("removeBalance", cur.getClass(), double.class).invoke(acct, cur, amt);
                                return true;
                            }
                        } catch (Exception ignored) {}
                        return false;
                    }
                    @Override public boolean deposit(OfflinePlayer p, double amt) {
                        if (amt < 0) return false;
                        try {
                            Object accounts = api.getClass().getMethod("getAccounts").invoke(api);
                            Optional<?> acc = (Optional<?>) accounts.getClass().getMethod("uuid", UUID.class).invoke(accounts, p.getUniqueId());
                            if (acc.isPresent()) {
                                Object acct = acc.get();
                                Object currenciesMgr = api.getClass().getMethod("getCurrencies").invoke(api);
                                Object cur = currenciesMgr.getClass().getMethod("name", String.class).invoke(currenciesMgr, key);
                                acct.getClass().getMethod("addBalance", cur.getClass(), double.class).invoke(acct, cur, amt);
                                return true;
                            }
                        } catch (Exception ignored) {}
                        return false;
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void registerTokenManager(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("TokenManager")) return;
        try {
            Object tm = Bukkit.getPluginManager().getPlugin("TokenManager");
            currencies.put("TokenManager", new BaseCurrency("TokenManager", sec, true, false) {
                @Override public double getBalance(OfflinePlayer p) {
                    try {
                        OptionalLong bal = (OptionalLong) tm.getClass().getMethod("getTokens", OfflinePlayer.class).invoke(tm, p);
                        return bal.orElse(0L);
                    } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    try { return (boolean) tm.getClass().getMethod("removeTokens", OfflinePlayer.class, long.class).invoke(tm, p, Math.round(amt)); } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { return (boolean) tm.getClass().getMethod("addTokens", OfflinePlayer.class, long.class).invoke(tm, p, Math.round(amt)); } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerRoyaleEconomy(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("RoyaleEconomy")) return;
        try {
            Class<?> api = Class.forName("me.qKing12.RoyaleEconomy.API.Currency");
            currencies.put("RoyaleEconomy", new BaseCurrency("RoyaleEconomy", sec, true, true) {
                @Override public double getBalance(OfflinePlayer p) {
                    try { return (double) api.getMethod("getBalance", UUID.class).invoke(null, p.getUniqueId()); } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try { api.getMethod("removeBalance", UUID.class, double.class).invoke(null, p.getUniqueId(), amt); return true; } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { api.getMethod("addBalance", UUID.class, double.class).invoke(null, p.getUniqueId(), amt); return true; } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerBeastTokens(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("BeastTokens")) return;
        try {
            Class<?> api = Class.forName("me.mraxetv.beasttokens.api.BeastTokensAPI");
            Object mgr = api.getMethod("getTokensManager").invoke(null);
            currencies.put("BeastTokens", new BaseCurrency("BeastTokens", sec, true, true) {
                @Override public double getBalance(OfflinePlayer p) {
                    try { return (double) mgr.getClass().getMethod("getTokens", OfflinePlayer.class).invoke(mgr, p); } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try { mgr.getClass().getMethod("removeTokens", OfflinePlayer.class, double.class).invoke(mgr, p, amt); return true; } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { mgr.getClass().getMethod("addTokens", OfflinePlayer.class, double.class).invoke(mgr, p, amt); return true; } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerSuperMobCoins(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SuperMobCoins")) return;
        try {
            Class<?> api = Class.forName("me.swanis.mobcoins.MobCoinsAPI");
            Object mgr = api.getMethod("getProfileManager").invoke(null);
            currencies.put("SuperMobCoins", new BaseCurrency("SuperMobCoins", sec, true, false) {
                @Override public double getBalance(OfflinePlayer p) {
                    try {
                        Object prof = mgr.getClass().getMethod("getProfile", UUID.class).invoke(mgr, p.getUniqueId());
                        return ((Number) prof.getClass().getMethod("getMobCoins").invoke(prof)).doubleValue();
                    } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try {
                        Object prof = mgr.getClass().getMethod("getProfile", UUID.class).invoke(mgr, p.getUniqueId());
                        prof.getClass().getMethod("setMobCoins", int.class).invoke(prof, (int) Math.max(0, getBalance(p) - Math.round(amt)));
                        return true;
                    } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try {
                        Object prof = mgr.getClass().getMethod("getProfile", UUID.class).invoke(mgr, p.getUniqueId());
                        prof.getClass().getMethod("setMobCoins", int.class).invoke(prof, (int) (getBalance(p) + Math.round(amt)));
                        return true;
                    } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerTheOnlyMobCoins(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("TheOnly-MobCoins")) return;
        try {
            Class<?> api = Class.forName("com.github.theonly.mobcoins.api.MobCoinsAPI");
            currencies.put("TheOnly-MobCoins", new BaseCurrency("TheOnly-MobCoins", sec, true, true) {
                @Override public double getBalance(OfflinePlayer p) {
                    try { return ((Number) api.getMethod("getCoins", UUID.class).invoke(null, p.getUniqueId())).doubleValue(); } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try { api.getMethod("removeCoins", UUID.class, double.class).invoke(null, p.getUniqueId(), amt); return true; } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { api.getMethod("addCoins", UUID.class, double.class).invoke(null, p.getUniqueId(), amt); return true; } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerRivalCredits(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("RivalCredits")) return;
        try {
            Class<?> api = Class.forName("me.rivaldev.rivalcredits.api.RivalCreditsAPI");
            currencies.put("RivalCredits", new BaseCurrency("RivalCredits", sec, true, true) {
                @Override public double getBalance(OfflinePlayer p) {
                    try { return ((Number) api.getMethod("getCredits", OfflinePlayer.class).invoke(null, p)).doubleValue(); } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try { api.getMethod("removeCredits", OfflinePlayer.class, double.class).invoke(null, p, amt); return true; } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { api.getMethod("addCredits", OfflinePlayer.class, double.class).invoke(null, p, amt); return true; } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerKingdomsX(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Kingdoms")) return;
        try {
            Class<?> kpClass = Class.forName("org.kingdoms.constants.player.KingdomPlayer");
            currencies.put("KingdomsX", new BaseCurrency("KingdomsX", sec, true, false) {
                @Override public double getBalance(OfflinePlayer p) {
                    try {
                        Object kp = kpClass.getMethod("getKingdomPlayer", OfflinePlayer.class).invoke(null, p);
                        Object k = kp.getClass().getMethod("getKingdom").invoke(kp);
                        return k != null ? ((Number) k.getClass().getMethod("getResourcePoints").invoke(k)).doubleValue() : 0;
                    } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try {
                        Object kp = kpClass.getMethod("getKingdomPlayer", OfflinePlayer.class).invoke(null, p);
                        Object k = kp.getClass().getMethod("getKingdom").invoke(kp);
                        if (k == null) return false;
                        k.getClass().getMethod("addResourcePoints", long.class).invoke(k, -Math.round(amt));
                        return true;
                    } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try {
                        Object kp = kpClass.getMethod("getKingdomPlayer", OfflinePlayer.class).invoke(null, p);
                        Object k = kp.getClass().getMethod("getKingdom").invoke(kp);
                        if (k == null) return false;
                        k.getClass().getMethod("addResourcePoints", long.class).invoke(k, Math.round(amt));
                        return true;
                    } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerRivalHarvesterHoes(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("register", false)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("RivalHarvesterHoes")) return;
        try {
            Class<?> api = Class.forName("me.rivaldev.harvesterhoes.api.RivalHarvesterHoesAPI");
            currencies.put("RivalHarvesterHoes", new BaseCurrency("RivalHarvesterHoes", sec, true, true) {
                @Override public double getBalance(OfflinePlayer p) {
                    try { return ((Number) api.getMethod("getEssence", OfflinePlayer.class).invoke(null, p)).doubleValue(); } catch (Exception e) { return 0; }
                }
                @Override public boolean has(OfflinePlayer p, double amt) { return getBalance(p) >= amt; }
                @Override public boolean withdraw(OfflinePlayer p, double amt) {
                    if (amt < 0 || !has(p, amt)) return false;
                    try { api.getMethod("removeEssence", OfflinePlayer.class, double.class).invoke(null, p, amt); return true; } catch (Exception e) { return false; }
                }
                @Override public boolean deposit(OfflinePlayer p, double amt) {
                    try { api.getMethod("addEssence", OfflinePlayer.class, double.class).invoke(null, p, amt); return true; } catch (Exception e) { return false; }
                }
            });
        } catch (Exception ignored) {}
    }

    private void registerPlaceholderCurrency(String id, ConfigurationSection sec) {
        boolean usesDouble = sec.getBoolean("uses-double", true);
        boolean worksOffline = sec.getBoolean("works-offline", false);
        String rawPlaceholder = sec.getString("settings.raw-placeholder", "");
        String giveCommand = sec.getString("settings.give-command", "");
        String takeCommand = sec.getString("settings.take-command", "");

        currencies.put(id, new BaseCurrency(id, sec, worksOffline, usesDouble) {
            @Override public double getBalance(OfflinePlayer player) {
                try {
                    Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    String out = (String) papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class).invoke(null, player, rawPlaceholder);
                    return Double.parseDouble(out.replace(",", "").trim());
                } catch (Exception e) { return 0; }
            }
            @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
            @Override public synchronized boolean withdraw(OfflinePlayer player, double amount) {
                if (amount < 0 || !has(player, amount)) return false;
                String amtStr = usesDouble ? String.valueOf(amount) : String.valueOf((long) Math.round(amount));
                String cmd = takeCommand.replace("%player%", Objects.requireNonNullElse(player.getName(), "")).replace("%amount%", amtStr);
                if (Bukkit.isPrimaryThread()) {
                    return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                SchedulerUtil.runSync(plugin, () -> future.complete(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)));
                try { return future.get(2, TimeUnit.SECONDS); } catch (Exception e) { return false; }
            }
            @Override public synchronized boolean deposit(OfflinePlayer player, double amount) {
                if (amount < 0) return false;
                String amtStr = usesDouble ? String.valueOf(amount) : String.valueOf((long) Math.round(amount));
                String cmd = giveCommand.replace("%player%", Objects.requireNonNullElse(player.getName(), "")).replace("%amount%", amtStr);
                if (Bukkit.isPrimaryThread()) {
                    return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                SchedulerUtil.runSync(plugin, () -> future.complete(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)));
                try { return future.get(2, TimeUnit.SECONDS); } catch (Exception e) { return false; }
            }
        });
    }

    public Currency getCurrency(String id) {
        if (id == null) return null;
        for (Map.Entry<String, Currency> entry : currencies.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(id)) return entry.getValue();
        }
        return null;
    }

    public Collection<Currency> getAllCurrencies() { return Collections.unmodifiableCollection(currencies.values()); }
    public Currency getDefaultCurrency() { return currencies.values().stream().findFirst().orElse(null); }

    private abstract class BaseCurrency implements Currency {
        private final String id;
        private final String displayRaw;
        private final String displayGui;
        private final double taxPercent;
        private final boolean worksOffline;
        private final boolean usesDouble;
        private final ItemStack selectorItem;

        protected BaseCurrency(String id, ConfigurationSection sec, boolean worksOffline, boolean usesDouble) {
            this.id = id;
            this.displayRaw = sec.getString("display.raw", id);
            this.displayGui = sec.getString("display.gui", "&f%price% " + id);
            this.taxPercent = sec.getDouble("tax", 0.0);
            this.worksOffline = worksOffline;
            this.usesDouble = usesDouble;
            this.selectorItem = ItemBuilder.build(sec.getConfigurationSection("item"), Map.of());
        }

        @Override public String getId() { return id; }
        @Override public String getDisplayRaw() { return displayRaw; }
        @Override public String formatGui(double price) {
            FileConfiguration cfg = plugin.getConfigManager().getConfig();
            int mode = cfg.getInt("number-formatting.mode", 0);
            String pattern = cfg.getString("number-formatting.formatted", "#,###.##");
            return ColorUtil.colorize(displayGui.replace("%price%", NumberUtil.format(price, mode, pattern)));
        }
        @Override public double getTaxPercent() { return taxPercent; }
        @Override public boolean worksOffline() { return worksOffline; }
        @Override public boolean usesDouble() { return usesDouble; }
        @Override public ItemStack getSelectorItem() { return selectorItem.clone(); }
    }
}
