package dev.jxauctions.model;

import java.util.Locale;

public enum SortType {
    NEWEST("newest"),
    OLDEST("oldest"),
    CHEAPEST("cheapest"),
    PRICIEST("priciest");

    private final String id;

    SortType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static SortType fromString(String value) {
        if (value == null) return NEWEST;
        for (SortType type : values()) {
            if (type.id.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return NEWEST;
    }

    public SortType next() {
        SortType[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }
}
