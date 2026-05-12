package de.xllogic.client.nocode;

public final class NoCodeBlock {
    private static final String DEFAULT_SIDE = "north";
    private static final String DEFAULT_TARGET_SIDE = "south";
    private static final String DEFAULT_ITEM_ID = "minecraft:cobblestone";
    private static final String DEFAULT_FLUID_ID = "minecraft:water";
    private static final int MAX_REDSTONE_LEVEL = 15;
    private static final int MAX_WORLD_RAIN_LEVEL = 15;
    private static final int MAX_WORLD_MOON_PHASE = 7;
    private static final int MAX_WORLD_DAY_TIME = 23_999;
    private static final int MAX_TRANSFER_AMOUNT = 64_000;
    private static final int DEFAULT_REDSTONE_THRESHOLD = 8;
    private static final int DEFAULT_WORLD_RAIN_LEVEL_THRESHOLD = 1;
    private static final int DEFAULT_WORLD_TIME_START = 0;
    private static final String DEFAULT_WORLD_TIME_END = "12000";

    private NoCodeBlockKind kind;
    private String text;
    private String deviceApiName;
    private String sideName;
    private String targetDeviceApiName;
    private String targetSideName;
    private int level;

    public NoCodeBlock(final NoCodeBlockKind kind) {
        this.kind = kind == null ? NoCodeBlockKind.PRINT_TEXT : kind;
        this.text = defaultText(this.kind);
        this.deviceApiName = "";
        this.sideName = DEFAULT_SIDE;
        this.targetDeviceApiName = "";
        this.targetSideName = DEFAULT_TARGET_SIDE;
        this.level = defaultLevel(this.kind);
    }

    public NoCodeBlock(final NoCodeBlockKind kind, final String text, final String deviceApiName, final String sideName, final int level) {
        this(kind, text, deviceApiName, sideName, "", DEFAULT_TARGET_SIDE, level);
    }

    public NoCodeBlock(final NoCodeBlockKind kind,
                       final String text,
                       final String deviceApiName,
                       final String sideName,
                       final String targetSideName,
                       final int level) {
        this(kind, text, deviceApiName, sideName, "", targetSideName, level);
    }

    public NoCodeBlock(final NoCodeBlockKind kind,
                       final String text,
                       final String deviceApiName,
                       final String sideName,
                       final String targetDeviceApiName,
                       final String targetSideName,
                       final int level) {
        this.kind = kind == null ? NoCodeBlockKind.PRINT_TEXT : kind;
        this.text = text == null ? defaultText(this.kind) : text;
        this.deviceApiName = deviceApiName == null ? "" : deviceApiName;
        this.sideName = normalizeSide(sideName);
        this.targetDeviceApiName = targetDeviceApiName == null ? "" : targetDeviceApiName;
        this.targetSideName = normalizeTargetSide(targetSideName);
        this.level = clampLevel(this.kind, level);
    }

    public NoCodeBlock copy() {
        return new NoCodeBlock(this.kind, this.text, this.deviceApiName, this.sideName, this.targetDeviceApiName, this.targetSideName, this.level);
    }

    public NoCodeBlockKind kind() {
        return this.kind;
    }

    public void setKind(final NoCodeBlockKind kind) {
        if (kind == null || kind == this.kind) {
            return;
        }
        this.kind = kind;
        this.level = clampLevel(kind, this.level);
        if (this.text.isBlank()) {
            this.text = defaultText(kind);
        }
    }

    public String text() {
        return this.text;
    }

    public void setText(final String text) {
        this.text = text == null ? "" : text;
    }

    public String deviceApiName() {
        return this.deviceApiName;
    }

    public void setDeviceApiName(final String deviceApiName) {
        this.deviceApiName = deviceApiName == null ? "" : deviceApiName;
    }

    public String sideName() {
        return this.sideName;
    }

    public void setSideName(final String sideName) {
        this.sideName = normalizeSide(sideName);
    }

    public String targetDeviceApiName() {
        return this.targetDeviceApiName;
    }

    public void setTargetDeviceApiName(final String targetDeviceApiName) {
        this.targetDeviceApiName = targetDeviceApiName == null ? "" : targetDeviceApiName;
    }

    public String targetSideName() {
        return this.targetSideName;
    }

    public void setTargetSideName(final String targetSideName) {
        this.targetSideName = normalizeTargetSide(targetSideName);
    }

    public int level() {
        return this.level;
    }

    public void setLevel(final int level) {
        this.level = clampLevel(this.kind, level);
    }

