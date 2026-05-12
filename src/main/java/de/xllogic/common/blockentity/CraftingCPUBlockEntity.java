package de.xllogic.common.blockentity;

import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import de.xllogic.common.util.XLItemFluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.ArrayList;
import java.util.List;

public final class CraftingCPUBlockEntity extends NamedNetworkEndpointBlockEntity {
    private static final String TAG_BUSY = "Busy";
    private static final String TAG_QUEUED_JOBS = "QueuedJobs";
    private static final String TAG_RECIPE_SLOT_PREFIX = "RecipeSlot";
    private static final int RECIPE_WIDTH = 3;
    private static final int RECIPE_HEIGHT = 3;
    private static final int RECIPE_SLOT_COUNT = RECIPE_WIDTH * RECIPE_HEIGHT;

    private boolean busy;
    private int queuedJobs = 1;
    private final NonNullList<ItemStack> recipeSlots = NonNullList.withSize(RECIPE_SLOT_COUNT, ItemStack.EMPTY);

    public CraftingCPUBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.CRAFTING_CPU.get(), pos, blockState);
    }

    @Override
    public boolean supportsSideNaming() {
        return true;
    }

    public boolean isBusy() {
        return this.busy;
    }

    public void setBusy(final boolean busy) {
        if (this.busy == busy) {
            return;
        }

        this.busy = busy;
        this.markStateChanged();
    }

    public void toggleBusy() {
        this.setBusy(!this.busy);
    }

    public int getQueuedJobs() {
        return this.queuedJobs;
    }

    public void setQueuedJobs(final int queuedJobs) {
        final int clampedQueuedJobs = Math.max(0, queuedJobs);
        if (this.queuedJobs == clampedQueuedJobs) {
            return;
        }

        this.queuedJobs = clampedQueuedJobs;
        this.markStateChanged();
    }

    public int getRecipeSlotCount() {
        return RECIPE_SLOT_COUNT;
    }

    public String getRecipeItemId(final int slot) {
        return XLItemFluidAccess.itemId(this.getRecipeSlot(slot));
    }

    public int getRecipeItemCount(final int slot) {
        return this.getRecipeSlot(slot).getCount();
    }

    public void setRecipeSlot(final int slot, final String itemId, final int count) {
        this.validateSlot(slot);
        if (itemId == null || itemId.isBlank() || count <= 0) {
            this.recipeSlots.set(slot, ItemStack.EMPTY);
            this.markStateChanged();
            return;
        }

        final var item = XLItemFluidAccess.resolveItem(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item id '" + itemId + "'.");
        }

        this.recipeSlots.set(slot, new ItemStack(item, Math.max(1, count)));
        this.markStateChanged();
    }

    public void clearRecipe() {
        boolean changed = false;
        for (int slot = 0; slot < RECIPE_SLOT_COUNT; slot++) {
            if (!this.recipeSlots.get(slot).isEmpty()) {
                this.recipeSlots.set(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            this.markStateChanged();
        }
    }

    public void setRecipePattern(final List<ItemStack> pattern) {
        if (pattern == null || pattern.size() != RECIPE_SLOT_COUNT) {
            throw new IllegalArgumentException("Recipe pattern must contain exactly 9 entries.");
        }

        boolean changed = false;
        for (int slot = 0; slot < RECIPE_SLOT_COUNT; slot++) {
            final ItemStack normalized = normalizePatternStack(pattern.get(slot));
            if (!sameRecipeStack(this.recipeSlots.get(slot), normalized)) {
                this.recipeSlots.set(slot, normalized);
                changed = true;
            }
        }

        if (changed) {
            this.markStateChanged();
        }
    }

    public String getPreviewRecipeId() {
        final RecipeContext context = this.resolveRecipeContext();
        return context == null ? "" : context.recipe().id().toString();
    }

    public String getPreviewRecipeIdForPattern(final List<ItemStack> pattern) {
        final RecipeContext context = this.resolveRecipeContext(pattern);
        return context == null ? "" : context.recipe().id().toString();
    }

    public String getPreviewResultItemId() {
        final RecipeContext context = this.resolveRecipeContext();
        return context == null ? "" : XLItemFluidAccess.itemId(context.result());
    }

    public String getPreviewResultItemIdForPattern(final List<ItemStack> pattern) {
        final RecipeContext context = this.resolveRecipeContext(pattern);
        return context == null ? "" : XLItemFluidAccess.itemId(context.result());
    }

    public int getPreviewResultCount() {
        final RecipeContext context = this.resolveRecipeContext();
        return context == null ? 0 : context.result().getCount();
    }

    public int getPreviewResultCountForPattern(final List<ItemStack> pattern) {
        final RecipeContext context = this.resolveRecipeContext(pattern);
        return context == null ? 0 : context.result().getCount();
    }

    public List<ItemStack> getPreviewOutputsForPattern(final List<ItemStack> pattern) {
        final RecipeContext context = this.resolveRecipeContext(pattern);
        return context == null ? List.of() : copySimulatedSlots(this.buildOutputs(context));
    }

    public int craft(final Direction inputSide, final Direction outputSide, final int requestedCrafts) {
        if (this.level == null || this.level.isClientSide() || requestedCrafts <= 0) {
            return 0;
        }
        if (inputSide == outputSide) {
            throw new IllegalArgumentException("Crafting CPU input and output side must be different.");
        }

        final IItemHandler input = XLItemFluidAccess.getAdjacentItemHandler(this.level, this.worldPosition, inputSide);
        final IItemHandler output = XLItemFluidAccess.getAdjacentItemHandler(this.level, this.worldPosition, outputSide);
        if (input == null || output == null) {
            return 0;
        }

        return this.craftWithHandlers(input, output, requestedCrafts);
    }

    public int craftWithHandlers(final IItemHandler input, final IItemHandler output, final int requestedCrafts) {
        if (input == null || output == null || requestedCrafts <= 0) {
            return 0;
        }

        final RecipeContext context = this.resolveRecipeContext();
        if (context == null || context.result().isEmpty()) {
            return 0;
        }

        int crafted = 0;
        this.setBusy(true);
        try {
            for (int round = 0; round < requestedCrafts; round++) {
                if (!this.tryCraftOnce(input, output, context)) {
                    return crafted;
                }
                crafted++;
            }
        } finally {
            this.setBusy(false);
        }

        return crafted;
    }

    public int countCraftableWithHandlers(final IItemHandler input, final IItemHandler output, final int requestedCrafts) {
        if (input == null || output == null || requestedCrafts <= 0) {
            return 0;
        }

        final RecipeContext context = this.resolveRecipeContext();
        if (context == null || context.result().isEmpty()) {
            return 0;
        }

        return this.countCraftableWithContext(input, output, requestedCrafts, context);
    }

    public int countCraftableForPatternWithHandlers(final List<ItemStack> pattern, final IItemHandler input,
                                                    final IItemHandler output, final int requestedCrafts) {
        if (input == null || output == null || requestedCrafts <= 0) {
            return 0;
        }

        final RecipeContext context = this.resolveRecipeContext(pattern);
        if (context == null || context.result().isEmpty()) {
            return 0;
        }

        return this.countCraftableWithContext(input, output, requestedCrafts, context);
    }

    public int simulatePatternCraftsWithHandlers(final List<ItemStack> pattern,
                                                 final IItemHandler input,
                                                 final IItemHandler output,
                                                 final int requestedCrafts,
                                                 final List<ItemStack> simulatedInputSlots,
                                                 final List<ItemStack> simulatedOutputSlots) {
        if (input == null || output == null || requestedCrafts <= 0 || simulatedInputSlots == null || simulatedOutputSlots == null) {
            return 0;
        }
        if (simulatedInputSlots.size() != input.getSlots()) {
            throw new IllegalArgumentException("Simulated input slots must match the input handler slot count.");
        }
        if (simulatedOutputSlots.size() != output.getSlots()) {
            throw new IllegalArgumentException("Simulated output slots must match the output handler slot count.");
        }

        final RecipeContext context = this.resolveRecipeContext(pattern);
        if (context == null || context.result().isEmpty()) {
            return 0;
        }

        return this.countCraftableWithContext(output, requestedCrafts, context, simulatedInputSlots, simulatedOutputSlots);
    }

    public CraftFailureKind diagnosePatternFailureWithHandlers(final List<ItemStack> pattern,
                                                              final IItemHandler input,
                                                              final IItemHandler output) {
        return this.diagnosePatternFailureWithHandlers(pattern, input, output, null, null);
    }

    public CraftFailureKind diagnosePatternFailureWithHandlers(final List<ItemStack> pattern,
                                                              final IItemHandler input,
                                                              final IItemHandler output,
                                                              final List<ItemStack> simulatedInputSlots,
                                                              final List<ItemStack> simulatedOutputSlots) {
        if (input == null || output == null) {
            return CraftFailureKind.OUTPUT_FULL;
        }

        final RecipeContext context = this.resolveRecipeContext(pattern);
        if (context == null || context.result().isEmpty()) {
            return CraftFailureKind.RECIPE_INVALID;
        }

        final List<ItemStack> effectiveInputSlots = simulatedInputSlots == null
                ? XLItemFluidAccess.copySlots(input)
                : copySimulatedSlots(simulatedInputSlots);
        final boolean sharedInventory = input == output || simulatedInputSlots == simulatedOutputSlots;
        final List<ItemStack> effectiveOutputSlots;
        if (sharedInventory) {
            effectiveOutputSlots = effectiveInputSlots;
        } else if (simulatedOutputSlots == null) {
            effectiveOutputSlots = XLItemFluidAccess.copySlots(output);
        } else {
            effectiveOutputSlots = copySimulatedSlots(simulatedOutputSlots);
        }

        if (!this.consumeRequirements(effectiveInputSlots, context.requiredInputs())) {
            return CraftFailureKind.MATERIAL_MISSING;
        }
        if (!XLItemFluidAccess.canInsertStacks(output, this.buildOutputs(context), effectiveOutputSlots)) {
            return CraftFailureKind.OUTPUT_FULL;
        }
        return CraftFailureKind.NONE;
    }

    public int craftQueued(final Direction inputSide, final Direction outputSide) {
        final int crafted = this.craft(inputSide, outputSide, this.queuedJobs);
        if (crafted > 0) {
            this.setQueuedJobs(Math.max(0, this.queuedJobs - crafted));
        }
        return crafted;
    }

    public String describeState() {
        final String preview = this.getPreviewResultItemId().isBlank() ? "no recipe" : this.getPreviewResultItemId() + " x" + this.getPreviewResultCount();
        final String aliases = this.sideAliasSummary();
        return "Endpoint: " + this.getEndpointName() + " | Crafting CPU busy: " + this.busy + " | queued jobs: " + this.queuedJobs + " | internal grid: 3x3 | preview: " + preview + (aliases.isBlank() ? "" : " | side aliases: " + aliases);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.busy = tag.getBoolean(TAG_BUSY);
        this.queuedJobs = Math.max(0, tag.getInt(TAG_QUEUED_JOBS));
        for (int slot = 0; slot < RECIPE_SLOT_COUNT; slot++) {
            final String itemId = tag.getString(TAG_RECIPE_SLOT_PREFIX + slot + "Item");
            final int count = tag.getInt(TAG_RECIPE_SLOT_PREFIX + slot + "Count");
            if (itemId.isBlank() || count <= 0) {
                this.recipeSlots.set(slot, ItemStack.EMPTY);
                continue;
            }

            final var item = XLItemFluidAccess.resolveItem(itemId);
            this.recipeSlots.set(slot, item == null ? ItemStack.EMPTY : new ItemStack(item, count));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_BUSY, this.busy);
        tag.putInt(TAG_QUEUED_JOBS, this.queuedJobs);
        for (int slot = 0; slot < RECIPE_SLOT_COUNT; slot++) {
            final ItemStack stack = this.recipeSlots.get(slot);
            tag.putString(TAG_RECIPE_SLOT_PREFIX + slot + "Item", XLItemFluidAccess.itemId(stack));
            tag.putInt(TAG_RECIPE_SLOT_PREFIX + slot + "Count", stack.getCount());
        }
    }

    private ItemStack getRecipeSlot(final int slot) {
        this.validateSlot(slot);
        return this.recipeSlots.get(slot).copy();
    }

    private void validateSlot(final int slot) {
        if (slot < 0 || slot >= RECIPE_SLOT_COUNT) {
            throw new IllegalArgumentException("Recipe slot must be between 0 and 8.");
        }
    }

    private RecipeContext resolveRecipeContext() {
        return this.resolveRecipeContext(this.copyCurrentPattern());
    }

    private RecipeContext resolveRecipeContext(final List<ItemStack> pattern) {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        boolean hasAnyInput = false;
        final List<ItemStack> normalizedPattern = new ArrayList<>(RECIPE_SLOT_COUNT);
        for (int slot = 0; slot < RECIPE_SLOT_COUNT; slot++) {
            final ItemStack stack = normalizePatternStack(pattern.get(slot));
            normalizedPattern.add(stack);
            hasAnyInput |= !stack.isEmpty();
        }
        if (!hasAnyInput) {
            return null;
        }

        final CraftingInput input = CraftingInput.of(RECIPE_WIDTH, RECIPE_HEIGHT, normalizedPattern);
        final RecipeHolder<CraftingRecipe> recipe = serverLevel.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, serverLevel).orElse(null);
        if (recipe == null) {
            return null;
        }

        final ItemStack result = recipe.value().assemble(input, serverLevel.registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        final List<ItemStack> requiredInputs = new ArrayList<>();
        for (final ItemStack stack : normalizedPattern) {
            if (!stack.isEmpty()) {
                requiredInputs.add(stack.copy());
            }
        }
        return new RecipeContext(input, recipe, result, List.copyOf(requiredInputs));
    }

    private boolean tryCraftOnce(final IItemHandler input, final IItemHandler output, final RecipeContext context) {
        if (!this.canConsumeRequirements(input, context.requiredInputs())) {
            return false;
        }

        final List<ItemStack> outputs = this.buildOutputs(context);
        if (!this.canAcceptOutputs(input, output, context.requiredInputs(), outputs)) {
            return false;
        }

        this.consumeRequirements(input, context.requiredInputs());
        this.insertOutputs(output, outputs);
        return true;
    }

    private int countCraftableWithContext(final IItemHandler input, final IItemHandler output,
                                          final int requestedCrafts, final RecipeContext context) {
        final List<ItemStack> simulatedInputSlots = XLItemFluidAccess.copySlots(input);
        final List<ItemStack> simulatedOutputSlots = input == output ? simulatedInputSlots : XLItemFluidAccess.copySlots(output);
        return this.countCraftableWithContext(output, requestedCrafts, context, simulatedInputSlots, simulatedOutputSlots);
    }

    private int countCraftableWithContext(final IItemHandler output,
                                          final int requestedCrafts,
                                          final RecipeContext context,
                                          final List<ItemStack> simulatedInputSlots,
                                          final List<ItemStack> simulatedOutputSlots) {
        final List<ItemStack> outputs = this.buildOutputs(context);

        int craftable = 0;
        for (int round = 0; round < requestedCrafts; round++) {
            if (!this.canSimulateCraftRound(output, simulatedInputSlots, simulatedOutputSlots,
                    context.requiredInputs(), outputs)) {
                return craftable;
            }
            craftable++;
        }

        return craftable;
    }

    private boolean canSimulateCraftRound(final IItemHandler output,
                                          final List<ItemStack> simulatedInputSlots,
                                          final List<ItemStack> simulatedOutputSlots,
                                          final List<ItemStack> requiredInputs,
                                          final List<ItemStack> outputs) {
        if (simulatedInputSlots == simulatedOutputSlots) {
            final List<ItemStack> combinedSlots = copySimulatedSlots(simulatedInputSlots);
            if (!this.consumeRequirements(combinedSlots, requiredInputs)) {
                return false;
            }
            if (!XLItemFluidAccess.canInsertStacks(output, outputs, combinedSlots)) {
                return false;
            }
            replaceSimulatedSlots(simulatedInputSlots, combinedSlots);
            return true;
        }

        final List<ItemStack> nextInputSlots = copySimulatedSlots(simulatedInputSlots);
        if (!this.consumeRequirements(nextInputSlots, requiredInputs)) {
            return false;
        }

        final List<ItemStack> nextOutputSlots = copySimulatedSlots(simulatedOutputSlots);
        if (!XLItemFluidAccess.canInsertStacks(output, outputs, nextOutputSlots)) {
            return false;
        }

        replaceSimulatedSlots(simulatedInputSlots, nextInputSlots);
        replaceSimulatedSlots(simulatedOutputSlots, nextOutputSlots);
        return true;
    }

    private List<ItemStack> buildOutputs(final RecipeContext context) {
        final List<ItemStack> outputs = new ArrayList<>();
        outputs.add(context.result().copy());
        for (final ItemStack remainder : context.recipe().value().getRemainingItems(context.input())) {
            if (!remainder.isEmpty()) {
                outputs.add(remainder.copy());
            }
        }
        return outputs;
    }

    private void insertOutputs(final IItemHandler output, final List<ItemStack> outputs) {
        for (final ItemStack outputStack : outputs) {
            final ItemStack remainder = XLItemFluidAccess.insertIntoItemHandler(output, outputStack, false);
            if (!remainder.isEmpty()) {
                throw new IllegalStateException("Crafting CPU failed to insert crafted output into the target inventory.");
            }
        }
    }

    private boolean canConsumeRequirements(final IItemHandler input, final List<ItemStack> requiredInputs) {
        final int[] remainingBySlot = new int[input.getSlots()];
        for (int slot = 0; slot < input.getSlots(); slot++) {
            remainingBySlot[slot] = input.getStackInSlot(slot).getCount();
        }

        for (final ItemStack required : requiredInputs) {
            int remaining = required.getCount();
            for (int slot = 0; slot < input.getSlots() && remaining > 0; slot++) {
                final ItemStack current = input.getStackInSlot(slot);
                if (current.isEmpty() || current.getItem() != required.getItem()) {
                    continue;
                }

                final int extracted = Math.min(remainingBySlot[slot], remaining);
                remainingBySlot[slot] -= extracted;
                remaining -= extracted;
            }
            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    private void consumeRequirements(final IItemHandler input, final List<ItemStack> requiredInputs) {
        for (final ItemStack required : requiredInputs) {
            int remaining = required.getCount();
            for (int slot = 0; slot < input.getSlots() && remaining > 0; slot++) {
                final ItemStack current = input.getStackInSlot(slot);
                if (current.isEmpty() || current.getItem() != required.getItem()) {
                    continue;
                }

                final ItemStack extracted = input.extractItem(slot, remaining, false);
                remaining -= extracted.getCount();
            }

            if (remaining > 0) {
                throw new IllegalStateException("Crafting CPU could not consume the required recipe inputs.");
            }
        }
    }

    private List<ItemStack> copyCurrentPattern() {
        final List<ItemStack> pattern = new ArrayList<>(RECIPE_SLOT_COUNT);
        for (int slot = 0; slot < RECIPE_SLOT_COUNT; slot++) {
            pattern.add(this.recipeSlots.get(slot).copy());
        }
        return pattern;
    }

    private static List<ItemStack> copySimulatedSlots(final List<ItemStack> slots) {
        final List<ItemStack> copy = new ArrayList<>(slots.size());
        for (final ItemStack stack : slots) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static void replaceSimulatedSlots(final List<ItemStack> target, final List<ItemStack> replacement) {
        target.clear();
        for (final ItemStack stack : replacement) {
            target.add(stack.copy());
        }
    }

    private boolean canAcceptOutputs(final IItemHandler input, final IItemHandler output, final List<ItemStack> requiredInputs, final List<ItemStack> outputs) {
        if (input != output) {
            return XLItemFluidAccess.canInsertStacks(output, outputs);
        }

        final List<ItemStack> simulatedSlots = XLItemFluidAccess.copySlots(input);
        if (!this.consumeRequirements(simulatedSlots, requiredInputs)) {
            return false;
        }
        return XLItemFluidAccess.canInsertStacks(output, outputs, simulatedSlots);
    }

    private boolean consumeRequirements(final List<ItemStack> simulatedSlots, final List<ItemStack> requiredInputs) {
        for (final ItemStack required : requiredInputs) {
            int remaining = required.getCount();
            for (int slot = 0; slot < simulatedSlots.size() && remaining > 0; slot++) {
                final ItemStack current = simulatedSlots.get(slot);
                if (current.isEmpty() || current.getItem() != required.getItem()) {
                    continue;
                }

                final int extracted = Math.min(current.getCount(), remaining);
                current.shrink(extracted);
                remaining -= extracted;
                if (current.isEmpty()) {
                    simulatedSlots.set(slot, ItemStack.EMPTY);
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack normalizePatternStack(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final ItemStack normalized = stack.copy();
        normalized.setCount(Math.max(1, normalized.getCount()));
        return normalized;
    }

    private static boolean sameRecipeStack(final ItemStack left, final ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() && right.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(left, right) && left.getCount() == right.getCount();
    }

    private record RecipeContext(CraftingInput input, RecipeHolder<CraftingRecipe> recipe, ItemStack result, List<ItemStack> requiredInputs) {
    }

    public enum CraftFailureKind {
        NONE,
        RECIPE_INVALID,
        MATERIAL_MISSING,
        OUTPUT_FULL
    }
}
