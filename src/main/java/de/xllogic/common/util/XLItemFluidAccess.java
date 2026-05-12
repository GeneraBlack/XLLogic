package de.xllogic.common.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class XLItemFluidAccess {
    private XLItemFluidAccess() {
    }

    public static IItemHandler getAdjacentItemHandler(final Level level, final BlockPos origin, final Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, origin.relative(side), side.getOpposite());
    }

    public static IFluidHandler getAdjacentFluidHandler(final Level level, final BlockPos origin, final Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, origin.relative(side), side.getOpposite());
    }

    public static String itemId(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public static Item resolveItem(final String rawItemId) {
        final ResourceLocation key = ResourceLocation.tryParse(rawItemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    public static String fluidId(final FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        final ResourceLocation key = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return key == null ? "" : key.toString();
    }

    public static Fluid resolveFluid(final String rawFluidId) {
        final ResourceLocation key = ResourceLocation.tryParse(rawFluidId);
        if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.FLUID.get(key);
    }

    public static ItemStack insertIntoItemHandler(final IItemHandler handler, final ItemStack stack, final boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    public static List<ItemStack> copySlots(final IItemHandler handler) {
        final List<ItemStack> slots = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            slots.add(handler.getStackInSlot(slot).copy());
        }
        return slots;
    }

    public static boolean canInsertStacks(final IItemHandler handler, final List<ItemStack> stacks) {
        return canInsertStacks(handler, stacks, copySlots(handler));
    }

    public static boolean canInsertStacks(final IItemHandler handler, final List<ItemStack> stacks, final List<ItemStack> simulatedSlots) {
        if (simulatedSlots.size() != handler.getSlots()) {
            throw new IllegalArgumentException("Simulated slots must match the handler slot count.");
        }

        for (final ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            if (!canInsertStack(handler, stack, simulatedSlots)) {
                return false;
            }
        }

        return true;
    }

    private static boolean canInsertStack(final IItemHandler handler, final ItemStack stack, final List<ItemStack> simulatedSlots) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            remaining = simulateSlotInsert(handler, stack, simulatedSlots, slot, remaining);
        }
        return remaining <= 0;
    }

    private static int simulateSlotInsert(final IItemHandler handler, final ItemStack stack, final List<ItemStack> simulatedSlots, final int slot, final int remaining) {
        if (!handler.isItemValid(slot, stack)) {
            return remaining;
        }

        final int slotLimit = Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize());
        if (slotLimit <= 0) {
            return remaining;
        }

        final ItemStack simulated = simulatedSlots.get(slot);
        if (simulated.isEmpty()) {
            return placeInEmptySlot(simulatedSlots, slot, stack, slotLimit, remaining);
        }

        if (simulated.getItem() != stack.getItem()) {
            return remaining;
        }

        final int inserted = Math.min(Math.max(0, slotLimit - simulated.getCount()), remaining);
        if (inserted > 0) {
            simulated.grow(inserted);
            return remaining - inserted;
        }
        return remaining;
    }

    private static int placeInEmptySlot(final List<ItemStack> simulatedSlots, final int slot, final ItemStack stack, final int slotLimit, final int remaining) {
        final int inserted = Math.min(slotLimit, remaining);
        if (inserted <= 0) {
            return remaining;
        }

        final ItemStack placed = stack.copy();
        placed.setCount(inserted);
        simulatedSlots.set(slot, placed);
        return remaining - inserted;
    }
}