package de.xllogic.common.device;

public enum RedstoneIOMode {
    INPUT,
    OUTPUT;

    public RedstoneIOMode next() {
        return this == INPUT ? OUTPUT : INPUT;
    }
}
