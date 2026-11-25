package dev.jxauctions.model;

import dev.jxauctions.util.ColorUtil;
import dev.jxauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.regex.Pattern;

public class Category {

    private final String id;
    private final String displayName;
    private final List<Rule> rules;

    public record Rule(String materialPattern, String namePattern, Integer customModelData) {
        public boolean matches(ItemStack item) {
            if (item == null) return false;
            if (materialPattern != null && !matchesWildcard(item.getType().name(), materialPattern)) {
                return false;
            }
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            if (namePattern != null) {
                String strippedName = meta != null && meta.hasDisplayName()
                        ? ColorUtil.stripColor(meta.getDisplayName())
                        : ItemSerializer.getDisplayName(item);
                if (!matchesWildcard(strippedName, namePattern)) {
                    return false;
                }
            }
            if (customModelData != null) {
                if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != customModelData) {
                    return false;
                }
            }
            return true;
        }
    }

    public Category(String id, String displayName, List<Rule> rules) {
        this.id = id;
        this.displayName = displayName;
        this.rules = rules;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<Rule> getRules() { return rules; }

    public boolean matches(ItemStack item) {
        if (rules.isEmpty()) return false;
        for (Rule rule : rules) {
            if (rule.matches(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesWildcard(String text, String pattern) {
        if (pattern == null || pattern.equals("*")) return true;
        if (text == null) return false;
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append("$");
        return text.matches(regex.toString());
    }
}