    public String summary() {
        return switch (this.kind) {
            case PRINT_TEXT -> "Print: " + summarize(this.text, 28);
            case SHOW_WORLD -> "Show world status";
            case IF_WORLD_DAY_NEXT -> "If day: run next block";
            case IF_WORLD_NIGHT_NEXT -> "If night: run next block";
            case IF_WORLD_THUNDERING_NEXT -> "If thunder: run next block";
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT -> "If rain >= " + this.level + ": run next block";
            case IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT -> "If rain > " + this.level + ": run next block";
            case IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT -> "If rain < " + this.level + ": run next block";
            case IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> "If rain = " + this.level + ": run next block";
            case IF_WORLD_TIME_WINDOW_NEXT -> "If time " + this.level + " to " + worldTimeWindowEnd(this.text) + ": run next block";
            case IF_WORLD_DAWN_NEXT -> "If dawn: run next block";
            case IF_WORLD_EVENING_NEXT -> "If evening: run next block";
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT -> "If moon >= " + this.level + ": run next block";
            case IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT -> "If moon > " + this.level + ": run next block";
            case IF_WORLD_MOON_PHASE_LESS_THAN_NEXT -> "If moon < " + this.level + ": run next block";
            case IF_WORLD_MOON_PHASE_EQUALS_NEXT -> "If moon = " + this.level + ": run next block";
            case SHOW_CLOCK -> "Show clock: " + fallback(this.deviceApiName, "choose clock");
            case SHOW_RAIN_SENSOR -> "Show rain: " + fallback(this.deviceApiName, "choose sensor");
            case IF_RAINING_NEXT -> "If raining: run next block";
            case IF_DRY_NEXT -> "If dry: run next block";
            case ELSE_NEXT -> "Otherwise: run next block";
            case LIST_DEVICES -> "List visible devices";
            case SHOW_DEVICE_STATE -> "Show device: " + fallback(this.deviceApiName, "choose device");
            case SHOW_MATERIAL_IO -> "Show material I/O: " + fallback(this.deviceApiName, "choose device");
            case COUNT_MATERIAL_ITEM -> "Count item: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName + " / " + fallback(this.text, DEFAULT_ITEM_ID);
            case MOVE_MATERIAL_ITEM -> "Move item: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName + " -> " + this.targetSideName + " / " + fallback(this.text, DEFAULT_ITEM_ID) + " x " + this.level;
            case MOVE_MATERIAL_ITEM_TO -> "Move item to I/O: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName + " -> " + fallback(this.targetDeviceApiName, "choose target") + " / " + this.targetSideName + " / " + fallback(this.text, DEFAULT_ITEM_ID) + " x " + this.level;
            case SHOW_MATERIAL_FLUIDS -> "Show fluid tanks: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName;
            case MOVE_MATERIAL_FLUID -> "Move fluid: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName + " -> " + this.targetSideName + " / " + fallback(this.text, DEFAULT_FLUID_ID) + " x " + this.level;
            case MOVE_MATERIAL_FLUID_TO -> "Move fluid to I/O: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName + " -> " + fallback(this.targetDeviceApiName, "choose target") + " / " + this.targetSideName + " / " + fallback(this.text, DEFAULT_FLUID_ID) + " x " + this.level;
            case IF_REDSTONE_AT_LEAST_NEXT -> "If redstone >= " + this.level + ": run next block";
            case IF_REDSTONE_GREATER_THAN_NEXT -> "If redstone > " + this.level + ": run next block";
            case IF_REDSTONE_LESS_THAN_NEXT -> "If redstone < " + this.level + ": run next block";
            case IF_REDSTONE_EQUALS_NEXT -> "If redstone = " + this.level + ": run next block";
            case IF_ITEM_COUNT_AT_LEAST_NEXT -> "If item >= " + this.level + ": " + fallback(this.text, DEFAULT_ITEM_ID);
            case IF_ITEM_COUNT_GREATER_THAN_NEXT -> "If item > " + this.level + ": " + fallback(this.text, DEFAULT_ITEM_ID);
            case IF_ITEM_COUNT_LESS_THAN_NEXT -> "If item < " + this.level + ": " + fallback(this.text, DEFAULT_ITEM_ID);
            case IF_ITEM_COUNT_EQUALS_NEXT -> "If item = " + this.level + ": " + fallback(this.text, DEFAULT_ITEM_ID);
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT -> "If fluid >= " + this.level + ": " + fallback(this.text, DEFAULT_FLUID_ID);
            case IF_FLUID_AMOUNT_GREATER_THAN_NEXT -> "If fluid > " + this.level + ": " + fallback(this.text, DEFAULT_FLUID_ID);
            case IF_FLUID_AMOUNT_LESS_THAN_NEXT -> "If fluid < " + this.level + ": " + fallback(this.text, DEFAULT_FLUID_ID);
            case IF_FLUID_AMOUNT_EQUALS_NEXT -> "If fluid = " + this.level + ": " + fallback(this.text, DEFAULT_FLUID_ID);
            case READ_REDSTONE -> "Read redstone: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName;
            case WRITE_REDSTONE -> "Write redstone: " + fallback(this.deviceApiName, "choose device") + " / " + this.sideName + " = " + this.level;
        };
    }

