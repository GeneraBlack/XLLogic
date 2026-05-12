package de.xllogic.client.nocode;

import java.util.ArrayList;
import java.util.List;

public final class NoCodeProgram {
    private boolean repeat;
    private int repeatTicks = 20;
    private final List<NoCodeBlock> blocks = new ArrayList<>();

    public NoCodeProgram() {
    }

    public NoCodeProgram(final boolean repeat, final int repeatTicks, final List<NoCodeBlock> blocks) {
        this.repeat = repeat;
        this.setRepeatTicks(repeatTicks);
        if (blocks != null) {
            for (final NoCodeBlock block : blocks) {
                if (block != null) {
                    this.blocks.add(block.copy());
                }
            }
        }
    }

    public NoCodeProgram copy() {
        return new NoCodeProgram(this.repeat, this.repeatTicks, this.blocks);
    }

    public boolean repeat() {
        return this.repeat;
    }

    public void setRepeat(final boolean repeat) {
        this.repeat = repeat;
    }

    public int repeatTicks() {
        return this.repeatTicks;
    }

    public void setRepeatTicks(final int repeatTicks) {
        this.repeatTicks = Math.max(1, Math.min(1_200, repeatTicks));
    }

    public List<NoCodeBlock> blocks() {
        return this.blocks;
    }
}