package com.crystalrealm.ecotalerewards.model;

/**
 * State of a single calendar day reward slot.
 */
public enum DayStatus {

    /** Day is in the future — not yet reachable. */
    LOCKED("locked"),

    /** Day is available for claim right now. */
    AVAILABLE("available"),

    /** Reward has already been claimed. */
    CLAIMED("claimed"),

    /** Day was missed (strict mode or grace expired). */
    MISSED("missed");

    private final String id;

    DayStatus(String id) { this.id = id; }

    public String getId() { return id; }

    public static DayStatus fromId(String id) {
        for (DayStatus s : values()) {
            if (s.id.equalsIgnoreCase(id)) return s;
        }
        return LOCKED;
    }
}
