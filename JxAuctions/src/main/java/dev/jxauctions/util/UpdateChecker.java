package dev.jxauctions.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jxauctions.JxAuctions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker implements Listener {

    private final JxAuctions plugin;
    private volatile String latestVersion;

    public UpdateChecker(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void check() {
        var cfg = plugin.getConfigManager().getConfig();
        if (!cfg.getBoolean("update-notifier.enabled", true)) return;

        String checkUrl = cfg.getString("update-notifier.url", "");
        if (checkUrl.isBlank()) return;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(checkUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestProperty("User-Agent", "JxAuctions-UpdateChecker");

                if (conn.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("tag_name")) {
                            String remote = json.get("tag_name").getAsString().replaceFirst("^v", "");
                            String current = plugin.getDescription().getVersion();
                            if (isNewerVersion(remote, current)) {
                                this.latestVersion = remote;
                                plugin.getLogger().info("A new version of JxAuctions is available: " + remote + " (current: " + current + ")");
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    public static boolean isNewerVersion(String remote, String current) {
        String[] rParts = remote.split("[^0-9]+");
        String[] cParts = current.split("[^0-9]+");
        int max = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < max; i++) {
            int r = (i < rParts.length && !rParts[i].isEmpty()) ? Integer.parseInt(rParts[i]) : 0;
            int c = (i < cParts.length && !cParts[i].isEmpty()) ? Integer.parseInt(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (latestVersion == null) return;
        if (!plugin.getConfigManager().getConfig().getBoolean("update-notifier.on-join", true)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("jxauctions.update-notify")) {
            String msg = plugin.getConfigManager().getPrefixedMessage("update-notifier")
                    .replace("%current%", plugin.getDescription().getVersion())
                    .replace("%latest%", latestVersion);
            SchedulerUtil.runEntityLater(plugin, player, () -> player.sendMessage(msg), 40L);
        }
    }
}
