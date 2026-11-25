package dev.jxauctions.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberUtil {

    private NumberUtil() {}

    public static double parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String clean = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier = 1.0;

        if (clean.endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("t")) {
            multiplier = 1_000_000_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        }

        try {
            double val = Double.parseDouble(clean);
            double result = val * multiplier;
            if (Double.isNaN(result) || Double.isInfinite(result) || result <= 0) {
                return -1;
            }
            return result;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String format(double value, int mode, String pattern) {
        switch (mode) {
            case 1 -> {
                return formatShort(value);
            }
            case 2 -> {
                if (value == Math.floor(value)) {
                    return String.valueOf((long) value);
                }
                return String.valueOf(value);
            }
            default -> {
                DecimalFormat df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
                return df.format(value);
            }
        }
    }

    public static String formatShort(double value) {
        if (value < 1_000) {
            return format(value, 0, "#,###.##");
        } else if (value < 1_000_000) {
            return String.format(Locale.US, "%.2fK", value / 1_000.0).replaceAll("\\.00K$", "K");
        } else if (value < 1_000_000_000) {
            return String.format(Locale.US, "%.2fM", value / 1_000_000.0).replaceAll("\\.00M$", "M");
        } else if (value < 1_000_000_000_000L) {
            return String.format(Locale.US, "%.2fB", value / 1_000_000_000.0).replaceAll("\\.00B$", "B");
        } else {
            return String.format(Locale.US, "%.2fT", value / 1_000_000_000_000.0).replaceAll("\\.00T$", "T");
        }
    }
}
