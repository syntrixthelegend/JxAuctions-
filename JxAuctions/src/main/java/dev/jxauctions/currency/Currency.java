package dev.jxauctions.currency;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

public interface Currency {

    String getId();

    String getDisplayRaw();

    String formatGui(double price);

    double getTaxPercent();

    boolean worksOffline();

    boolean usesDouble();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    ItemStack getSelectorItem();
}