    private static int clampLevel(final NoCodeBlockKind kind, final int level) {
        if (kind == NoCodeBlockKind.IF_WORLD_MOON_PHASE_AT_LEAST_NEXT
                || kind == NoCodeBlockKind.IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT
                || kind == NoCodeBlockKind.IF_WORLD_MOON_PHASE_LESS_THAN_NEXT
                || kind == NoCodeBlockKind.IF_WORLD_MOON_PHASE_EQUALS_NEXT) {
            return Math.max(0, Math.min(MAX_WORLD_MOON_PHASE, level));
        }
        if (kind == NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT
                || kind == NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT
                || kind == NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT
                || kind == NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_EQUALS_NEXT) {
            return Math.max(0, Math.min(MAX_WORLD_RAIN_LEVEL, level));
        }
        if (kind == NoCodeBlockKind.IF_WORLD_TIME_WINDOW_NEXT) {
            return Math.max(0, Math.min(MAX_WORLD_DAY_TIME, level));
        }
        if (kind == NoCodeBlockKind.WRITE_REDSTONE
                || kind == NoCodeBlockKind.IF_REDSTONE_AT_LEAST_NEXT
                || kind == NoCodeBlockKind.IF_REDSTONE_GREATER_THAN_NEXT
                || kind == NoCodeBlockKind.IF_REDSTONE_LESS_THAN_NEXT
                || kind == NoCodeBlockKind.IF_REDSTONE_EQUALS_NEXT) {
            return Math.max(0, Math.min(MAX_REDSTONE_LEVEL, level));
        }
        if (kind == NoCodeBlockKind.MOVE_MATERIAL_ITEM
            || kind == NoCodeBlockKind.MOVE_MATERIAL_ITEM_TO
                || kind == NoCodeBlockKind.MOVE_MATERIAL_FLUID
            || kind == NoCodeBlockKind.MOVE_MATERIAL_FLUID_TO
                || kind == NoCodeBlockKind.IF_ITEM_COUNT_AT_LEAST_NEXT
                || kind == NoCodeBlockKind.IF_ITEM_COUNT_GREATER_THAN_NEXT
                || kind == NoCodeBlockKind.IF_ITEM_COUNT_LESS_THAN_NEXT
                || kind == NoCodeBlockKind.IF_ITEM_COUNT_EQUALS_NEXT
                || kind == NoCodeBlockKind.IF_FLUID_AMOUNT_AT_LEAST_NEXT
                || kind == NoCodeBlockKind.IF_FLUID_AMOUNT_GREATER_THAN_NEXT
                || kind == NoCodeBlockKind.IF_FLUID_AMOUNT_LESS_THAN_NEXT
                || kind == NoCodeBlockKind.IF_FLUID_AMOUNT_EQUALS_NEXT) {
            return Math.max(1, Math.min(MAX_TRANSFER_AMOUNT, level));
        }
        return Math.max(0, level);
    }

    private static String normalizeSide(final String sideName) {
        if (sideName == null || sideName.isBlank()) {
            return DEFAULT_SIDE;
        }
        return sideName;
    }

    private static String normalizeTargetSide(final String sideName) {
        if (sideName == null || sideName.isBlank()) {
            return DEFAULT_TARGET_SIDE;
        }
        return sideName;
    }

    private static String defaultText(final NoCodeBlockKind kind) {
        return switch (kind) {
            case PRINT_TEXT -> "Hello from XL Logic";
            case IF_WORLD_TIME_WINDOW_NEXT -> DEFAULT_WORLD_TIME_END;
            case COUNT_MATERIAL_ITEM, MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT -> DEFAULT_ITEM_ID;
            case MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO, IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> DEFAULT_FLUID_ID;
            default -> "";
        };
    }

    private static int defaultLevel(final NoCodeBlockKind kind) {
        return switch (kind) {
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT, IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT, IF_WORLD_MOON_PHASE_LESS_THAN_NEXT, IF_WORLD_MOON_PHASE_EQUALS_NEXT -> 0;
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT, IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> DEFAULT_WORLD_RAIN_LEVEL_THRESHOLD;
            case IF_WORLD_TIME_WINDOW_NEXT -> DEFAULT_WORLD_TIME_START;
            case WRITE_REDSTONE -> 15;
            case MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO -> 1;
            case MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO -> 1_000;
            case IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> DEFAULT_REDSTONE_THRESHOLD;
            case IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT -> 1;
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> 1_000;
            default -> 0;
        };
    }

    private static String fallback(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String summarize(final String value, final int maxLength) {
        final String safeValue = value == null ? "" : value.trim();
        if (safeValue.length() <= maxLength) {
            return safeValue;
        }
        return safeValue.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static int worldTimeWindowEnd(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Integer.parseInt(DEFAULT_WORLD_TIME_END);
        }
        try {
            return Math.max(0, Math.min(MAX_WORLD_DAY_TIME, Integer.parseInt(rawValue.trim())));
        } catch (NumberFormatException exception) {
            return Integer.parseInt(DEFAULT_WORLD_TIME_END);
        }
    }
}