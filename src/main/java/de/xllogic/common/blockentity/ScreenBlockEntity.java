package de.xllogic.common.blockentity;

import de.xllogic.common.block.ScreenBlock;
import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import de.xllogic.runtime.ComputerOutputEntry;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

public final class ScreenBlockEntity extends NamedNetworkEndpointBlockEntity {
    private static final String TAG_SPAN_X = "SpanX";
    private static final String TAG_SPAN_Y = "SpanY";
    private static final String TAG_CONTROLLER = "Controller";
    private static final String TAG_CONTROLLER_POS = "ControllerPos";
    private static final String TAG_LINKED_COMPUTER_POS = "LinkedComputerPos";
    private static final String TAG_PAGE_CURSOR = "PageCursor";
    private static final String TAG_FOCUS_ENTRY_CURSOR = "FocusEntryCursor";
    private static final String TAG_FOCUS_FIELD_OFFSET = "FocusFieldOffset";
    private static final String TAG_FOCUS_ROW_OFFSET = "FocusRowOffset";
    private static final String TAG_FOCUS_COLUMN_OFFSET = "FocusColumnOffset";
    private static final String TAG_TARGET_OUTPUT_ENTRIES = "TargetOutputEntries";
    private static final int MAX_TARGET_OUTPUT_ENTRIES = 256;
    private static final long ERROR_BLINK_DURATION_MILLIS = 2600L;

    private BlockPos linkedComputerPos;
    private BlockPos controllerPos;
    private int spanX = 1;
    private int spanY = 1;
    private boolean controller = true;
    private int pageCursor;
    private int focusEntryCursor = -1;
    private int focusFieldOffset;
    private int focusRowOffset;
    private int focusColumnOffset;
    private List<ComputerOutputEntry> targetOutputEntries = List.of();
    private transient String observedLedMode = "unlinked";
    private transient long observedLedSinceMillis;
    private transient long errorBlinkUntilMillis;

