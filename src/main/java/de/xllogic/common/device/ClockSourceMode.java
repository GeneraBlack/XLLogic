package de.xllogic.common.device;

public enum ClockSourceMode {
    GAME_TIME,
    REAL_TIME,
    BOTH;

    public ClockSourceMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
