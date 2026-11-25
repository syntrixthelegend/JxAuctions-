package dev.jxauctions.util;

import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ItemSerializer {

    private ItemSerializer() {}

    public static String toBase64(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize ItemStack", e);
        }
    }

    public static ItemStack fromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to deserialize ItemStack", e);
        }
    }

    public static int getSerializedByteSize(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            dataOutput.flush();
            return outputStream.size();
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
    }

    public static boolean isContainer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            return state instanceof Container;
        }
        return meta instanceof BundleMeta;
    }

    public static List<ItemStack> getContainerContents(ItemStack item) {
        List<ItemStack> contents = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return contents;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof Container container) {
                Inventory inv = container.getInventory();
                for (ItemStack content : inv.getContents()) {
                    contents.add(content);
                }
            }
        } else if (meta instanceof BundleMeta bundleMeta) {
            contents.addAll(bundleMeta.getItems());
        }
        return contents;
    }

    public static String getDisplayName(ItemStack item) {
        if (item == null) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ColorUtil.stripColor(item.getItemMeta().getDisplayName());
        }
        String[] parts = item.getType().name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
