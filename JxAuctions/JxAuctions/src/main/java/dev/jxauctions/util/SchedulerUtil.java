package dev.jxauctions.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class SchedulerUtil {

    private static final boolean FOLIA;

    static {
        boolean isFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
        FOLIA = isFolia;
    }

    private SchedulerUtil() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(),
                    Math.max(1L, delayTicks) * 50L, Math.max(1L, periodTicks) * 50L, TimeUnit.MILLISECONDS);
            return;
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    public static void runSync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runEntity(Plugin plugin, Player player, Runnable task) {
        runEntity(plugin, player, task, null);
    }

    public static void runEntity(Plugin plugin, Player player, Runnable task, Runnable onRetired) {
        if (FOLIA) {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), onRetired != null ? onRetired : () -> {});
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            } else if (onRetired != null) {
                onRetired.run();
            }
        });
    }

    public static void runEntityLater(Plugin plugin, Player player, Runnable task, long delayTicks) {
        if (FOLIA) {
            player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), () -> {}, Math.max(1L, delayTicks));
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
