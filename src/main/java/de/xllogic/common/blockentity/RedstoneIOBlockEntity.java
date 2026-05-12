package de.xllogic.common.blockentity;

import de.xllogic.common.device.RedstoneIOMode;
import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.network.XLRedstoneBusResolver;
import de.xllogic.common.registry.XLBlockEntities;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class RedstoneIOBlockEntity extends NamedNetworkEndpointBlockEntity {
    private RedstoneIOMode mode = RedstoneIOMode.INPUT;
    private int[] sideLevels = new int[Direction.values().length];
    private int[] sideBusChannels = new int[]{0, 1, 2, 3, 4, 5};

    public RedstoneIOBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.REDSTONE_IO.get(), pos, blockState);
    }

    @Override
    public boolean supportsSideNaming() {
        return true;
    }

    public RedstoneIOMode getMode() {
        return this.mode;
    }

    public void cycleMode() {
        this.setMode(this.mode.next());
    }

    public void setMode(final RedstoneIOMode mode) {
        if (mode == null || this.mode == mode) {
            return;
        }

        final boolean redstoneStateMayChange = this.mode == RedstoneIOMode.OUTPUT || mode == RedstoneIOMode.OUTPUT;
        this.mode = mode;
        if (this.mode == RedstoneIOMode.INPUT) {
            this.captureInputs();
            if (redstoneStateMayChange) {
                this.notifyBusChanged();
            }
            return;
        }

        if (redstoneStateMayChange) {
            this.notifyRedstoneChanged();
        } else {
            this.markStateChanged();
        }
    }

    public void captureInputs() {
        if (this.level == null || this.mode != RedstoneIOMode.INPUT) {
            return;
        }

        final int[] sampled = new int[Direction.values().length];
        for (final Direction direction : Direction.values()) {
            sampled[direction.ordinal()] = this.sampleInput(direction);
        }
        if (!Arrays.equals(this.sideLevels, sampled)) {
            this.sideLevels = sampled;
            this.markStateChanged();
        }
    }

    public int getSideLevel(final Direction side) {
        return this.sideLevels[side.ordinal()];
    }

    public int setSideLevel(final Direction side, final int level) {
        if (this.mode != RedstoneIOMode.OUTPUT) {
            throw new IllegalStateException("Redstone I/O must be in OUTPUT mode before writing signals.");
        }

        final int clampedLevel = Mth.clamp(level, 0, 15);
        if (this.sideLevels[side.ordinal()] == clampedLevel) {
            return clampedLevel;
        }

        this.sideLevels[side.ordinal()] = clampedLevel;
        this.notifyRedstoneChanged();
        return clampedLevel;
    }

    public int getBusChannel(final Direction side) {
        return this.sideBusChannels[side.ordinal()];
    }

    public int setBusChannel(final Direction side, final int channel) {
        final int clampedChannel = Mth.clamp(channel, 0, 15);
        if (this.sideBusChannels[side.ordinal()] == clampedChannel) {
            return clampedChannel;
        }

        this.sideBusChannels[side.ordinal()] = clampedChannel;
        if (this.mode == RedstoneIOMode.INPUT) {
            this.captureInputs();
        } else {
            this.markStateChanged();
            this.notifyBusChanged();
        }
        return clampedChannel;
    }

    public int getSignal(final Direction side) {
        if (this.mode != RedstoneIOMode.OUTPUT) {
            return 0;
        }
        return this.sideLevels[side.ordinal()];
    }

    public int getMaxSignalLevel() {
        return Arrays.stream(this.sideLevels).max().orElse(0);
    }

    public String describeState() {
        final String aliases = this.sideAliasSummary();
        return "Endpoint: " + this.getEndpointName() + " | Redstone I/O mode: " + this.mode + " | levels: " + Arrays.toString(this.sideLevels) + " | max level: " + this.getMaxSignalLevel() + " | channels: " + Arrays.toString(this.sideBusChannels) + (aliases.isBlank() ? "" : " | side aliases: " + aliases);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mode = RedstoneIOMode.valueOf(tag.getString("Mode"));
        this.sideLevels = ensureSize(tag.getIntArray("SideLevels"), Direction.values().length);
        this.sideBusChannels = ensureSize(tag.getIntArray("SideBusChannels"), Direction.values().length);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Mode", this.mode.name());
        tag.putIntArray("SideLevels", this.sideLevels);
        tag.putIntArray("SideBusChannels", this.sideBusChannels);
    }

    private static int[] ensureSize(final int[] source, final int size) {
        if (source.length == size) {
            return source;
        }
        final int[] result = new int[size];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, size));
        return result;
    }

    private void notifyRedstoneChanged() {
        this.markStateChanged();
        if (this.level == null) {
            return;
        }

        final BlockState state = this.getBlockState();
        this.level.updateNeighborsAt(this.worldPosition, state.getBlock());
        for (final Direction direction : Direction.values()) {
            this.level.updateNeighborsAt(this.worldPosition.relative(direction), state.getBlock());
        }
        this.notifyBusChanged();
    }

    private int sampleInput(final Direction direction) {
        if (this.level == null) {
            return 0;
        }

        final BlockPos neighborPos = this.worldPosition.relative(direction);
        if (XLRedstoneBusResolver.isBusCable(this.level.getBlockState(neighborPos))) {
            return XLRedstoneBusResolver.resolveChannelSignal(this.level, neighborPos, this.getBusChannel(direction));
        }
        return this.level.getSignal(neighborPos, direction);
    }

    private void notifyBusChanged() {
        if (this.level != null && !this.level.isClientSide()) {
            XLRedstoneBusResolver.notifyAdjacentNetworks(this.level, this.worldPosition);
        }
    }
}