    public ScreenBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.SCREEN.get(), pos, blockState);
    }

    public BlockPos getLinkedComputerPos() {
        return this.linkedComputerPos == null ? null : this.linkedComputerPos.immutable();
    }

    public boolean hasLinkedComputer() {
        return this.linkedComputerPos != null;
    }

    public int getSpanX() {
        return this.spanX;
    }

    public int getSpanY() {
        return this.spanY;
    }

    public boolean isController() {
        return this.controller;
    }

    public boolean isSoloScreen() {
        return this.spanX <= 1 && this.spanY <= 1;
    }

    public BlockPos getControllerPos() {
        if (this.controller || this.controllerPos == null) {
            return this.worldPosition.immutable();
        }
        return this.controllerPos.immutable();
    }

    public boolean controllerChunkLoaded() {
        return this.controller || this.level != null && this.level.isLoaded(this.getControllerPos());
    }

    public boolean hasLoadedControllerScreen() {
        return this.resolveLoadedControllerScreen() != null;
    }

    public int getPageCursor() {
        return Math.max(0, this.pageCursor);
    }

    public boolean hasFocusedOutput() {
        return this.focusEntryCursor >= 0;
    }

    public int getFocusEntryCursor() {
        return this.focusEntryCursor;
    }

    public int getFocusFieldOffset() {
        return Math.max(0, this.focusFieldOffset);
    }

    public int getFocusRowOffset() {
        return Math.max(0, this.focusRowOffset);
    }

    public int getFocusColumnOffset() {
        return Math.max(0, this.focusColumnOffset);
    }

    public boolean isLinkedTo(final BlockPos computerPos) {
        return this.linkedComputerPos != null && this.linkedComputerPos.equals(computerPos);
    }

    public boolean setLinkedComputerPos(final BlockPos computerPos) {
        final BlockPos sanitizedPos = computerPos == null ? null : computerPos.immutable();
        if (Objects.equals(this.linkedComputerPos, sanitizedPos)) {
            return false;
        }

        this.linkedComputerPos = sanitizedPos;
        this.resetViewState();
        this.targetOutputEntries = List.of();
        this.markStateChanged();
        if (this.level instanceof net.minecraft.world.level.Level level && !level.isClientSide()) {
            ScreenMultiblockManager.rebuildAround(level, this.worldPosition);
        }
        return true;
    }

    public boolean clearLinkedComputer() {
        return this.setLinkedComputerPos(null);
    }

    public List<ComputerOutputEntry> getTargetOutputEntries() {
        return this.targetOutputEntries;
    }

    public boolean hasTargetOutput() {
        return !this.targetOutputEntries.isEmpty();
    }

    public List<ComputerOutputEntry> resolveDisplayOutputEntries(final ComputerRuntimeSnapshot runtimeState) {
        return this.resolveDisplayOutputEntries(runtimeState == null ? List.of() : runtimeState.outputEntries());
    }

    public List<ComputerOutputEntry> resolveDisplayOutputEntries(final List<ComputerOutputEntry> fallbackEntries) {
        final ScreenBlockEntity outputTarget = this.resolveOutputTargetScreen();
        if (outputTarget != null && !outputTarget.targetOutputEntries.isEmpty()) {
            return outputTarget.targetOutputEntries;
        }
        return fallbackEntries == null ? List.of() : fallbackEntries;
    }

    public boolean emitTargetOutput(final ComputerOutputEntry outputEntry) {
        if (outputEntry == null) {
            return false;
        }

        final ScreenBlockEntity outputTarget = this.resolveOutputTargetScreen();
        if (outputTarget == null) {
            return false;
        }

        final ArrayList<ComputerOutputEntry> entries = new ArrayList<>(outputTarget.targetOutputEntries);
        entries.add(outputEntry);
        if (entries.size() > MAX_TARGET_OUTPUT_ENTRIES) {
            entries.subList(0, entries.size() - MAX_TARGET_OUTPUT_ENTRIES).clear();
        }
        outputTarget.targetOutputEntries = List.copyOf(entries);
        outputTarget.markStateChanged();
        return true;
    }

    public boolean clearTargetOutput() {
        final ScreenBlockEntity outputTarget = this.resolveOutputTargetScreen();
        if (outputTarget == null || outputTarget.targetOutputEntries.isEmpty()) {
            return false;
        }

        outputTarget.targetOutputEntries = List.of();
        outputTarget.resetViewState();
        outputTarget.markStateChanged();
        return true;
    }

    public ComputerBlockEntity resolveLinkedComputer() {
        if (this.level == null || this.linkedComputerPos == null || !this.level.isLoaded(this.linkedComputerPos)) {
            return null;
        }
        return this.level.getBlockEntity(this.linkedComputerPos) instanceof ComputerBlockEntity computer ? computer : null;
    }

    public ScreenBlockEntity resolveLoadedControllerScreen() {
        if (this.controller) {
            return this;
        }
        if (this.level == null) {
            return null;
        }

        final BlockPos targetControllerPos = this.getControllerPos();
        if (!this.level.isLoaded(targetControllerPos)) {
            return null;
        }
        return this.level.getBlockEntity(targetControllerPos) instanceof ScreenBlockEntity screen && screen.isController() ? screen : null;
    }

    public ScreenBlockEntity resolveControllerScreen() {
        final ScreenBlockEntity loadedController = this.resolveLoadedControllerScreen();
        return loadedController == null ? this : loadedController;
    }

    public int getPanelColumnIndex() {
        if (this.isController()) {
            return 1;
        }

        final Direction facing = this.getBlockState().getValue(ScreenBlock.FACING);
        final Direction horizontalDirection = facing.getCounterClockWise();
        return Math.max(1, distanceAlong(this.getControllerPos(), this.worldPosition, horizontalDirection) + 1);
    }

    public int getPanelRowIndex() {
        if (this.isController()) {
            return 1;
        }
        return Math.max(1, this.worldPosition.getY() - this.getControllerPos().getY() + 1);
    }

    public String multiblockSummary() {
        if (this.isSoloScreen()) {
            return "1x1 solo";
        }
        if (this.isController()) {
            return this.spanX + "x" + this.spanY + " controller";
        }
        return this.spanX + "x" + this.spanY + " panel " + this.getPanelColumnIndex() + "," + this.getPanelRowIndex();
    }

    public boolean setMultiblockState(final BlockPos newControllerPos, final int newSpanX, final int newSpanY) {
        final BlockPos sanitizedControllerPos = newControllerPos == null ? this.worldPosition.immutable() : newControllerPos.immutable();
        final int sanitizedSpanX = Math.max(1, newSpanX);
        final int sanitizedSpanY = Math.max(1, newSpanY);
        final boolean newController = this.worldPosition.equals(sanitizedControllerPos);
        boolean changed = false;

        if (!Objects.equals(this.controllerPos, sanitizedControllerPos)) {
            this.controllerPos = sanitizedControllerPos;
            changed = true;
        }
        if (this.spanX != sanitizedSpanX) {
            this.spanX = sanitizedSpanX;
            changed = true;
        }
        if (this.spanY != sanitizedSpanY) {
            this.spanY = sanitizedSpanY;
            changed = true;
        }
        if (this.controller != newController) {
            this.controller = newController;
            changed = true;
        }
        if (!this.controller && (this.pageCursor != 0 || this.hasFocusedOutput() || this.focusFieldOffset != 0 || this.focusRowOffset != 0 || this.focusColumnOffset != 0)) {
            this.resetViewState();
            changed = true;
        }
        if (!this.controller && !this.targetOutputEntries.isEmpty()) {
            this.targetOutputEntries = List.of();
            changed = true;
        }
        if (changed) {
            this.markStateChanged();
        }
        return changed;
    }

    public boolean advancePageCursor() {
        if (this.pageCursor == Integer.MAX_VALUE) {
            return false;
        }
        return this.setPageCursor(this.pageCursor + 1);
    }

    public boolean retreatPageCursor() {
        return this.setPageCursor(Math.max(0, this.pageCursor - 1));
    }

    public boolean resetPageCursor() {
        return this.setPageCursor(0);
    }

    public boolean setFocusedOutputCursor(final int newFocusEntryCursor) {
        return this.focusOutput(newFocusEntryCursor, 0, 0, 0);
    }

    public boolean focusOutput(final int newFocusEntryCursor, final int newFocusFieldOffset, final int newFocusRowOffset, final int newFocusColumnOffset) {
        final int sanitizedCursor = Math.max(-1, newFocusEntryCursor);
        final int sanitizedFieldOffset = sanitizedCursor < 0 ? 0 : Math.max(0, newFocusFieldOffset);
        final int sanitizedRowOffset = sanitizedCursor < 0 ? 0 : Math.max(0, newFocusRowOffset);
        final int sanitizedColumnOffset = sanitizedCursor < 0 ? 0 : Math.max(0, newFocusColumnOffset);
        if (this.focusEntryCursor == sanitizedCursor
                && this.focusFieldOffset == sanitizedFieldOffset
                && this.focusRowOffset == sanitizedRowOffset
                && this.focusColumnOffset == sanitizedColumnOffset) {
            return false;
        }

        this.focusEntryCursor = sanitizedCursor;
        this.focusFieldOffset = sanitizedFieldOffset;
        this.focusRowOffset = sanitizedRowOffset;
        this.focusColumnOffset = sanitizedColumnOffset;
        this.markStateChanged();
        return true;
    }

    public boolean clearFocusedOutput() {
        return this.focusOutput(-1, 0, 0, 0);
    }

    public boolean adjustFocusFieldOffset(final int delta) {
        return this.setFocusOffsets(this.focusFieldOffset + delta, this.focusRowOffset, this.focusColumnOffset);
    }

    public boolean adjustFocusRowOffset(final int delta) {
        return this.setFocusOffsets(this.focusFieldOffset, this.focusRowOffset + delta, this.focusColumnOffset);
    }

    public boolean adjustFocusColumnOffset(final int delta) {
        return this.setFocusOffsets(this.focusFieldOffset, this.focusRowOffset, this.focusColumnOffset + delta);
    }

    public LedAnimationState observeLedMode(final String mode) {
        final String safeMode = sanitizeLedMode(mode);
        final long now = Util.getMillis();
        if (!safeMode.equals(this.observedLedMode)) {
            this.observedLedMode = safeMode;
            this.observedLedSinceMillis = now;
            this.errorBlinkUntilMillis = "error".equals(safeMode) ? now + ERROR_BLINK_DURATION_MILLIS : 0L;
        }
        return new LedAnimationState(safeMode, Math.max(0L, now - this.observedLedSinceMillis), now < this.errorBlinkUntilMillis);
    }

    public String describeState() {
        final ScreenBlockEntity activeScreen = this.resolveLoadedControllerScreen();
        final String activePageCursor = activeScreen == null ? "controller-unloaded" : Integer.toString(activeScreen.getPageCursor());
        final String activeFocus;
        if (activeScreen == null) {
            activeFocus = "controller-unloaded";
        } else if (activeScreen.hasFocusedOutput()) {
            activeFocus = Integer.toString(activeScreen.getFocusEntryCursor());
        } else {
            activeFocus = "none";
        }
        final String computer;
        if (this.linkedComputerPos == null) {
            computer = "none";
        } else {
            final ComputerBlockEntity linkedComputer = this.resolveLinkedComputer();
            final String suffix = linkedComputer == null ? "offline" : linkedComputer.runtimeStatus();
            computer = this.linkedComputerPos.toShortString() + " (" + suffix + ")";
        }
        final String controllerLabel;
        if (this.isController()) {
            controllerLabel = "self";
        } else if (!this.controllerChunkLoaded()) {
            controllerLabel = this.getControllerPos().toShortString() + " (chunk unloaded)";
        } else {
            controllerLabel = this.getControllerPos().toShortString() + (this.hasLoadedControllerScreen() ? " (loaded)" : " (unavailable)");
        }
        return "Endpoint: " + this.getEndpointName() + " | panel: " + this.multiblockSummary() + " | controller: " + controllerLabel + " | facing: " + this.getBlockState().getValue(ScreenBlock.FACING).getSerializedName() + " | linked computer: " + computer + " | page cursor: " + activePageCursor + " | focus: " + activeFocus;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof net.minecraft.world.level.Level level && !level.isClientSide()) {
            ScreenMultiblockManager.rebuildAround(level, this.worldPosition);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.spanX = Math.max(1, tag.getInt(TAG_SPAN_X));
        this.spanY = Math.max(1, tag.getInt(TAG_SPAN_Y));
        this.controller = !tag.contains(TAG_CONTROLLER) || tag.getBoolean(TAG_CONTROLLER);
        this.controllerPos = tag.contains(TAG_CONTROLLER_POS) ? BlockPos.of(tag.getLong(TAG_CONTROLLER_POS)) : this.worldPosition;
        this.linkedComputerPos = tag.contains(TAG_LINKED_COMPUTER_POS) ? BlockPos.of(tag.getLong(TAG_LINKED_COMPUTER_POS)) : null;
        this.pageCursor = Math.max(0, tag.getInt(TAG_PAGE_CURSOR));
        this.focusEntryCursor = tag.contains(TAG_FOCUS_ENTRY_CURSOR) ? Math.max(-1, tag.getInt(TAG_FOCUS_ENTRY_CURSOR)) : -1;
        this.focusFieldOffset = Math.max(0, tag.getInt(TAG_FOCUS_FIELD_OFFSET));
        this.focusRowOffset = Math.max(0, tag.getInt(TAG_FOCUS_ROW_OFFSET));
        this.focusColumnOffset = Math.max(0, tag.getInt(TAG_FOCUS_COLUMN_OFFSET));
        this.targetOutputEntries = readOutputEntries(tag, TAG_TARGET_OUTPUT_ENTRIES);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_SPAN_X, this.spanX);
        tag.putInt(TAG_SPAN_Y, this.spanY);
        tag.putBoolean(TAG_CONTROLLER, this.controller);
        tag.putLong(TAG_CONTROLLER_POS, this.getControllerPos().asLong());
        tag.putInt(TAG_PAGE_CURSOR, this.getPageCursor());
        tag.putInt(TAG_FOCUS_ENTRY_CURSOR, this.focusEntryCursor);
        tag.putInt(TAG_FOCUS_FIELD_OFFSET, this.getFocusFieldOffset());
        tag.putInt(TAG_FOCUS_ROW_OFFSET, this.getFocusRowOffset());
        tag.putInt(TAG_FOCUS_COLUMN_OFFSET, this.getFocusColumnOffset());
        if (this.linkedComputerPos != null) {
            tag.putLong(TAG_LINKED_COMPUTER_POS, this.linkedComputerPos.asLong());
        }
        if (!this.targetOutputEntries.isEmpty()) {
            tag.put(TAG_TARGET_OUTPUT_ENTRIES, writeOutputEntries(this.targetOutputEntries));
        }
    }

    private boolean setPageCursor(final int newPageCursor) {
        final int sanitizedCursor = Math.max(0, newPageCursor);
        if (this.pageCursor == sanitizedCursor) {
            return false;
        }

        this.pageCursor = sanitizedCursor;
        this.markStateChanged();
        return true;
    }

    private boolean setFocusOffsets(final int newFocusFieldOffset, final int newFocusRowOffset, final int newFocusColumnOffset) {
        final int sanitizedFieldOffset = Math.max(0, newFocusFieldOffset);
        final int sanitizedRowOffset = Math.max(0, newFocusRowOffset);
        final int sanitizedColumnOffset = Math.max(0, newFocusColumnOffset);
        if (this.focusFieldOffset == sanitizedFieldOffset
                && this.focusRowOffset == sanitizedRowOffset
                && this.focusColumnOffset == sanitizedColumnOffset) {
            return false;
        }

        this.focusFieldOffset = sanitizedFieldOffset;
        this.focusRowOffset = sanitizedRowOffset;
        this.focusColumnOffset = sanitizedColumnOffset;
        this.markStateChanged();
        return true;
    }

    private void resetFocusState() {
        this.focusEntryCursor = -1;
        this.resetFocusOffsets();
    }

    private void resetViewState() {
        this.pageCursor = 0;
        this.resetFocusState();
    }

    private void resetFocusOffsets() {
        this.focusFieldOffset = 0;
        this.focusRowOffset = 0;
        this.focusColumnOffset = 0;
    }

    private ScreenBlockEntity resolveOutputTargetScreen() {
        final ScreenBlockEntity controllerScreen = this.resolveLoadedControllerScreen();
        return controllerScreen == null ? this : controllerScreen;
    }

    private static List<ComputerOutputEntry> readOutputEntries(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }

        final ListTag listTag = tag.getList(key, Tag.TAG_COMPOUND);
        final ArrayList<ComputerOutputEntry> entries = new ArrayList<>(listTag.size());
        for (int index = 0; index < listTag.size(); index++) {
            entries.add(ComputerOutputEntry.fromTag(listTag.getCompound(index)));
        }
        return List.copyOf(entries);
    }

    private static ListTag writeOutputEntries(final List<ComputerOutputEntry> outputEntries) {
        final ListTag listTag = new ListTag();
        for (final ComputerOutputEntry outputEntry : outputEntries) {
            listTag.add(outputEntry.toTag());
        }
        return listTag;
    }

    private static String sanitizeLedMode(final String mode) {
        return switch (mode == null ? "" : mode) {
            case "idle", "running", "ok", "error", "offline" -> mode;
            default -> "unlinked";
        };
    }

    private static int distanceAlong(final BlockPos origin, final BlockPos target, final Direction direction) {
        final BlockPos delta = target.subtract(origin);
        return delta.getX() * direction.getStepX() + delta.getY() * direction.getStepY() + delta.getZ() * direction.getStepZ();
    }

    public record LedAnimationState(String mode, long elapsedMillis, boolean errorBlinkActive) {
    }
}
