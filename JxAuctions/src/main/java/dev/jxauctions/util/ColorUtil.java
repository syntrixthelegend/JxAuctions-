package dev.jxauctions.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ColorUtil {

    private static final Pattern HEX_AMPERSAND_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_BRACKET_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ColorUtil() {}

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String text = input;

        // Render full MiniMessage gradients/tags via Kyori Adventure when present
        if (text.contains("<gradient:") || text.contains("<b>") || text.contains("<white>") || text.contains("<gray>") || text.contains("<#")) {
            try {
                Component comp = toComponent(text);
                return LegacyComponentSerializer.legacySection().serialize(comp);
            } catch (Exception ignored) {
                text = convertSimpleMiniMessageToLegacy(text);
            }
        }

        Matcher hexMatcher = HEX_AMPERSAND_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            hexMatcher.appendReplacement(buffer, Matcher.quoteReplacement(ChatColor.of("#" + hex).toString()));
        }
        hexMatcher.appendTail(buffer);
        text = buffer.toString();

        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> colorize(List<String> lines) {
        return lines.stream().map(ColorUtil::colorize).collect(Collectors.toList());
    }

    public static String stripColor(String input) {
        if (input == null) return "";
        return ChatColor.stripColor(colorize(input));
    }

    public static Component toComponent(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        try {
            if (input.contains("<gradient:") || input.contains("<#") || input.contains("<b>")) {
                String normalized = HEX_AMPERSAND_PATTERN.matcher(input).replaceAll("<#$1>");
                normalized = normalized
                        .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                        .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                        .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                        .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                        .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                        .replace("&f", "<white>").replace("&l", "<b>").replace("&m", "<st>")
                        .replace("&n", "<u>").replace("&o", "<i>").replace("&k", "<obf>")
                        .replace("&r", "<reset>");
                return MINI_MESSAGE.deserialize(normalized);
            }
        } catch (Exception ignored) {
        }
        return LegacyComponentSerializer.legacySection().deserialize(colorize(input));
    }

    private static String convertSimpleMiniMessageToLegacy(String input) {
        String result = input;
        // Convert <#RRGGBB> to &#RRGGBB
        result = HEX_BRACKET_PATTERN.matcher(result).replaceAll("&#$1");
        result = result.replace("<b>", "&l").replace("</b>", "&r")
                .replace("<white>", "&f").replace("</white>", "")
                .replace("<gray>", "&7").replace("</gray>", "")
                .replace("<red>", "&c").replace("</red>", "")
                .replace("<green>", "&a").replace("</green>", "");
        // Strip gradient wrapper tags to fallback hex color
        result = result.replaceAll("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>", "&$1")
                .replace("</gradient>", "");
        return result;
    }
}
