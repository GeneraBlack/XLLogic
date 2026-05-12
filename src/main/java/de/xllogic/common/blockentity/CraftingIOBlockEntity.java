package de.xllogic.common.blockentity;

import de.xllogic.common.device.QueuedPlanJobStatus;
import de.xllogic.common.device.QueuedPlanReservationMode;
import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import de.xllogic.common.util.XLItemFluidAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public final class CraftingIOBlockEntity extends NamedNetworkEndpointBlockEntity {
    private static final String TAG_GRID_WIDTH = "GridWidth";
    private static final String TAG_GRID_HEIGHT = "GridHeight";
    private static final String TAG_WINDOW_X = "WindowX";
    private static final String TAG_WINDOW_Y = "WindowY";
    private static final String TAG_LINKED_CPU = "LinkedCpu";
    private static final String TAG_INPUT_ENDPOINT = "InputEndpoint";
    private static final String TAG_INPUT_SIDE = "InputSide";
    private static final String TAG_OUTPUT_ENDPOINT = "OutputEndpoint";
    private static final String TAG_OUTPUT_SIDE = "OutputSide";
    private static final String TAG_GRID_SLOT_PREFIX = "GridSlot";
    private static final String TAG_ROUTE_PREFIX = "Route";
    private static final String TAG_ROUTE_COUNT = "RouteCount";
    private static final String TAG_PLAN_STEP_PREFIX = "PlanStep";
    private static final String TAG_PLAN_STEP_COUNT = "PlanStepCount";
    private static final String TAG_QUEUED_PLAN_CYCLES = "QueuedPlanCycles";
    private static final String TAG_QUEUED_PLAN_CYCLE_INDEX = "QueuedPlanCycleIndex";
    private static final String TAG_QUEUED_PLAN_STEP_INDEX = "QueuedPlanStepIndex";
    private static final String TAG_QUEUED_PLAN_REMAINING_CRAFTS = "QueuedPlanRemainingCrafts";
    private static final String TAG_QUEUED_PLAN_TOTAL_CYCLES = "QueuedPlanTotalCycles";
    private static final String TAG_QUEUED_PLAN_RESERVATION_MODE = "QueuedPlanReservationMode";
    private static final String TAG_QUEUED_PLAN_JOB_STATUS = "QueuedPlanJobStatus";
    private static final String TAG_QUEUED_PLAN_ERROR_CLASS = "QueuedPlanErrorClass";
    private static final String TAG_QUEUED_PLAN_ACTION_HINT = "QueuedPlanActionHint";
    private static final String TAG_QUEUED_PLAN_MESSAGE = "QueuedPlanMessage";
    private static final String TAG_QUEUED_PLAN_INPUT_ROUTE = "QueuedPlanInputRoute";
    private static final String TAG_QUEUED_PLAN_OUTPUT_ROUTE = "QueuedPlanOutputRoute";
    private static final String TAG_QUEUED_PLAN_TRACKED_INTERMEDIATES = "QueuedPlanTrackedIntermediates";
    private static final int MIN_GRID_SIZE = 3;
    private static final int MAX_GRID_SIZE = 7;
    private static final int WINDOW_SIZE = 3;
    private static final int MAX_SLOT_COUNT = MAX_GRID_SIZE * MAX_GRID_SIZE;
    private static final int MAX_JOB_TEXT_LENGTH = 128;
    private static final int MAX_JOB_MESSAGE_LENGTH = 256;
    private static final String ACTION_HINT_IDLE = "idle";
    private static final String ACTION_HINT_READY = "ready";
    private static final String ACTION_HINT_COMPLETED = "completed";
    private static final String UNBOUND = "unbound";

    private int gridWidth = 5;
    private int gridHeight = 5;
    private int windowX;
    private int windowY;
    private String linkedCpuEndpoint = "";
    private String materialInputEndpoint = "";
    private Direction materialInputSide = Direction.WEST;
    private String materialOutputEndpoint = "";
    private Direction materialOutputSide = Direction.EAST;
    private final NonNullList<ItemStack> recipeGrid = NonNullList.withSize(MAX_SLOT_COUNT, ItemStack.EMPTY);
    private final List<NamedRoute> namedRoutes = new ArrayList<>();
    private final List<RecipePlanStep> recipePlan = new ArrayList<>();
    private int queuedPlanCycles;
    private int queuedPlanCycleIndex;
    private int queuedPlanStepIndex;
    private int queuedPlanRemainingCrafts;
    private int queuedPlanTotalCycles;
    private QueuedPlanReservationMode queuedPlanReservationMode = QueuedPlanReservationMode.FULL_QUEUE;
    private QueuedPlanJobStatus queuedPlanJobStatus = QueuedPlanJobStatus.IDLE;
    private String queuedPlanJobErrorClass = "";
    private String queuedPlanJobActionHint = ACTION_HINT_IDLE;
    private String queuedPlanJobMessage = "";
    private String queuedPlanJobInputRoute = "";
    private String queuedPlanJobOutputRoute = "";
    private final List<TrackedIntermediateState> trackedIntermediateStates = new ArrayList<>();

    public CraftingIOBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.CRAFTING_IO.get(), pos, blockState);
    }

    public int getGridWidth() {
        return this.gridWidth;
    }

    public int getGridHeight() {
        return this.gridHeight;
    }

    public int getGridSlotCount() {
        return this.gridWidth * this.gridHeight;
    }

    public String getGridItemId(final int slot) {
        return XLItemFluidAccess.itemId(this.getGridSlot(slot));
    }

    public int getGridItemCount(final int slot) {
        return this.getGridSlot(slot).getCount();
    }

    public void setGridSlot(final int slot, final String itemId, final int count) {
        this.validateVisibleSlot(slot);
        final int gridIndex = this.visibleSlotToGridIndex(slot);
        final ItemStack replacement = createGridStack(itemId, count);
        if (sameGridStack(this.recipeGrid.get(gridIndex), replacement)) {
            return;
        }

        this.recipeGrid.set(gridIndex, replacement);
        this.markStateChanged();
    }

    public void clearGrid() {
        boolean changed = false;
        for (int slot = 0; slot < MAX_SLOT_COUNT; slot++) {
            if (!this.recipeGrid.get(slot).isEmpty()) {
                this.recipeGrid.set(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            this.markStateChanged();
        }
    }

    public int getWindowX() {
        return this.windowX;
    }

    public int getWindowY() {
        return this.windowY;
    }

    public void setWindowOrigin(final int windowX, final int windowY) {
        final int clampedX = clampWindowCoordinate(windowX, this.gridWidth);
        final int clampedY = clampWindowCoordinate(windowY, this.gridHeight);
        if (this.windowX == clampedX && this.windowY == clampedY) {
            return;
        }

        this.windowX = clampedX;
        this.windowY = clampedY;
        this.markStateChanged();
    }

    public String getLinkedCpuEndpoint() {
        return this.linkedCpuEndpoint;
    }

    public void setLinkedCpuEndpoint(final String linkedCpuEndpoint) {
        final String sanitized = sanitizeReference(linkedCpuEndpoint);
        if (this.linkedCpuEndpoint.equals(sanitized)) {
            return;
        }

        this.linkedCpuEndpoint = sanitized;
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public String getMaterialInputEndpoint() {
        return this.materialInputEndpoint;
    }

    public void setMaterialInputEndpoint(final String materialInputEndpoint) {
        final String sanitized = sanitizeReference(materialInputEndpoint);
        if (this.materialInputEndpoint.equals(sanitized)) {
            return;
        }

        this.materialInputEndpoint = sanitized;
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public Direction getMaterialInputSide() {
        return this.materialInputSide;
    }

    public void setMaterialInputSide(final Direction materialInputSide) {
        final Direction resolvedSide = materialInputSide == null ? Direction.WEST : materialInputSide;
        if (this.materialInputSide == resolvedSide) {
            return;
        }

        this.materialInputSide = resolvedSide;
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public String getMaterialOutputEndpoint() {
        return this.materialOutputEndpoint;
    }

    public void setMaterialOutputEndpoint(final String materialOutputEndpoint) {
        final String sanitized = sanitizeReference(materialOutputEndpoint);
        if (this.materialOutputEndpoint.equals(sanitized)) {
            return;
        }

        this.materialOutputEndpoint = sanitized;
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public Direction getMaterialOutputSide() {
        return this.materialOutputSide;
    }

    public void setMaterialOutputSide(final Direction materialOutputSide) {
        final Direction resolvedSide = materialOutputSide == null ? Direction.EAST : materialOutputSide;
        if (this.materialOutputSide == resolvedSide) {
            return;
        }

        this.materialOutputSide = resolvedSide;
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public int getRouteCount() {
        return this.namedRoutes.size();
    }

    public String getRouteName(final int index) {
        return this.getRoute(index).name();
    }

    public String getRouteEndpoint(final int index) {
        return this.getRoute(index).endpoint();
    }

    public Direction getRouteSide(final int index) {
        return this.getRoute(index).side();
    }

    public String getRouteEndpoint(final String routeName) {
        final NamedRoute route = this.findRoute(routeName);
        return route == null ? "" : route.endpoint();
    }

    public Direction getRouteSide(final String routeName) {
        final NamedRoute route = this.findRoute(routeName);
        return route == null ? null : route.side();
    }

    public String setRoute(final String routeName, final String endpoint, final Direction side) {
        final String sanitizedRouteName = sanitizeRouteName(routeName);
        if (sanitizedRouteName.isBlank()) {
            throw new IllegalArgumentException("Crafting I/O route names must not be blank.");
        }

        final String sanitizedEndpoint = sanitizeReference(endpoint);
        if (sanitizedEndpoint.isBlank()) {
            throw new IllegalArgumentException("Crafting I/O routes must target a named Material I/O endpoint.");
        }

        final NamedRoute updatedRoute = new NamedRoute(sanitizedRouteName, sanitizedEndpoint, side == null ? Direction.NORTH : side);
        for (int index = 0; index < this.namedRoutes.size(); index++) {
            final NamedRoute currentRoute = this.namedRoutes.get(index);
            if (!currentRoute.name().equals(sanitizedRouteName)) {
                continue;
            }
            if (currentRoute.equals(updatedRoute)) {
                return sanitizedRouteName;
            }

            this.namedRoutes.set(index, updatedRoute);
            this.rearmQueuedPlanAfterSteeringChange();
            this.markStateChanged();
            return sanitizedRouteName;
        }

        this.namedRoutes.add(updatedRoute);
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
        return sanitizedRouteName;
    }

    public boolean clearRoute(final String routeName) {
        final String sanitizedRouteName = sanitizeRouteName(routeName);
        if (sanitizedRouteName.isBlank()) {
            return false;
        }

        for (int index = 0; index < this.namedRoutes.size(); index++) {
            if (!this.namedRoutes.get(index).name().equals(sanitizedRouteName)) {
                continue;
            }

            this.namedRoutes.remove(index);
            this.rearmQueuedPlanAfterSteeringChange();
            this.markStateChanged();
            return true;
        }

        return false;
    }

    public void clearRoutes() {
        if (this.namedRoutes.isEmpty()) {
            return;
        }

        this.namedRoutes.clear();
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public List<ItemStack> copyActiveRecipeWindow() {
        return this.copyWindow(this.windowX, this.windowY);
    }

    public List<ItemStack> copyPlanWindow(final int index) {
        final RecipePlanStep step = this.getPlanStep(index);
        return this.copyWindow(step.windowX(), step.windowY());
    }

    public int getPlanStepCount() {
        return this.recipePlan.size();
    }

    public int getPlanStepWindowX(final int index) {
        return this.getPlanStep(index).windowX();
    }

    public int getPlanStepWindowY(final int index) {
        return this.getPlanStep(index).windowY();
    }

    public int getPlanStepCrafts(final int index) {
        return this.getPlanStep(index).crafts();
    }

    public String getPlanStepInputRoute(final int index) {
        return this.getPlanStep(index).inputRouteName();
    }

    public String getPlanStepOutputRoute(final int index) {
        return this.getPlanStep(index).outputRouteName();
    }

    public int getQueuedPlanCycles() {
        return this.queuedPlanCycles;
    }

    public int getQueuedPlanTotalCycles() {
        return this.queuedPlanTotalCycles;
    }

    public int getQueuedPlanCycleIndex() {
        return this.hasQueuedPlan() ? this.queuedPlanCycleIndex : 0;
    }

    public int getQueuedPlanStepIndex() {
        return this.hasQueuedPlan() ? this.queuedPlanStepIndex : 0;
    }

    public int getQueuedPlanRemainingCrafts() {
        return this.hasQueuedPlan() ? this.queuedPlanRemainingCrafts : 0;
    }

    public int getQueuedPlanRequestedCrafts() {
        if (!this.hasQueuedPlan()) {
            return 0;
        }

        final int configuredCrafts = this.recipePlan.get(this.queuedPlanStepIndex).crafts();
        if (this.queuedPlanRemainingCrafts > 0) {
            return Math.min(configuredCrafts, this.queuedPlanRemainingCrafts);
        }
        return configuredCrafts;
    }

    public QueuedPlanReservationMode getQueuedPlanReservationMode() {
        return this.queuedPlanReservationMode;
    }

    public QueuedPlanJobStatus getQueuedPlanJobStatus() {
        return this.queuedPlanJobStatus;
    }

    public String getQueuedPlanJobErrorClass() {
        return this.queuedPlanJobErrorClass;
    }

    public String getQueuedPlanJobActionHint() {
        return this.queuedPlanJobActionHint;
    }

    public String getQueuedPlanJobMessage() {
        return this.queuedPlanJobMessage;
    }

    public String getQueuedPlanJobInputRoute() {
        return this.queuedPlanJobInputRoute;
    }

    public String getQueuedPlanJobOutputRoute() {
        return this.queuedPlanJobOutputRoute;
    }

    public List<TrackedIntermediateState> copyTrackedIntermediateStates() {
        return List.copyOf(this.trackedIntermediateStates);
    }

    public List<TrackedIntermediateState> copyTrackedIntermediateStates(final String routeName) {
        final String sanitizedRouteName = sanitizeRouteName(routeName);
        if (sanitizedRouteName.isBlank() || this.trackedIntermediateStates.isEmpty()) {
            return List.of();
        }

        final ArrayList<TrackedIntermediateState> matchingStates = new ArrayList<>();
        for (final TrackedIntermediateState trackedIntermediateState : this.trackedIntermediateStates) {
            if (trackedIntermediateState.routeName().equals(sanitizedRouteName)) {
                matchingStates.add(trackedIntermediateState);
            }
        }
        return List.copyOf(matchingStates);
    }

    public int getTrackedIntermediateItemCount() {
        int total = 0;
        for (final TrackedIntermediateState trackedIntermediateState : this.trackedIntermediateStates) {
            total += trackedIntermediateState.expectedCount();
        }
        return total;
    }

    public int trackedIntermediateExpectedCount(final String routeName, final String itemId) {
        final String sanitizedRouteName = sanitizeRouteName(routeName);
        final String sanitizedItemId = sanitizeItemId(itemId);
        if (sanitizedRouteName.isBlank() || sanitizedItemId.isBlank()) {
            return 0;
        }

        for (final TrackedIntermediateState trackedIntermediateState : this.trackedIntermediateStates) {
            if (trackedIntermediateState.routeName().equals(sanitizedRouteName)
                    && trackedIntermediateState.itemId().equals(sanitizedItemId)) {
                return trackedIntermediateState.expectedCount();
            }
        }
        return 0;
    }

    public void setTrackedIntermediateExpectation(final String routeName, final String itemId, final int expectedCount) {
        if (this.applyTrackedIntermediateExpectation(routeName, itemId, expectedCount)) {
            this.markStateChanged();
        }
    }

    public void clearTrackedIntermediateExpectations() {
        if (this.trackedIntermediateStates.isEmpty()) {
            return;
        }

        this.trackedIntermediateStates.clear();
        this.markStateChanged();
    }

    public void noteProducedIntermediate(final String routeName, final String itemId, final int producedCount) {
        if (producedCount <= 0) {
            return;
        }
        this.setTrackedIntermediateExpectation(routeName, itemId, this.trackedIntermediateExpectedCount(routeName, itemId) + producedCount);
    }

    public void noteConsumedIntermediate(final String routeName, final String itemId, final int consumedCount) {
        if (consumedCount <= 0) {
            return;
        }
        this.setTrackedIntermediateExpectation(routeName, itemId, this.trackedIntermediateExpectedCount(routeName, itemId) - consumedCount);
    }

    public void setQueuedPlanReservationMode(final QueuedPlanReservationMode queuedPlanReservationMode) {
        final QueuedPlanReservationMode resolvedMode = queuedPlanReservationMode == null
                ? QueuedPlanReservationMode.FULL_QUEUE
                : queuedPlanReservationMode;
        if (this.queuedPlanReservationMode == resolvedMode) {
            return;
        }

        this.queuedPlanReservationMode = resolvedMode;
        this.rearmQueuedPlanAfterSteeringChange();
        this.markStateChanged();
    }

    public boolean canExecuteQueuedPlan() {
        return this.hasQueuedPlan() && this.queuedPlanJobStatus.allowsExecution();
    }

    public boolean canResumeQueuedPlan() {
        return this.hasQueuedPlan() && this.queuedPlanJobStatus.requiresResume();
    }

    public boolean canAbortQueuedPlan() {
        return this.hasQueuedPlan() && this.queuedPlanJobStatus.canAbort();
    }

    public boolean canReserveQueuedPlanStep(final CraftingCPUBlockEntity craftingCpu,
                                            final IItemHandler inputHandler,
                                            final IItemHandler outputHandler) {
        if (!this.hasQueuedPlan() || craftingCpu == null || inputHandler == null || outputHandler == null) {
            return false;
        }

        final List<ItemStack> pattern = this.copyPlanWindow(this.queuedPlanStepIndex);
        final int requestedCrafts = this.getQueuedPlanRequestedCrafts();
        return craftingCpu.countCraftableForPatternWithHandlers(pattern, inputHandler, outputHandler, requestedCrafts) >= requestedCrafts;
    }

    public int craftReservedQueuedPlanStep(final CraftingCPUBlockEntity craftingCpu,
                                           final IItemHandler inputHandler,
                                           final IItemHandler outputHandler) {
        if (!this.hasQueuedPlan() || craftingCpu == null || inputHandler == null || outputHandler == null) {
            return 0;
        }

        final List<ItemStack> pattern = this.copyPlanWindow(this.queuedPlanStepIndex);
        final int requestedCrafts = this.getQueuedPlanRequestedCrafts();
        if (craftingCpu.countCraftableForPatternWithHandlers(pattern, inputHandler, outputHandler, requestedCrafts) < requestedCrafts) {
            return 0;
        }

        craftingCpu.setRecipePattern(pattern);
        final int crafted = craftingCpu.craftWithHandlers(inputHandler, outputHandler, requestedCrafts);
        if (crafted > 0) {
            this.applyQueuedPlanStepResult(crafted);
        }
        return crafted;
    }

    public boolean hasQueuedPlan() {
        return this.queuedPlanCycles > 0 && !this.recipePlan.isEmpty();
    }

    public void markQueuedPlanResumable() {
        if (!this.hasQueuedPlan()) {
            this.clearQueuedPlanProgress(QueuedPlanJobStatus.IDLE);
            this.markStateChanged();
            return;
        }
        this.updateQueuedPlanJobState(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
    }

    public void markQueuedPlanBlocked() {
        this.markQueuedPlanBlocked(this.queuedPlanJobErrorClass, this.queuedPlanJobMessage, this.queuedPlanJobInputRoute, this.queuedPlanJobOutputRoute);
    }

    public void markQueuedPlanBlocked(final String errorClass, final String message, final String inputRoute, final String outputRoute) {
        if (!this.hasQueuedPlan()) {
            return;
        }
        this.updateQueuedPlanJobState(
                QueuedPlanJobStatus.BLOCKED,
                errorClass,
                message,
                inputRoute,
                outputRoute,
                defaultActionHint(QueuedPlanJobStatus.BLOCKED, errorClass));
    }

    public void markQueuedPlanFailed() {
        this.markQueuedPlanFailed(this.queuedPlanJobErrorClass, this.queuedPlanJobMessage, this.queuedPlanJobInputRoute, this.queuedPlanJobOutputRoute);
    }

    public void markQueuedPlanFailed(final String errorClass, final String message, final String inputRoute, final String outputRoute) {
        if (!this.hasQueuedPlan()) {
            return;
        }
        this.updateQueuedPlanJobState(
                QueuedPlanJobStatus.FAILED,
                errorClass,
                message,
                inputRoute,
                outputRoute,
                defaultActionHint(QueuedPlanJobStatus.FAILED, errorClass));
    }

    public void markQueuedPlanCompleted() {
        this.clearQueuedPlanProgress(QueuedPlanJobStatus.COMPLETED);
        this.markStateChanged();
    }

    public boolean resumeQueuedPlan() {
        if (!this.hasQueuedPlan()) {
            return false;
        }
        if (this.queuedPlanJobStatus == QueuedPlanJobStatus.RESUMABLE) {
            return true;
        }
        if (!this.queuedPlanJobStatus.requiresResume()) {
            return false;
        }

        this.updateQueuedPlanJobState(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        return true;
    }

    public boolean abortQueuedPlan() {
        if (!this.canAbortQueuedPlan()) {
            return false;
        }

        this.clearQueuedPlanState();
        return true;
    }

    public void setQueuedPlanCycles(final int queuedPlanCycles) {
        final int normalizedCycles = Math.max(0, queuedPlanCycles);
        if (normalizedCycles <= 0) {
            if (this.queuedPlanCycles <= 0 && this.isQueuedPlanAtStart() && this.queuedPlanJobStatus == QueuedPlanJobStatus.IDLE) {
                return;
            }
            this.clearQueuedPlanState();
            return;
        }

        if (this.queuedPlanCycles == normalizedCycles
                && this.isQueuedPlanAtStart()
                && this.queuedPlanJobStatus == QueuedPlanJobStatus.RESUMABLE) {
            return;
        }

        this.queuedPlanCycles = normalizedCycles;
        this.queuedPlanTotalCycles = normalizedCycles;
        this.resetQueuedPlanProgress();
        this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        this.markStateChanged();
    }

    public int queuePlanCycles(final int additionalCycles) {
        final int normalizedCycles = Math.max(0, additionalCycles);
        if (normalizedCycles <= 0) {
            return this.queuedPlanCycles;
        }

        final boolean wasInactive = this.queuedPlanCycles <= 0;
        this.queuedPlanCycles += normalizedCycles;
        if (wasInactive) {
            this.queuedPlanTotalCycles = this.queuedPlanCycles;
            this.resetQueuedPlanProgress();
        } else {
            this.queuedPlanTotalCycles = Math.max(this.queuedPlanTotalCycles, this.queuedPlanCycles + this.queuedPlanCycleIndex);
        }
        this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        this.markStateChanged();
        return this.queuedPlanCycles;
    }

    public void clearQueuedPlan() {
        if (this.queuedPlanCycles <= 0 && this.isQueuedPlanAtStart() && this.queuedPlanJobStatus == QueuedPlanJobStatus.IDLE) {
            return;
        }
        this.clearQueuedPlanState();
    }

    public void applyQueuedPlanStepResult(final int crafted) {
        if (!this.hasQueuedPlan()) {
            return;
        }

        final int requestedCrafts = this.getQueuedPlanRequestedCrafts();
        if (crafted <= 0 || requestedCrafts <= 0) {
            return;
        }

        if (crafted >= requestedCrafts) {
            this.advanceQueuedPlan();
            return;
        }

        this.queuedPlanRemainingCrafts = requestedCrafts - crafted;
        this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        this.markStateChanged();
    }

    public void appendPlanStep(final int windowX, final int windowY, final int crafts) {
        this.appendPlanStep(windowX, windowY, crafts, "", "");
    }

    public void appendPlanStep(final int windowX, final int windowY, final int crafts, final String inputRouteName, final String outputRouteName) {
        this.validateWindowOrigin(windowX, windowY);
        final List<RecipePlanStep> updatedPlan = new ArrayList<>(this.recipePlan);
        updatedPlan.add(new RecipePlanStep(windowX, windowY, normalizeCraftCount(crafts), sanitizeRouteName(inputRouteName), sanitizeRouteName(outputRouteName)));
        this.replacePlan(updatedPlan);
    }

    public void setPlanStep(final int index, final int windowX, final int windowY, final int crafts) {
        this.setPlanStep(index, windowX, windowY, crafts, "", "");
    }

    public void setPlanStep(final int index, final int windowX, final int windowY, final int crafts, final String inputRouteName, final String outputRouteName) {
        this.validatePlanIndex(index);
        this.validateWindowOrigin(windowX, windowY);
        final List<RecipePlanStep> updatedPlan = new ArrayList<>(this.recipePlan);
        updatedPlan.set(index, new RecipePlanStep(windowX, windowY, normalizeCraftCount(crafts), sanitizeRouteName(inputRouteName), sanitizeRouteName(outputRouteName)));
        this.replacePlan(updatedPlan);
    }

    public void removePlanStep(final int index) {
        this.validatePlanIndex(index);
        final List<RecipePlanStep> updatedPlan = new ArrayList<>(this.recipePlan);
        updatedPlan.remove(index);
        this.replacePlan(updatedPlan);
    }

    public void clearPlan() {
        if (this.recipePlan.isEmpty() && this.queuedPlanJobStatus == QueuedPlanJobStatus.IDLE) {
            return;
        }

        this.recipePlan.clear();
        this.clearQueuedPlanProgress(QueuedPlanJobStatus.IDLE);
        this.markStateChanged();
    }

    public int rebuildPlanFromGrid() {
        final List<RecipePlanStep> rebuiltPlan = new ArrayList<>();
        final NonNullList<ItemStack> remainingGrid = this.copyRecipeGrid();
        final List<RecipePlanStep> preservedSteps = new ArrayList<>(this.recipePlan);

        this.rebuildKnownPlanWindows(remainingGrid, preservedSteps, rebuiltPlan);
        while (this.rebuildPlanPass(remainingGrid, preservedSteps, rebuiltPlan)) {
            // Continue until every remaining populated 3x3 window has been consumed into plan steps.
        }

        this.replacePlan(rebuiltPlan);
        return this.recipePlan.size();
    }

    public int craftPlan(final CraftingCPUBlockEntity craftingCpu, final IItemHandler inputHandler, final IItemHandler outputHandler, final int cycles) {
        if (craftingCpu == null || inputHandler == null || outputHandler == null || cycles <= 0 || this.recipePlan.isEmpty()) {
            return 0;
        }

        int totalCrafts = 0;
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (int stepIndex = 0; stepIndex < this.recipePlan.size(); stepIndex++) {
                final RecipePlanStep step = this.recipePlan.get(stepIndex);
                craftingCpu.setRecipePattern(this.copyWindow(step.windowX(), step.windowY()));
                final int crafted = craftingCpu.craftWithHandlers(inputHandler, outputHandler, step.crafts());
                totalCrafts += crafted;
                if (crafted < step.crafts()) {
                    return totalCrafts;
                }
            }
        }

        return totalCrafts;
    }

    private List<ItemStack> copyWindow(final int windowX, final int windowY) {
        this.validateWindowOrigin(windowX, windowY);
        final List<ItemStack> window = new ArrayList<>(WINDOW_SIZE * WINDOW_SIZE);
        for (int row = 0; row < WINDOW_SIZE; row++) {
            for (int column = 0; column < WINDOW_SIZE; column++) {
                window.add(this.recipeGrid.get(gridIndex(windowX + column, windowY + row)).copy());
            }
        }
        return List.copyOf(window);
    }

    public void pushRecipeWindowTo(final CraftingCPUBlockEntity craftingCpu) {
        if (craftingCpu == null) {
            throw new IllegalArgumentException("Linked Crafting CPU must not be null.");
        }
        craftingCpu.setRecipePattern(this.copyActiveRecipeWindow());
    }

    public void cycleGridSize() {
        if (this.gridWidth == 3) {
            this.setGridSize(5);
        } else if (this.gridWidth == 5) {
            this.setGridSize(7);
        } else {
            this.setGridSize(3);
        }
    }

    public void setGridSize(final int size) {
        if (size != 3 && size != 5 && size != 7) {
            throw new IllegalArgumentException("Crafting I/O grid size must be 3, 5 or 7.");
        }
        if (this.gridWidth == size && this.gridHeight == size) {
            return;
        }

        this.gridWidth = size;
        this.gridHeight = size;
        this.windowX = clampWindowCoordinate(this.windowX, this.gridWidth);
        this.windowY = clampWindowCoordinate(this.windowY, this.gridHeight);
        this.normalizePlanSteps();
        this.markStateChanged();
    }

    public String describeState() {
        final String linkedCpu = this.linkedCpuEndpoint.isBlank() ? UNBOUND : this.linkedCpuEndpoint;
        final String inputRoute = this.materialInputEndpoint.isBlank() ? UNBOUND : this.materialInputEndpoint + ":" + this.materialInputSide.getSerializedName();
        final String outputRoute = this.materialOutputEndpoint.isBlank() ? UNBOUND : this.materialOutputEndpoint + ":" + this.materialOutputSide.getSerializedName();
        final String queuedPlanState = this.hasQueuedPlan()
                ? this.queuedPlanCycles + " remaining | cycle " + (this.queuedPlanCycleIndex + 1)
                + " step " + (this.queuedPlanStepIndex + 1)
                + " | crafts left in step: " + this.getQueuedPlanRequestedCrafts()
                : "0 remaining";
        return "Endpoint: " + this.getEndpointName() + " | Crafting I/O recipe grid: " + this.gridWidth + "x" + this.gridHeight
                + " | window: " + this.windowX + "," + this.windowY
            + " | plan steps: " + this.recipePlan.size()
            + " | named routes: " + this.namedRoutes.size()
                + " | queued plan: " + queuedPlanState
                + " | job: " + this.queuedPlanJobStatus.serializedName()
                + " | action: " + this.queuedPlanJobActionHint
                + (this.queuedPlanJobErrorClass.isBlank() ? "" : " | error: " + this.queuedPlanJobErrorClass)
                + (this.queuedPlanJobMessage.isBlank() ? "" : " | note: " + this.queuedPlanJobMessage)
                + " | tracked intermediates: " + this.getTrackedIntermediateItemCount()
                + " | reservation: " + this.queuedPlanReservationMode.serializedName()
                + " | cpu: " + linkedCpu
                + " | input: " + inputRoute
                + " | output: " + outputRoute;
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.gridWidth = normalizeGridSize(tag.getInt(TAG_GRID_WIDTH));
        this.gridHeight = normalizeGridSize(tag.getInt(TAG_GRID_HEIGHT));
        this.windowX = clampWindowCoordinate(tag.getInt(TAG_WINDOW_X), this.gridWidth);
        this.windowY = clampWindowCoordinate(tag.getInt(TAG_WINDOW_Y), this.gridHeight);
        this.linkedCpuEndpoint = sanitizeReference(tag.getString(TAG_LINKED_CPU));
        this.materialInputEndpoint = sanitizeReference(tag.getString(TAG_INPUT_ENDPOINT));
        this.materialInputSide = readSide(tag.getString(TAG_INPUT_SIDE), Direction.WEST);
        this.materialOutputEndpoint = sanitizeReference(tag.getString(TAG_OUTPUT_ENDPOINT));
        this.materialOutputSide = readSide(tag.getString(TAG_OUTPUT_SIDE), Direction.EAST);
        this.namedRoutes.clear();
        this.recipePlan.clear();
        for (int slot = 0; slot < MAX_SLOT_COUNT; slot++) {
            final String itemId = tag.getString(TAG_GRID_SLOT_PREFIX + slot + "Item");
            final int count = tag.getInt(TAG_GRID_SLOT_PREFIX + slot + "Count");
            this.recipeGrid.set(slot, createGridStack(itemId, count));
        }
        final int routeCount = Math.max(0, tag.getInt(TAG_ROUTE_COUNT));
        for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
            final String routeName = sanitizeRouteName(tag.getString(TAG_ROUTE_PREFIX + routeIndex + "Name"));
            final String endpoint = sanitizeReference(tag.getString(TAG_ROUTE_PREFIX + routeIndex + "Endpoint"));
            if (routeName.isBlank() || endpoint.isBlank()) {
                continue;
            }
            this.namedRoutes.add(new NamedRoute(routeName, endpoint, readSide(tag.getString(TAG_ROUTE_PREFIX + routeIndex + "Side"), Direction.NORTH)));
        }
        final int planStepCount = Math.max(0, tag.getInt(TAG_PLAN_STEP_COUNT));
        for (int stepIndex = 0; stepIndex < planStepCount; stepIndex++) {
            final int stepX = clampWindowCoordinate(tag.getInt(TAG_PLAN_STEP_PREFIX + stepIndex + "X"), this.gridWidth);
            final int stepY = clampWindowCoordinate(tag.getInt(TAG_PLAN_STEP_PREFIX + stepIndex + "Y"), this.gridHeight);
            final int stepCrafts = normalizeCraftCount(tag.getInt(TAG_PLAN_STEP_PREFIX + stepIndex + "Crafts"));
            this.recipePlan.add(new RecipePlanStep(
                    stepX,
                    stepY,
                    stepCrafts,
                    sanitizeRouteName(tag.getString(TAG_PLAN_STEP_PREFIX + stepIndex + "InputRoute")),
                    sanitizeRouteName(tag.getString(TAG_PLAN_STEP_PREFIX + stepIndex + "OutputRoute"))
            ));
        }
        this.queuedPlanCycles = Math.max(0, tag.getInt(TAG_QUEUED_PLAN_CYCLES));
        this.queuedPlanCycleIndex = Math.max(0, tag.getInt(TAG_QUEUED_PLAN_CYCLE_INDEX));
        this.queuedPlanStepIndex = Math.max(0, tag.getInt(TAG_QUEUED_PLAN_STEP_INDEX));
        this.queuedPlanRemainingCrafts = Math.max(0, tag.getInt(TAG_QUEUED_PLAN_REMAINING_CRAFTS));
        this.queuedPlanTotalCycles = Math.max(0, tag.getInt(TAG_QUEUED_PLAN_TOTAL_CYCLES));
        this.queuedPlanReservationMode = QueuedPlanReservationMode.fromSerializedName(tag.getString(TAG_QUEUED_PLAN_RESERVATION_MODE));
        this.queuedPlanJobStatus = QueuedPlanJobStatus.fromSerializedName(tag.getString(TAG_QUEUED_PLAN_JOB_STATUS));
        this.queuedPlanJobErrorClass = sanitizeJobToken(tag.contains(TAG_QUEUED_PLAN_ERROR_CLASS) ? tag.getString(TAG_QUEUED_PLAN_ERROR_CLASS) : "");
        this.queuedPlanJobActionHint = sanitizeJobToken(tag.contains(TAG_QUEUED_PLAN_ACTION_HINT) ? tag.getString(TAG_QUEUED_PLAN_ACTION_HINT) : "");
        this.queuedPlanJobMessage = sanitizeJobMessage(tag.contains(TAG_QUEUED_PLAN_MESSAGE) ? tag.getString(TAG_QUEUED_PLAN_MESSAGE) : "");
        this.queuedPlanJobInputRoute = sanitizeJobText(tag.contains(TAG_QUEUED_PLAN_INPUT_ROUTE) ? tag.getString(TAG_QUEUED_PLAN_INPUT_ROUTE) : "", MAX_JOB_TEXT_LENGTH);
        this.queuedPlanJobOutputRoute = sanitizeJobText(tag.contains(TAG_QUEUED_PLAN_OUTPUT_ROUTE) ? tag.getString(TAG_QUEUED_PLAN_OUTPUT_ROUTE) : "", MAX_JOB_TEXT_LENGTH);
        this.trackedIntermediateStates.clear();
        this.trackedIntermediateStates.addAll(readTrackedIntermediateStates(tag));
        this.normalizeQueuedPlanState();
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_GRID_WIDTH, this.gridWidth);
        tag.putInt(TAG_GRID_HEIGHT, this.gridHeight);
        tag.putInt(TAG_WINDOW_X, this.windowX);
        tag.putInt(TAG_WINDOW_Y, this.windowY);
        if (!this.linkedCpuEndpoint.isBlank()) {
            tag.putString(TAG_LINKED_CPU, this.linkedCpuEndpoint);
        }
        if (!this.materialInputEndpoint.isBlank()) {
            tag.putString(TAG_INPUT_ENDPOINT, this.materialInputEndpoint);
        }
        tag.putString(TAG_INPUT_SIDE, this.materialInputSide.getSerializedName());
        if (!this.materialOutputEndpoint.isBlank()) {
            tag.putString(TAG_OUTPUT_ENDPOINT, this.materialOutputEndpoint);
        }
        tag.putString(TAG_OUTPUT_SIDE, this.materialOutputSide.getSerializedName());
        tag.putInt(TAG_ROUTE_COUNT, this.namedRoutes.size());
        for (int routeIndex = 0; routeIndex < this.namedRoutes.size(); routeIndex++) {
            final NamedRoute route = this.namedRoutes.get(routeIndex);
            tag.putString(TAG_ROUTE_PREFIX + routeIndex + "Name", route.name());
            tag.putString(TAG_ROUTE_PREFIX + routeIndex + "Endpoint", route.endpoint());
            tag.putString(TAG_ROUTE_PREFIX + routeIndex + "Side", route.side().getSerializedName());
        }
        for (int slot = 0; slot < MAX_SLOT_COUNT; slot++) {
            final ItemStack stack = this.recipeGrid.get(slot);
            tag.putString(TAG_GRID_SLOT_PREFIX + slot + "Item", XLItemFluidAccess.itemId(stack));
            tag.putInt(TAG_GRID_SLOT_PREFIX + slot + "Count", stack.getCount());
        }
        tag.putInt(TAG_PLAN_STEP_COUNT, this.recipePlan.size());
        for (int stepIndex = 0; stepIndex < this.recipePlan.size(); stepIndex++) {
            final RecipePlanStep step = this.recipePlan.get(stepIndex);
            tag.putInt(TAG_PLAN_STEP_PREFIX + stepIndex + "X", step.windowX());
            tag.putInt(TAG_PLAN_STEP_PREFIX + stepIndex + "Y", step.windowY());
            tag.putInt(TAG_PLAN_STEP_PREFIX + stepIndex + "Crafts", step.crafts());
            if (!step.inputRouteName().isBlank()) {
                tag.putString(TAG_PLAN_STEP_PREFIX + stepIndex + "InputRoute", step.inputRouteName());
            }
            if (!step.outputRouteName().isBlank()) {
                tag.putString(TAG_PLAN_STEP_PREFIX + stepIndex + "OutputRoute", step.outputRouteName());
            }
        }
        this.writeQueuedPlanState(tag);
    }

    private void writeQueuedPlanState(final CompoundTag tag) {
        tag.putInt(TAG_QUEUED_PLAN_CYCLES, this.queuedPlanCycles);
        tag.putInt(TAG_QUEUED_PLAN_CYCLE_INDEX, this.queuedPlanCycleIndex);
        tag.putInt(TAG_QUEUED_PLAN_STEP_INDEX, this.queuedPlanStepIndex);
        tag.putInt(TAG_QUEUED_PLAN_REMAINING_CRAFTS, this.queuedPlanRemainingCrafts);
        tag.putInt(TAG_QUEUED_PLAN_TOTAL_CYCLES, this.queuedPlanTotalCycles);
        tag.putString(TAG_QUEUED_PLAN_RESERVATION_MODE, this.queuedPlanReservationMode.serializedName());
        tag.putString(TAG_QUEUED_PLAN_JOB_STATUS, this.queuedPlanJobStatus.serializedName());
        if (!this.queuedPlanJobErrorClass.isBlank()) {
            tag.putString(TAG_QUEUED_PLAN_ERROR_CLASS, this.queuedPlanJobErrorClass);
        }
        if (!this.queuedPlanJobActionHint.isBlank()) {
            tag.putString(TAG_QUEUED_PLAN_ACTION_HINT, this.queuedPlanJobActionHint);
        }
        if (!this.queuedPlanJobMessage.isBlank()) {
            tag.putString(TAG_QUEUED_PLAN_MESSAGE, this.queuedPlanJobMessage);
        }
        if (!this.queuedPlanJobInputRoute.isBlank()) {
            tag.putString(TAG_QUEUED_PLAN_INPUT_ROUTE, this.queuedPlanJobInputRoute);
        }
        if (!this.queuedPlanJobOutputRoute.isBlank()) {
            tag.putString(TAG_QUEUED_PLAN_OUTPUT_ROUTE, this.queuedPlanJobOutputRoute);
        }
        if (!this.trackedIntermediateStates.isEmpty()) {
            tag.put(TAG_QUEUED_PLAN_TRACKED_INTERMEDIATES, writeTrackedIntermediateStates(this.trackedIntermediateStates));
        }
    }

    private ItemStack getGridSlot(final int slot) {
        this.validateVisibleSlot(slot);
        return this.recipeGrid.get(this.visibleSlotToGridIndex(slot)).copy();
    }

    private void validateVisibleSlot(final int slot) {
        if (slot < 0 || slot >= this.getGridSlotCount()) {
            throw new IllegalArgumentException("Crafting I/O slot must be between 0 and " + (this.getGridSlotCount() - 1) + ".");
        }
    }

    private void validateWindowOrigin(final int windowX, final int windowY) {
        final int maxWindowX = Math.max(0, this.gridWidth - WINDOW_SIZE);
        final int maxWindowY = Math.max(0, this.gridHeight - WINDOW_SIZE);
        if (windowX < 0 || windowX > maxWindowX || windowY < 0 || windowY > maxWindowY) {
            throw new IllegalArgumentException("Crafting I/O window origin must stay within the current " + this.gridWidth + "x" + this.gridHeight + " grid.");
        }
    }

    private void validatePlanIndex(final int index) {
        if (index < 0 || index >= this.recipePlan.size()) {
            throw new IllegalArgumentException("Crafting I/O plan step must be between 0 and " + (this.recipePlan.size() - 1) + ".");
        }
    }

    private RecipePlanStep getPlanStep(final int index) {
        this.validatePlanIndex(index);
        return this.recipePlan.get(index);
    }

    private NamedRoute getRoute(final int index) {
        if (index < 0 || index >= this.namedRoutes.size()) {
            throw new IllegalArgumentException("Crafting I/O route must be between 0 and " + (this.namedRoutes.size() - 1) + ".");
        }
        return this.namedRoutes.get(index);
    }

    private NamedRoute findRoute(final String routeName) {
        final String sanitizedRouteName = sanitizeRouteName(routeName);
        if (sanitizedRouteName.isBlank()) {
            return null;
        }

        for (final NamedRoute route : this.namedRoutes) {
            if (route.name().equals(sanitizedRouteName)) {
                return route;
            }
        }
        return null;
    }

    private void replacePlan(final List<RecipePlanStep> updatedPlan) {
        if (this.recipePlan.equals(updatedPlan)) {
            return;
        }

        this.recipePlan.clear();
        this.recipePlan.addAll(updatedPlan);
        if (updatedPlan.isEmpty()) {
            this.clearQueuedPlanProgress(QueuedPlanJobStatus.IDLE);
        } else if (this.queuedPlanCycles > 0) {
            this.resetQueuedPlanProgress();
            this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        } else {
            this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.IDLE, "", "", "", "", ACTION_HINT_IDLE);
        }
        this.markStateChanged();
    }

    private void normalizePlanSteps() {
        if (this.recipePlan.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (int index = 0; index < this.recipePlan.size(); index++) {
            final RecipePlanStep currentStep = this.recipePlan.get(index);
            final RecipePlanStep normalizedStep = new RecipePlanStep(
                    clampWindowCoordinate(currentStep.windowX(), this.gridWidth),
                    clampWindowCoordinate(currentStep.windowY(), this.gridHeight),
                    normalizeCraftCount(currentStep.crafts()),
                    sanitizeRouteName(currentStep.inputRouteName()),
                    sanitizeRouteName(currentStep.outputRouteName())
            );
            if (!currentStep.equals(normalizedStep)) {
                this.recipePlan.set(index, normalizedStep);
                changed = true;
            }
        }

        if (changed) {
            if (this.queuedPlanCycles > 0) {
                this.resetQueuedPlanProgress();
                this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
            } else {
                this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.IDLE, "", "", "", "", ACTION_HINT_IDLE);
            }
            this.markStateChanged();
        }
    }

    private void advanceQueuedPlan() {
        if (!this.hasQueuedPlan()) {
            return;
        }

        this.queuedPlanRemainingCrafts = 0;
        if (this.queuedPlanStepIndex + 1 < this.recipePlan.size()) {
            this.queuedPlanStepIndex++;
            this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
            this.markStateChanged();
            return;
        }

        this.queuedPlanCycles = Math.max(0, this.queuedPlanCycles - 1);
        if (this.queuedPlanCycles <= 0) {
            this.clearQueuedPlanProgress(QueuedPlanJobStatus.COMPLETED);
            this.markStateChanged();
            return;
        }

        this.queuedPlanCycleIndex++;
        this.queuedPlanStepIndex = 0;
        this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        this.markStateChanged();
    }

    private void normalizeQueuedPlanState() {
        if (this.recipePlan.isEmpty() || this.queuedPlanCycles <= 0) {
            this.clearQueuedPlanProgressInternal();
            if (this.queuedPlanJobStatus != QueuedPlanJobStatus.COMPLETED) {
                this.queuedPlanTotalCycles = 0;
                this.clearTrackedIntermediateStatesInternal();
                this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.IDLE, "", "", "", "", ACTION_HINT_IDLE);
            } else {
                this.clearTrackedIntermediateStatesInternal();
                this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.COMPLETED, "", this.queuedPlanJobMessage, "", "", ACTION_HINT_COMPLETED);
            }
            return;
        }

        this.queuedPlanTotalCycles = Math.max(this.queuedPlanTotalCycles, this.queuedPlanCycles + this.queuedPlanCycleIndex);
        this.queuedPlanCycleIndex = Math.max(0, this.queuedPlanCycleIndex);
        this.queuedPlanStepIndex = Math.max(0, Math.min(this.queuedPlanStepIndex, this.recipePlan.size() - 1));
        final int configuredCrafts = this.recipePlan.get(this.queuedPlanStepIndex).crafts();
        this.queuedPlanRemainingCrafts = Math.max(0, Math.min(this.queuedPlanRemainingCrafts, configuredCrafts));
        if (this.queuedPlanJobStatus == QueuedPlanJobStatus.IDLE || this.queuedPlanJobStatus == QueuedPlanJobStatus.COMPLETED) {
            this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        } else if (this.queuedPlanJobActionHint.isBlank()) {
            this.queuedPlanJobActionHint = defaultActionHint(this.queuedPlanJobStatus, this.queuedPlanJobErrorClass);
        }

        for (int index = this.trackedIntermediateStates.size() - 1; index >= 0; index--) {
            if (this.trackedIntermediateStates.get(index).expectedCount() <= 0) {
                this.trackedIntermediateStates.remove(index);
            }
        }
    }

    private void resetQueuedPlanProgress() {
        this.queuedPlanCycleIndex = 0;
        this.queuedPlanStepIndex = 0;
        this.queuedPlanRemainingCrafts = 0;
        this.clearTrackedIntermediateStatesInternal();
        this.normalizeQueuedPlanState();
    }

    private void clearQueuedPlanState() {
        this.clearQueuedPlanProgress(QueuedPlanJobStatus.IDLE);
        this.markStateChanged();
    }

    private void clearQueuedPlanProgress(final QueuedPlanJobStatus queuedPlanJobStatus) {
        this.clearQueuedPlanProgressInternal();
        this.clearTrackedIntermediateStatesInternal();
        final QueuedPlanJobStatus resolvedStatus = queuedPlanJobStatus == null ? QueuedPlanJobStatus.IDLE : queuedPlanJobStatus;
        if (resolvedStatus != QueuedPlanJobStatus.COMPLETED) {
            this.queuedPlanTotalCycles = 0;
        }
        this.updateQueuedPlanJobStateInternal(
                resolvedStatus,
                "",
                resolvedStatus == QueuedPlanJobStatus.COMPLETED ? "Queued plan completed." : "",
                "",
                "",
                defaultActionHint(resolvedStatus, ""));
    }

    private void clearQueuedPlanProgressInternal() {
        this.queuedPlanCycles = 0;
        this.queuedPlanCycleIndex = 0;
        this.queuedPlanStepIndex = 0;
        this.queuedPlanRemainingCrafts = 0;
    }

    private void rearmQueuedPlanAfterSteeringChange() {
        if (this.hasQueuedPlan() && this.queuedPlanJobStatus.requiresResume()) {
            this.updateQueuedPlanJobStateInternal(QueuedPlanJobStatus.RESUMABLE, "", "", "", "", ACTION_HINT_READY);
        }
    }

    private void updateQueuedPlanJobState(final QueuedPlanJobStatus queuedPlanJobStatus,
                                          final String errorClass,
                                          final String message,
                                          final String inputRoute,
                                          final String outputRoute,
                                          final String actionHint) {
        this.updateQueuedPlanJobStateInternal(queuedPlanJobStatus, errorClass, message, inputRoute, outputRoute, actionHint);
        this.markStateChanged();
    }

    private void updateQueuedPlanJobStateInternal(final QueuedPlanJobStatus queuedPlanJobStatus,
                                                  final String errorClass,
                                                  final String message,
                                                  final String inputRoute,
                                                  final String outputRoute,
                                                  final String actionHint) {
        this.queuedPlanJobStatus = queuedPlanJobStatus == null ? QueuedPlanJobStatus.IDLE : queuedPlanJobStatus;
        this.queuedPlanJobErrorClass = sanitizeJobToken(errorClass);
        this.queuedPlanJobMessage = sanitizeJobMessage(message);
        this.queuedPlanJobInputRoute = sanitizeJobText(inputRoute, MAX_JOB_TEXT_LENGTH);
        this.queuedPlanJobOutputRoute = sanitizeJobText(outputRoute, MAX_JOB_TEXT_LENGTH);
        final String sanitizedActionHint = sanitizeJobToken(actionHint);
        this.queuedPlanJobActionHint = sanitizedActionHint.isBlank()
                ? defaultActionHint(this.queuedPlanJobStatus, this.queuedPlanJobErrorClass)
                : sanitizedActionHint;
    }

    private boolean applyTrackedIntermediateExpectation(final String routeName, final String itemId, final int expectedCount) {
        final String sanitizedRouteName = sanitizeRouteName(routeName);
        final String sanitizedItemId = sanitizeItemId(itemId);
        final int normalizedCount = Math.max(0, expectedCount);
        for (int index = 0; index < this.trackedIntermediateStates.size(); index++) {
            final TrackedIntermediateState trackedIntermediateState = this.trackedIntermediateStates.get(index);
            if (!trackedIntermediateState.routeName().equals(sanitizedRouteName)
                    || !trackedIntermediateState.itemId().equals(sanitizedItemId)) {
                continue;
            }

            if (sanitizedRouteName.isBlank() || sanitizedItemId.isBlank() || normalizedCount <= 0) {
                this.trackedIntermediateStates.remove(index);
                return true;
            }

            final TrackedIntermediateState updatedState = new TrackedIntermediateState(sanitizedRouteName, sanitizedItemId, normalizedCount);
            if (trackedIntermediateState.equals(updatedState)) {
                return false;
            }
            this.trackedIntermediateStates.set(index, updatedState);
            return true;
        }

        if (sanitizedRouteName.isBlank() || sanitizedItemId.isBlank() || normalizedCount <= 0) {
            return false;
        }

        this.trackedIntermediateStates.add(new TrackedIntermediateState(sanitizedRouteName, sanitizedItemId, normalizedCount));
        return true;
    }

    private void clearTrackedIntermediateStatesInternal() {
        this.trackedIntermediateStates.clear();
    }

    private boolean isQueuedPlanAtStart() {
        return this.queuedPlanCycleIndex == 0 && this.queuedPlanStepIndex == 0 && this.queuedPlanRemainingCrafts == 0;
    }

    private NonNullList<ItemStack> copyRecipeGrid() {
        final NonNullList<ItemStack> copy = NonNullList.withSize(MAX_SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < MAX_SLOT_COUNT; slot++) {
            copy.set(slot, this.recipeGrid.get(slot).copy());
        }
        return copy;
    }

    private boolean rebuildPlanPass(final List<ItemStack> remainingGrid,
                                    final List<RecipePlanStep> preservedSteps,
                                    final List<RecipePlanStep> rebuiltPlan) {
        boolean addedStep = false;
        for (int planWindowY = 0; planWindowY <= this.gridHeight - WINDOW_SIZE; planWindowY++) {
            for (int planWindowX = 0; planWindowX <= this.gridWidth - WINDOW_SIZE; planWindowX++) {
                final int crafts = this.windowCraftCount(remainingGrid, planWindowX, planWindowY);
                if (crafts <= 0) {
                    continue;
                }

                final RecipePlanStep preservedStep = takeMatchingPlanStep(preservedSteps, planWindowX, planWindowY);
                rebuiltPlan.add(new RecipePlanStep(
                        planWindowX,
                        planWindowY,
                        crafts,
                        preservedStep == null ? "" : preservedStep.inputRouteName(),
                        preservedStep == null ? "" : preservedStep.outputRouteName()));
                this.consumeWindowCrafts(remainingGrid, planWindowX, planWindowY, crafts);
                addedStep = true;
            }
        }
        return addedStep;
    }

    private void rebuildKnownPlanWindows(final List<ItemStack> remainingGrid,
                                         final List<RecipePlanStep> preservedSteps,
                                         final List<RecipePlanStep> rebuiltPlan) {
        for (final RecipePlanStep knownStep : List.copyOf(this.recipePlan)) {
            final int crafts = this.windowCraftCount(remainingGrid, knownStep.windowX(), knownStep.windowY());
            if (crafts <= 0) {
                continue;
            }

            rebuiltPlan.add(new RecipePlanStep(
                    knownStep.windowX(),
                    knownStep.windowY(),
                    crafts,
                    knownStep.inputRouteName(),
                    knownStep.outputRouteName()));
            this.consumeWindowCrafts(remainingGrid, knownStep.windowX(), knownStep.windowY(), crafts);
            takeMatchingPlanStep(preservedSteps, knownStep.windowX(), knownStep.windowY());
        }
    }

    private int windowCraftCount(final List<ItemStack> grid, final int windowX, final int windowY) {
        int craftCount = Integer.MAX_VALUE;
        boolean hasAnyInput = false;
        for (int row = 0; row < WINDOW_SIZE; row++) {
            for (int column = 0; column < WINDOW_SIZE; column++) {
                final ItemStack stack = grid.get(gridIndex(windowX + column, windowY + row));
                if (stack.isEmpty()) {
                    continue;
                }
                hasAnyInput = true;
                craftCount = Math.min(craftCount, stack.getCount());
            }
        }
        return hasAnyInput ? Math.max(1, craftCount) : 0;
    }

    private void consumeWindowCrafts(final List<ItemStack> grid, final int windowX, final int windowY, final int crafts) {
        if (crafts <= 0) {
            return;
        }

        for (int row = 0; row < WINDOW_SIZE; row++) {
            for (int column = 0; column < WINDOW_SIZE; column++) {
                final int slotIndex = gridIndex(windowX + column, windowY + row);
                final ItemStack stack = grid.get(slotIndex);
                if (stack.isEmpty()) {
                    continue;
                }

                stack.shrink(crafts);
                if (stack.isEmpty()) {
                    grid.set(slotIndex, ItemStack.EMPTY);
                }
            }
        }
    }

    private static RecipePlanStep takeMatchingPlanStep(final List<RecipePlanStep> preservedSteps, final int windowX, final int windowY) {
        for (int index = 0; index < preservedSteps.size(); index++) {
            final RecipePlanStep preservedStep = preservedSteps.get(index);
            if (preservedStep.windowX() == windowX && preservedStep.windowY() == windowY) {
                preservedSteps.remove(index);
                return preservedStep;
            }
        }
        return null;
    }

    private int visibleSlotToGridIndex(final int slot) {
        final int row = slot / this.gridWidth;
        final int column = slot % this.gridWidth;
        return gridIndex(column, row);
    }

    private static int gridIndex(final int column, final int row) {
        return row * MAX_GRID_SIZE + column;
    }

    private static ItemStack createGridStack(final String itemId, final int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return ItemStack.EMPTY;
        }

        final var item = XLItemFluidAccess.resolveItem(itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, count));
    }

    private static int clampWindowCoordinate(final int coordinate, final int size) {
        return Math.max(0, Math.min(Math.max(0, size - WINDOW_SIZE), coordinate));
    }

    private static int normalizeCraftCount(final int crafts) {
        return Math.max(1, crafts);
    }

    private static List<TrackedIntermediateState> readTrackedIntermediateStates(final CompoundTag tag) {
        if (!tag.contains(TAG_QUEUED_PLAN_TRACKED_INTERMEDIATES, Tag.TAG_LIST)) {
            return List.of();
        }

        final ListTag listTag = tag.getList(TAG_QUEUED_PLAN_TRACKED_INTERMEDIATES, Tag.TAG_COMPOUND);
        final ArrayList<TrackedIntermediateState> trackedStates = new ArrayList<>(listTag.size());
        for (int index = 0; index < listTag.size(); index++) {
            final TrackedIntermediateState trackedIntermediateState = TrackedIntermediateState.fromTag(listTag.getCompound(index));
            if (trackedIntermediateState.expectedCount() > 0) {
                trackedStates.add(trackedIntermediateState);
            }
        }
        return List.copyOf(trackedStates);
    }

    private static ListTag writeTrackedIntermediateStates(final List<TrackedIntermediateState> trackedIntermediateStates) {
        final ListTag listTag = new ListTag();
        for (final TrackedIntermediateState trackedIntermediateState : trackedIntermediateStates) {
            listTag.add(trackedIntermediateState.toTag());
        }
        return listTag;
    }

    private static int normalizeGridSize(final int size) {
        if (size >= MAX_GRID_SIZE) {
            return MAX_GRID_SIZE;
        }
        if (size >= 5) {
            return 5;
        }
        return MIN_GRID_SIZE;
    }

    private static Direction readSide(final String rawSide, final Direction fallback) {
        final Direction side = rawSide == null ? null : Direction.byName(rawSide);
        return side == null ? fallback : side;
    }

    private static String sanitizeReference(final String rawName) {
        return rawName == null ? "" : rawName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    private static String sanitizeRouteName(final String rawName) {
        return sanitizeReference(rawName);
    }

    private static String sanitizeItemId(final String rawItemId) {
        return rawItemId == null ? "" : rawItemId.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeJobToken(final String rawValue) {
        return rawValue == null ? "" : rawValue.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    private static String sanitizeJobMessage(final String rawValue) {
        return sanitizeJobText(rawValue, MAX_JOB_MESSAGE_LENGTH);
    }

    private static String sanitizeJobText(final String rawValue, final int maxLength) {
        final String safeValue = rawValue == null ? "" : rawValue;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private static String defaultActionHint(final QueuedPlanJobStatus queuedPlanJobStatus, final String errorClass) {
        if (queuedPlanJobStatus == null || queuedPlanJobStatus == QueuedPlanJobStatus.IDLE) {
            return ACTION_HINT_IDLE;
        }
        if (queuedPlanJobStatus == QueuedPlanJobStatus.COMPLETED) {
            return ACTION_HINT_COMPLETED;
        }
        if (queuedPlanJobStatus == QueuedPlanJobStatus.RESUMABLE) {
            return ACTION_HINT_READY;
        }

        return switch (sanitizeJobToken(errorClass)) {
            case "material_missing" -> "fix_materials";
            case "buffer_full" -> "fix_buffer";
            case "output_full" -> "fix_output";
            case "route_missing" -> "fix_routes";
            case "recipe_invalid" -> "review_recipe";
            case "cpu_unavailable" -> "check_cpu";
            case "intermediate_contaminated" -> "clean_buffer";
            case "intermediate_missing" -> "restore_intermediate";
            default -> "abort_or_resume";
        };
    }

    private static boolean sameGridStack(final ItemStack left, final ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() && right.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(left, right) && left.getCount() == right.getCount();
    }

    private record NamedRoute(String name, String endpoint, Direction side) {
    }

    private record RecipePlanStep(int windowX, int windowY, int crafts, String inputRouteName, String outputRouteName) {
    }

    public record TrackedIntermediateState(String routeName, String itemId, int expectedCount) {
        public TrackedIntermediateState {
            routeName = sanitizeRouteName(routeName);
            itemId = sanitizeItemId(itemId);
            expectedCount = Math.max(0, expectedCount);
        }

        private CompoundTag toTag() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("RouteName", this.routeName);
            tag.putString("ItemId", this.itemId);
            tag.putInt("ExpectedCount", this.expectedCount);
            return tag;
        }

        private static TrackedIntermediateState fromTag(final CompoundTag tag) {
            return new TrackedIntermediateState(
                    tag.getString("RouteName"),
                    tag.getString("ItemId"),
                    tag.getInt("ExpectedCount"));
        }
    }
}
