package de.xllogic.client;

import de.xllogic.client.input.XLKeyMappings;
import de.xllogic.client.render.NetworkActivityBlockEntityRenderer;
import de.xllogic.client.render.ScreenBlockEntityRenderer;
import de.xllogic.client.screen.EndpointNamingScreen;
import de.xllogic.client.screen.GuideBookScreen;
import de.xllogic.client.screen.NoCodeBuilderScreen;
import de.xllogic.client.screen.PythonComputerScreen;
import de.xllogic.common.block.ColoredRedstoneCableBlock;
import de.xllogic.common.network.payload.ComputerSessionStatus;
import de.xllogic.common.network.payload.OpenEndpointNamingPayload;
import de.xllogic.common.network.payload.ResumeRecoveryDraftResultPayload;
import de.xllogic.common.registry.XLBlockEntities;
import de.xllogic.common.registry.XLBlocks;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class XLLogicClient {
    private static final int DEBUGGER_BACKGROUND = 0xCC0B0F14;
    private static final int DEBUGGER_BORDER = 0xAA2F3A4A;
    private static final int DEBUGGER_TEXT = 0xFFE6EDF3;
    private static final int DEBUGGER_HEADER = 0xFF79C0FF;
    private static final int DEBUGGER_SPIKE = 0xFFF2CC60;
    private static final String DEBUGGER_VALUE_SEPARATOR = " ms | ";

    private XLLogicClient() {
    }

    public static void openComputerScreen() {
        Minecraft.getInstance().setScreen(new PythonComputerScreen());
    }

    public static void openComputerScreen(final String initialScript, final PythonExecutionContext executionContext) {
        Minecraft.getInstance().setScreen(new PythonComputerScreen(initialScript, executionContext));
    }

    public static void openGuideBookScreen() {
        Minecraft.getInstance().setScreen(new GuideBookScreen());
    }

    public static void openComputerScreen(final BlockPos computerPos, final String initialScript, final ComputerRuntimeSnapshot runtimeState,
                                          final PythonExecutionContext executionContext, final boolean editable, final String activeEditorName,
                                          final boolean autoStartOnLoad) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PythonComputerScreen screen && screen.isBoundTo(computerPos)) {
            screen.applyOpenedComputerState(runtimeState, executionContext, editable, activeEditorName, autoStartOnLoad);
            return;
        }

        minecraft.setScreen(new PythonComputerScreen(computerPos, initialScript, runtimeState, executionContext, editable, activeEditorName, autoStartOnLoad));
    }

    public static void updateComputerRuntime(final BlockPos computerPos, final ComputerRuntimeSnapshot runtimeState,
                                             final PythonExecutionContext executionContext, final boolean editable, final String activeEditorName,
                                             final ComputerSessionStatus sessionStatus, final String sessionMessage) {
        final PythonComputerScreen screen = activeComputerScreen();
        if (screen != null && screen.isBoundTo(computerPos)) {
            screen.applyServerRuntimeState(runtimeState, executionContext, editable, activeEditorName, sessionStatus, sessionMessage);
        }
    }

    public static void applyRecoveryDraftResumeResult(final ResumeRecoveryDraftResultPayload payload) {
        final PythonComputerScreen screen = activeComputerScreen();
        if (screen != null) {
            screen.applyRecoveryDraftResumeResult(payload);
        }
    }

    private static PythonComputerScreen activeComputerScreen() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PythonComputerScreen screen) {
            return screen;
        }
        if (minecraft.screen instanceof GuideBookScreen guideBookScreen) {
            return guideBookScreen.returnComputerScreen();
        }
        if (minecraft.screen instanceof NoCodeBuilderScreen builderScreen) {
            return builderScreen.returnComputerScreen();
        }
        return null;
    }

    public static void openEndpointNamingScreen(final OpenEndpointNamingPayload payload) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof EndpointNamingScreen screen && screen.isBoundTo(payload.endpointPos())) {
            screen.applyPayload(payload);
            return;
        }

        minecraft.setScreen(new EndpointNamingScreen(payload));
    }

    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(XLLogicClient::onRegisterKeyMappings);
        modEventBus.addListener(XLLogicClient::onRegisterRenderers);
        modEventBus.addListener(XLLogicClient::onRegisterBlockColors);
        modEventBus.addListener(XLLogicClient::onRegisterItemColors);
        NeoForge.EVENT_BUS.addListener(XLLogicClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(XLLogicClient::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(XLLogicClient::onRenderGuiPost);
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        XLKeyMappings.register(event);
    }

    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(XLBlockEntities.SCREEN.get(), ScreenBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.COMPUTER.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.LIGHT_SENSOR.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.CLOCK.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.RAIN_SENSOR.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.XLAPI_BLOCK.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.REDSTONE_IO.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.MATERIAL_IO.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.CRAFTING_IO.get(), NetworkActivityBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(XLBlockEntities.CRAFTING_CPU.get(), NetworkActivityBlockEntityRenderer::new);
    }

    public static void onRegisterBlockColors(final RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
                    if (tintIndex != 0 || !(state.getBlock() instanceof ColoredRedstoneCableBlock coloredCable)) {
                        return -1;
                    }
                    final int channel = state.hasProperty(ColoredRedstoneCableBlock.CHANNEL)
                            ? state.getValue(ColoredRedstoneCableBlock.CHANNEL)
                            : coloredCable.fixedChannel();
                    return ColoredRedstoneCableBlock.colorForChannel(channel);
                },
                XLBlocks.coloredRedstoneCableBlocks());
    }

    public static void onRegisterItemColors(final RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
                    if (tintIndex != 0) {
                        return -1;
                    }
                    final Block block = Block.byItem(stack.getItem());
                    return block instanceof ColoredRedstoneCableBlock coloredCable
                            ? ColoredRedstoneCableBlock.colorForChannel(coloredCable.fixedChannel())
                            : -1;
                },
                XLBlocks.coloredRedstoneCableBlocks());
    }

    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        XLRuntimeDebugger.markClientTick();
        handleDebuggerHotkeys(minecraft);
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        while (XLKeyMappings.OPEN_COMPUTER.consumeClick()) {
            openComputerScreen();
        }
    }

    public static void onRenderFramePre(final RenderFrameEvent.Pre event) {
        XLRuntimeDebugger.markClientFrame();
    }

    public static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        if (!XLRuntimeDebugger.enabled()) {
            return;
        }

        renderRuntimeDebuggerOverlay(event.getGuiGraphics());
    }

    private static void handleDebuggerHotkeys(final Minecraft minecraft) {
        while (XLKeyMappings.TOGGLE_RUNTIME_DEBUGGER.consumeClick()) {
            final boolean enabled = XLRuntimeDebugger.toggleEnabled();
            showClientMessage(minecraft, enabled
                    ? "XL runtime debugger enabled. Reproduce the stutter now. Hitchs dump automatically; F9 still writes the current summary to the log."
                    : "XL runtime debugger disabled.");
        }

        while (XLKeyMappings.DUMP_RUNTIME_DEBUGGER.consumeClick()) {
            XLRuntimeDebugger.dumpToLog();
            showClientMessage(minecraft, "XL runtime debugger summary written to the log.");
        }
    }

    private static void renderRuntimeDebuggerOverlay(final GuiGraphics graphics) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Font font = minecraft.font;
        if (font == null) {
            return;
        }

        final XLRuntimeDebugger.DebugSnapshot snapshot = XLRuntimeDebugger.overlaySnapshot();
        final List<String> lines = runtimeDebuggerOverlayLines(snapshot);
        final int maxWidth = runtimeDebuggerOverlayWidth(font, lines);
        renderRuntimeDebuggerBox(graphics, font, lines, maxWidth);
    }

    private static List<String> runtimeDebuggerOverlayLines(final XLRuntimeDebugger.DebugSnapshot snapshot) {
        final List<String> lines = new ArrayList<>();
        lines.add("XL Runtime Debugger [F8 toggle | F9 log]");
        lines.add("Frame gap last/worst: " + formatMillis(snapshot.lastFrameGapNanos()) + " / " + formatMillis(snapshot.worstFrameGapNanos()) + " ms");
        lines.add("Client tick last/worst: " + formatMillis(snapshot.lastClientTickGapNanos()) + " / " + formatMillis(snapshot.worstClientTickGapNanos()) + " ms");

        if (snapshot.topSections().isEmpty()) {
            lines.add("No mod samples yet. Reproduce the stutter now.");
        } else {
            lines.add("Top sections:");
            for (final XLRuntimeDebugger.SectionSnapshot section : snapshot.topSections()) {
                lines.add("  max " + formatMillis(section.maxNanos())
                        + DEBUGGER_VALUE_SEPARATOR + "avg " + formatMillis(section.averageNanos())
                        + DEBUGGER_VALUE_SEPARATOR + section.callCount()
                        + "x | " + abbreviate(section.name(), 58));
            }
        }

        if (!snapshot.latestSpikes().isEmpty()) {
            lines.add("Latest spikes:");
            for (final XLRuntimeDebugger.SpikeSnapshot spike : snapshot.latestSpikes()) {
                lines.add("  " + formatMillis(spike.durationNanos()) + DEBUGGER_VALUE_SEPARATOR + abbreviate(spike.name(), 58));
            }
        }

        return lines;
    }

    private static int runtimeDebuggerOverlayWidth(final Font font, final List<String> lines) {
        int maxWidth = 0;
        for (final String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        return maxWidth;
    }

    private static void renderRuntimeDebuggerBox(final GuiGraphics graphics, final Font font, final List<String> lines, final int maxWidth) {
        final int left = 8;
        final int top = 8;
        final int lineHeight = font.lineHeight + 2;
        final int right = left + maxWidth + 10;
        final int bottom = top + lines.size() * lineHeight + 6;
        graphics.fill(left, top, right, bottom, DEBUGGER_BACKGROUND);
        graphics.renderOutline(left, top, right - left, bottom - top, DEBUGGER_BORDER);

        int y = top + 4;
        for (final String line : lines) {
            graphics.drawString(font, line, left + 5, y, runtimeDebuggerLineColor(line));
            y += lineHeight;
        }
    }

    private static int runtimeDebuggerLineColor(final String line) {
        if (line.startsWith("XL Runtime Debugger") || line.endsWith(":")) {
            return DEBUGGER_HEADER;
        }
        if (line.startsWith("  ") && line.contains(DEBUGGER_VALUE_SEPARATOR)) {
            return line.contains("Latest spikes") ? DEBUGGER_SPIKE : DEBUGGER_TEXT;
        }
        return DEBUGGER_TEXT;
    }

    private static void showClientMessage(final Minecraft minecraft, final String message) {
        if (minecraft.player == null || message == null || message.isBlank()) {
            return;
        }
        minecraft.player.displayClientMessage(Component.literal(message), true);
    }

    private static String formatMillis(final long durationNanos) {
        return String.format(Locale.ROOT, "%.2f", durationNanos / 1_000_000.0d);
    }

    private static String abbreviate(final String value, final int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
