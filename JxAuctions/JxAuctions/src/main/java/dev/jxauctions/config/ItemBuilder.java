package dev.jxauctions.config;

import dev.jxauctions.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemBuilder {

    private ItemBuilder() {}

    public static ItemStack build(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }

        String matName = section.getString("material", section.getString("type", "STONE"));
        Material material = Material.matchMaterial(matName.toUpperCase());
        if (material == null) {
            material = Material.STONE;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (section.contains("name")) {
            meta.setDisplayName(ColorUtil.colorize(applyPlaceholders(section.getString("name", ""), placeholders)));
        }

        if (section.contains("lore")) {
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(ColorUtil.colorize(applyPlaceholders(line, placeholders)));
            }
            meta.setLore(lore);
        }

        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }

        if (section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return input;
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public static List<Integer> parseSlots(ConfigurationSection section) {
        List<Integer> slots = new ArrayList<>();
        if (section == null) return slots;

        if (section.isInt("slot")) {
            slots.add(section.getInt("slot"));
        } else if (section.isList("slot")) {
            for (Object obj : section.getList("slot", List.of())) {
                String str = String.valueOf(obj).trim();
                if (str.contains("-")) {
                    String[] parts = str.split("-");
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                } else {
                    try {
                        slots.add(Integer.parseInt(str));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return slots;
    }
}
