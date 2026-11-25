package dev.jxauctions.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jxauctions.JxAuctions;
import dev.jxauctions.util.SchedulerUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class DiscordWebhookManager {

    private final JxAuctions plugin;

    public DiscordWebhookManager(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void sendAuctionStart(String seller, String itemName, int amount, String formattedPrice) {
        sendWebhook("auction-start", Map.of(
                "%player%", seller,
                "%item%", itemName,
                "%amount%", String.valueOf(amount),
                "%price%", formattedPrice
        ));
    }

    public void sendAuctionBuy(String buyer, String seller, String itemName, int amount, String formattedPrice) {
        sendWebhook("auction-buy", Map.of(
                "%player%", buyer,
                "%seller%", seller,
                "%item%", itemName,
                "%amount%", String.valueOf(amount),
                "%price%", formattedPrice
        ));
    }

    public void sendAuctionCancel(String seller, String itemName, int amount, String formattedPrice) {
        sendWebhook("auction-cancel", Map.of(
                "%player%", seller,
                "%item%", itemName,
                "%amount%", String.valueOf(amount),
                "%price%", formattedPrice
        ));
    }

    private void sendWebhook(String sectionName, Map<String, String> placeholders) {
        FileConfiguration cfg = plugin.getConfigManager().getDiscordConfig();
        String globalUrl = cfg.getString("url", "");
        ConfigurationSection sec = cfg.getConfigurationSection(sectionName);
        if (sec == null || !sec.getBoolean("enabled", false)) return;

        String webhookUrl = sec.getString("url", globalUrl);
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                JsonObject root = new JsonObject();
                if (sec.contains("content")) {
                    root.addProperty("content", replace(sec.getString("content", ""), placeholders));
                }

                JsonObject embed = new JsonObject();
                if (sec.contains("color")) {
                    String hex = sec.getString("color", "#00DDFF").replace("#", "");
                    embed.addProperty("color", Integer.parseInt(hex, 16));
                }
                if (sec.contains("description")) {
                    embed.addProperty("description", replace(sec.getString("description", ""), placeholders));
                }
                if (sec.isConfigurationSection("title")) {
                    String titleText = replace(sec.getString("title.text", ""), placeholders);
                    if (!titleText.isBlank()) embed.addProperty("title", titleText);
                    String titleUrl = replace(sec.getString("title.url", ""), placeholders);
                    if (!titleUrl.isBlank()) embed.addProperty("url", titleUrl);
                }
                if (sec.isConfigurationSection("author")) {
                    JsonObject author = new JsonObject();
                    String authorName = replace(sec.getString("author.name", ""), placeholders);
                    if (!authorName.isBlank()) {
                        author.addProperty("name", authorName);
                        String iconUrl = replace(sec.getString("author.icon-url", ""), placeholders);
                        if (!iconUrl.isBlank()) author.addProperty("icon_url", iconUrl);
                        String authorUrl = replace(sec.getString("author.url", ""), placeholders);
                        if (!authorUrl.isBlank()) author.addProperty("url", authorUrl);
                        embed.add("author", author);
                    }
                }
                if (sec.isConfigurationSection("footer")) {
                    JsonObject footer = new JsonObject();
                    String footerText = replace(sec.getString("footer.text", ""), placeholders);
                    if (!footerText.isBlank()) {
                        footer.addProperty("text", footerText);
                        String footerIcon = replace(sec.getString("footer.icon", ""), placeholders);
                        if (!footerIcon.isBlank()) footer.addProperty("icon_url", footerIcon);
                        embed.add("footer", footer);
                    }
                }
                String imageUrl = replace(sec.getString("image-url", ""), placeholders);
                if (!imageUrl.isBlank()) {
                    JsonObject img = new JsonObject();
                    img.addProperty("url", imageUrl);
                    embed.add("image", img);
                }
                String thumbUrl = replace(sec.getString("thumbnail-url", ""), placeholders);
                if (!thumbUrl.isBlank()) {
                    JsonObject thumb = new JsonObject();
                    thumb.addProperty("url", thumbUrl);
                    embed.add("thumbnail", thumb);
                }

                ConfigurationSection fieldsSec = sec.getConfigurationSection("fields");
                if (fieldsSec != null) {
                    JsonArray fields = new JsonArray();
                    for (String key : fieldsSec.getKeys(false)) {
                        ConfigurationSection f = fieldsSec.getConfigurationSection(key);
                        if (f == null) continue;
                        JsonObject field = new JsonObject();
                        field.addProperty("name", replace(f.getString("name", ""), placeholders));
                        field.addProperty("value", replace(f.getString("value", ""), placeholders));
                        field.addProperty("inline", f.getBoolean("inline", false));
                        fields.add(field);
                    }
                    embed.add("fields", fields);
                }

                JsonArray embeds = new JsonArray();
                embeds.add(embed);
                root.add("embeds", embeds);

                HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                if (plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Discord webhook error: " + e.getMessage());
                }
            }
        });
    }

    private String replace(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        String res = input;
        for (var entry : placeholders.entrySet()) {
            res = res.replace(entry.getKey(), entry.getValue());
        }
        return res;
    }
}
