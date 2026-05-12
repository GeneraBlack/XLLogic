package de.xllogic.common.blockentity;

import de.xllogic.common.device.MaterialIOMode;
import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import de.xllogic.common.util.XLItemFluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class MaterialIOBlockEntity extends NamedNetworkEndpointBlockEntity {
    private static final String TAG_MODE = "Mode";
    private static final String TAG_ITEM_INPUT_ENABLED = "ItemInputEnabled";
    private static final String TAG_ITEM_OUTPUT_ENABLED = "ItemOutputEnabled";
    private static final String TAG_FLUID_INPUT_ENABLED = "FluidInputEnabled";
    private static final String TAG_FLUID_OUTPUT_ENABLED = "FluidOutputEnabled";

    private MaterialIOMode mode = MaterialIOMode.HYBRID;
    private boolean itemInputEnabled = true;
    private boolean itemOutputEnabled = true;
    private boolean fluidInputEnabled = true;
    private boolean fluidOutputEnabled = true;

    public MaterialIOBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.MATERIAL_IO.get(), pos, blockState);
    }

    @Override
    public boolean supportsSideNaming() {
        return true;
    }

    public MaterialIOMode getMode() {
        return this.mode;
    }

    public void cycleMode() {
        this.setMode(this.mode.next());
    }

    public void setMode(final MaterialIOMode mode) {
        if (mode == null || this.mode == mode) {
            return;
        }

        this.mode = mode;
        this.markStateChanged();
    }

    public boolean isItemInputEnabled() {
        return this.itemInputEnabled;
    }

    public void setItemInputEnabled(final boolean itemInputEnabled) {
        if (this.itemInputEnabled == itemInputEnabled) {
            return;
        }

        this.itemInputEnabled = itemInputEnabled;
        this.markStateChanged();
    }

    public boolean isItemOutputEnabled() {
        return this.itemOutputEnabled;
    }

    public void setItemOutputEnabled(final boolean itemOutputEnabled) {
        if (this.itemOutputEnabled == itemOutputEnabled) {
            return;
        }

        this.itemOutputEnabled = itemOutputEnabled;
        this.markStateChanged();
    }

    public boolean isFluidInputEnabled() {
        return this.fluidInputEnabled;
    }

    public void setFluidInputEnabled(final boolean fluidInputEnabled) {
        if (this.fluidInputEnabled == fluidInputEnabled) {
            return;
        }

        this.fluidInputEnabled = fluidInputEnabled;
        this.markStateChanged();
    }

    public boolean isFluidOutputEnabled() {
        return this.fluidOutputEnabled;
    }

    public void setFluidOutputEnabled(final boolean fluidOutputEnabled) {
        if (this.fluidOutputEnabled == fluidOutputEnabled) {
            return;
        }

        this.fluidOutputEnabled = fluidOutputEnabled;
        this.markStateChanged();
    }

    public int getItemSlotCount(final Direction side) {
        final IItemHandler handler = this.getAdjacentItemHandler(side);
        return handler == null ? 0 : handler.getSlots();
    }

    public IItemHandler getInputItemHandler(final Direction side) {
        return this.canPullItems() ? this.getAdjacentItemHandler(side) : null;
    }

    public IItemHandler getOutputItemHandler(final Direction side) {
        return this.canPushItems() ? this.getAdjacentItemHandler(side) : null;
    }

    public String getItemId(final Direction side, final int slot) {
        final IItemHandler handler = this.getAdjacentItemHandler(side);
        if (handler == null || slot < 0 || slot >= handler.getSlots()) {
            return "";
        }
        return XLItemFluidAccess.itemId(handler.getStackInSlot(slot));
    }

    public int getItemCount(final Direction side, final int slot) {
        final IItemHandler handler = this.getAdjacentItemHandler(side);
        if (handler == null || slot < 0 || slot >= handler.getSlots()) {
            return 0;
        }
        return handler.getStackInSlot(slot).getCount();
    }

    public int countItem(final Direction side, final String itemId) {
        final IItemHandler handler = this.getAdjacentItemHandler(side);
        if (handler == null) {
            return 0;
        }

        final Item filterItem = itemId == null || itemId.isBlank() ? null : XLItemFluidAccess.resolveItem(itemId);
        if (itemId != null && !itemId.isBlank() && filterItem == null) {
            return 0;
        }

        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            final var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (filterItem == null || stack.getItem() == filterItem) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public int transferItem(final Direction sourceSide, final Direction targetSide, final int sourceSlot, final int maxAmount) {
        if (sourceSide == targetSide) {
            throw new IllegalArgumentException("Source and target side must be different for item transfer.");
        }

        return this.transferItemTo(this, sourceSide, targetSide, sourceSlot, maxAmount);
    }

    public int transferItemTo(final MaterialIOBlockEntity targetMaterialIo,
                              final Direction sourceSide,
                              final Direction targetSide,
                              final int sourceSlot,
                              final int maxAmount) {
        if (!this.canPullItems()) {
            throw new IllegalStateException("Source Material I/O item transfer is disabled by the current mode or flags.");
        }
        if (targetMaterialIo == null) {
            throw new IllegalArgumentException("Target Material I/O is required for item transfer.");
        }
        if (!targetMaterialIo.canPushItems()) {
            throw new IllegalStateException("Target Material I/O item transfer is disabled by the current mode or flags.");
        }
        if (this == targetMaterialIo && sourceSide == targetSide) {
            throw new IllegalArgumentException("Source and target side must be different for item transfer on the same Material I/O.");
        }

        final IItemHandler source = this.getAdjacentItemHandler(sourceSide);
        final IItemHandler target = targetMaterialIo.getAdjacentItemHandler(targetSide);
        return transferBetweenItemHandlers(source, target, sourceSlot, maxAmount);
    }

    public int getFluidTankCount(final Direction side) {
        final IFluidHandler handler = this.getAdjacentFluidHandler(side);
        return handler == null ? 0 : handler.getTanks();
    }

    public String getFluidId(final Direction side, final int tank) {
        final IFluidHandler handler = this.getAdjacentFluidHandler(side);
        if (handler == null || tank < 0 || tank >= handler.getTanks()) {
            return "";
        }
        return XLItemFluidAccess.fluidId(handler.getFluidInTank(tank));
    }

    public int getFluidAmount(final Direction side, final int tank) {
        final IFluidHandler handler = this.getAdjacentFluidHandler(side);
        if (handler == null || tank < 0 || tank >= handler.getTanks()) {
            return 0;
        }
        return handler.getFluidInTank(tank).getAmount();
    }

    public int transferFluid(final Direction sourceSide, final Direction targetSide, final int sourceTank, final int amount) {
        if (sourceSide == targetSide) {
            throw new IllegalArgumentException("Source and target side must be different for fluid transfer.");
        }

        return this.transferFluidTo(this, sourceSide, targetSide, sourceTank, amount);
    }

    public int transferFluidTo(final MaterialIOBlockEntity targetMaterialIo,
                               final Direction sourceSide,
                               final Direction targetSide,
                               final int sourceTank,
                               final int amount) {
        if (!this.canPullFluids()) {
            throw new IllegalStateException("Source Material I/O fluid transfer is disabled by the current mode or flags.");
        }
        if (targetMaterialIo == null) {
            throw new IllegalArgumentException("Target Material I/O is required for fluid transfer.");
        }
        if (!targetMaterialIo.canPushFluids()) {
            throw new IllegalStateException("Target Material I/O fluid transfer is disabled by the current mode or flags.");
        }
        if (this == targetMaterialIo && sourceSide == targetSide) {
            throw new IllegalArgumentException("Source and target side must be different for fluid transfer on the same Material I/O.");
        }

        final IFluidHandler source = this.getAdjacentFluidHandler(sourceSide);
        final IFluidHandler target = targetMaterialIo.getAdjacentFluidHandler(targetSide);
        return transferBetweenFluidHandlers(source, target, sourceTank, amount);
    }

    public String describeState() {
        final String aliases = this.sideAliasSummary();
        return "Endpoint: " + this.getEndpointName() + " | Material I/O mode: " + this.mode + " | items in/out: " + this.itemInputEnabled + "/" + this.itemOutputEnabled + " | fluids in/out: " + this.fluidInputEnabled + "/" + this.fluidOutputEnabled + (aliases.isBlank() ? "" : " | side aliases: " + aliases);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mode = MaterialIOMode.valueOf(tag.getString(TAG_MODE));
        this.itemInputEnabled = !tag.contains(TAG_ITEM_INPUT_ENABLED) || tag.getBoolean(TAG_ITEM_INPUT_ENABLED);
        this.itemOutputEnabled = !tag.contains(TAG_ITEM_OUTPUT_ENABLED) || tag.getBoolean(TAG_ITEM_OUTPUT_ENABLED);
        this.fluidInputEnabled = !tag.contains(TAG_FLUID_INPUT_ENABLED) || tag.getBoolean(TAG_FLUID_INPUT_ENABLED);
        this.fluidOutputEnabled = !tag.contains(TAG_FLUID_OUTPUT_ENABLED) || tag.getBoolean(TAG_FLUID_OUTPUT_ENABLED);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_MODE, this.mode.name());
        tag.putBoolean(TAG_ITEM_INPUT_ENABLED, this.itemInputEnabled);
        tag.putBoolean(TAG_ITEM_OUTPUT_ENABLED, this.itemOutputEnabled);
        tag.putBoolean(TAG_FLUID_INPUT_ENABLED, this.fluidInputEnabled);
        tag.putBoolean(TAG_FLUID_OUTPUT_ENABLED, this.fluidOutputEnabled);
    }

    private IItemHandler getAdjacentItemHandler(final Direction side) {
        if (this.level == null || !this.supportsItems()) {
            return null;
        }
        return XLItemFluidAccess.getAdjacentItemHandler(this.level, this.worldPosition, side);
    }

    private IFluidHandler getAdjacentFluidHandler(final Direction side) {
        if (this.level == null || !this.supportsFluids()) {
            return null;
        }
        return XLItemFluidAccess.getAdjacentFluidHandler(this.level, this.worldPosition, side);
    }

    private boolean supportsItems() {
        return this.mode != MaterialIOMode.FLUIDS_ONLY;
    }

    private boolean supportsFluids() {
        return this.mode != MaterialIOMode.ITEMS_ONLY;
    }

    private boolean canPullItems() {
        return this.supportsItems() && this.itemInputEnabled;
    }

    private boolean canPushItems() {
        return this.supportsItems() && this.itemOutputEnabled;
    }

    private boolean canPullFluids() {
        return this.supportsFluids() && this.fluidInputEnabled;
    }

    private boolean canPushFluids() {
        return this.supportsFluids() && this.fluidOutputEnabled;
    }

    private static int transferBetweenItemHandlers(final IItemHandler source, final IItemHandler target, final int sourceSlot, final int maxAmount) {
        if (source == null || target == null || sourceSlot < 0 || sourceSlot >= source.getSlots() || maxAmount <= 0) {
            return 0;
        }

        final var simulatedExtraction = source.extractItem(sourceSlot, maxAmount, true);
        if (simulatedExtraction.isEmpty()) {
            return 0;
        }

        final var simulatedRemainder = XLItemFluidAccess.insertIntoItemHandler(target, simulatedExtraction, true);
        final int transferable = simulatedExtraction.getCount() - simulatedRemainder.getCount();
        if (transferable <= 0) {
            return 0;
        }

        final var extracted = source.extractItem(sourceSlot, transferable, false);
        if (extracted.isEmpty()) {
            return 0;
        }

        final var remainder = XLItemFluidAccess.insertIntoItemHandler(target, extracted, false);
        final int inserted = extracted.getCount() - remainder.getCount();
        if (!remainder.isEmpty()) {
            XLItemFluidAccess.insertIntoItemHandler(source, remainder, false);
        }
        return inserted;
    }

    private static int transferBetweenFluidHandlers(final IFluidHandler source, final IFluidHandler target, final int sourceTank, final int amount) {
        if (source == null || target == null || sourceTank < 0 || sourceTank >= source.getTanks() || amount <= 0) {
            return 0;
        }

        final FluidStack available = source.getFluidInTank(sourceTank);
        if (available.isEmpty()) {
            return 0;
        }

        final FluidStack requested = new FluidStack(available.getFluid(), Math.min(amount, available.getAmount()));
        final FluidStack simulatedDrain = source.drain(requested, IFluidHandler.FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty()) {
            return 0;
        }

        final int accepted = target.fill(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }

        final FluidStack drained = source.drain(new FluidStack(simulatedDrain.getFluid(), accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return 0;
        }

        return target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }
}
