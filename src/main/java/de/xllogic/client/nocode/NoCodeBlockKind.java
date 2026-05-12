package de.xllogic.client.nocode;

public enum NoCodeBlockKind {
    PRINT_TEXT("Print text", "Writes a plain text line to the screen output."),
    SHOW_WORLD("Show world", "Displays the current world status when server data is available."),
    IF_WORLD_DAY_NEXT("If world is day", "Runs the next block only while the world reports daytime."),
    IF_WORLD_NIGHT_NEXT("If world is night", "Runs the next block only while the world reports nighttime."),
    IF_WORLD_THUNDERING_NEXT("If world is thundering", "Runs the next block only while the world reports thunder."),
    IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT("If rain level >=", "Runs the next block only when the world rain level reaches at least the chosen amount."),
    IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT("If rain level >", "Runs the next block only when the world rain level is greater than the chosen amount."),
    IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT("If rain level <", "Runs the next block only when the world rain level is lower than the chosen amount."),
    IF_WORLD_RAIN_LEVEL_EQUALS_NEXT("If rain level =", "Runs the next block only when the world rain level equals the chosen amount."),
    IF_WORLD_TIME_WINDOW_NEXT("If world time in window", "Runs the next block only when the current world time is inside the chosen tick window."),
    IF_WORLD_DAWN_NEXT("If world is dawn", "Runs the next block only during a built-in dawn window around sunrise."),
    IF_WORLD_EVENING_NEXT("If world is evening", "Runs the next block only during a built-in evening window around sunset."),
    IF_WORLD_MOON_PHASE_AT_LEAST_NEXT("If moon phase >=", "Runs the next block only when the world moon phase reaches at least the chosen amount."),
    IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT("If moon phase >", "Runs the next block only when the world moon phase is greater than the chosen amount."),
    IF_WORLD_MOON_PHASE_LESS_THAN_NEXT("If moon phase <", "Runs the next block only when the world moon phase is lower than the chosen amount."),
    IF_WORLD_MOON_PHASE_EQUALS_NEXT("If moon phase =", "Runs the next block only when the world moon phase equals the chosen amount."),
    SHOW_CLOCK("Show clock", "Reads game time, day time and real time from one clock device."),
    SHOW_RAIN_SENSOR("Show rain sensor", "Shows whether rain currently reaches one rain sensor."),
    IF_RAINING_NEXT("If raining", "Runs the next block only while the selected rain sensor reports rain."),
    IF_DRY_NEXT("If dry", "Runs the next block only while the selected rain sensor reports no rain."),
    ELSE_NEXT("Otherwise", "Runs the next block only when the previous guard did not match."),
    LIST_DEVICES("List devices", "Shows a table with all visible devices."),
    SHOW_DEVICE_STATE("Show device state", "Displays the current state snapshot of one device."),
    SHOW_MATERIAL_IO("Show material I/O", "Displays mode, channels and capacity of one material I/O device."),
    COUNT_MATERIAL_ITEM("Count item", "Counts one item on one side of a material I/O device."),
    MOVE_MATERIAL_ITEM("Move item", "Moves one matching item amount from one side to another on a material I/O device."),
    MOVE_MATERIAL_ITEM_TO("Move item to material I/O", "Moves one matching item amount from one material I/O device side to another named material I/O device."),
    SHOW_MATERIAL_FLUIDS("Show fluid tanks", "Shows all non-empty fluid tanks on one side of a material I/O device."),
    MOVE_MATERIAL_FLUID("Move fluid", "Moves one matching fluid amount from one side to another on a material I/O device."),
    MOVE_MATERIAL_FLUID_TO("Move fluid to material I/O", "Moves one matching fluid amount from one material I/O device side to another named material I/O device."),
    IF_REDSTONE_AT_LEAST_NEXT("If redstone >=", "Runs the next block only when one redstone side reaches at least the chosen level."),
    IF_REDSTONE_GREATER_THAN_NEXT("If redstone >", "Runs the next block only when one redstone side is greater than the chosen level."),
    IF_REDSTONE_LESS_THAN_NEXT("If redstone <", "Runs the next block only when one redstone side is lower than the chosen level."),
    IF_REDSTONE_EQUALS_NEXT("If redstone =", "Runs the next block only when one redstone side equals the chosen level."),
    IF_ITEM_COUNT_AT_LEAST_NEXT("If item count >=", "Runs the next block only when the selected item count reaches the chosen amount."),
    IF_ITEM_COUNT_GREATER_THAN_NEXT("If item count >", "Runs the next block only when the selected item count is greater than the chosen amount."),
    IF_ITEM_COUNT_LESS_THAN_NEXT("If item count <", "Runs the next block only when the selected item count is lower than the chosen amount."),
    IF_ITEM_COUNT_EQUALS_NEXT("If item count =", "Runs the next block only when the selected item count equals the chosen amount."),
    IF_FLUID_AMOUNT_AT_LEAST_NEXT("If fluid amount >=", "Runs the next block only when the selected fluid amount reaches the chosen amount."),
    IF_FLUID_AMOUNT_GREATER_THAN_NEXT("If fluid amount >", "Runs the next block only when the selected fluid amount is greater than the chosen amount."),
    IF_FLUID_AMOUNT_LESS_THAN_NEXT("If fluid amount <", "Runs the next block only when the selected fluid amount is lower than the chosen amount."),
    IF_FLUID_AMOUNT_EQUALS_NEXT("If fluid amount =", "Runs the next block only when the selected fluid amount equals the chosen amount."),
    READ_REDSTONE("Read redstone", "Reads one redstone side and shows the measured level."),
    WRITE_REDSTONE("Write redstone", "Sets one redstone side to a chosen level.");

    private final String label;
    private final String description;

    NoCodeBlockKind(final String label, final String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return this.label;
    }

    public String description() {
        return this.description;
    }
}