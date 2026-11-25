package dev.jxauctions.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class FloodgateHook {

    private static Object floodgateApi;
    private static Method isFloodgatePlayerMethod;

    static {
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (Exception ignored) {
                floodgateApi = null;
            }
        }
    }

    private FloodgateHook() {}

    public static boolean isBedrockPlayer(Player player) {
        if (player == null || floodgateApi == null || isFloodgatePlayerMethod == null) {
            return false;
        }
        try {
            return (boolean) isFloodgatePlayerMethod.invoke(floodgateApi, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}
