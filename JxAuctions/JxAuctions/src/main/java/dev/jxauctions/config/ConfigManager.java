package dev.jxauctions.config;

import dev.jxauctions.JxAuctions;
import dev.jxauctions.model.Category;
import dev.jxauctions.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ConfigManager {

    private final JxAuctions plugin;
    private FileConfiguration config;
    private FileConfiguration categoriesConfig;
    private FileConfiguration currenciesConfig;
    private FileConfiguration discordConfig;
    private FileConfiguration langConfig;
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final List<Category.Rule> blacklistRules = new ArrayList<>();

    public ConfigManager(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public boolean loadAll() {
        try {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            this.config = plugin.getConfig();

            this.categoriesConfig = loadYaml("categories.yml");
            this.currenciesConfig = loadYaml("currencies.yml");
            this.discordConfig = loadYaml("discord.yml");
            this.langConfig = loadYaml("lang.yml");

            String[] guiFiles = {
                    "main-gui.yml", "category-gui.yml", "my-items-gui.yml",
                    "expired-items-gui.yml", "history-gui.yml", "deleted-gui.yml",
                    "logs-gui.yml", "confirm-purchase-gui.yml", "confirm-listing-gui.yml",
                    "currency-selector-gui.yml", "admin-removal-gui.yml", "shulker-preview-gui.yml"
            };
            guiConfigs.clear();
            for (String guiFile : guiFiles) {
                String key = guiFile.replace("-gui.yml", "");
                guiConfigs.put(key, loadYaml("guis/" + guiFile));
            }

            loadCategories();
            loadBlacklist();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private FileConfiguration loadYaml(String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(relativePath, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void loadCategories() {
        categories.clear();
        for (String key : categoriesConfig.getKeys(false)) {
            if (key.equals("enabled") || key.equals("version")) continue;
            ConfigurationSection sec = categoriesConfig.getConfigurationSection(key);
            if (sec == null) continue;
            String displayName = sec.getString("name", key);
            List<Category.Rule> rules = parseRuleList(sec.getMapList("items"));
            categories.put(key, new Category(key, displayName, rules));
        }
    }

    private void loadBlacklist() {
        blacklistRules.clear();
        blacklistRules.addAll(parseRuleList(config.getMapList("blacklist-items")));
    }

    private List<Category.Rule> parseRuleList(List<Map<?, ?>> list) {
        List<Category.Rule> rules = new ArrayList<>();
        for (Map<?, ?> map : list) {
            String mat = map.containsKey("material") ? String.valueOf(map.get("material")) : null;
            if (mat == null && map.containsKey("type")) {
                mat = String.valueOf(map.get("type"));
            }
            String name = map.containsKey("name") ? String.valueOf(map.get("name")) : null;
            Integer cmd = null;
            if (map.containsKey("custom-model-data")) {
                try {
                    cmd = Integer.parseInt(String.valueOf(map.get("custom-model-data")));
                } catch (NumberFormatException ignored) {}
            }
            rules.add(new Category.Rule(mat, name, cmd));
        }
        return rules;
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getCategoriesConfig() { return categoriesConfig; }
    public FileConfiguration getCurrenciesConfig() { return currenciesConfig; }
    public FileConfiguration getDiscordConfig() { return discordConfig; }
    public FileConfiguration getLangConfig() { return langConfig; }

    public FileConfiguration getGuiConfig(String name) {
        return guiConfigs.getOrDefault(name, guiConfigs.get("main"));
    }

    public Map<String, Category> getCategories() { return Collections.unmodifiableMap(categories); }
    public boolean isCategoriesEnabled() { return categoriesConfig.getBoolean("enabled", false); }
    public List<Category.Rule> getBlacklistRules() { return Collections.unmodifiableList(blacklistRules); }

    public String getPrefix() {
        return ColorUtil.colorize(config.getString("prefix", "&bJxAuctions &7» "));
    }

    public String getMessage(String path) {
        String raw = langConfig.getString(path, path);
        return ColorUtil.colorize(raw);
    }

    public String getPrefixedMessage(String path) {
        return getPrefix() + getMessage(path);
    }

    public List<String> getMessageList(String path) {
        return ColorUtil.colorize(langConfig.getStringList(path));
    }
}
