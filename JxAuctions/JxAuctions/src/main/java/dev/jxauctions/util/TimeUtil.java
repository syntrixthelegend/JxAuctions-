package dev.jxauctions.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class TimeUtil {

    private TimeUtil() {}

    public static String formatRemaining(long millisRemaining, int timerFormat, String secUnit, String minUnit, String hourUnit, String dayUnit, String expiredLabel) {
        if (millisRemaining <= 0) {
            return expiredLabel;
        }
        long totalSeconds = millisRemaining / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        return switch (timerFormat) {
            case 2 -> {
                if (days > 0) yield days + dayUnit;
                if (hours > 0) yield hours + hourUnit;
                if (minutes > 0) yield minutes + minUnit;
                yield seconds + secUnit;
            }
            case 3 -> {
                StringBuilder sb = new StringBuilder();
                if (days > 0) sb.append(days).append(dayUnit).append(" ");
                if (hours > 0) sb.append(String.format("%02d", hours)).append(hourUnit).append(" ");
                if (minutes > 0) sb.append(String.format("%02d", minutes)).append(minUnit).append(" ");
                sb.append(String.format("%02d", seconds)).append(secUnit);
                yield sb.toString().trim();
            }
            default -> {
                long totalHours = totalSeconds / 3600L;
                yield String.format("%02d:%02d:%02d", totalHours, minutes, seconds);
            }
        };
    }

    public static String formatDate(long epochMillis, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date(epochMillis));
    }
}
