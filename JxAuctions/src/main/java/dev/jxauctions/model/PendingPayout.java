package dev.jxauctions.model;

import java.util.UUID;

public record PendingPayout(
        int id,
        UUID sellerUuid,
        String buyerName,
        String itemName,
        int itemAmount,
        String currencyId,
        double grossAmount,
        double netAmount,
        double taxPercent,
        long createdAt
) {}
