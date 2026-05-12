package de.xllogic.common.device;

public enum MaterialIOMode {
    ITEMS_ONLY,
    FLUIDS_ONLY,
    HYBRID;

    public MaterialIOMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
