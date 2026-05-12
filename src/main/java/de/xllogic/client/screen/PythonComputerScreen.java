package de.xllogic.client.screen;

import de.xllogic.client.editor.PythonEditorDiagnostics;
import de.xllogic.client.editor.PythonSuggestionEngine;
import de.xllogic.client.editor.PythonSyntaxHighlighter;
import de.xllogic.client.editor.ScriptDiff;
import de.xllogic.client.editor.SyntaxToken;
import de.xllogic.client.editor.TextDocument;
import de.xllogic.client.editor.TokenStyle;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.common.network.payload.ComputerSessionStatus;
import de.xllogic.common.network.payload.CloseComputerSessionPayload;
import de.xllogic.common.network.payload.ExecuteComputerScriptPayload;
import de.xllogic.common.network.payload.HeartbeatComputerSessionPayload;
import de.xllogic.common.network.payload.RecoveryDraftResumeStatus;
import de.xllogic.common.network.payload.ResumeRecoveryDraftPayload;
import de.xllogic.common.network.payload.ResumeRecoveryDraftResultPayload;
import de.xllogic.common.network.payload.SaveComputerStatePayload;
import de.xllogic.common.network.payload.StopComputerScriptPayload;
import de.xllogic.runtime.ComputerOutputEntry;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonExecutionResult;
import de.xllogic.runtime.PythonRuntime;
import de.xllogic.runtime.RuntimeFactory;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class PythonComputerScreen extends Screen {
    private static final int RECOVERY_RESUME_HANDSHAKE_INTERVAL_TICKS = 20;
    private static final int RUNNING_SESSION_HEARTBEAT_INTERVAL_TICKS = 1;
    private static final AtomicInteger LOCAL_EXECUTION_WORKER_COUNTER = new AtomicInteger();
    private static final ExecutorService LOCAL_EXECUTION_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "xllogic-python-standalone-" + LOCAL_EXECUTION_WORKER_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final Component TITLE = Component.literal("XL Logic Python Computer");
    private static final int APP_BACKGROUND = 0xFF0B0F14;
    private static final int PANEL_BACKGROUND = 0xFF111827;
    private static final int PANEL_BORDER = 0xFF2F3A4A;
    private static final int CURRENT_LINE = 0x332E8B57;
    private static final int OUTPUT_OK = 0xFF56D364;
    private static final int OUTPUT_ERROR = 0xFFF85149;
    private static final int OUTPUT_INFO = 0xFF79C0FF;
    private static final int OUTPUT_WARN = 0xFFF2CC60;
    private static final int OUTPUT_DIM = 0xFF8B949E;
    private static final int OUTPUT_TEXT = 0xFFD8E0E8;
    private static final int OUTPUT_CARD_BACKGROUND = 0x3322384D;
    private static final int OUTPUT_TABLE_HEADER = 0x22334A60;
    private static final int CURSOR = 0xFFF2F2F2;
    private static final int LINE_NUMBER = 0xFF6E7681;
    private static final int SELECTION = 0x663B82F6;
    private static final int DIAGNOSTIC_ERROR = 0xFFF85149;
    private static final int SUGGESTION_BACKGROUND = 0xFF0F172A;
    private static final int SUGGESTION_BORDER = 0xFF355070;
    private static final int SUGGESTION_SELECTED = 0xFF1D4ED8;
    private static final int RECOVERY_COMPARE_ROW = 0x22334A60;
    private static final int RECOVERY_COMPARE_SELECTED = 0x334D7CFE;
    private static final int RECOVERY_COMPARE_CONFLICT = 0x334B2F20;
    private static final int RECOVERY_COMPARE_LOCAL = 0x223E5E34;
    private static final int RECOVERY_COMPARE_SERVER = 0x22304B63;
    private static final int ACTION_BUTTON = 0x22334A60;
    private static final int ACTION_BUTTON_HOVER = 0x334A6A8A;
    private static final int HEADER_TEXT = 0xFFE6EDF3;
    private static final int STATUS_TEXT = 0xFF9FB3C8;
    private static final int EDITOR_HORIZONTAL_SCROLL_STEP = 32;

    private final BlockPos boundComputerPos;
    private final BlockPos recoveryTargetPos;
    private final TextDocument document;
    private final PythonSyntaxHighlighter highlighter = new PythonSyntaxHighlighter();
    private final PythonEditorDiagnostics diagnosticEngine = new PythonEditorDiagnostics();
    private final PythonSuggestionEngine suggestionEngine = new PythonSuggestionEngine();
    private final PythonRuntime runtime = RuntimeFactory.createPythonRuntime();
    private final List<ComputerOutputEntry> outputEntries = new ArrayList<>();
    private PythonExecutionContext executionContext;
    private ComputerRuntimeSnapshot runtimeState;
    private PythonEditorDiagnostics.DiagnosticReport diagnosticReport = new PythonEditorDiagnostics.DiagnosticReport(List.of());
    private Map<Integer, List<PythonEditorDiagnostics.Diagnostic>> diagnosticsByLine = Map.of();
    private PythonSuggestionEngine.SuggestionSession suggestionSession;
    private String lastSyncedScript;
    private boolean autoStartOnLoad;
    private boolean lastSyncedAutoStartOnLoad;
    private int editorScroll;
    private int editorHorizontalScroll;
    private int outputScrollEntries;
    private int selectedSuggestionIndex;
    private int sessionHeartbeatTicks;
    private boolean draggingSelection;
    private boolean followEditorCursor = true;
    private boolean startRequested;
    private boolean stopRequested;
    private boolean editable;
    private String activeEditorName;
    private ComputerSessionStatus sessionStatus = ComputerSessionStatus.ACTIVE;
    private String sessionMessage = "";
    private int unavailableTicks;
    private boolean recoveryResumeEligible;
    private final boolean recoveryDraft;
    private int recoveryResumeHandshakeTicks;
    private RecoveryDraftResumeStatus recoveryResumeStatus = RecoveryDraftResumeStatus.TARGET_UNAVAILABLE;
    private String recoveryResumeMessage = "";
    private String recoveryServerScript = "";
    private String recoveryBaseScript = "";
    private ScriptDiff.MergeResult recoveryMergeResult = new ScriptDiff.MergeResult("", List.of(), 0);
    private int selectedRecoveryCompareConflict;
    private int standaloneExecutionGeneration;
    private boolean overlayHandoff;

    public PythonComputerScreen() {
        this(null, null, TextDocument.starterPythonDocument().getText(), ComputerRuntimeSnapshot.idle(), PythonExecutionContext.empty(), true, "");
    }

    public PythonComputerScreen(final String initialScript, final PythonExecutionContext executionContext) {
        this(null, null, initialScript, ComputerRuntimeSnapshot.idle(), executionContext, true, "");
    }

    public PythonComputerScreen(final BlockPos boundComputerPos, final String initialScript, final ComputerRuntimeSnapshot runtimeState, final PythonExecutionContext executionContext) {
        this(boundComputerPos, null, initialScript, runtimeState, executionContext, true, "");
    }

    public PythonComputerScreen(final BlockPos boundComputerPos, final String initialScript, final ComputerRuntimeSnapshot runtimeState,
                                final PythonExecutionContext executionContext, final boolean editable, final String activeEditorName) {
        this(boundComputerPos, null, initialScript, runtimeState, executionContext, editable, activeEditorName);
    }

    public PythonComputerScreen(final BlockPos boundComputerPos, final String initialScript, final ComputerRuntimeSnapshot runtimeState,
                                final PythonExecutionContext executionContext, final boolean editable, final String activeEditorName,
                                final boolean autoStartOnLoad) {
        this(boundComputerPos, null, initialScript, runtimeState, executionContext, editable, activeEditorName);
        this.autoStartOnLoad = autoStartOnLoad;
        this.lastSyncedAutoStartOnLoad = autoStartOnLoad;
    }

    public static PythonComputerScreen recoveryDraft(final BlockPos recoveryTargetPos, final String initialScript, final ComputerRuntimeSnapshot runtimeState,
                                                     final PythonExecutionContext executionContext, final boolean autoStartOnLoad) {
        return recoveryDraft(recoveryTargetPos, initialScript, initialScript, runtimeState, executionContext, autoStartOnLoad);
    }

    public static PythonComputerScreen recoveryDraft(final BlockPos recoveryTargetPos, final String initialScript, final String baseScript,
                                                     final ComputerRuntimeSnapshot runtimeState, final PythonExecutionContext executionContext,
                                                     final boolean autoStartOnLoad) {
        final PythonComputerScreen screen = new PythonComputerScreen(null, recoveryTargetPos, initialScript, runtimeState, executionContext, true, "");
        screen.autoStartOnLoad = autoStartOnLoad;
        screen.lastSyncedAutoStartOnLoad = autoStartOnLoad;
        screen.recoveryBaseScript = baseScript == null ? screen.lastSyncedScript : baseScript;
        return screen;
    }

    private PythonComputerScreen(final BlockPos boundComputerPos, final BlockPos recoveryTargetPos, final String initialScript, final ComputerRuntimeSnapshot runtimeState,
                                 final PythonExecutionContext executionContext, final boolean editable, final String activeEditorName) {
        super(TITLE);
        this.boundComputerPos = boundComputerPos;
        this.recoveryTargetPos = recoveryTargetPos == null ? null : recoveryTargetPos.immutable();
        this.document = new TextDocument(initialScript);
        this.executionContext = executionContext;
        this.runtimeState = runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState;
        this.suggestionSession = PythonSuggestionEngine.SuggestionSession.empty(this.document.getCursorLine(), this.document.getCursorColumn());
        this.lastSyncedScript = this.document.getText();
        this.editable = editable || boundComputerPos == null;
        this.activeEditorName = activeEditorName == null ? "" : activeEditorName;
        this.recoveryDraft = this.boundComputerPos == null && this.recoveryTargetPos != null;
        this.recoveryBaseScript = this.lastSyncedScript;
        this.recoveryResumeEligible = this.isBoundComputer() && this.editable;
        if (this.recoveryDraft) {
            this.recoveryResumeMessage = "Recovery draft is waiting to resume on the original computer.";
        }
        this.initializeOutputLines();
        this.refreshSuggestions(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isBoundComputer()) {
            this.sessionHeartbeatTicks = 0;
            this.unavailableTicks = 0;
            this.tickRecoveryDraftResume();
            return;
        }

        if (!this.sessionStatus.targetAvailable()) {
            this.unavailableTicks++;
            if (this.advancePersistentUnavailablePolicy()) {
                return;
            }
        } else {
            this.unavailableTicks = 0;
        }

        this.sessionHeartbeatTicks++;
        final int heartbeatInterval = this.runtimeState.running() ? RUNNING_SESSION_HEARTBEAT_INTERVAL_TICKS : 20;
        if (this.sessionHeartbeatTicks >= heartbeatInterval) {
            PacketDistributor.sendToServer(new HeartbeatComputerSessionPayload(this.boundComputerPos));
            this.sessionHeartbeatTicks = 0;
        }
    }

    void tickOverlayHost() {
        if (!this.isBoundComputer()) {
            if (this.recoveryDraft) {
                this.tickRecoveryDraftResume();
            }
            return;
        }

        this.sessionHeartbeatTicks++;
        final int heartbeatInterval = this.runtimeState.running() ? RUNNING_SESSION_HEARTBEAT_INTERVAL_TICKS : 20;
        if (this.sessionHeartbeatTicks >= heartbeatInterval) {
            PacketDistributor.sendToServer(new HeartbeatComputerSessionPayload(this.boundComputerPos));
            this.sessionHeartbeatTicks = 0;
        }
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.handleClipboardShortcuts(keyCode)) {
            return true;
        }

        if (this.handleRecoveryDraftConflictShortcuts(keyCode)) {
            return true;
        }

        if (this.handleOutputNavigationKeys(keyCode)) {
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_SPACE) {
            this.refreshSuggestions(true);
            return true;
        }

        if (this.handleSuggestionKeys(keyCode)) {
            return true;
        }

        final boolean selecting = hasShiftDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_F5 -> {
                this.executeDocument();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    return true;
                }
                this.document.insertNewLine();
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    return true;
                }
                this.document.backspace();
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    return true;
                }
                this.document.deleteForward();
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    return true;
                }
                this.document.insertSpaces(4);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                this.document.moveLeft(selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                this.document.moveRight(selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                this.document.moveUp(selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                this.document.moveDown(selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.document.moveHome(selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.document.moveEnd(selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                this.document.movePageUp(this.visibleEditorLines(), selecting);
                this.refreshSuggestions(false);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.document.movePageDown(this.visibleEditorLines(), selecting);
                this.refreshSuggestions(false);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        if (!this.canEditDocument()) {
            return false;
        }
        if (!Character.isISOControl(codePoint)) {
            this.document.insert(codePoint);
            this.refreshSuggestions(false);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (this.handleNoCodeBuilderClick(mouseX, mouseY)) {
            return true;
        }

        if (this.handleGuideBookClick(mouseX, mouseY)) {
            return true;
        }

        if (this.handleAutoStartToggleClick(mouseX, mouseY)) {
            return true;
        }

        if (this.handleRecoveryCompareClick(mouseX, mouseY)) {
            return true;
        }

        final EditorLayout layout = this.editorLayout();
        final SuggestionPopupLayout popupLayout = this.suggestionPopupLayout(layout);
        if (popupLayout != null && popupLayout.contains(mouseX, mouseY)) {
            return this.acceptSuggestion(popupLayout.itemIndex(mouseY));
        }

        if (layout.contains(mouseX, mouseY)) {
            this.placeCursorAt(layout, mouseX, mouseY, hasShiftDown());
            this.draggingSelection = true;
            this.refreshSuggestions(false);
            return true;
        }

        this.draggingSelection = false;
        this.clearSuggestions();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleNoCodeBuilderClick(final double mouseX, final double mouseY) {
        final HeaderActionButtonLayout layout = this.noCodeBuilderButtonLayout(this.recoveryCompareLayout());
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        this.openNoCodeBuilder();
        return true;
    }

    private boolean handleGuideBookClick(final double mouseX, final double mouseY) {
        final HeaderActionButtonLayout layout = this.guideBookButtonLayout(this.recoveryCompareLayout());
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        this.openGuideBook();
        return true;
    }

    private boolean handleAutoStartToggleClick(final double mouseX, final double mouseY) {
        if (!this.showsAutoStartToggle()) {
            return false;
        }
        final HeaderActionButtonLayout layout = this.autoStartButtonLayout(this.recoveryCompareLayout());
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        this.toggleAutoStartOnLoad();
        return true;
    }

    private boolean handleRecoveryCompareClick(final double mouseX, final double mouseY) {
        final RecoveryCompareLayout compareLayout = this.recoveryCompareLayout();
        if (compareLayout == null || !compareLayout.contains(mouseX, mouseY)) {
            return false;
        }

        final int actionIndex = compareLayout.actionIndex(mouseX, mouseY);
        if (actionIndex >= 0) {
            this.activateRecoveryCompareAction(actionIndex);
            return true;
        }

        final int hunkIndex = compareLayout.hunkIndex(mouseY, this.recoveryMergeResult.conflicts().size(), this.selectedRecoveryCompareConflict);
        if (hunkIndex >= 0) {
            this.selectedRecoveryCompareConflict = hunkIndex;
        }
        return true;
    }

    private void activateRecoveryCompareAction(final int actionIndex) {
        switch (actionIndex) {
            case 0 -> this.applySelectedServerHunk();
            case 1 -> this.loadServerScriptIntoRecoveryDraft();
            case 2 -> this.forceOverwriteRecoveryDraftResume();
            default -> throw new IllegalArgumentException("Unsupported recovery compare action: " + actionIndex);
        }
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingSelection) {
            this.placeCursorAt(this.editorLayout(), mouseX, mouseY, true);
            this.refreshSuggestions(false);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.draggingSelection = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        final int verticalDirection = (int) Math.signum(scrollY);
        final int horizontalDirection = (int) Math.signum(scrollX);
        if (verticalDirection == 0 && horizontalDirection == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        final EditorLayout layout = this.editorLayout();
        if (layout.outputContains(mouseX, mouseY) && verticalDirection != 0) {
            this.scrollOutput(verticalDirection);
            return true;
        }

        if (horizontalDirection != 0 || hasShiftDown()) {
            final int direction = horizontalDirection != 0 ? horizontalDirection : verticalDirection;
            this.editorHorizontalScroll -= direction * EDITOR_HORIZONTAL_SCROLL_STEP;
            this.followEditorCursor = false;
            this.clampEditorViewport(layout);
            return true;
        }

        this.editorScroll -= verticalDirection;
        this.followEditorCursor = false;
        this.clampEditorViewport(layout);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (this.overlayHandoff) {
            this.overlayHandoff = false;
            super.removed();
            return;
        }
        this.standaloneExecutionGeneration++;
        this.pushBoundStateToServer();
        if (this.isBoundComputer()) {
            PacketDistributor.sendToServer(new CloseComputerSessionPayload(this.boundComputerPos));
        }
        super.removed();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("client.screen.pythonComputerScreen.render");
        try {
            graphics.fillGradient(0, 0, this.width, this.height, APP_BACKGROUND, 0xFF101826);

            final EditorLayout layout = this.editorLayout();
            final RecoveryCompareLayout compareLayout = this.recoveryCompareLayout();
            final int margin = 12;
            final int headerInfoLeft = margin + 170;
            final int headerLineHeight = this.font.lineHeight + 2;
            final HeaderActionButtonLayout guideBookButton = this.guideBookButtonLayout(compareLayout);
            final HeaderActionButtonLayout builderButton = this.noCodeBuilderButtonLayout(compareLayout);
            final HeaderActionButtonLayout autoStartButton = this.showsAutoStartToggle() ? this.autoStartButtonLayout(compareLayout) : null;
            final int headerTextRight = Math.max(headerInfoLeft + 120, (autoStartButton == null ? builderButton.left() : autoStartButton.left()) - 8);
            final int headerTextWidth = Math.max(120, headerTextRight - headerInfoLeft);
            final String runtimeLine = "Runtime: " + this.runtime.displayName() + (this.runtime.available() ? "" : " (unavailable)");
            final String computerLine = "Computer: " + this.executionContext.computerName() + " | endpoints: "
                    + this.executionContext.endpointCount();
            final HeaderBadge executionBadge = this.executionBadge();
            final String executionLine = this.executionStatusLine();
            final String statusLine = this.editorStatusLine();
            final String helpLine = this.headerHelpLine();
            final int executionY = margin + headerLineHeight * 2;

            graphics.drawString(this.font, this.title, margin, margin, HEADER_TEXT);
            graphics.drawString(this.font, Component.literal(this.fitToWidth(runtimeLine, headerTextWidth)), headerInfoLeft, margin, STATUS_TEXT);
            graphics.drawString(this.font, Component.literal(this.fitToWidth(computerLine, headerTextWidth)), headerInfoLeft, margin + headerLineHeight, STATUS_TEXT);
            final int badgeWidth = this.renderHeaderBadge(graphics, headerInfoLeft, executionY, executionBadge);
            final int executionTextLeft = headerInfoLeft + badgeWidth + 8;
            final int executionTextWidth = Math.max(32, headerTextWidth - badgeWidth - 8);
            graphics.drawString(this.font, Component.literal(this.fitToWidth(executionLine, executionTextWidth)), executionTextLeft, executionY,
                this.executionStatusColor());
            graphics.drawString(this.font, Component.literal(this.fitToWidth(statusLine, headerTextWidth)), headerInfoLeft, margin + headerLineHeight * 3,
                this.sessionStatus.targetAvailable() && this.editable ? STATUS_TEXT : OUTPUT_ERROR);
            graphics.drawString(this.font, Component.literal(this.fitToWidth(helpLine, headerTextWidth)), headerInfoLeft, margin + headerLineHeight * 4, STATUS_TEXT);
            if (autoStartButton != null) {
                this.renderHeaderActionButton(graphics, autoStartButton, mouseX, mouseY, this.canToggleAutoStart());
            }
            this.renderHeaderActionButton(graphics, builderButton, mouseX, mouseY);
            this.renderHeaderActionButton(graphics, guideBookButton, mouseX, mouseY);

            this.drawPanel(graphics, layout.left(), layout.top(), layout.right(), layout.bottom());
            this.drawPanel(graphics, layout.left(), layout.outputTop(), layout.right(), layout.outputTop() + layout.outputHeight());
            if (compareLayout != null) {
                this.drawPanel(graphics, compareLayout.left(), compareLayout.top(), compareLayout.right(), compareLayout.bottom());
            }

            this.renderEditor(graphics, layout);
            this.renderOutput(graphics, layout.left(), layout.outputTop(), layout.outputHeight());
            if (compareLayout != null) {
                this.renderRecoveryComparePanel(graphics, compareLayout);
            }

            // Skip Screen.render here; it would repaint the vanilla blurred/menu background over this custom UI.
        } finally {
            XLRuntimeDebugger.endSection("client.screen.pythonComputerScreen.render", debugStartedAt);
        }
    }

    private void drawPanel(final GuiGraphics graphics, final int left, final int top, final int right, final int bottom) {
        graphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        graphics.renderOutline(left, top, right - left, bottom - top, PANEL_BORDER);
    }

    private int renderHeaderBadge(final GuiGraphics graphics, final int left, final int textY, final HeaderBadge badge) {
        final int badgeWidth = this.font.width(badge.label()) + 14;
        final int badgeHeight = this.font.lineHeight + 2;
        final int badgeTop = textY - 1;
        final int fill = badge.pulse() && (Util.getMillis() / 350L) % 2L == 0L
                ? withAlpha(badge.accentColor(), 0x55)
                : badge.fillColor();
        graphics.fill(left, badgeTop, left + badgeWidth, badgeTop + badgeHeight, fill);
        graphics.renderOutline(left, badgeTop, badgeWidth, badgeHeight, badge.accentColor());
        graphics.drawString(this.font, badge.label(), left + 7, textY, badge.textColor());
        return badgeWidth;
    }

    private void renderEditor(final GuiGraphics graphics, final EditorLayout layout) {
        if (this.followEditorCursor) {
            this.ensureCursorVisible(layout);
        } else {
            this.clampEditorViewport(layout);
        }

        this.renderEditorLineDecorations(graphics, layout);
        graphics.enableScissor(layout.textLeft(), layout.top() + 1, layout.right() - 1, layout.bottom() - 1);
        try {
            this.renderEditorTextLayer(graphics, layout);
            this.renderEditorCursor(graphics, layout);
        } finally {
            graphics.disableScissor();
        }

        this.renderSuggestionPopup(graphics, layout);
        this.renderDiagnosticStatus(graphics, layout);
    }

    private void renderEditorLineDecorations(final GuiGraphics graphics, final EditorLayout layout) {
        for (int screenLine = 0; screenLine < layout.visibleLines(); screenLine++) {
            final int documentLine = this.editorScroll + screenLine;
            if (documentLine >= this.document.getLineCount()) {
                break;
            }

            final int y = layout.textTop() + screenLine * layout.lineHeight();
            final int recoveryHighlight = this.recoveryDiffHighlight(documentLine);
            if (recoveryHighlight != 0) {
                graphics.fill(layout.left() + 1, y - 1, layout.right() - 1, y + this.font.lineHeight + 1, recoveryHighlight);
            }
            if (documentLine == this.document.getCursorLine()) {
                graphics.fill(layout.left() + 1, y - 1, layout.right() - 1, y + this.font.lineHeight + 1, CURRENT_LINE);
            }

            final String lineNumber = String.valueOf(documentLine + 1);
            final int lineNumberX = layout.textLeft() - 4 - this.font.width(lineNumber);
            graphics.drawString(this.font, lineNumber, lineNumberX, y, LINE_NUMBER);
        }
    }

    private void renderEditorTextLayer(final GuiGraphics graphics, final EditorLayout layout) {
        for (int screenLine = 0; screenLine < layout.visibleLines(); screenLine++) {
            final int documentLine = this.editorScroll + screenLine;
            if (documentLine >= this.document.getLineCount()) {
                break;
            }

            final int y = layout.textTop() + screenLine * layout.lineHeight();
            this.renderSelection(graphics, layout, documentLine, y);
            this.renderHighlightedLine(graphics, this.document.getLine(documentLine), this.editorTextRenderLeft(layout), y);
            this.renderDiagnostics(graphics, layout, documentLine, y);
        }
    }

    private void renderEditorCursor(final GuiGraphics graphics, final EditorLayout layout) {
        if (this.document.getCursorLine() < this.editorScroll || this.document.getCursorLine() >= this.editorScroll + layout.visibleLines()) {
            return;
        }

        final int visibleLine = this.document.getCursorLine() - this.editorScroll;
        final int cursorY = layout.textTop() + visibleLine * layout.lineHeight() - 1;
        final String line = this.document.getLine(this.document.getCursorLine());
        final int safeColumn = Math.min(this.document.getCursorColumn(), line.length());
        final String prefix = line.substring(0, safeColumn);
        final int cursorX = this.editorTextRenderLeft(layout) + this.font.width(prefix);
        if ((Util.getMillis() / 400L) % 2L == 0L) {
            graphics.fill(cursorX, cursorY, cursorX + 1, cursorY + this.font.lineHeight + 2, CURSOR);
        }
    }

    private void renderSelection(final GuiGraphics graphics, final EditorLayout layout, final int documentLine, final int y) {
        final TextDocument.SelectionSegment selection = this.document.getSelectionSegment(documentLine);
        if (selection == null || selection.startColumn() == selection.endColumn()) {
            return;
        }

        final String line = this.document.getLine(documentLine);
        final int startColumn = Math.min(selection.startColumn(), line.length());
        final int endColumn = Math.min(selection.endColumn(), line.length());
        final int startX = this.editorTextRenderLeft(layout) + this.font.width(line.substring(0, startColumn));
        final int endX = this.editorTextRenderLeft(layout) + this.font.width(line.substring(0, endColumn));
        graphics.fill(startX, y - 1, Math.max(startX + 2, endX), y + this.font.lineHeight + 1, SELECTION);
    }

    private void renderHighlightedLine(final GuiGraphics graphics, final String line, final int x, final int y) {
        final List<SyntaxToken> tokens = this.highlighter.highlight(line);
        int cursor = 0;
        int drawX = x;

        for (final SyntaxToken token : tokens) {
            if (token.start() > cursor) {
                final String plain = line.substring(cursor, token.start());
                graphics.drawString(this.font, plain, drawX, y, TokenStyle.DEFAULT.color());
                drawX += this.font.width(plain);
            }

            final String segment = line.substring(token.start(), token.end());
            graphics.drawString(this.font, segment, drawX, y, token.style().color());
            drawX += this.font.width(segment);
            cursor = token.end();
        }

        if (cursor < line.length()) {
            graphics.drawString(this.font, line.substring(cursor), drawX, y, TokenStyle.DEFAULT.color());
        }
    }

    private void renderDiagnostics(final GuiGraphics graphics, final EditorLayout layout, final int documentLine, final int y) {
        final List<PythonEditorDiagnostics.Diagnostic> diagnostics = this.diagnosticsByLine.get(documentLine);
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }

        final String line = this.document.getLine(documentLine);
        for (final PythonEditorDiagnostics.Diagnostic diagnostic : diagnostics) {
            final int startColumn = Math.max(0, Math.min(diagnostic.startColumn(), line.length()));
            final int endColumn = Math.max(startColumn + 1, Math.min(Math.max(diagnostic.endColumn(), startColumn + 1), Math.max(line.length(), startColumn + 1)));
            final int startX = this.editorTextRenderLeft(layout) + this.font.width(line.substring(0, Math.min(startColumn, line.length())));
            final int endX = startColumn >= line.length()
                    ? startX + 6
                    : this.editorTextRenderLeft(layout) + this.font.width(line.substring(0, Math.min(endColumn, line.length())));
            final int underlineY = y + this.font.lineHeight + 1;
            for (int x = startX; x < Math.max(startX + 4, endX); x += 4) {
                graphics.fill(x, underlineY, Math.min(x + 2, Math.max(startX + 4, endX)), underlineY + 1, DIAGNOSTIC_ERROR);
            }
        }
    }

    private void renderDiagnosticStatus(final GuiGraphics graphics, final EditorLayout layout) {
        final PythonEditorDiagnostics.Diagnostic diagnostic = this.activeDiagnostic();
        if (diagnostic == null) {
            return;
        }

        final String message = "Parse: " + diagnostic.message();
        graphics.drawString(this.font, this.fitToWidth(message, layout.right() - layout.left() - 16), layout.left() + 8, layout.bottom() - this.font.lineHeight - 6, DIAGNOSTIC_ERROR);
    }

    private void renderSuggestionPopup(final GuiGraphics graphics, final EditorLayout layout) {
        final SuggestionPopupLayout popupLayout = this.suggestionPopupLayout(layout);
        if (popupLayout == null) {
            return;
        }

        graphics.fill(popupLayout.left(), popupLayout.top(), popupLayout.left() + popupLayout.width(), popupLayout.top() + popupLayout.height(), SUGGESTION_BACKGROUND);
        graphics.renderOutline(popupLayout.left(), popupLayout.top(), popupLayout.width(), popupLayout.height(), SUGGESTION_BORDER);

        for (int index = 0; index < this.suggestionSession.items().size(); index++) {
            final int rowTop = popupLayout.top() + 4 + index * popupLayout.itemHeight();
            if (index == this.selectedSuggestionIndex) {
                graphics.fill(popupLayout.left() + 1, rowTop, popupLayout.left() + popupLayout.width() - 1, rowTop + popupLayout.itemHeight(), SUGGESTION_SELECTED);
            }

            final PythonSuggestionEngine.SuggestionItem item = this.suggestionSession.items().get(index);
            final int textY = rowTop + 2;
            final int detailWidth = item.detail().isBlank() ? 0 : Math.min(this.font.width(item.detail()), popupLayout.width() / 2);
            final int labelWidth = popupLayout.width() - 14 - (detailWidth == 0 ? 0 : detailWidth + 6);
            graphics.drawString(this.font, this.fitToWidth(item.label(), Math.max(30, labelWidth)), popupLayout.left() + 6, textY, HEADER_TEXT);
            if (!item.detail().isBlank()) {
                final String detail = this.fitToWidth(item.detail(), Math.max(30, detailWidth));
                final int detailX = popupLayout.left() + popupLayout.width() - 6 - this.font.width(detail);
                graphics.drawString(this.font, detail, detailX, textY, STATUS_TEXT);
            }
        }
    }

    private void renderOutput(final GuiGraphics graphics, final int left, final int top, final int height) {
        graphics.drawString(this.font, Component.literal("Output"), left + 8, top + 6, HEADER_TEXT);
        graphics.drawString(this.font, Component.literal("Wheel scroll  |  Ctrl+End latest"), left + 56, top + 6, STATUS_TEXT);
        final int contentTop = top + 20;
        final int contentWidth = Math.max(40, this.width - left - 28);
        final List<ComputerOutputEntry> visibleEntries = this.visibleOutputEntries(height - 24);
        int y = contentTop;
        for (final ComputerOutputEntry outputEntry : visibleEntries) {
            y += this.renderOutputEntry(graphics, outputEntry, left + 8, y, contentWidth) + 4;
        }
    }

    private int renderOutputEntry(final GuiGraphics graphics, final ComputerOutputEntry outputEntry, final int left, final int y, final int width) {
        if (outputEntry.tableKind()) {
            return this.renderTableEntry(graphics, outputEntry, left, y, width);
        }
        if (outputEntry.keyValueKind() || outputEntry.planCardKind()) {
            return this.renderFieldCard(graphics, outputEntry, left, y, width);
        }

        final int accent = outputAccentColor(outputEntry);
        final int labelWidth = this.font.width(outputEntry.displayLabel()) + 8;
        graphics.fill(left, y - 1, left + labelWidth, y + this.font.lineHeight + 1, outputTagBackground(outputEntry));
        graphics.renderOutline(left, y - 1, labelWidth, this.font.lineHeight + 2, accent);
        graphics.drawString(this.font, outputEntry.displayLabel(), left + 4, y, HEADER_TEXT);

        final int textLeft = left + labelWidth + 6;
        final String text = this.fitToWidth(outputEntry.text(), Math.max(10, width - labelWidth - 6));
        graphics.drawString(this.font, text, textLeft, y, outputTextColor(outputEntry));
        return this.font.lineHeight + 2;
    }

    private int renderFieldCard(final GuiGraphics graphics, final ComputerOutputEntry outputEntry, final int left, final int top, final int width) {
        final List<ComputerOutputEntry.OutputField> fields = outputEntry.fields();
        final int height = this.measureFieldCardHeight(outputEntry, fields);
        final int accent = outputAccentColor(outputEntry);
        final int right = left + width;
        graphics.fill(left, top, right, top + height, OUTPUT_CARD_BACKGROUND);
        graphics.renderOutline(left, top, width, height, accent);
        this.renderCardHeader(graphics, outputEntry, left + 6, top + 5, width - 12);

        int y = top + 19;
        if (!outputEntry.text().isBlank()) {
            graphics.drawString(this.font, this.fitToWidth(outputEntry.text(), width - 12), left + 6, y, outputTextColor(outputEntry));
            y += this.font.lineHeight + 2;
        }

        final int keyWidth = this.measureFieldKeyWidth(fields, width / 2);
        for (final ComputerOutputEntry.OutputField field : fields) {
            graphics.drawString(this.font, this.fitToWidth(field.key(), keyWidth), left + 6, y, STATUS_TEXT);
            graphics.drawString(this.font, this.fitToWidth(field.value(), width - keyWidth - 16), left + 10 + keyWidth, y, outputTextColor(outputEntry));
            y += this.font.lineHeight + 1;
        }
        return height;
    }

    private int renderTableEntry(final GuiGraphics graphics, final ComputerOutputEntry outputEntry, final int left, final int top, final int width) {
        final ComputerOutputEntry.TableData tableData = outputEntry.tableData();
        final int height = this.measureTableHeight(outputEntry, tableData);
        final int accent = outputAccentColor(outputEntry);
        final int right = left + width;
        graphics.fill(left, top, right, top + height, OUTPUT_CARD_BACKGROUND);
        graphics.renderOutline(left, top, width, height, accent);
        this.renderCardHeader(graphics, outputEntry, left + 6, top + 5, width - 12);

        int y = top + 19;
        if (!outputEntry.text().isBlank()) {
            graphics.drawString(this.font, this.fitToWidth(outputEntry.text(), width - 12), left + 6, y, outputTextColor(outputEntry));
            y += this.font.lineHeight + 2;
        }

        final List<String> columns = tableData.columns().isEmpty() ? List.of("Value") : tableData.columns();
        final int columnWidth = Math.max(30, (width - 14) / Math.max(1, columns.size()));
        graphics.fill(left + 5, y - 1, right - 5, y + this.font.lineHeight + 2, OUTPUT_TABLE_HEADER);

        int x = left + 6;
        for (final String column : columns) {
            graphics.drawString(this.font, this.fitToWidth(column, columnWidth - 4), x, y, HEADER_TEXT);
            x += columnWidth;
        }

        y += this.font.lineHeight + 4;
        for (final List<String> row : tableData.rows()) {
            x = left + 6;
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                final String cell = columnIndex < row.size() ? row.get(columnIndex) : "";
                graphics.drawString(this.font, this.fitToWidth(cell, columnWidth - 4), x, y, OUTPUT_TEXT);
                x += columnWidth;
            }
            y += this.font.lineHeight + 1;
        }

        return height;
    }

    private void renderRecoveryComparePanel(final GuiGraphics graphics, final RecoveryCompareLayout layout) {
        final int contentLeft = layout.left() + 8;
        final int contentWidth = layout.right() - layout.left() - 16;
        final ScriptDiff.MergeConflict selectedConflict = this.selectedRecoveryCompareConflict();

        graphics.drawString(this.font, Component.literal("Compare / Merge"), contentLeft, layout.top() + 8, HEADER_TEXT);
        graphics.drawString(this.font, Component.literal(this.fitToWidth(this.recoveryConflictSummary(), contentWidth)),
                contentLeft, layout.top() + 8 + this.font.lineHeight + 4, STATUS_TEXT);

        this.renderRecoveryCompareButton(graphics, layout.buttonTop(), layout.buttonHeight(), contentLeft, contentWidth,
            "Take selected server conflict  |  Ctrl+Right", OUTPUT_INFO);
        this.renderRecoveryCompareButton(graphics, layout.buttonTop() + layout.buttonStride(), layout.buttonHeight(), contentLeft, contentWidth,
            "Load full server script  |  Ctrl+R", OUTPUT_OK);
        this.renderRecoveryCompareButton(graphics, layout.buttonTop() + layout.buttonStride() * 2, layout.buttonHeight(), contentLeft, contentWidth,
            "Publish merged draft  |  Ctrl+Enter", 0xFFE3B341);

        graphics.drawString(this.font, Component.literal("Conflict hunks  |  Alt+Up/Down select"), contentLeft,
                layout.hunkListTop() - this.font.lineHeight - 4, HEADER_TEXT);
        this.renderRecoveryCompareHunks(graphics, layout, contentLeft, contentWidth);
        this.renderRecoveryPreview(graphics,
            new RecoveryComparePreviewLayout(contentLeft, layout.localPreviewTop(), contentWidth, layout.previewHeight(),
                "Local draft", true, RECOVERY_COMPARE_LOCAL),
            selectedConflict);
        this.renderRecoveryPreview(graphics,
            new RecoveryComparePreviewLayout(contentLeft, layout.serverPreviewTop(), contentWidth, layout.previewHeight(),
                "Server snapshot", false, RECOVERY_COMPARE_SERVER),
            selectedConflict);
    }

    private void renderRecoveryCompareButton(final GuiGraphics graphics, final int top, final int height, final int left,
                             final int width, final String label, final int accent) {
        graphics.fill(left, top, left + width, top + height, ACTION_BUTTON);
        graphics.renderOutline(left, top, width, height, accent);
        graphics.drawString(this.font, this.fitToWidth(label, width - 12), left + 6, top + 4, HEADER_TEXT);
    }

    private void renderRecoveryCompareHunks(final GuiGraphics graphics, final RecoveryCompareLayout layout,
                                            final int left, final int width) {
        if (this.recoveryMergeResult.conflicts().isEmpty()) {
            graphics.drawString(this.font, Component.literal("No unresolved merge conflicts remain."), left, layout.hunkListTop(), STATUS_TEXT);
            return;
        }

        final int firstVisible = layout.firstVisibleHunkIndex(this.recoveryMergeResult.conflicts().size(), this.selectedRecoveryCompareConflict);
        final int lastVisible = Math.min(this.recoveryMergeResult.conflicts().size(), firstVisible + layout.visibleHunkRows());
        for (int index = firstVisible; index < lastVisible; index++) {
            final int rowIndex = index - firstVisible;
            final int rowTop = layout.hunkListTop() + rowIndex * layout.hunkRowHeight();
            final ScriptDiff.MergeConflict conflict = this.recoveryMergeResult.conflicts().get(index);
            final boolean selected = index == this.selectedRecoveryCompareConflict;
            graphics.fill(left, rowTop, left + width, rowTop + layout.hunkRowHeight() - 2, selected ? RECOVERY_COMPARE_SELECTED : RECOVERY_COMPARE_ROW);
            graphics.renderOutline(left, rowTop, width, layout.hunkRowHeight() - 2, selected ? OUTPUT_INFO : PANEL_BORDER);
            graphics.drawString(this.font, this.fitToWidth(this.recoveryHunkTitle(index, conflict), width - 12), left + 6, rowTop + 3, HEADER_TEXT);
            graphics.drawString(this.font, this.fitToWidth(this.recoveryHunkDetail(conflict), width - 12), left + 6,
                    rowTop + this.font.lineHeight + 5, STATUS_TEXT);
        }
    }

    private void renderRecoveryPreview(final GuiGraphics graphics, final RecoveryComparePreviewLayout previewLayout,
                                       final ScriptDiff.MergeConflict conflict) {
        graphics.fill(previewLayout.left(), previewLayout.top(), previewLayout.left() + previewLayout.width(),
            previewLayout.top() + previewLayout.height(), previewLayout.accentFill());
        graphics.renderOutline(previewLayout.left(), previewLayout.top(), previewLayout.width(), previewLayout.height(),
            previewLayout.localSide() ? OUTPUT_INFO : OUTPUT_OK);

        final String rangeLabel = conflict == null
            ? previewLayout.title()
            : previewLayout.title() + "  |  " + this.recoveryRangeLabel(conflict, previewLayout.localSide());
        graphics.drawString(this.font, this.fitToWidth(rangeLabel, previewLayout.width() - 12),
            previewLayout.left() + 6, previewLayout.top() + 4, HEADER_TEXT);

        final List<RecoveryComparePreviewLine> previewLines = this.recoveryPreviewLines(conflict, previewLayout.localSide(), previewLayout.maxVisibleLines(this.font.lineHeight));
        int y = previewLayout.top() + this.font.lineHeight + 10;
        for (final RecoveryComparePreviewLine line : previewLines) {
            if (line.changed()) {
                graphics.fill(previewLayout.left() + 4, y - 1, previewLayout.left() + previewLayout.width() - 4, y + this.font.lineHeight + 1,
                        line.placeholder() ? RECOVERY_COMPARE_ROW : RECOVERY_COMPARE_SELECTED);
            }
            if (!line.lineNumber().isBlank()) {
                graphics.drawString(this.font, line.lineNumber(), previewLayout.left() + 6, y, LINE_NUMBER);
            }
            graphics.drawString(this.font, this.fitToWidth(line.text(), previewLayout.width() - 42), previewLayout.left() + 34, y,
                    line.placeholder() ? STATUS_TEXT : OUTPUT_TEXT);
            y += this.font.lineHeight + 2;
        }
    }

    private void renderCardHeader(final GuiGraphics graphics, final ComputerOutputEntry outputEntry, final int left, final int top, final int width) {
        final int accent = outputAccentColor(outputEntry);
        final int labelWidth = this.font.width(outputEntry.displayLabel()) + 8;
        graphics.fill(left, top - 1, left + labelWidth, top + this.font.lineHeight + 1, outputTagBackground(outputEntry));
        graphics.renderOutline(left, top - 1, labelWidth, this.font.lineHeight + 2, accent);
        graphics.drawString(this.font, outputEntry.displayLabel(), left + 4, top, HEADER_TEXT);

        final String title = outputEntry.title().isBlank() ? outputEntry.summaryLine() : outputEntry.title();
        graphics.drawString(this.font, this.fitToWidth(title, Math.max(10, width - labelWidth - 6)), left + labelWidth + 6, top, outputTextColor(outputEntry));
    }

    private List<ComputerOutputEntry> visibleOutputEntries(final int availableHeight) {
        final ArrayList<ComputerOutputEntry> visible = new ArrayList<>();
        if (this.outputEntries.isEmpty()) {
            return visible;
        }

        final int newestIndex = this.outputEntries.size() - 1;
        final int startIndex = Mth.clamp(newestIndex - this.outputScrollEntries, 0, newestIndex);
        int usedHeight = 0;
        for (int index = startIndex; index >= 0; index--) {
            final ComputerOutputEntry entry = this.outputEntries.get(index);
            final int entryHeight = this.measureOutputEntryHeight(entry);
            final int nextHeight = visible.isEmpty() ? entryHeight : usedHeight + 4 + entryHeight;
            if (!visible.isEmpty() && nextHeight > availableHeight) {
                break;
            }
            visible.add(entry);
            usedHeight = nextHeight;
        }
        Collections.reverse(visible);
        return visible;
    }

    private int measureOutputEntryHeight(final ComputerOutputEntry outputEntry) {
        if (outputEntry.tableKind()) {
            return this.measureTableHeight(outputEntry, outputEntry.tableData());
        }
        if (outputEntry.keyValueKind() || outputEntry.planCardKind()) {
            return this.measureFieldCardHeight(outputEntry, outputEntry.fields());
        }
        return this.font.lineHeight + 2;
    }

    private int measureFieldCardHeight(final ComputerOutputEntry outputEntry, final List<ComputerOutputEntry.OutputField> fields) {
        int height = 20;
        if (!outputEntry.text().isBlank()) {
            height += this.font.lineHeight + 2;
        }
        height += fields.size() * (this.font.lineHeight + 1);
        return Math.max(height + 5, 24);
    }

    private int measureTableHeight(final ComputerOutputEntry outputEntry, final ComputerOutputEntry.TableData tableData) {
        int height = 20;
        if (!outputEntry.text().isBlank()) {
            height += this.font.lineHeight + 2;
        }
        height += this.font.lineHeight + 4;
        height += tableData.rows().size() * (this.font.lineHeight + 1);
        return Math.max(height + 5, 28);
    }

    private int measureFieldKeyWidth(final List<ComputerOutputEntry.OutputField> fields, final int maxWidth) {
        int width = 40;
        for (final ComputerOutputEntry.OutputField field : fields) {
            width = Math.max(width, this.font.width(field.key()));
        }
        return Math.min(width, Math.max(40, maxWidth));
    }

    private void executeDocument() {
        if (this.isBoundComputer()) {
            if (!this.editable) {
                this.showReadOnlyHint();
                return;
            }
            if (this.runtimeState.running()) {
                this.startRequested = false;
                this.stopRequested = true;
                PacketDistributor.sendToServer(new StopComputerScriptPayload(this.boundComputerPos));
                this.showClientStatusMessage("Stop requested for the running Python program.");
                return;
            }

            this.startRequested = true;
            this.stopRequested = false;
            this.applyRuntimeState(ComputerRuntimeSnapshot.running(this.runtimeState));
            PacketDistributor.sendToServer(new ExecuteComputerScriptPayload(this.boundComputerPos, this.document.getText()));
            return;
        }

        if (this.runtimeState.running()) {
            this.showClientStatusMessage("Standalone Python execution is already running.");
            return;
        }

        this.startStandaloneExecution();
    }

    private void startStandaloneExecution() {
        final String source = this.document.getText();
        final PythonExecutionContext context = this.executionContext;
        final int executionGeneration = ++this.standaloneExecutionGeneration;
        this.startRequested = false;
        this.stopRequested = false;
        this.applyRuntimeState(ComputerRuntimeSnapshot.running(this.runtimeState));

        CompletableFuture
                .supplyAsync(() -> this.runtime.execute(source, context), LOCAL_EXECUTION_EXECUTOR)
                .whenComplete((result, throwable) -> {
                    if (this.minecraft == null) {
                        return;
                    }
                    this.minecraft.execute(() -> this.completeStandaloneExecution(executionGeneration, result, throwable));
                });
    }

    private void completeStandaloneExecution(final int executionGeneration, final PythonExecutionResult result, final Throwable throwable) {
        if (executionGeneration != this.standaloneExecutionGeneration) {
            return;
        }

        if (throwable != null) {
            final String failureMessage = throwable.getMessage() == null || throwable.getMessage().isBlank()
                    ? "Standalone Python execution failed unexpectedly."
                    : "Standalone Python execution failed unexpectedly: " + throwable.getMessage();
            this.applyRuntimeState(ComputerRuntimeSnapshot.guardrailRejected(this.runtimeState, failureMessage));
            return;
        }

        this.applyRuntimeState(ComputerRuntimeSnapshot.fromExecutionResult(result));
    }

    private boolean handleClipboardShortcuts(final int keyCode) {
        if (!hasControlDown() || this.minecraft == null) {
            return false;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_A -> {
                this.document.selectAll();
                this.refreshSuggestions(false);
                yield true;
            }
            case GLFW.GLFW_KEY_C -> {
                if (this.document.hasSelection()) {
                    this.minecraft.keyboardHandler.setClipboard(this.document.getSelectedText());
                    yield true;
                }
                yield false;
            }
            case GLFW.GLFW_KEY_X -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    yield true;
                }
                if (this.document.hasSelection()) {
                    this.minecraft.keyboardHandler.setClipboard(this.document.getSelectedText());
                    this.document.deleteSelection();
                    this.refreshSuggestions(false);
                    yield true;
                }
                yield false;
            }
            case GLFW.GLFW_KEY_V -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    yield true;
                }
                this.document.insert(this.minecraft.keyboardHandler.getClipboard());
                this.refreshSuggestions(false);
                yield true;
            }
            case GLFW.GLFW_KEY_Z -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    yield true;
                }
                final boolean changed = hasShiftDown() ? this.document.redo() : this.document.undo();
                if (changed) {
                    this.refreshSuggestions(false);
                }
                yield changed;
            }
            case GLFW.GLFW_KEY_Y -> {
                if (!this.canEditDocument()) {
                    this.showReadOnlyHint();
                    yield true;
                }
                final boolean changed = this.document.redo();
                if (changed) {
                    this.refreshSuggestions(false);
                }
                yield changed;
            }
            default -> false;
        };
    }

    private boolean handleOutputNavigationKeys(final int keyCode) {
        if (!hasControlDown()) {
            return false;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> {
                this.scrollOutputPage(1);
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.scrollOutputPage(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.jumpToOldestOutput();
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.outputScrollEntries = 0;
                yield true;
            }
            default -> false;
        };
    }

    private void scrollOutputPage(final int direction) {
        final int pageSize = Math.max(1, this.visibleOutputEntries(this.editorLayout().outputHeight() - 24).size() - 1);
        this.scrollOutput(direction * pageSize);
    }

    private void jumpToOldestOutput() {
        this.outputScrollEntries = Math.max(0, this.outputEntries.size() - 1);
        this.clampOutputScroll();
    }

    private void scrollOutput(final int delta) {
        if (delta == 0) {
            return;
        }

        this.outputScrollEntries += delta;
        this.clampOutputScroll();
    }

    private void clampOutputScroll() {
        this.outputScrollEntries = Mth.clamp(this.outputScrollEntries, 0, Math.max(0, this.outputEntries.size() - 1));
    }

    private boolean handleSuggestionKeys(final int keyCode) {
        if (!this.suggestionSession.visible()) {
            return false;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                this.moveSuggestionSelection(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                this.moveSuggestionSelection(1);
                yield true;
            }
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> this.acceptSelectedSuggestion();
            case GLFW.GLFW_KEY_ESCAPE -> {
                this.clearSuggestions();
                yield true;
            }
            default -> false;
        };
    }

    private void moveSuggestionSelection(final int delta) {
        if (!this.suggestionSession.visible()) {
            return;
        }
        final int size = this.suggestionSession.items().size();
        this.selectedSuggestionIndex = Math.floorMod(this.selectedSuggestionIndex + delta, size);
    }

    private boolean acceptSelectedSuggestion() {
        return this.acceptSuggestion(this.selectedSuggestionIndex);
    }

    private boolean acceptSuggestion(final int suggestionIndex) {
        if (!this.suggestionSession.visible() || suggestionIndex < 0 || suggestionIndex >= this.suggestionSession.items().size()) {
            return false;
        }

        final PythonSuggestionEngine.SuggestionItem item = this.suggestionSession.items().get(suggestionIndex);
        this.document.replaceRange(
                this.suggestionSession.line(),
                this.suggestionSession.replaceStartColumn(),
                this.suggestionSession.line(),
                this.suggestionSession.replaceEndColumn(),
                item.insertText()
        );
        this.diagnosticReport = this.diagnosticEngine.analyze(this.document);
        this.diagnosticsByLine = this.diagnosticReport.byLine();
        this.followEditorCursor = true;
        this.clearSuggestions();
        return true;
    }

    private void refreshSuggestions(final boolean forceAll) {
        this.followEditorCursor = true;
        this.suggestionSession = this.suggestionEngine.suggest(this.document, this.executionContext, forceAll);
        this.diagnosticReport = this.diagnosticEngine.analyze(this.document);
        this.diagnosticsByLine = this.diagnosticReport.byLine();
        if (!this.suggestionSession.visible()) {
            this.selectedSuggestionIndex = 0;
            this.refreshRecoveryMergeState();
            return;
        }
        this.selectedSuggestionIndex = Mth.clamp(this.selectedSuggestionIndex, 0, this.suggestionSession.items().size() - 1);
        this.refreshRecoveryMergeState();
    }

    private void clearSuggestions() {
        this.suggestionSession = PythonSuggestionEngine.SuggestionSession.empty(this.document.getCursorLine(), this.document.getCursorColumn());
        this.selectedSuggestionIndex = 0;
    }

    private PythonEditorDiagnostics.Diagnostic activeDiagnostic() {
        final List<PythonEditorDiagnostics.Diagnostic> currentLineDiagnostics = this.diagnosticsByLine.get(this.document.getCursorLine());
        if (currentLineDiagnostics != null && !currentLineDiagnostics.isEmpty()) {
            return currentLineDiagnostics.getFirst();
        }
        if (!this.diagnosticReport.diagnostics().isEmpty()) {
            return this.diagnosticReport.diagnostics().getFirst();
        }
        return null;
    }

    private void ensureCursorVisible(final EditorLayout layout) {
        final int visibleLines = layout.visibleLines();
        if (this.document.getCursorLine() < this.editorScroll) {
            this.editorScroll = this.document.getCursorLine();
        }
        if (this.document.getCursorLine() >= this.editorScroll + visibleLines) {
            this.editorScroll = this.document.getCursorLine() - visibleLines + 1;
        }

        final String currentLine = this.document.getLine(this.document.getCursorLine());
        final int safeColumn = Math.min(this.document.getCursorColumn(), currentLine.length());
        final int cursorPixel = this.font.width(currentLine.substring(0, safeColumn));
        if (cursorPixel < this.editorHorizontalScroll) {
            this.editorHorizontalScroll = cursorPixel;
        }
        if (cursorPixel > this.editorHorizontalScroll + layout.textWidth() - 4) {
            this.editorHorizontalScroll = cursorPixel - layout.textWidth() + 4;
        }

        this.clampEditorViewport(layout);
    }

    private void clampEditorScroll(final int visibleLines) {
        final int maxScroll = Math.max(0, this.document.getLineCount() - visibleLines);
        this.editorScroll = Mth.clamp(this.editorScroll, 0, maxScroll);
    }

    private void clampEditorHorizontalScroll(final EditorLayout layout) {
        int maxLineWidth = 0;
        for (int lineIndex = 0; lineIndex < this.document.getLineCount(); lineIndex++) {
            maxLineWidth = Math.max(maxLineWidth, this.font.width(this.document.getLine(lineIndex)));
        }
        final int maxScroll = Math.max(0, maxLineWidth - layout.textWidth() + 4);
        this.editorHorizontalScroll = Mth.clamp(this.editorHorizontalScroll, 0, maxScroll);
    }

    private void clampEditorViewport(final EditorLayout layout) {
        this.clampEditorScroll(layout.visibleLines());
        this.clampEditorHorizontalScroll(layout);
    }

    private int visibleEditorLines() {
        return this.editorLayout().visibleLines();
    }

    private void initializeOutputLines() {
        this.outputScrollEntries = 0;
        if (this.isBoundComputer()) {
            this.applyRuntimeState(this.runtimeState);
            return;
        }

        this.outputEntries.clear();
        if (this.recoveryDraft) {
            if (!this.runtimeState.outputEntries().isEmpty()) {
                this.outputEntries.addAll(this.runtimeState.outputEntries());
            }
            this.outputEntries.add(ComputerOutputEntry.hint("Recovery draft: the bound computer stayed unavailable too long."));
            this.outputEntries.add(ComputerOutputEntry.hint("Press F5 to run locally while the client retries a resume handshake in the background."));
            this.outputEntries.add(ComputerOutputEntry.info("Endpoint snapshot: " + this.executionContext.endpointCount()));
            return;
        }
        this.outputEntries.add(ComputerOutputEntry.hint("Press F5 to execute the current Python script."));
        this.outputEntries.add(ComputerOutputEntry.hint("Press P in-game to reopen this screen."));
        this.outputEntries.add(ComputerOutputEntry.hint("Python API: computer, endpoints, peripherals, output, show_table(), show_kv(), show_plan_card(), yield from sleep_ticks(), yield from run_loop() | yielded slices resume every "
            + XLServerConfig.INSTANCE.persistentResumeIntervalTicks()
            + " server ticks by default"));
        this.outputEntries.add(ComputerOutputEntry.info("Bound endpoints: " + this.executionContext.endpointCount()));
    }

    private void applyRuntimeState(final ComputerRuntimeSnapshot runtimeState) {
        final ComputerRuntimeSnapshot safeRuntimeState = runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState;
        if (safeRuntimeState.equals(this.runtimeState)) {
            this.runtimeState = safeRuntimeState;
            if (!safeRuntimeState.running()) {
                this.startRequested = false;
                this.stopRequested = false;
            }
            return;
        }
        this.runtimeState = safeRuntimeState;
        if (!safeRuntimeState.running()) {
            this.startRequested = false;
            this.stopRequested = false;
        }
        this.outputScrollEntries = 0;
        this.outputEntries.clear();
        this.outputEntries.addAll(safeRuntimeState.outputEntries());
    }

    public void applyServerRuntimeState(final ComputerRuntimeSnapshot runtimeState, final PythonExecutionContext executionContext,
                                        final boolean editable, final String activeEditorName) {
        this.applyServerRuntimeState(runtimeState, executionContext, editable, activeEditorName, ComputerSessionStatus.ACTIVE, "");
    }

    public void applyOpenedComputerState(final ComputerRuntimeSnapshot runtimeState, final PythonExecutionContext executionContext,
                                         final boolean editable, final String activeEditorName, final boolean autoStartOnLoad) {
        this.applyServerRuntimeState(runtimeState, executionContext, editable, activeEditorName, ComputerSessionStatus.ACTIVE, "");
        this.autoStartOnLoad = autoStartOnLoad;
        this.lastSyncedAutoStartOnLoad = autoStartOnLoad;
    }

    public void applyServerRuntimeState(final ComputerRuntimeSnapshot runtimeState, final PythonExecutionContext executionContext,
                                        final boolean editable, final String activeEditorName, final ComputerSessionStatus sessionStatus,
                                        final String sessionMessage) {
        final ComputerSessionStatus previousSessionStatus = this.sessionStatus;
        final boolean previousEditable = this.editable;
        final String previousActiveEditorName = this.activeEditorName;
        final ComputerSessionStatus safeSessionStatus = sessionStatus == null ? ComputerSessionStatus.ACTIVE : sessionStatus;
        final boolean nextEditable = safeSessionStatus.targetAvailable() && (editable || !this.isBoundComputer());

        if (safeSessionStatus.targetAvailable() || this.executionContext == null) {
            this.executionContext = executionContext == null ? PythonExecutionContext.empty() : executionContext;
        }
        this.sessionStatus = safeSessionStatus;
        this.sessionMessage = safeSessionStatus.targetAvailable() ? "" : this.defaultUnavailableMessage(sessionMessage);
        this.editable = nextEditable;
        this.activeEditorName = this.resolveActiveEditorName(safeSessionStatus, activeEditorName);
        if (safeSessionStatus.targetAvailable()) {
            this.unavailableTicks = 0;
            this.recoveryResumeEligible = this.isBoundComputer() && nextEditable;
        } else if (previousSessionStatus.targetAvailable()) {
            this.unavailableTicks = 0;
        }
        this.sessionHeartbeatTicks = 0;
        this.applyRuntimeState(runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState);
        if (!safeSessionStatus.targetAvailable()) {
            this.startRequested = false;
            this.stopRequested = false;
        } else if (this.runtimeState.running()) {
            this.startRequested = false;
        }
        this.refreshSuggestions(false);
        this.announceSessionTransition(previousSessionStatus, previousEditable, previousActiveEditorName);
    }

    private void pushBoundStateToServer() {
        if (!this.isBoundComputer() || !this.editable) {
            return;
        }

        final String currentScript = this.document.getText();
        if (currentScript.equals(this.lastSyncedScript) && this.autoStartOnLoad == this.lastSyncedAutoStartOnLoad) {
            return;
        }

        PacketDistributor.sendToServer(new SaveComputerStatePayload(this.boundComputerPos, currentScript, this.autoStartOnLoad));
        this.lastSyncedScript = currentScript;
        this.lastSyncedAutoStartOnLoad = this.autoStartOnLoad;
    }

    public boolean applyNoCodeBuilderProgram(final String script, final boolean executeAfterApply) {
        if (!this.canEditDocument()) {
            this.showReadOnlyHint();
            return false;
        }
        this.replaceDocumentText(script);
        this.pushBoundStateToServer();
        this.showClientStatusMessage(executeAfterApply
                ? "No-code builder updated the current program and started it."
                : "No-code builder updated the current program.");
        if (executeAfterApply) {
            this.executeDocument();
        }
        return true;
    }

    public String currentScriptText() {
        return this.document.getText();
    }

    public PythonExecutionContext currentExecutionContext() {
        return this.executionContext;
    }

    public boolean isDocumentEditable() {
        return this.canEditDocument();
    }

    public boolean isBoundTo(final BlockPos computerPos) {
        return this.boundComputerPos != null && this.boundComputerPos.equals(computerPos);
    }

    public boolean isRecoveryDraftFor(final BlockPos computerPos) {
        return this.recoveryDraft && this.recoveryTargetPos != null && this.recoveryTargetPos.equals(computerPos);
    }

    public void applyRecoveryDraftResumeResult(final ResumeRecoveryDraftResultPayload payload) {
        if (payload == null || !this.isRecoveryDraftFor(payload.computerPos())) {
            return;
        }

        this.recoveryResumeHandshakeTicks = 0;
        if (payload.status().resumed()) {
            this.showClientStatusMessage(payload.message());
            if (this.minecraft != null) {
                this.minecraft.setScreen(new PythonComputerScreen(payload.computerPos(), payload.script(), payload.runtimeState(), payload.executionContext(),
                        payload.editable(), payload.activeEditorName(), payload.autoStartOnLoad()));
            }
            return;
        }

        final RecoveryDraftResumeStatus previousStatus = this.recoveryResumeStatus;
        final String previousMessage = this.recoveryResumeMessage;
        this.recoveryResumeStatus = payload.status();
        this.recoveryServerScript = payload.status() == RecoveryDraftResumeStatus.DIVERGED ? payload.script() : "";
        this.recoveryResumeMessage = this.defaultRecoveryResumeMessage(payload.status(), payload.message());
        this.refreshRecoveryMergeState();
        if (previousStatus != this.recoveryResumeStatus || !previousMessage.equals(this.recoveryResumeMessage)) {
            this.showClientStatusMessage(this.recoveryResumeMessage);
        }
    }

    private boolean isBoundComputer() {
        return this.boundComputerPos != null;
    }

    private void tickRecoveryDraftResume() {
        if (!this.recoveryDraft || this.recoveryTargetPos == null) {
            this.recoveryResumeHandshakeTicks = 0;
            return;
        }

        if (this.recoveryResumeStatus == RecoveryDraftResumeStatus.DIVERGED) {
            this.recoveryResumeHandshakeTicks = 0;
            return;
        }

        this.recoveryResumeHandshakeTicks++;
        if (this.recoveryResumeHandshakeTicks >= RECOVERY_RESUME_HANDSHAKE_INTERVAL_TICKS) {
            this.requestRecoveryDraftResume(false);
        }
    }

    private boolean handleRecoveryDraftConflictShortcuts(final int keyCode) {
        if (!this.recoveryDraft || this.recoveryResumeStatus != RecoveryDraftResumeStatus.DIVERGED) {
            return false;
        }

        if (hasAltDown()) {
            return switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> {
                    this.moveRecoveryCompareSelection(-1);
                    yield true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    this.moveRecoveryCompareSelection(1);
                    yield true;
                }
                default -> false;
            };
        }

        if (!hasControlDown()) {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.applySelectedServerHunk();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.forceOverwriteRecoveryDraftResume();
            return true;
        }

        if (keyCode != GLFW.GLFW_KEY_R) {
            return false;
        }

        if (hasShiftDown()) {
            this.forceOverwriteRecoveryDraftResume();
            return true;
        }

        this.loadServerScriptIntoRecoveryDraft();
        return true;
    }

    private boolean canEditDocument() {
        return !this.isBoundComputer() || this.editable;
    }

    private String editorStatusLine() {
        if (!this.isBoundComputer()) {
            if (this.hasRecoveryCompareView()) {
                return this.fitToWidth(this.recoveryConflictStatusLine(), 84);
            }
            return this.recoveryDraft ? ComputerSessionLossPolicy.recoveryDraftStatusLine(this.recoveryResumeMessage) : "Standalone editor";
        }
        if (!this.sessionStatus.targetAvailable()) {
            return this.fitToWidth(ComputerSessionLossPolicy.unavailableStatusLine(this.sessionMessage, this.unavailableTicks, this.recoveryResumeEligible), 84);
        }
        if (this.editable) {
            return "Editor lock: you";
        }
        if (this.activeEditorName.isBlank()) {
            return "Editor lock: read-only";
        }
        return "Editor lock: " + this.activeEditorName + " | read-only";
    }

    private String executionStatusLine() {
        final String summary = abbreviate(this.runtimeState.summary(), 72);
        if (this.isBoundComputer() && !this.sessionStatus.targetAvailable()) {
            return "target unavailable | last snapshot: " + summary;
        }
        if (this.startRequested) {
            return "waiting for the first server tick";
        }
        if (this.stopRequested && this.runtimeState.running()) {
            return "waiting for the current tick slice to finish";
        }
        if (!this.isBoundComputer() && this.runtimeState.running()) {
            return "standalone execution running";
        }
        if (this.runtimeState.running()) {
            return "waiting for the next server tick";
        }
        if (this.runtimeState.neverExecuted()) {
            return this.isBoundComputer() ? "press F5 to start" : "standalone editor idle";
        }
        if (this.runtimeState.stopped()) {
            return summary;
        }
        return summary;
    }

    private int executionStatusColor() {
        if (this.isBoundComputer() && !this.sessionStatus.targetAvailable()) {
            return OUTPUT_ERROR;
        }
        if (this.startRequested || this.runtimeState.running()) {
            return this.stopRequested ? OUTPUT_WARN : OUTPUT_INFO;
        }
        if (this.runtimeState.neverExecuted()) {
            return OUTPUT_DIM;
        }
        if (this.runtimeState.stopped()) {
            return OUTPUT_WARN;
        }
        return this.runtimeState.success() ? OUTPUT_OK : OUTPUT_ERROR;
    }

    private HeaderBadge executionBadge() {
        if (this.isBoundComputer() && !this.sessionStatus.targetAvailable()) {
            return new HeaderBadge("OFFLINE", OUTPUT_ERROR, 0x44F85149, HEADER_TEXT, false);
        }
        if (this.startRequested) {
            return new HeaderBadge("STARTING", OUTPUT_INFO, 0x3379C0FF, HEADER_TEXT, true);
        }
        if (this.stopRequested && this.runtimeState.running()) {
            return new HeaderBadge("STOPPING", OUTPUT_WARN, 0x33F2CC60, HEADER_TEXT, true);
        }
        if (this.runtimeState.running()) {
            return new HeaderBadge("RUNNING", OUTPUT_INFO, 0x3379C0FF, HEADER_TEXT, true);
        }
        if (this.runtimeState.neverExecuted()) {
            return new HeaderBadge("IDLE", OUTPUT_DIM, 0x334A5568, HEADER_TEXT, false);
        }
        if (this.runtimeState.stopped()) {
            return new HeaderBadge("STOPPED", OUTPUT_WARN, 0x33F2CC60, HEADER_TEXT, false);
        }
        if (this.runtimeState.success()) {
            return new HeaderBadge("DONE", OUTPUT_OK, 0x3356D364, HEADER_TEXT, false);
        }
        return new HeaderBadge("FAILED", OUTPUT_ERROR, 0x44F85149, HEADER_TEXT, false);
    }

    private String headerHelpLine() {
        if (!this.isBoundComputer()) {
            if (!this.recoveryDraft && this.runtimeState.running()) {
                return "Standalone run active  |  Builder opens the no-code editor  |  Guide opens the handbook";
            }
            return this.recoveryDraft
                    ? ComputerSessionLossPolicy.recoveryDraftHeaderLine(this.recoveryResumeStatus)
                    : "F5 run  |  Builder opens the no-code editor  |  Guide opens the handbook";
        }
        if (!this.sessionStatus.targetAvailable()) {
            return ComputerSessionLossPolicy.unavailableHeaderLine(this.unavailableTicks, this.recoveryResumeEligible);
        }
        if (!this.editable) {
            return "Read-only  |  Builder and Guide stay available  |  Ctrl+PgUp/PgDn output";
        }
        if (this.stopRequested && this.runtimeState.running()) {
            return "Stop pending  |  Builder and Guide stay available  |  Ctrl+PgUp/PgDn output";
        }
        if (this.startRequested) {
            return "Start pending  |  Builder and Guide stay available  |  Ctrl+PgUp/PgDn output";
        }
        if (this.runtimeState.running()) {
            return "F5 stop  |  Builder opens the no-code editor  |  Guide opens the handbook";
        }
        return "F5 run  |  Builder opens the no-code editor  |  Guide opens the handbook";
    }

    private boolean showsAutoStartToggle() {
        return this.isBoundComputer() || this.recoveryDraft;
    }

    private boolean canToggleAutoStart() {
        return this.recoveryDraft || (this.isBoundComputer() && this.canEditDocument());
    }

    private void toggleAutoStartOnLoad() {
        if (!this.canToggleAutoStart()) {
            this.showReadOnlyHint();
            return;
        }

        this.autoStartOnLoad = !this.autoStartOnLoad;
        if (this.isBoundComputer()) {
            this.pushBoundStateToServer();
        }
        this.showClientStatusMessage(this.autoStartOnLoad
                ? "Auto-start enabled for the next computer reload."
                : "Auto-start disabled for the next computer reload.");
    }

    private void openGuideBook() {
        if (this.minecraft == null) {
            return;
        }
        this.overlayHandoff = true;
        this.minecraft.setScreen(new GuideBookScreen(this));
    }

    private void openNoCodeBuilder() {
        if (this.minecraft == null) {
            return;
        }
        this.overlayHandoff = true;
        this.minecraft.setScreen(new NoCodeBuilderScreen(this, this.document.getText(), this.executionContext, this.canEditDocument()));
    }

    private HeaderActionButtonLayout guideBookButtonLayout(final RecoveryCompareLayout compareLayout) {
        final int margin = 12;
        final int right = (compareLayout == null ? this.width - margin : compareLayout.left() - 8);
        return this.headerActionButtonLayout(Component.translatable("screen.xllogic.guide_book.open").getString(), right);
    }

    private HeaderActionButtonLayout noCodeBuilderButtonLayout(final RecoveryCompareLayout compareLayout) {
        final HeaderActionButtonLayout guideLayout = this.guideBookButtonLayout(compareLayout);
        return this.headerActionButtonLayout(Component.translatable("screen.xllogic.no_code_builder.open").getString(), guideLayout.left() - 8);
    }

    private HeaderActionButtonLayout autoStartButtonLayout(final RecoveryCompareLayout compareLayout) {
        final HeaderActionButtonLayout builderLayout = this.noCodeBuilderButtonLayout(compareLayout);
        return this.headerActionButtonLayout(this.autoStartButtonLabel(), builderLayout.left() - 8);
    }

    private String autoStartButtonLabel() {
        return (this.autoStartOnLoad ? "[x] " : "[ ] ") + "Auto-start";
    }

    private HeaderActionButtonLayout headerActionButtonLayout(final String label, final int right) {
        final int margin = 12;
        final int width = Math.max(64, this.font.width(label) + 16);
        final int height = this.font.lineHeight + 8;
        return new HeaderActionButtonLayout(Math.max(margin, right - width), margin, width, height, label);
    }

    private void renderHeaderActionButton(final GuiGraphics graphics,
                                          final HeaderActionButtonLayout layout,
                                          final int mouseX,
                                          final int mouseY) {
        this.renderHeaderActionButton(graphics, layout, mouseX, mouseY, true);
    }

    private void renderHeaderActionButton(final GuiGraphics graphics,
                                          final HeaderActionButtonLayout layout,
                                          final int mouseX,
                                          final int mouseY,
                                          final boolean enabled) {
        final int fillColor = enabled && layout.contains(mouseX, mouseY) ? ACTION_BUTTON_HOVER : ACTION_BUTTON;
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), fillColor);
        graphics.renderOutline(layout.left(), layout.top(), layout.width(), layout.height(), enabled ? PANEL_BORDER : OUTPUT_DIM);
        graphics.drawString(this.font, layout.label(), layout.left() + 8, layout.top() + 4, enabled ? HEADER_TEXT : STATUS_TEXT);
    }

    private void showReadOnlyHint() {
        if (!this.sessionStatus.targetAvailable()) {
            this.showClientStatusMessage(ComputerSessionLossPolicy.unavailableStatusLine(this.sessionMessage, this.unavailableTicks, this.recoveryResumeEligible));
            return;
        }

        final String message = this.activeEditorName.isBlank()
                ? "This computer is currently read-only."
                : "This computer is currently edited by " + this.activeEditorName + ".";
        this.showClientStatusMessage(message);
    }

    private String defaultUnavailableMessage(final String sessionMessage) {
        if (sessionMessage == null || sessionMessage.isBlank()) {
            return "Computer session target is unavailable. Move back into range or reopen after the chunk reloads.";
        }
        return sessionMessage;
    }

    private String resolveActiveEditorName(final ComputerSessionStatus sessionStatus, final String activeEditorName) {
        if (!sessionStatus.targetAvailable() || activeEditorName == null || activeEditorName.isBlank()) {
            return "";
        }
        return activeEditorName;
    }

    private void announceSessionTransition(final ComputerSessionStatus previousSessionStatus, final boolean previousEditable,
                                           final String previousActiveEditorName) {
        if (!this.isBoundComputer()) {
            return;
        }

        if (previousSessionStatus != this.sessionStatus) {
            if (!this.sessionStatus.targetAvailable()) {
                this.showClientStatusMessage(ComputerSessionLossPolicy.unavailableTransitionMessage(this.sessionMessage, this.recoveryResumeEligible));
                return;
            }
            if (!previousSessionStatus.targetAvailable()) {
                this.showClientStatusMessage(this.restoredSessionMessage());
                return;
            }
        }

        if (previousEditable != this.editable) {
            this.showClientStatusMessage(this.lockTransitionMessage());
            return;
        }

        if (!this.editable && !previousActiveEditorName.equals(this.activeEditorName) && !this.activeEditorName.isBlank()) {
            this.showClientStatusMessage("Read-only: " + this.activeEditorName + " now holds the editor lock.");
        }
    }

    private boolean advancePersistentUnavailablePolicy() {
        final ComputerSessionLossPolicy.PersistentUnavailableAction action = ComputerSessionLossPolicy.resolvePersistentUnavailableAction(
                this.unavailableTicks,
                this.recoveryResumeEligible);
        return switch (action) {
            case WAIT -> false;
            case AUTO_CLOSE -> {
                this.closeForPersistentTargetLoss();
                yield true;
            }
            case OPEN_RECOVERY_DRAFT -> {
                this.openRecoveryDraftForPersistentTargetLoss();
                yield true;
            }
        };
    }

    private void closeForPersistentTargetLoss() {
        this.showClientStatusMessage(ComputerSessionLossPolicy.persistentLossMessage(false));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void openRecoveryDraftForPersistentTargetLoss() {
        this.showClientStatusMessage(ComputerSessionLossPolicy.persistentLossMessage(true));
        if (this.minecraft != null) {
            this.minecraft.setScreen(PythonComputerScreen.recoveryDraft(
                    this.boundComputerPos,
                    this.document.getText(),
                    this.lastSyncedScript,
                    this.runtimeState,
                    this.executionContext,
                    this.autoStartOnLoad));
        }
    }

    private void loadServerScriptIntoRecoveryDraft() {
        if (this.recoveryServerScript.isBlank()) {
            this.showClientStatusMessage("No server script snapshot is available for conflict resolution yet.");
            return;
        }

        final String serverScript = this.recoveryServerScript;
        this.recoveryBaseScript = serverScript;
        this.recoveryResumeStatus = RecoveryDraftResumeStatus.TARGET_UNAVAILABLE;
        this.recoveryResumeMessage = "Loaded the full server script into the recovery draft. Auto-resume is retrying.";
        this.recoveryServerScript = "";
        this.recoveryMergeResult = this.emptyRecoveryMergeResult();
        this.selectedRecoveryCompareConflict = 0;
        this.replaceDocumentText(serverScript);
        this.showClientStatusMessage(this.recoveryResumeMessage);
        this.requestRecoveryDraftResume(false);
    }

    private void forceOverwriteRecoveryDraftResume() {
        this.recoveryResumeStatus = RecoveryDraftResumeStatus.TARGET_UNAVAILABLE;
        this.recoveryResumeMessage = "Publishing the merged recovery draft back to the server.";
        this.recoveryServerScript = "";
        this.recoveryMergeResult = this.emptyRecoveryMergeResult();
        this.selectedRecoveryCompareConflict = 0;
        this.showClientStatusMessage(this.recoveryResumeMessage);
        this.requestRecoveryDraftResume(true);
    }

    private void applySelectedServerHunk() {
        final ScriptDiff.MergeConflict selectedConflict = this.selectedRecoveryCompareConflict();
        if (selectedConflict == null) {
            this.showClientStatusMessage("No merge conflict is currently selected.");
            return;
        }

        this.replaceDocumentText(ScriptDiff.applyServerConflict(this.document.getText(), selectedConflict));
        if (this.recoveryResumeStatus == RecoveryDraftResumeStatus.DIVERGED) {
            this.showClientStatusMessage("Applied the selected server conflict to the local recovery draft. "
                    + this.recoveryMergeResult.conflicts().size() + " unresolved conflict(s) remain.");
        }
    }

    private void requestRecoveryDraftResume(final boolean forceOverwrite) {
        if (this.recoveryTargetPos == null) {
            return;
        }
        PacketDistributor.sendToServer(new ResumeRecoveryDraftPayload(this.recoveryTargetPos, this.document.getText(), this.autoStartOnLoad, forceOverwrite));
        this.recoveryResumeHandshakeTicks = 0;
    }

    private void replaceDocumentText(final String newText) {
        final int lastLine = this.document.getLineCount() - 1;
        final int lastColumn = this.document.getLine(lastLine).length();
        this.document.replaceRange(0, 0, lastLine, lastColumn, newText == null ? "" : newText);
        this.editorScroll = 0;
        this.refreshSuggestions(false);
    }

    private void refreshRecoveryMergeState() {
        if (!this.recoveryDraft || this.recoveryResumeStatus != RecoveryDraftResumeStatus.DIVERGED || this.recoveryServerScript.isBlank()) {
            this.recoveryMergeResult = this.emptyRecoveryMergeResult();
            this.selectedRecoveryCompareConflict = 0;
            return;
        }

        if (this.recoveryBaseScript.isBlank()) {
            this.recoveryBaseScript = this.lastSyncedScript;
        }

        final ScriptDiff.MergeResult mergeResult = ScriptDiff.merge(this.recoveryBaseScript, this.document.getText(), this.recoveryServerScript);
        if (!mergeResult.mergedText().equals(this.document.getText())) {
            this.recoveryMergeResult = mergeResult;
            this.selectedRecoveryCompareConflict = mergeResult.clampSelection(this.selectedRecoveryCompareConflict);
            this.replaceDocumentText(mergeResult.mergedText());
            return;
        }

        this.recoveryMergeResult = mergeResult;
        if (!mergeResult.hasConflicts()) {
            this.publishAutomaticallyMergedRecoveryDraft(mergeResult);
            return;
        }

        this.selectedRecoveryCompareConflict = mergeResult.clampSelection(this.selectedRecoveryCompareConflict);
        this.recoveryResumeMessage = this.recoveryConflictResumeMessage(mergeResult);
    }

    private boolean hasRecoveryCompareView() {
        return this.recoveryDraft
                && this.recoveryResumeStatus == RecoveryDraftResumeStatus.DIVERGED
                && !this.recoveryServerScript.isBlank()
                && this.recoveryMergeResult.hasConflicts();
    }

    private ScriptDiff.MergeConflict selectedRecoveryCompareConflict() {
        return this.recoveryMergeResult.selectedConflict(this.selectedRecoveryCompareConflict);
    }

    private void moveRecoveryCompareSelection(final int delta) {
        if (this.recoveryMergeResult.conflicts().isEmpty()) {
            return;
        }
        this.selectedRecoveryCompareConflict = Math.floorMod(this.selectedRecoveryCompareConflict + delta, this.recoveryMergeResult.conflicts().size());
    }

    private int recoveryDiffHighlight(final int documentLine) {
        if (!this.hasRecoveryCompareView()) {
            return 0;
        }

        final ScriptDiff.MergeConflict selectedConflict = this.selectedRecoveryCompareConflict();
        if (selectedConflict != null && this.lineInsideConflict(selectedConflict, documentLine)) {
            return RECOVERY_COMPARE_SELECTED;
        }
        for (final ScriptDiff.MergeConflict conflict : this.recoveryMergeResult.conflicts()) {
            if (this.lineInsideConflict(conflict, documentLine)) {
                return RECOVERY_COMPARE_CONFLICT;
            }
        }
        return 0;
    }

    private boolean lineInsideConflict(final ScriptDiff.MergeConflict conflict, final int documentLine) {
        return conflict.localLineCount() > 0 && documentLine >= conflict.mergedStartLine() && documentLine < conflict.mergedEndLineExclusive();
    }

    private String recoveryConflictSummary() {
        if (!this.recoveryMergeResult.hasConflicts()) {
            return "No unresolved merge conflicts remain.";
        }
        final String prefix = this.recoveryMergeResult.autoMergedServerChangeCount() > 0
                ? this.recoveryMergeResult.autoMergedServerChangeCount() + " non-conflicting server hunk(s) merged automatically. "
                : "";
        return prefix + this.recoveryMergeResult.conflicts().size() + " unresolved merge conflict(s) remain between the local recovery draft and the current server snapshot.";
    }

    private String recoveryHunkTitle(final int index, final ScriptDiff.MergeConflict conflict) {
        return "#" + (index + 1) + "  " + this.recoveryBaseRangeLabel(conflict) + "  |  " + this.recoveryRangeLabel(conflict, true)
                + "  <>  " + this.recoveryRangeLabel(conflict, false);
    }

    private String recoveryHunkDetail(final ScriptDiff.MergeConflict conflict) {
        if (conflict.baseLineCount() == 0) {
            return conflict.localLines().size() + " local insertion line(s) versus " + conflict.serverLineCount() + " server insertion line(s).";
        }
        if (conflict.localLineCount() == 0) {
            return "Local draft removes " + conflict.baseLineCount() + " base line(s); server keeps or replaces them with "
                    + conflict.serverLineCount() + " line(s).";
        }
        if (conflict.serverLineCount() == 0) {
            return "Server removes " + conflict.baseLineCount() + " base line(s); local draft keeps or replaces them with "
                    + conflict.localLineCount() + " line(s).";
        }
        return conflict.localLineCount() + " local line(s) versus " + conflict.serverLineCount() + " server line(s) on top of "
                + conflict.baseLineCount() + " base line(s).";
    }

    private String recoveryRangeLabel(final ScriptDiff.MergeConflict conflict, final boolean localSide) {
        return localSide
                ? this.recoveryRangeLabel(conflict.mergedStartLine(), conflict.mergedEndLineExclusive(), "L")
                : this.recoveryRangeLabel(conflict.serverStartLine(), conflict.serverEndLineExclusive(), "S");
    }

    private String recoveryBaseRangeLabel(final ScriptDiff.MergeConflict conflict) {
        return this.recoveryRangeLabel(conflict.baseStartLine(), conflict.baseEndLineExclusive(), "B");
    }

    private String recoveryRangeLabel(final int start, final int endExclusive, final String prefix) {
        if (endExclusive <= start) {
            return prefix + (start + 1) + " insert";
        }
        if (endExclusive - start == 1) {
            return prefix + (start + 1);
        }
        return prefix + (start + 1) + "-" + endExclusive;
    }

    private List<RecoveryComparePreviewLine> recoveryPreviewLines(final ScriptDiff.MergeConflict conflict, final boolean localSide, final int maxLines) {
        if (conflict == null) {
            return List.of(new RecoveryComparePreviewLine("", "No merge conflict is currently selected.", true, true));
        }

        final List<String> sourceLines = this.recoveryPreviewSourceLines(localSide);
        final int start = localSide ? conflict.mergedStartLine() : conflict.serverStartLine();
        final int endExclusive = localSide ? conflict.mergedEndLineExclusive() : conflict.serverEndLineExclusive();
        final ArrayList<RecoveryComparePreviewLine> previewLines = new ArrayList<>();
        this.addRecoveryPreviewContextBefore(previewLines, sourceLines, start, maxLines);
        this.addRecoveryPreviewChangedLines(previewLines, sourceLines, start, endExclusive, localSide, maxLines);
        this.addRecoveryPreviewContextAfter(previewLines, sourceLines, endExclusive, maxLines);
        return previewLines;
    }

    private List<String> recoveryPreviewSourceLines(final boolean localSide) {
        final String sourceText = localSide ? this.document.getText() : this.recoveryServerScript;
        final String normalized = sourceText == null ? "" : sourceText.replace("\r\n", "\n").replace('\r', '\n');
        return List.of(normalized.split("\n", -1));
    }

    private void addRecoveryPreviewContextBefore(final List<RecoveryComparePreviewLine> previewLines, final List<String> sourceLines,
                                                 final int start, final int maxLines) {
        if (start <= 0 || previewLines.size() >= maxLines) {
            return;
        }
        previewLines.add(new RecoveryComparePreviewLine(Integer.toString(start), sourceLines.get(start - 1), false, false));
    }

    private void addRecoveryPreviewChangedLines(final List<RecoveryComparePreviewLine> previewLines, final List<String> sourceLines,
                                                final int start, final int endExclusive, final boolean localSide,
                                                final int maxLines) {
        if (previewLines.size() >= maxLines) {
            return;
        }

        if (start == endExclusive) {
            final int anchorLine = Math.max(1, Math.min(start + 1, sourceLines.size() + 1));
            previewLines.add(new RecoveryComparePreviewLine(Integer.toString(anchorLine),
                    localSide ? "No local conflict lines exist for this hunk yet." : "No server lines exist for this conflict.", true, true));
            return;
        }

        final int remainingChangedCapacity = Math.max(1, maxLines - previewLines.size() - (endExclusive < sourceLines.size() ? 1 : 0));
        final int shownChangedLines = Math.min(endExclusive - start, remainingChangedCapacity);
        for (int lineIndex = start; lineIndex < start + shownChangedLines; lineIndex++) {
            previewLines.add(new RecoveryComparePreviewLine(Integer.toString(lineIndex + 1), sourceLines.get(lineIndex), true, false));
        }
        if (shownChangedLines < endExclusive - start && previewLines.size() < maxLines) {
            previewLines.add(new RecoveryComparePreviewLine("", "...", true, true));
        }
    }

    private void addRecoveryPreviewContextAfter(final List<RecoveryComparePreviewLine> previewLines, final List<String> sourceLines,
                                                final int endExclusive, final int maxLines) {
        if (endExclusive >= sourceLines.size() || previewLines.size() >= maxLines) {
            return;
        }
        previewLines.add(new RecoveryComparePreviewLine(Integer.toString(endExclusive + 1), sourceLines.get(endExclusive), false, false));
    }

    private ScriptDiff.MergeResult emptyRecoveryMergeResult() {
        return new ScriptDiff.MergeResult("", List.of(), 0);
    }

    private void publishAutomaticallyMergedRecoveryDraft(final ScriptDiff.MergeResult mergeResult) {
        this.recoveryResumeStatus = RecoveryDraftResumeStatus.TARGET_UNAVAILABLE;
        this.recoveryResumeMessage = this.automaticMergeResumeMessage(mergeResult.autoMergedServerChangeCount());
        this.recoveryServerScript = "";
        this.recoveryMergeResult = this.emptyRecoveryMergeResult();
        this.selectedRecoveryCompareConflict = 0;
        this.showClientStatusMessage(this.recoveryResumeMessage);
        this.requestRecoveryDraftResume(true);
    }

    private String automaticMergeResumeMessage(final int autoMergedServerChangeCount) {
        if (autoMergedServerChangeCount > 0) {
            return "Automatically merged " + autoMergedServerChangeCount + " non-conflicting server hunk(s). Publishing the merged recovery draft.";
        }
        return "Resolved all remaining merge conflicts. Publishing the merged recovery draft.";
    }

    private String recoveryConflictResumeMessage(final ScriptDiff.MergeResult mergeResult) {
        if (mergeResult.autoMergedServerChangeCount() > 0) {
            return "Automatically merged " + mergeResult.autoMergedServerChangeCount() + " non-conflicting server hunk(s). "
                    + mergeResult.conflicts().size() + " unresolved merge conflict(s) remain.";
        }
        return mergeResult.conflicts().size() + " unresolved merge conflict(s) remain between the recovery draft and the current server script.";
    }

    private String recoveryConflictStatusLine() {
        final String autoMergedPrefix = this.recoveryMergeResult.autoMergedServerChangeCount() > 0
                ? this.recoveryMergeResult.autoMergedServerChangeCount() + " auto-merged server hunk(s); "
                : "";
        return "Resume conflict: " + autoMergedPrefix + this.recoveryMergeResult.conflicts().size() + " unresolved merge conflict(s).";
    }

    private String defaultRecoveryResumeMessage(final RecoveryDraftResumeStatus status, final String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return switch (status) {
            case BLOCKED_BY_OTHER_EDITOR -> "Recovery draft is waiting because another player currently holds the editor lock.";
            case DIVERGED -> "Recovery draft and server script diverged. Use the compare panel to take server hunks or publish a merged draft.";
            case RESUMED -> "Recovery draft resumed. Server-backed editing restored.";
            case TARGET_UNAVAILABLE -> "Recovery draft is waiting for the computer to come back into range or reload.";
        };
    }

    private void showClientStatusMessage(final String message) {
        if (message == null || message.isBlank() || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.displayClientMessage(Component.literal(message), true);
    }

    private String restoredSessionMessage() {
        if (this.editable) {
            return "Computer session restored. You can edit again.";
        }
        if (this.activeEditorName.isBlank()) {
            return "Computer session restored in read-only mode.";
        }
        return "Computer session restored. " + this.activeEditorName + " currently holds the editor lock.";
    }

    private String lockTransitionMessage() {
        if (this.editable) {
            return "Editor lock granted.";
        }
        if (this.activeEditorName.isBlank()) {
            return "This computer is now read-only.";
        }
        return "This computer is now read-only while " + this.activeEditorName + " edits.";
    }

    private static String abbreviate(final String text, final int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static int withAlpha(final int color, final int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private EditorLayout editorLayout() {
        final int margin = 12;
        final int headerHeight = (this.font.lineHeight + 2) * 5 + 2;
        final int gap = 8;
        final int compareWidth = this.hasRecoveryCompareView()
            ? Math.min(360, Math.max(240, (this.width - margin * 2) / 3))
            : 0;
        final int outputHeight = Math.min(170, Math.max(120, this.height / 3));
        final int editorTop = margin + headerHeight;
        final int editorHeight = this.height - margin - editorTop - outputHeight - gap;
        final int editorLeft = margin;
        final int editorRight = this.width - margin - compareWidth - (compareWidth == 0 ? 0 : gap);
        final int outputTop = editorTop + editorHeight + gap;
        final int lineHeight = this.font.lineHeight + 2;
        final int visibleLines = Math.max(1, editorHeight / lineHeight - 1);
        final int lineNumberWidth = this.font.width(String.valueOf(this.document.getLineCount()));
        final int contentLeft = editorLeft + 6;
        final int textLeft = contentLeft + lineNumberWidth + 8;
        return new EditorLayout(editorLeft, editorRight, editorTop, editorHeight, outputTop, outputHeight, lineHeight, visibleLines, contentLeft, textLeft);
    }

    private RecoveryCompareLayout recoveryCompareLayout() {
        if (!this.hasRecoveryCompareView()) {
            return null;
        }

        final EditorLayout layout = this.editorLayout();
        final int gap = 8;
        final int left = layout.right() + gap;
        final int right = this.width - 12;
        final int top = layout.top();
        final int bottom = layout.outputBottom();
        final int buttonHeight = this.font.lineHeight + 8;
        final int buttonGap = 4;
        final int buttonTop = top + 8 + this.font.lineHeight * 2 + 8;
        final int hunkRowHeight = this.font.lineHeight * 2 + 8;
        final int hunkListTop = buttonTop + (buttonHeight + buttonGap) * 3 + this.font.lineHeight + 14;
        final int remainingHeight = Math.max(120, bottom - hunkListTop - 8);
        final int hunkListHeight = Math.max(80, remainingHeight / 3);
        final int visibleRows = Math.max(2, hunkListHeight / hunkRowHeight);
        final int localPreviewTop = hunkListTop + visibleRows * hunkRowHeight + 8;
        final int previewHeight = Math.max(56, (bottom - localPreviewTop - 16) / 2);
        return new RecoveryCompareLayout(left, right, top, bottom, buttonTop, buttonHeight, buttonHeight + buttonGap,
                hunkListTop, hunkRowHeight, visibleRows, localPreviewTop, localPreviewTop + previewHeight + 8, previewHeight);
    }

    private void placeCursorAt(final EditorLayout layout, final double mouseX, final double mouseY, final boolean keepSelection) {
        final int relativeLine = Mth.floor((mouseY - layout.textTop()) / layout.lineHeight());
        final int documentLine = Mth.clamp(this.editorScroll + relativeLine, 0, this.document.getLineCount() - 1);
        final String line = this.document.getLine(documentLine);
        final int column = this.columnForX(line, (int) Math.round(mouseX) - this.editorTextRenderLeft(layout));
        this.document.setCursor(documentLine, column, keepSelection);
    }

    private int columnForX(final String line, final int relativeX) {
        if (relativeX <= 0) {
            return 0;
        }

        int width = 0;
        for (int column = 0; column < line.length(); column++) {
            final int characterWidth = this.font.width(String.valueOf(line.charAt(column)));
            if (relativeX < width + Math.max(1, characterWidth / 2)) {
                return column;
            }
            width += characterWidth;
        }
        return line.length();
    }

    private SuggestionPopupLayout suggestionPopupLayout(final EditorLayout layout) {
        if (!this.suggestionSession.visible()) {
            return null;
        }
        if (this.document.getCursorLine() < this.editorScroll || this.document.getCursorLine() >= this.editorScroll + layout.visibleLines()) {
            return null;
        }

        final int visibleLine = this.document.getCursorLine() - this.editorScroll;
        final String line = this.document.getLine(this.document.getCursorLine());
        final int safeColumn = Math.min(this.document.getCursorColumn(), line.length());
        final int cursorX = this.editorTextRenderLeft(layout) + this.font.width(line.substring(0, safeColumn));
        final int cursorY = layout.textTop() + visibleLine * layout.lineHeight();
        final int itemHeight = this.font.lineHeight + 4;
        final int itemCount = this.suggestionSession.items().size();
        int popupWidth = 150;
        for (final PythonSuggestionEngine.SuggestionItem item : this.suggestionSession.items()) {
            popupWidth = Math.max(popupWidth, this.font.width(item.label()) + this.font.width(item.detail()) + 24);
        }
        popupWidth = Math.min(popupWidth, Math.max(150, layout.right() - layout.textLeft() - 8));
        final int left = Mth.clamp(cursorX, layout.textLeft(), Math.max(layout.textLeft(), layout.right() - popupWidth - 8));
        final int height = itemCount * itemHeight + 8;
        int top = cursorY + this.font.lineHeight + 4;
        if (top + height > layout.bottom() - 4) {
            top = Math.max(layout.top() + 4, cursorY - height - 4);
        }
        return new SuggestionPopupLayout(left, top, popupWidth, height, itemHeight, itemCount);
    }

    private int editorTextRenderLeft(final EditorLayout layout) {
        return layout.textLeft() - this.editorHorizontalScroll;
    }

    private String fitToWidth(final String text, final int maxWidth) {
        if (text.isEmpty() || this.font.width(text) <= maxWidth) {
            return text;
        }

        final String ellipsis = "...";
        final int prefixWidth = Math.max(0, maxWidth - this.font.width(ellipsis));
        if (prefixWidth == 0) {
            return ellipsis;
        }
        return this.font.plainSubstrByWidth(text, prefixWidth) + ellipsis;
    }

    private static int outputAccentColor(final ComputerOutputEntry outputEntry) {
        if (outputEntry.errorTone()) {
            return OUTPUT_ERROR;
        }
        if (outputEntry.okTone()) {
            return OUTPUT_OK;
        }
        if (outputEntry.planChannel()) {
            return OUTPUT_INFO;
        }
        return OUTPUT_DIM;
    }

    private static int outputTagBackground(final ComputerOutputEntry outputEntry) {
        if (outputEntry.errorTone()) {
            return 0x44F85149;
        }
        if (outputEntry.okTone()) {
            return 0x4456D364;
        }
        if (outputEntry.planChannel()) {
            return 0x4479C0FF;
        }
        return 0x334A5568;
    }

    private static int outputTextColor(final ComputerOutputEntry outputEntry) {
        if (outputEntry.errorTone()) {
            return OUTPUT_ERROR;
        }
        if (outputEntry.okTone()) {
            return OUTPUT_OK;
        }
        if (outputEntry.planChannel()) {
            return OUTPUT_INFO;
        }
        return OUTPUT_TEXT;
    }

    private record HeaderBadge(String label, int accentColor, int fillColor, int textColor, boolean pulse) {
    }

    private record HeaderActionButtonLayout(int left, int top, int width, int height, String label) {
        private int right() {
            return this.left + this.width;
        }

        private int bottom() {
            return this.top + this.height;
        }

        private boolean contains(final double mouseX, final double mouseY) {
            return mouseX >= this.left && mouseX < this.right() && mouseY >= this.top && mouseY < this.bottom();
        }
    }

    private record EditorLayout(int left, int right, int top, int height, int outputTop, int outputHeight, int lineHeight, int visibleLines,
                                int contentLeft, int textLeft) {
        private int bottom() {
            return this.top + this.height;
        }

        private int outputBottom() {
            return this.outputTop + this.outputHeight;
        }

        private int textTop() {
            return this.top + 8;
        }

        private int textWidth() {
            return Math.max(40, this.right - this.textLeft - 8);
        }

        private boolean contains(final double mouseX, final double mouseY) {
            return mouseX >= this.left && mouseX < this.right && mouseY >= this.top && mouseY < this.bottom();
        }

        private boolean outputContains(final double mouseX, final double mouseY) {
            return mouseX >= this.left && mouseX < this.right && mouseY >= this.outputTop && mouseY < this.outputBottom();
        }
    }

    private record SuggestionPopupLayout(int left, int top, int width, int height, int itemHeight, int itemCount) {
        private boolean contains(final double mouseX, final double mouseY) {
            return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.top + this.height;
        }

        private int itemIndex(final double mouseY) {
            final int index = Mth.floor((mouseY - (this.top + 4)) / this.itemHeight);
            return Mth.clamp(index, 0, this.itemCount - 1);
        }
    }

    private record RecoveryCompareLayout(int left, int right, int top, int bottom, int buttonTop, int buttonHeight, int buttonStride,
                                         int hunkListTop, int hunkRowHeight, int visibleHunkRows, int localPreviewTop,
                                         int serverPreviewTop, int previewHeight) {
        private boolean contains(final double mouseX, final double mouseY) {
            return mouseX >= this.left && mouseX < this.right && mouseY >= this.top && mouseY < this.bottom;
        }

        private int actionIndex(final double mouseX, final double mouseY) {
            if (mouseX < this.left + 8 || mouseX >= this.right - 8) {
                return -1;
            }
            for (int index = 0; index < 3; index++) {
                final int top = this.buttonTop + index * this.buttonStride;
                if (mouseY >= top && mouseY < top + this.buttonHeight) {
                    return index;
                }
            }
            return -1;
        }

        private int firstVisibleHunkIndex(final int itemCount, final int selectedIndex) {
            if (itemCount <= this.visibleHunkRows) {
                return 0;
            }
            return Math.max(0, Math.min(selectedIndex - this.visibleHunkRows / 2, itemCount - this.visibleHunkRows));
        }

        private int hunkIndex(final double mouseY, final int itemCount, final int selectedIndex) {
            if (mouseY < this.hunkListTop || mouseY >= this.hunkListTop + this.visibleHunkRows * this.hunkRowHeight) {
                return -1;
            }
            final int row = Mth.floor((mouseY - this.hunkListTop) / this.hunkRowHeight);
            final int index = this.firstVisibleHunkIndex(itemCount, selectedIndex) + row;
            return index >= itemCount ? -1 : index;
        }
    }

    private record RecoveryComparePreviewLine(String lineNumber, String text, boolean changed, boolean placeholder) {
    }

    private record RecoveryComparePreviewLayout(int left, int top, int width, int height, String title,
                                                boolean localSide, int accentFill) {
        private int maxVisibleLines(final int fontLineHeight) {
            return Math.max(3, (this.height - 18) / (fontLineHeight + 2));
        }
    }
}