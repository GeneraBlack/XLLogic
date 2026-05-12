package de.xllogic.client.screen;

import de.xllogic.client.nocode.NoCodeBlock;
import de.xllogic.client.nocode.NoCodeBuilderTemplate;
import de.xllogic.client.nocode.NoCodeBlockKind;
import de.xllogic.client.nocode.NoCodeProgram;
import de.xllogic.client.nocode.NoCodeProgramCodec;
import de.xllogic.client.nocode.NoCodeScriptGenerator;
import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonPeripheralBinding;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class NoCodeBuilderScreen extends Screen {
    private static final int BACKDROP = 0xB0080B10;
    private static final int PANEL_BACKGROUND = 0xE60B0F14;
    private static final int PANEL_BORDER = 0xAA2F3A4A;
    private static final int TITLE_COLOR = 0xFFE6EDF3;
    private static final int TEXT_COLOR = 0xFFD9E1EA;
    private static final int INFO_COLOR = 0xFF8B949E;
    private static final int WARN_COLOR = 0xFFF2CC60;
    private static final int SECTION_COLOR = 0xFF79C0FF;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MAX_VISIBLE_ROWS = 11;
    private static final int TEMPLATE_COLUMNS = 4;
    private static final String DEFAULT_ITEM_ID = "minecraft:cobblestone";
    private static final String DEFAULT_FLUID_ID = "minecraft:water";
    private static final String DEFAULT_SIDE = "north";
    private static final String DEFAULT_TARGET_SIDE = "south";
    private static final int MAX_WORLD_DAY_TIME = 23_999;
    private static final int DEFAULT_WORLD_TIME_WINDOW_END = 12_000;
    private static final String GUARDED_NEXT_BLOCK_TEXT = "Only the directly following block is guarded.";
    private static final String ELSE_BRANCH_TEXT = "Place this directly after a guarded block. The next block becomes the else branch.";
    private static final int FLUID_TRANSFER_STEP = 250;

    private final PythonComputerScreen returnScreen;
    private final PythonExecutionContext executionContext;
    private final NoCodeProgram program;
    private final boolean editable;
    private final boolean foundMetadata;
    private final boolean metadataMatchesCurrentScript;
    private final boolean parseError;
    private final boolean currentScriptBlank;
    private int selectedBlockIndex;
    private int blockListScroll;
    private EditBox messageBox;

    public NoCodeBuilderScreen(final PythonComputerScreen returnScreen,
                               final String currentScript,
                               final PythonExecutionContext executionContext,
                               final boolean editable) {
        super(Component.literal("XL Logic Builder"));
        final NoCodeProgramCodec.DecodedProgram decodedProgram = NoCodeProgramCodec.decode(currentScript);
        this.returnScreen = returnScreen;
        this.executionContext = executionContext == null ? PythonExecutionContext.empty() : executionContext;
        this.program = decodedProgram.program();
        this.editable = editable;
        this.foundMetadata = decodedProgram.foundMetadata();
        this.metadataMatchesCurrentScript = decodedProgram.matchesGeneratedScript();
        this.parseError = decodedProgram.parseError();
        this.currentScriptBlank = currentScript == null || currentScript.isBlank();
        this.selectedBlockIndex = this.program.blocks().isEmpty() ? -1 : 0;
    }

    public PythonComputerScreen returnComputerScreen() {
        return this.returnScreen;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.returnScreen != null) {
            this.returnScreen.tickOverlayHost();
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.messageBox = null;
        final Layout layout = this.layout();
        this.clampSelection();

        this.buildTemplateButtons(layout);

        int catalogY = layout.contentTop() + 18;
        for (final NoCodeBlockKind kind : NoCodeBlockKind.values()) {
            final NoCodeBlockKind targetKind = kind;
            this.addRenderableWidget(Button.builder(Component.literal("+ " + kind.label()), ignored -> this.addBlock(targetKind))
                    .bounds(layout.catalogLeft() + 10, catalogY, layout.catalogWidth() - 20, 20)
                    .build());
            catalogY += 24;
        }

        final String repeatLabel = this.program.repeat() ? "Looping" : "Run once";
        this.addRenderableWidget(Button.builder(Component.literal(repeatLabel), ignored -> {
                    this.program.setRepeat(!this.program.repeat());
                    this.init();
                })
                .bounds(layout.detailLeft() + 12, layout.contentTop() + 16, 98, 20)
                .build());
        final EditBox repeatTicksBox = new EditBox(this.font, layout.detailLeft() + 118, layout.contentTop() + 16, 56, 20, Component.literal("Repeat ticks"));
        repeatTicksBox.setMaxLength(4);
        repeatTicksBox.setValue(String.valueOf(this.program.repeatTicks()));
        repeatTicksBox.setEditable(this.editable);
        repeatTicksBox.setResponder(this::applyRepeatTicks);
        this.addRenderableWidget(repeatTicksBox);

        this.addRenderableWidget(Button.builder(Component.literal("Up"), ignored -> this.moveSelectedBlock(-1))
                .bounds(layout.programLeft() + 10, layout.contentBottom() - 60, 52, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Down"), ignored -> this.moveSelectedBlock(1))
                .bounds(layout.programLeft() + 66, layout.contentBottom() - 60, 52, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Copy"), ignored -> this.duplicateSelectedBlock())
                .bounds(layout.programLeft() + 122, layout.contentBottom() - 60, 52, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> this.removeSelectedBlock())
                .bounds(layout.programLeft() + 178, layout.contentBottom() - 60, 62, 20)
                .build());

        if (this.blockListScroll > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("^"), ignored -> {
                        this.blockListScroll--;
                        this.init();
                    })
                    .bounds(layout.programRight() - 28, layout.contentTop() + 16, 18, 18)
                    .build());
        }
        if (this.blockListScroll + layout.visibleRows() < this.program.blocks().size()) {
            this.addRenderableWidget(Button.builder(Component.literal("v"), ignored -> {
                        this.blockListScroll++;
                        this.init();
                    })
                    .bounds(layout.programRight() - 28, layout.contentBottom() - 86, 18, 18)
                    .build());
        }

        int rowY = layout.contentTop() + 40;
        for (int row = 0; row < layout.visibleRows(); row++) {
            final int blockIndex = this.blockListScroll + row;
            if (blockIndex >= this.program.blocks().size()) {
                break;
            }
            final String prefix = blockIndex == this.selectedBlockIndex ? "> " : "  ";
            final String label = prefix + this.font.plainSubstrByWidth(this.program.blocks().get(blockIndex).summary(), layout.programWidth() - 38);
            final int targetIndex = blockIndex;
            this.addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
                        this.selectedBlockIndex = targetIndex;
                        this.init();
                    })
                    .bounds(layout.programLeft() + 10, rowY, layout.programWidth() - 20, 20)
                    .build());
            rowY += 22;
        }

        this.buildDetailWidgets(layout);

        final Button applyButton = this.addRenderableWidget(Button.builder(Component.literal("Use In Editor"), ignored -> this.applyProgram(false))
                .bounds(layout.panelLeft() + layout.panelWidth() - 254, layout.panelBottom() - 28, 118, 20)
                .build());
        final Button runButton = this.addRenderableWidget(Button.builder(Component.literal("Use And Run"), ignored -> this.applyProgram(true))
                .bounds(layout.panelLeft() + layout.panelWidth() - 132, layout.panelBottom() - 28, 118, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> this.onClose())
                .bounds(layout.panelLeft() + 12, layout.panelBottom() - 28, 90, 20)
                .build());
        applyButton.active = this.editable;
        runButton.active = this.editable;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.returnScreen != null) {
            this.minecraft.setScreen(this.returnScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        final Layout layout = this.layout();
        graphics.fill(0, 0, this.width, this.height, BACKDROP);
        this.drawPanel(graphics, layout.panelLeft(), layout.panelTop(), layout.panelWidth(), layout.panelHeight());
        this.drawPanel(graphics, layout.catalogLeft(), layout.contentTop(), layout.catalogWidth(), layout.contentHeight());
        this.drawPanel(graphics, layout.programLeft(), layout.contentTop(), layout.programWidth(), layout.contentHeight());
        this.drawPanel(graphics, layout.detailLeft(), layout.contentTop(), layout.detailWidth(), layout.contentHeight());

        graphics.drawString(this.font, this.title, layout.panelLeft() + 12, layout.panelTop() + 12, TITLE_COLOR, false);
        graphics.drawString(this.font,
                Component.literal("Build programs from blocks and generate Python automatically."),
                layout.panelLeft() + 12,
                layout.panelTop() + 26,
                INFO_COLOR,
                false);
        graphics.drawString(this.font,
                Component.literal(this.font.plainSubstrByWidth(this.headerStateLine(), layout.panelWidth() - 24)),
                layout.panelLeft() + 12,
                layout.panelTop() + 40,
                this.headerStateColor(),
                false);
            graphics.drawString(this.font,
                Component.literal("Templates"),
                layout.panelLeft() + 12,
                layout.panelTop() + 56,
                SECTION_COLOR,
                false);

        graphics.drawString(this.font, Component.literal("Blocks"), layout.catalogLeft() + 10, layout.contentTop() + 6, SECTION_COLOR, false);
        graphics.drawString(this.font, Component.literal("Program"), layout.programLeft() + 10, layout.contentTop() + 6, SECTION_COLOR, false);
        graphics.drawString(this.font, Component.literal("Details"), layout.detailLeft() + 12, layout.contentTop() + 6, SECTION_COLOR, false);

        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            graphics.drawString(this.font,
                    Component.literal("No blocks yet. Add one from the left column or load a template above."),
                    layout.detailLeft() + 12,
                    layout.contentTop() + 48,
                    INFO_COLOR,
                    false);
        } else {
            graphics.drawString(this.font,
                    Component.literal(selectedBlock.kind().label()),
                    layout.detailLeft() + 12,
                    layout.contentTop() + 48,
                    TITLE_COLOR,
                    false);
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(selectedBlock.kind().description(), layout.detailWidth() - 24)),
                    layout.detailLeft() + 12,
                    layout.contentTop() + 62,
                    INFO_COLOR,
                    false);
            this.renderDetailValues(graphics, layout, selectedBlock);
        }

        if (!this.editable) {
            graphics.drawString(this.font,
                    Component.literal("This screen is read-only because another editor currently owns the computer."),
                    layout.panelLeft() + 112,
                    layout.panelBottom() - 24,
                    WARN_COLOR,
                    false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void buildTemplateButtons(final Layout layout) {
        final int gap = 6;
        final int availableWidth = layout.panelWidth() - 24;
        final int buttonWidth = (availableWidth - gap * (TEMPLATE_COLUMNS - 1)) / TEMPLATE_COLUMNS;
        final int startX = layout.panelLeft() + 12;
        final int startY = layout.panelTop() + 68;
        int index = 0;
        for (final NoCodeBuilderTemplate template : NoCodeBuilderTemplate.values()) {
            final int row = index / TEMPLATE_COLUMNS;
            final int column = index % TEMPLATE_COLUMNS;
            final Button button = this.addRenderableWidget(Button.builder(Component.literal(template.buttonLabel()), ignored -> this.loadTemplate(template))
                    .bounds(startX + column * (buttonWidth + gap), startY + row * 24, buttonWidth, 20)
                    .build());
            button.active = this.editable;
            index++;
        }
    }

    private void buildDetailWidgets(final Layout layout) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            return;
        }

        final int detailLeft = layout.detailLeft() + 12;
        final int fieldTop = layout.contentTop() + 92;
        switch (selectedBlock.kind()) {
            case PRINT_TEXT -> {
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop, layout.detailWidth() - 24, 20, Component.literal("Message"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
            }
            case SHOW_WORLD, IF_WORLD_DAY_NEXT, IF_WORLD_NIGHT_NEXT, IF_WORLD_THUNDERING_NEXT,
                    IF_WORLD_DAWN_NEXT, IF_WORLD_EVENING_NEXT, LIST_DEVICES, ELSE_NEXT -> {
                // These block types only need descriptive text in the detail panel.
            }
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT, IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> {
                final int controlsTop = fieldTop + 18;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                        .bounds(detailLeft, controlsTop, 20, 20)
                        .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                        .bounds(detailLeft + 94, controlsTop, 20, 20)
                        .build());
            }
                case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT, IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT, IF_WORLD_MOON_PHASE_LESS_THAN_NEXT, IF_WORLD_MOON_PHASE_EQUALS_NEXT -> {
                final int controlsTop = fieldTop + 18;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                    .bounds(detailLeft, controlsTop, 20, 20)
                    .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                    .bounds(detailLeft + 94, controlsTop, 20, 20)
                    .build());
                }
            case IF_WORLD_TIME_WINDOW_NEXT -> {
                final EditBox startTickBox = new EditBox(this.font, detailLeft, fieldTop, layout.detailWidth() - 24, 20, Component.literal("Start tick"));
                startTickBox.setMaxLength(5);
                startTickBox.setValue(String.valueOf(this.clampWorldDayTime(selectedBlock.level())));
                startTickBox.setEditable(this.editable);
                startTickBox.setResponder(this::applySelectedWorldTimeWindowStart);
                this.addRenderableWidget(startTickBox);

                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 52, layout.detailWidth() - 24, 20, Component.literal("End tick"));
                this.messageBox.setMaxLength(5);
                this.messageBox.setValue(String.valueOf(this.worldTimeWindowEnd(selectedBlock)));
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(this::applySelectedWorldTimeWindowEnd);
                this.addRenderableWidget(this.messageBox);
            }
            case SHOW_CLOCK, SHOW_RAIN_SENSOR, IF_RAINING_NEXT, IF_DRY_NEXT, SHOW_DEVICE_STATE, SHOW_MATERIAL_IO -> this.buildDeviceSelectors(layout, selectedBlock, false, false);
            case COUNT_MATERIAL_ITEM -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, false);
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 84, layout.detailWidth() - 24, 20, Component.literal("Item id"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
            }
            case IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, false);
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 84, layout.detailWidth() - 24, 20, Component.literal("Item id"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
                final int controlsTop = fieldTop + 126;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                        .bounds(detailLeft, controlsTop, 20, 20)
                        .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                        .bounds(detailLeft + 94, controlsTop, 20, 20)
                        .build());
            }
            case MOVE_MATERIAL_ITEM -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, true);
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 124, layout.detailWidth() - 24, 20, Component.literal("Item id"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
                final int controlsTop = fieldTop + 166;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                        .bounds(detailLeft, controlsTop, 20, 20)
                        .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                        .bounds(detailLeft + 94, controlsTop, 20, 20)
                        .build());
            }
                case MOVE_MATERIAL_ITEM_TO -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, true, true);
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 164, layout.detailWidth() - 24, 20, Component.literal("Item id"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
                final int controlsTop = fieldTop + 206;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                    .bounds(detailLeft, controlsTop, 20, 20)
                    .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                    .bounds(detailLeft + 94, controlsTop, 20, 20)
                    .build());
                }
            case SHOW_MATERIAL_FLUIDS -> this.buildDeviceSelectors(layout, selectedBlock, true, false);
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> {
                    this.buildDeviceSelectors(layout, selectedBlock, true, false);
                    this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 84, layout.detailWidth() - 24, 20, Component.literal("Fluid id"));
                    this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                    this.messageBox.setValue(selectedBlock.text());
                    this.messageBox.setEditable(this.editable);
                    this.messageBox.setResponder(selectedBlock::setText);
                    this.addRenderableWidget(this.messageBox);
                    final int controlsTop = fieldTop + 126;
                    this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                        .bounds(detailLeft, controlsTop, 20, 20)
                        .build());
                    this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                        .bounds(detailLeft + 94, controlsTop, 20, 20)
                        .build());
                    }
                    case MOVE_MATERIAL_FLUID -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, true);
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 124, layout.detailWidth() - 24, 20, Component.literal("Fluid id"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
                final int controlsTop = fieldTop + 166;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                    .bounds(detailLeft, controlsTop, 20, 20)
                    .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                    .bounds(detailLeft + 94, controlsTop, 20, 20)
                    .build());
            }
            case MOVE_MATERIAL_FLUID_TO -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, true, true);
                this.messageBox = new EditBox(this.font, detailLeft, fieldTop + 164, layout.detailWidth() - 24, 20, Component.literal("Fluid id"));
                this.messageBox.setMaxLength(MAX_MESSAGE_LENGTH);
                this.messageBox.setValue(selectedBlock.text());
                this.messageBox.setEditable(this.editable);
                this.messageBox.setResponder(selectedBlock::setText);
                this.addRenderableWidget(this.messageBox);
                final int controlsTop = fieldTop + 206;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                    .bounds(detailLeft, controlsTop, 20, 20)
                    .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                    .bounds(detailLeft + 94, controlsTop, 20, 20)
                    .build());
            }
            case READ_REDSTONE -> this.buildDeviceSelectors(layout, selectedBlock, true, false);
            case IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, false);
                final int controlsTop = fieldTop + 78;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                        .bounds(detailLeft, controlsTop, 20, 20)
                        .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                        .bounds(detailLeft + 94, controlsTop, 20, 20)
                        .build());
            }
            case WRITE_REDSTONE -> {
                this.buildDeviceSelectors(layout, selectedBlock, true, false);
                final int controlsTop = fieldTop + 78;
                this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustSelectedLevel(-1))
                        .bounds(detailLeft, controlsTop, 20, 20)
                        .build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustSelectedLevel(1))
                        .bounds(detailLeft + 94, controlsTop, 20, 20)
                        .build());
            }
        }
    }

    private void buildDeviceSelectors(final Layout layout,
                                      final NoCodeBlock selectedBlock,
                                      final boolean includePrimarySide,
                                      final boolean includeTargetSide) {
        this.buildDeviceSelectors(layout, selectedBlock, includePrimarySide, includeTargetSide, false);
        }

        private void buildDeviceSelectors(final Layout layout,
                          final NoCodeBlock selectedBlock,
                          final boolean includePrimarySide,
                          final boolean includeTargetSide,
                          final boolean includeTargetDevice) {
        final int detailLeft = layout.detailLeft() + 12;
        final int fieldTop = layout.contentTop() + 92;
        this.addRenderableWidget(Button.builder(Component.literal("<"), ignored -> this.cycleSelectedDevice(-1))
                .bounds(detailLeft, fieldTop, 20, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), ignored -> this.cycleSelectedDevice(1))
                .bounds(detailLeft + 238, fieldTop, 20, 20)
                .build());
        int rowTop = fieldTop + 40;
        if (includePrimarySide) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), ignored -> this.cycleSelectedSide(-1))
                .bounds(detailLeft, rowTop, 20, 20)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), ignored -> this.cycleSelectedSide(1))
                .bounds(detailLeft + 238, rowTop, 20, 20)
                    .build());
            rowTop += 40;
        }
        if (includeTargetDevice && this.usesTargetDevice(selectedBlock == null ? null : selectedBlock.kind())) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), ignored -> this.cycleSelectedTargetDevice(-1))
                .bounds(detailLeft, rowTop, 20, 20)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), ignored -> this.cycleSelectedTargetDevice(1))
                .bounds(detailLeft + 238, rowTop, 20, 20)
                .build());
            rowTop += 40;
        }
        if (includeTargetSide && this.usesTargetSide(selectedBlock == null ? null : selectedBlock.kind())) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), ignored -> this.cycleSelectedTargetSide(-1))
                .bounds(detailLeft, rowTop, 20, 20)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), ignored -> this.cycleSelectedTargetSide(1))
                .bounds(detailLeft + 238, rowTop, 20, 20)
                    .build());
        }
    }

    private void renderDetailValues(final GuiGraphics graphics, final Layout layout, final NoCodeBlock selectedBlock) {
        final int detailLeft = layout.detailLeft() + 12;
        final int fieldTop = layout.contentTop() + 96;
        switch (selectedBlock.kind()) {
            case PRINT_TEXT -> graphics.drawString(this.font, Component.literal("Message"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
            case SHOW_WORLD -> {
                graphics.drawString(this.font, Component.literal("Shows dimension, day time and rain status."), detailLeft, fieldTop, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("When world data is unavailable, the program prints a short note instead."), detailLeft, fieldTop + 14, INFO_COLOR, false);
            }
            case IF_WORLD_DAY_NEXT -> {
                graphics.drawString(this.font, Component.literal("World state"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Runs the next block only while the world reports daytime."), detailLeft, fieldTop + 6, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Uses the global world day/night state, not a separate sensor block."), detailLeft, fieldTop + 22, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 36, WARN_COLOR, false);
            }
            case IF_WORLD_NIGHT_NEXT -> {
                graphics.drawString(this.font, Component.literal("World state"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Runs the next block only while the world reports nighttime."), detailLeft, fieldTop + 6, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Uses the global world day/night state, not a separate sensor block."), detailLeft, fieldTop + 22, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 36, WARN_COLOR, false);
            }
            case IF_WORLD_THUNDERING_NEXT -> {
                graphics.drawString(this.font, Component.literal("World state"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Runs the next block only while the world reports thunder."), detailLeft, fieldTop + 6, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("This is stricter than rain and only matches active thunderstorms."), detailLeft, fieldTop + 22, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 36, WARN_COLOR, false);
            }
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT, IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> {
                graphics.drawString(this.font, Component.literal("World rain"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.guardThresholdLabel(selectedBlock.kind())), detailLeft, fieldTop + 6, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 22, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.guardDescription(selectedBlock.kind())), detailLeft, fieldTop + 46, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal("World rain_level is scaled from 0 to 15."), detailLeft, fieldTop + 60, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 74, WARN_COLOR, false);
            }
            case IF_WORLD_TIME_WINDOW_NEXT -> {
                graphics.drawString(this.font, Component.literal("World time window"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Start tick"), detailLeft, fieldTop - 2, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(this.clampWorldDayTime(selectedBlock.level()))), detailLeft + 28, fieldTop + 18, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("End tick"), detailLeft, fieldTop + 50, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(this.worldTimeWindowEnd(selectedBlock))), detailLeft + 28, fieldTop + 68, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Runs the next block while world.day_time() stays inside this tick window."), detailLeft, fieldTop + 94, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal("Windows can wrap past midnight, for example 18000 to 2000."), detailLeft, fieldTop + 108, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 122, WARN_COLOR, false);
            }
            case IF_WORLD_DAWN_NEXT -> {
                graphics.drawString(this.font, Component.literal("Preset world window"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Runs the next block during the built-in dawn window 23000 to 1000."), detailLeft, fieldTop + 6, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Useful for sunrise actions without manually entering a wrap-around time range."), detailLeft, fieldTop + 22, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 36, WARN_COLOR, false);
            }
            case IF_WORLD_EVENING_NEXT -> {
                graphics.drawString(this.font, Component.literal("Preset world window"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Runs the next block during the built-in evening window 11000 to 14000."), detailLeft, fieldTop + 6, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Useful for sunset lighting and short evening-only routines."), detailLeft, fieldTop + 22, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 36, WARN_COLOR, false);
            }
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT, IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT, IF_WORLD_MOON_PHASE_LESS_THAN_NEXT, IF_WORLD_MOON_PHASE_EQUALS_NEXT -> {
                graphics.drawString(this.font, Component.literal("Moon phase"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.guardThresholdLabel(selectedBlock.kind())), detailLeft, fieldTop + 6, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 22, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.guardDescription(selectedBlock.kind())), detailLeft, fieldTop + 46, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal("Moon phases use Minecraft values 0 to 7, where 0 is full moon."), detailLeft, fieldTop + 60, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 74, WARN_COLOR, false);
            }
            case SHOW_CLOCK -> {
                graphics.drawString(this.font, Component.literal("Clock device"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Reads Minecraft day time, total game time and the local real time."), detailLeft, fieldTop + 36, INFO_COLOR, false);
            }
            case SHOW_RAIN_SENSOR -> {
                graphics.drawString(this.font, Component.literal("Rain sensor"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Shows whether rain currently reaches the selected sensor."), detailLeft, fieldTop + 36, INFO_COLOR, false);
            }
            case IF_RAINING_NEXT -> {
                graphics.drawString(this.font, Component.literal("Rain sensor"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Runs the next block only while this sensor reports rain."), detailLeft, fieldTop + 36, INFO_COLOR, false);
                    graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 50, WARN_COLOR, false);
            }
            case IF_DRY_NEXT -> {
                graphics.drawString(this.font, Component.literal("Rain sensor"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Runs the next block only while this sensor reports dry weather."), detailLeft, fieldTop + 36, INFO_COLOR, false);
                graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 50, WARN_COLOR, false);
            }
            case ELSE_NEXT -> {
                graphics.drawString(this.font, Component.literal("Otherwise"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(ELSE_BRANCH_TEXT), detailLeft, fieldTop + 6, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Use this after a condition block and its guarded action."), detailLeft, fieldTop + 22, INFO_COLOR, false);
            }
            case LIST_DEVICES -> {
                graphics.drawString(this.font, Component.literal("Lists all current device API names, types, scopes and remote policies."), detailLeft, fieldTop, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Useful as a first discovery program for new players."), detailLeft, fieldTop + 14, INFO_COLOR, false);
            }
            case SHOW_DEVICE_STATE -> {
                graphics.drawString(this.font, Component.literal("Device"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
            }
            case SHOW_MATERIAL_IO -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Shows current mode, enabled channels and storage capacities."), detailLeft, fieldTop + 36, INFO_COLOR, false);
            }
            case COUNT_MATERIAL_ITEM -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Item id"), detailLeft, fieldTop + 72, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Use full ids like " + DEFAULT_ITEM_ID + "."), detailLeft, fieldTop + 110, INFO_COLOR, false);
            }
            case IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Item id"), detailLeft, fieldTop + 72, SECTION_COLOR, false);
                    graphics.drawString(this.font, Component.literal(this.guardThresholdLabel(selectedBlock.kind())), detailLeft, fieldTop + 110, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(Math.max(1, selectedBlock.level()))), detailLeft + 32, fieldTop + 126, TEXT_COLOR, false);
                    graphics.drawString(this.font, Component.literal(this.guardDescription(selectedBlock.kind())), detailLeft, fieldTop + 150, INFO_COLOR, false);
                    graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 164, WARN_COLOR, false);
            }
            case MOVE_MATERIAL_ITEM -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Source side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Target side"), detailLeft, fieldTop + 68, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentTargetSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 86, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Item id"), detailLeft, fieldTop + 112, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Amount"), detailLeft, fieldTop + 150, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(Math.max(1, selectedBlock.level()))), detailLeft + 32, fieldTop + 166, TEXT_COLOR, false);
            }
            case MOVE_MATERIAL_ITEM_TO -> {
                graphics.drawString(this.font, Component.literal("Source Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Source side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Target Material I/O"), detailLeft, fieldTop + 68, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentTargetDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 86,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Target side"), detailLeft, fieldTop + 108, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentTargetSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 126, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Item id"), detailLeft, fieldTop + 152, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Amount"), detailLeft, fieldTop + 190, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(Math.max(1, selectedBlock.level()))), detailLeft + 32, fieldTop + 206, TEXT_COLOR, false);
            }
            case SHOW_MATERIAL_FLUIDS -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Shows all non-empty tanks on the selected side."), detailLeft, fieldTop + 72, INFO_COLOR, false);
            }
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Fluid id"), detailLeft, fieldTop + 72, SECTION_COLOR, false);
                    graphics.drawString(this.font, Component.literal(this.guardThresholdLabel(selectedBlock.kind())), detailLeft, fieldTop + 110, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 126, TEXT_COLOR, false);
                    graphics.drawString(this.font, Component.literal(this.guardDescription(selectedBlock.kind())), detailLeft, fieldTop + 150, INFO_COLOR, false);
                    graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 164, WARN_COLOR, false);
                    graphics.drawString(this.font, Component.literal("Use full ids like " + DEFAULT_FLUID_ID + ". Buttons change by " + FLUID_TRANSFER_STEP + " mB."), detailLeft, fieldTop + 178, INFO_COLOR, false);
            }
            case MOVE_MATERIAL_FLUID -> {
                graphics.drawString(this.font, Component.literal("Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Source side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Target side"), detailLeft, fieldTop + 68, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentTargetSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 86, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Fluid id"), detailLeft, fieldTop + 112, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Amount (mB)"), detailLeft, fieldTop + 150, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 166, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Use full ids like " + DEFAULT_FLUID_ID + ". Buttons change by " + FLUID_TRANSFER_STEP + " mB."), detailLeft, fieldTop + 190, INFO_COLOR, false);
            }
            case MOVE_MATERIAL_FLUID_TO -> {
                graphics.drawString(this.font, Component.literal("Source Material I/O"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Source side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Target Material I/O"), detailLeft, fieldTop + 68, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentTargetDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 86,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Target side"), detailLeft, fieldTop + 108, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentTargetSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 126, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Fluid id"), detailLeft, fieldTop + 152, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal("Amount (mB)"), detailLeft, fieldTop + 190, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 206, TEXT_COLOR, false);
            }
            case READ_REDSTONE -> {
                graphics.drawString(this.font, Component.literal("Redstone device"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("The generated script switches the device to input mode automatically."), detailLeft, fieldTop + 70, INFO_COLOR, false);
            }
            case IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> {
                graphics.drawString(this.font, Component.literal("Redstone device"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                    graphics.drawString(this.font, Component.literal(this.guardThresholdLabel(selectedBlock.kind())), detailLeft, fieldTop + 68, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 84, TEXT_COLOR, false);
                    graphics.drawString(this.font, Component.literal(this.guardDescription(selectedBlock.kind())), detailLeft, fieldTop + 108, INFO_COLOR, false);
                    graphics.drawString(this.font, Component.literal(GUARDED_NEXT_BLOCK_TEXT), detailLeft, fieldTop + 122, WARN_COLOR, false);
            }
            case WRITE_REDSTONE -> {
                graphics.drawString(this.font, Component.literal("Redstone device"), detailLeft, fieldTop - 12, SECTION_COLOR, false);
                graphics.drawString(this.font,
                        Component.literal(this.font.plainSubstrByWidth(this.currentDeviceLabel(selectedBlock), 206)),
                        detailLeft + 28,
                        fieldTop + 6,
                        TEXT_COLOR,
                        false);
                graphics.drawString(this.font, Component.literal("Side"), detailLeft, fieldTop + 28, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(this.currentSideLabel(selectedBlock)), detailLeft + 28, fieldTop + 46, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("Level"), detailLeft, fieldTop + 68, SECTION_COLOR, false);
                graphics.drawString(this.font, Component.literal(String.valueOf(selectedBlock.level())), detailLeft + 32, fieldTop + 84, TEXT_COLOR, false);
                graphics.drawString(this.font, Component.literal("The generated script switches the device to output mode automatically."), detailLeft, fieldTop + 108, INFO_COLOR, false);
            }
        }
    }

    private String guardThresholdLabel(final NoCodeBlockKind kind) {
        if (kind == null) {
            return "Threshold";
        }
        return switch (kind) {
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT -> "Minimum rain level";
            case IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT -> "Greater than rain level";
            case IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT -> "Lower than rain level";
            case IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> "Exact rain level";
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT -> "Minimum moon phase";
            case IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT -> "Greater than moon phase";
            case IF_WORLD_MOON_PHASE_LESS_THAN_NEXT -> "Lower than moon phase";
            case IF_WORLD_MOON_PHASE_EQUALS_NEXT -> "Exact moon phase";
            case IF_REDSTONE_AT_LEAST_NEXT -> "Minimum level";
            case IF_REDSTONE_GREATER_THAN_NEXT -> "Greater than level";
            case IF_REDSTONE_LESS_THAN_NEXT -> "Lower than level";
            case IF_REDSTONE_EQUALS_NEXT -> "Exact level";
            case IF_ITEM_COUNT_AT_LEAST_NEXT -> "Count threshold";
            case IF_ITEM_COUNT_GREATER_THAN_NEXT -> "Greater than count";
            case IF_ITEM_COUNT_LESS_THAN_NEXT -> "Lower than count";
            case IF_ITEM_COUNT_EQUALS_NEXT -> "Exact count";
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT -> "Amount threshold (mB)";
            case IF_FLUID_AMOUNT_GREATER_THAN_NEXT -> "Greater than amount (mB)";
            case IF_FLUID_AMOUNT_LESS_THAN_NEXT -> "Lower than amount (mB)";
            case IF_FLUID_AMOUNT_EQUALS_NEXT -> "Exact amount (mB)";
            default -> "Threshold";
        };
    }

    private String guardDescription(final NoCodeBlockKind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT -> "Runs the next block when the world rain level reaches at least this amount.";
            case IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT -> "Runs the next block when the world rain level is greater than this amount.";
            case IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT -> "Runs the next block when the world rain level is lower than this amount.";
            case IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> "Runs the next block when the world rain level exactly matches this amount.";
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT -> "Runs the next block when the world moon phase reaches at least this value.";
            case IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT -> "Runs the next block when the world moon phase is greater than this value.";
            case IF_WORLD_MOON_PHASE_LESS_THAN_NEXT -> "Runs the next block when the world moon phase is lower than this value.";
            case IF_WORLD_MOON_PHASE_EQUALS_NEXT -> "Runs the next block when the world moon phase exactly matches this value.";
            case IF_REDSTONE_AT_LEAST_NEXT -> "Runs the next block when the measured level is at least this amount.";
            case IF_REDSTONE_GREATER_THAN_NEXT -> "Runs the next block when the measured level is greater than this amount.";
            case IF_REDSTONE_LESS_THAN_NEXT -> "Runs the next block when the measured level is lower than this amount.";
            case IF_REDSTONE_EQUALS_NEXT -> "Runs the next block when the measured level exactly matches this amount.";
            case IF_ITEM_COUNT_AT_LEAST_NEXT -> "Runs the next block when the matching item count reaches at least this amount.";
            case IF_ITEM_COUNT_GREATER_THAN_NEXT -> "Runs the next block when the matching item count is greater than this amount.";
            case IF_ITEM_COUNT_LESS_THAN_NEXT -> "Runs the next block when the matching item count is lower than this amount.";
            case IF_ITEM_COUNT_EQUALS_NEXT -> "Runs the next block when the matching item count exactly matches this amount.";
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT -> "Runs the next block when the matching fluid amount reaches at least this amount.";
            case IF_FLUID_AMOUNT_GREATER_THAN_NEXT -> "Runs the next block when the matching fluid amount is greater than this amount.";
            case IF_FLUID_AMOUNT_LESS_THAN_NEXT -> "Runs the next block when the matching fluid amount is lower than this amount.";
            case IF_FLUID_AMOUNT_EQUALS_NEXT -> "Runs the next block when the matching fluid amount exactly matches this amount.";
            default -> "";
        };
    }

    private void addBlock(final NoCodeBlockKind kind) {
        final NoCodeBlock block = new NoCodeBlock(kind);
        if (this.requiresDevice(kind)) {
            block.setDeviceApiName(this.firstAvailableDevice(kind));
        }
        if (this.usesTargetDevice(kind)) {
            block.setTargetDeviceApiName(this.firstAvailableTargetDevice(block));
        }
        if (this.usesPrimarySide(kind)) {
            block.setSideName(this.firstAvailableSide(block));
        }
        if (this.usesTargetSide(kind)) {
            block.setTargetSideName(this.firstTargetSide(block));
        }
        this.program.blocks().add(block);
        this.selectedBlockIndex = this.program.blocks().size() - 1;
        this.ensureSelectedVisible();
        this.init();
    }

    private void moveSelectedBlock(final int delta) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            return;
        }
        final int targetIndex = this.selectedBlockIndex + delta;
        if (targetIndex < 0 || targetIndex >= this.program.blocks().size()) {
            return;
        }
        this.program.blocks().set(this.selectedBlockIndex, this.program.blocks().get(targetIndex));
        this.program.blocks().set(targetIndex, selectedBlock);
        this.selectedBlockIndex = targetIndex;
        this.ensureSelectedVisible();
        this.init();
    }

    private void duplicateSelectedBlock() {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            return;
        }
        this.program.blocks().add(this.selectedBlockIndex + 1, selectedBlock.copy());
        this.selectedBlockIndex++;
        this.ensureSelectedVisible();
        this.init();
    }

    private void removeSelectedBlock() {
        if (this.selectedBlockIndex < 0 || this.selectedBlockIndex >= this.program.blocks().size()) {
            return;
        }
        this.program.blocks().remove(this.selectedBlockIndex);
        if (this.program.blocks().isEmpty()) {
            this.selectedBlockIndex = -1;
            this.blockListScroll = 0;
        } else if (this.selectedBlockIndex >= this.program.blocks().size()) {
            this.selectedBlockIndex = this.program.blocks().size() - 1;
        }
        this.ensureSelectedVisible();
        this.init();
    }

    private void cycleSelectedDevice(final int delta) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            return;
        }
        final List<PythonPeripheralBinding> devices = this.availableDevices(selectedBlock);
        if (devices.isEmpty()) {
            selectedBlock.setDeviceApiName("");
            this.init();
            return;
        }

        int currentIndex = 0;
        for (int index = 0; index < devices.size(); index++) {
            if (devices.get(index).apiName().equals(selectedBlock.deviceApiName())) {
                currentIndex = index;
                break;
            }
        }
        final int targetIndex = Math.floorMod(currentIndex + delta, devices.size());
        selectedBlock.setDeviceApiName(devices.get(targetIndex).apiName());
        if (this.usesTargetDevice(selectedBlock.kind())
                && devices.stream().noneMatch(binding -> binding.apiName().equals(selectedBlock.targetDeviceApiName()))) {
            selectedBlock.setTargetDeviceApiName(this.firstAvailableTargetDevice(selectedBlock));
        }
        if (this.usesPrimarySide(selectedBlock.kind())) {
            selectedBlock.setSideName(this.firstAvailableSide(selectedBlock));
        }
        if (this.usesTargetSide(selectedBlock.kind())) {
            selectedBlock.setTargetSideName(this.firstTargetSide(selectedBlock));
        }
        this.ensureDistinctTargetSide(selectedBlock);
        this.init();
    }

    private void cycleSelectedTargetDevice(final int delta) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null || !this.usesTargetDevice(selectedBlock.kind())) {
            return;
        }
        final List<PythonPeripheralBinding> devices = this.availableDevices(selectedBlock);
        if (devices.isEmpty()) {
            selectedBlock.setTargetDeviceApiName("");
            this.init();
            return;
        }

        int currentIndex = 0;
        for (int index = 0; index < devices.size(); index++) {
            if (devices.get(index).apiName().equals(selectedBlock.targetDeviceApiName())) {
                currentIndex = index;
                break;
            }
        }
        final int targetIndex = Math.floorMod(currentIndex + delta, devices.size());
        selectedBlock.setTargetDeviceApiName(devices.get(targetIndex).apiName());
        if (this.usesTargetSide(selectedBlock.kind())) {
            selectedBlock.setTargetSideName(this.firstTargetSide(selectedBlock));
        }
        this.ensureDistinctTargetSide(selectedBlock);
        this.init();
    }

    private void cycleSelectedSide(final int delta) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            return;
        }
        final List<String> sideOptions = this.sourceSideOptions(selectedBlock);
        if (sideOptions.isEmpty()) {
            selectedBlock.setSideName(DEFAULT_SIDE);
            this.init();
            return;
        }
        int currentIndex = 0;
        for (int index = 0; index < sideOptions.size(); index++) {
            if (sideOptions.get(index).equals(selectedBlock.sideName())) {
                currentIndex = index;
                break;
            }
        }
        selectedBlock.setSideName(sideOptions.get(Math.floorMod(currentIndex + delta, sideOptions.size())));
        this.ensureDistinctTargetSide(selectedBlock);
        this.init();
    }

    private void cycleSelectedTargetSide(final int delta) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null || !this.usesTargetSide(selectedBlock.kind())) {
            return;
        }
        final List<String> sideOptions = this.targetSideOptions(selectedBlock);
        if (sideOptions.isEmpty()) {
            selectedBlock.setTargetSideName(DEFAULT_TARGET_SIDE);
            this.init();
            return;
        }
        int currentIndex = 0;
        for (int index = 0; index < sideOptions.size(); index++) {
            if (sideOptions.get(index).equals(selectedBlock.targetSideName())) {
                currentIndex = index;
                break;
            }
        }
        selectedBlock.setTargetSideName(sideOptions.get(Math.floorMod(currentIndex + delta, sideOptions.size())));
        this.ensureDistinctTargetSide(selectedBlock);
        this.init();
    }

    private void adjustSelectedLevel(final int delta) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null) {
            return;
        }
        selectedBlock.setLevel(selectedBlock.level() + delta * this.levelAdjustmentStep(selectedBlock.kind()));
        this.init();
    }

    private void applyRepeatTicks(final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            this.program.setRepeatTicks(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            this.program.setRepeatTicks(20);
        }
    }

    private void applySelectedWorldTimeWindowStart(final String value) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null || selectedBlock.kind() != NoCodeBlockKind.IF_WORLD_TIME_WINDOW_NEXT || value == null || value.isBlank()) {
            return;
        }
        try {
            selectedBlock.setLevel(this.clampWorldDayTime(Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            // Keep the previous value while the user edits the field.
        }
    }

    private void applySelectedWorldTimeWindowEnd(final String value) {
        final NoCodeBlock selectedBlock = this.selectedBlock();
        if (selectedBlock == null || selectedBlock.kind() != NoCodeBlockKind.IF_WORLD_TIME_WINDOW_NEXT || value == null || value.isBlank()) {
            return;
        }
        try {
            selectedBlock.setText(String.valueOf(this.clampWorldDayTime(Integer.parseInt(value.trim()))));
        } catch (NumberFormatException ignored) {
            // Keep the previous value while the user edits the field.
        }
    }

    private void applyProgram(final boolean executeAfterApply) {
        if (this.returnScreen == null) {
            return;
        }
        final boolean applied = this.returnScreen.applyNoCodeBuilderProgram(NoCodeScriptGenerator.generate(this.program), executeAfterApply);
        if (applied && this.minecraft != null) {
            this.minecraft.setScreen(this.returnScreen);
        }
    }

    private String headerStateLine() {
        if (this.parseError) {
            return "The current script contains broken builder metadata. Applying will replace it with a fresh no-code program.";
        }
        if (this.foundMetadata && this.metadataMatchesCurrentScript) {
            return "Builder state restored from the current script.";
        }
        if (this.foundMetadata) {
            return "The current script was edited manually after generation. Reapplying will replace those manual edits.";
        }
        if (this.currentScriptBlank) {
            return "No builder metadata found yet. Start with blocks and generate a first program.";
        }
        return "The current script was not created by the builder. Applying will replace the editor text with the generated no-code program.";
    }

    private int headerStateColor() {
        if (this.parseError || !this.metadataMatchesCurrentScript || (!this.foundMetadata && !this.currentScriptBlank)) {
            return WARN_COLOR;
        }
        return INFO_COLOR;
    }

    private void loadTemplate(final NoCodeBuilderTemplate template) {
        if (!this.editable || template == null) {
            return;
        }
        this.replaceProgram(template.create(this.executionContext));
    }

    private void replaceProgram(final NoCodeProgram replacement) {
        final NoCodeProgram nextProgram = replacement == null ? new NoCodeProgram() : replacement.copy();
        this.program.setRepeat(nextProgram.repeat());
        this.program.setRepeatTicks(nextProgram.repeatTicks());
        this.program.blocks().clear();
        this.program.blocks().addAll(nextProgram.blocks());
        this.selectedBlockIndex = this.program.blocks().isEmpty() ? -1 : 0;
        this.blockListScroll = 0;
        this.init();
    }

    private void clampSelection() {
        if (this.program.blocks().isEmpty()) {
            this.selectedBlockIndex = -1;
        } else {
            this.selectedBlockIndex = Math.max(0, Math.min(this.selectedBlockIndex, this.program.blocks().size() - 1));
        }
        this.blockListScroll = Math.max(0, Math.min(this.blockListScroll, Math.max(0, this.program.blocks().size() - 1)));
        this.ensureSelectedVisible();
    }

    private void ensureSelectedVisible() {
        final int visibleRows = this.layout().visibleRows();
        if (this.selectedBlockIndex < 0) {
            this.blockListScroll = 0;
            return;
        }
        if (this.selectedBlockIndex < this.blockListScroll) {
            this.blockListScroll = this.selectedBlockIndex;
        }
        final int maxVisibleIndex = this.blockListScroll + visibleRows - 1;
        if (this.selectedBlockIndex > maxVisibleIndex) {
            this.blockListScroll = this.selectedBlockIndex - visibleRows + 1;
        }
    }

    private NoCodeBlock selectedBlock() {
        if (this.selectedBlockIndex < 0 || this.selectedBlockIndex >= this.program.blocks().size()) {
            return null;
        }
        return this.program.blocks().get(this.selectedBlockIndex);
    }

    private String firstAvailableDevice(final NoCodeBlockKind kind) {
        final List<PythonPeripheralBinding> devices = this.availableDevices(kind);
        return devices.isEmpty() ? "" : devices.get(0).apiName();
    }

    private String firstAvailableSide(final NoCodeBlock block) {
        final List<String> sides = this.sourceSideOptions(block);
        return this.preferredSide(sides, DEFAULT_SIDE);
    }

    private String firstAlternativeSide(final NoCodeBlock block) {
        final List<String> sides = this.sourceSideOptions(block);
        if (sides.isEmpty()) {
            return DEFAULT_TARGET_SIDE;
        }
        if (sides.contains(DEFAULT_TARGET_SIDE) && !DEFAULT_TARGET_SIDE.equals(block.sideName())) {
            return DEFAULT_TARGET_SIDE;
        }
        for (final String side : sides) {
            if (!side.equals(block.sideName())) {
                return side;
            }
        }
        return sides.get(0);
    }

    private String firstTargetSide(final NoCodeBlock block) {
        if (block == null) {
            return DEFAULT_TARGET_SIDE;
        }
        if (!this.usesTargetDevice(block.kind())) {
            return this.firstAlternativeSide(block);
        }
        return this.preferredSide(this.targetSideOptions(block), DEFAULT_TARGET_SIDE);
    }

    private String firstAvailableTargetDevice(final NoCodeBlock block) {
        final List<PythonPeripheralBinding> devices = this.availableDevices(block);
        if (devices.isEmpty()) {
            return "";
        }
        final String sourceDevice = block == null ? "" : block.deviceApiName();
        for (final PythonPeripheralBinding binding : devices) {
            if (!binding.apiName().equals(sourceDevice)) {
                return binding.apiName();
            }
        }
        return devices.getFirst().apiName();
    }

    private String currentDeviceLabel(final NoCodeBlock block) {
        final List<PythonPeripheralBinding> devices = this.availableDevices(block);
        final String requiredType = this.requiredDeviceType(block == null ? null : block.kind());
        if (devices.isEmpty()) {
            return switch (requiredType) {
                case "clock" -> "No visible clock";
                case "rain_sensor" -> "No visible rain sensor";
                case "material_io" -> "No visible material I/O";
                case "redstone_io" -> "No visible redstone device";
                default -> "No visible device";
            };
        }
        for (final PythonPeripheralBinding binding : devices) {
            if (binding.apiName().equals(block.deviceApiName())) {
                return binding.apiName() + " [" + binding.type() + "]";
            }
        }
        final PythonPeripheralBinding first = devices.get(0);
        return first.apiName() + " [" + first.type() + "]";
    }

    private String currentSideLabel(final NoCodeBlock block) {
        return this.currentResolvedSide(this.sourceSideOptions(block), block == null ? DEFAULT_SIDE : block.sideName(), DEFAULT_SIDE);
    }

    private String currentTargetDeviceLabel(final NoCodeBlock block) {
        final List<PythonPeripheralBinding> devices = this.availableDevices(block);
        if (devices.isEmpty()) {
            return "No visible target material I/O";
        }
        for (final PythonPeripheralBinding binding : devices) {
            if (binding.apiName().equals(block.targetDeviceApiName())) {
                return binding.apiName() + " [" + binding.type() + "]";
            }
        }
        final PythonPeripheralBinding first = devices.getFirst();
        return first.apiName() + " [" + first.type() + "]";
    }

    private String currentTargetSideLabel(final NoCodeBlock block) {
        return this.currentResolvedSide(this.targetSideOptions(block), block == null ? DEFAULT_TARGET_SIDE : block.targetSideName(), DEFAULT_TARGET_SIDE);
    }

    private String currentResolvedSide(final List<String> sideOptions, final String selectedSide, final String fallbackSide) {
        if (sideOptions.isEmpty()) {
            return fallbackSide;
        }
        if (sideOptions.contains(selectedSide)) {
            return selectedSide;
        }
        return sideOptions.get(0);
    }

    private String preferredSide(final List<String> sideOptions, final String preferredDefault) {
        if (sideOptions == null || sideOptions.isEmpty()) {
            return preferredDefault;
        }
        if (sideOptions.contains(preferredDefault)) {
            return preferredDefault;
        }
        return sideOptions.get(0);
    }

    private List<PythonPeripheralBinding> availableDevices(final NoCodeBlock block) {
        return this.availableDevices(block == null ? null : block.kind());
    }

    private List<PythonPeripheralBinding> availableDevices(final NoCodeBlockKind kind) {
        return this.availableDevices(this.requiredDeviceType(kind));
    }

    private List<PythonPeripheralBinding> availableDevices(final String requiredType) {
        final ArrayList<PythonPeripheralBinding> devices = new ArrayList<>();
        for (final PythonPeripheralBinding binding : this.executionContext.peripherals()) {
            if (requiredType == null || requiredType.equals(binding.type())) {
                devices.add(binding);
            }
        }
        return devices;
    }

    private String requiredDeviceType(final NoCodeBlockKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case SHOW_CLOCK -> "clock";
            case SHOW_RAIN_SENSOR, IF_RAINING_NEXT, IF_DRY_NEXT -> "rain_sensor";
            case SHOW_MATERIAL_IO, COUNT_MATERIAL_ITEM, MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, SHOW_MATERIAL_FLUIDS, MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO,
                    IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT,
                    IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> "material_io";
            case READ_REDSTONE, WRITE_REDSTONE, IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> "redstone_io";
            default -> null;
        };
    }

    private boolean requiresDevice(final NoCodeBlockKind kind) {
        return switch (kind) {
            case SHOW_CLOCK, SHOW_RAIN_SENSOR, IF_RAINING_NEXT, IF_DRY_NEXT, SHOW_DEVICE_STATE, SHOW_MATERIAL_IO,
                    COUNT_MATERIAL_ITEM, MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, SHOW_MATERIAL_FLUIDS, MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO,
                    IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT,
                    IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT,
                    READ_REDSTONE, WRITE_REDSTONE, IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> true;
            default -> false;
        };
    }

    private boolean usesPrimarySide(final NoCodeBlockKind kind) {
        return switch (kind) {
            case COUNT_MATERIAL_ITEM, MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, SHOW_MATERIAL_FLUIDS, MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO,
                    IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT,
                    IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT,
                    READ_REDSTONE, WRITE_REDSTONE, IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> true;
            default -> false;
        };
    }

    private boolean usesTargetSide(final NoCodeBlockKind kind) {
        return switch (kind) {
            case MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO -> true;
            default -> false;
        };
    }

    private boolean usesTargetDevice(final NoCodeBlockKind kind) {
        return switch (kind) {
            case MOVE_MATERIAL_ITEM_TO, MOVE_MATERIAL_FLUID_TO -> true;
            default -> false;
        };
    }

    private int levelAdjustmentStep(final NoCodeBlockKind kind) {
        return switch (kind) {
            case MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO, IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> FLUID_TRANSFER_STEP;
            default -> 1;
        };
    }

    private int worldTimeWindowEnd(final NoCodeBlock block) {
        if (block == null) {
            return DEFAULT_WORLD_TIME_WINDOW_END;
        }
        final String rawValue = block.text();
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_WORLD_TIME_WINDOW_END;
        }
        try {
            return this.clampWorldDayTime(Integer.parseInt(rawValue.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_WORLD_TIME_WINDOW_END;
        }
    }

    private int clampWorldDayTime(final int value) {
        return Math.max(0, Math.min(MAX_WORLD_DAY_TIME, value));
    }

    private void ensureDistinctTargetSide(final NoCodeBlock block) {
        if (block == null || !this.usesTargetSide(block.kind())) {
            return;
        }
        final boolean sameDeviceEndpoint = !this.usesTargetDevice(block.kind()) || block.deviceApiName().equals(block.targetDeviceApiName());
        if (!sameDeviceEndpoint) {
            return;
        }
        final List<String> sides = this.targetSideOptions(block);
        if (sides.size() < 2) {
            return;
        }
        if (block.sideName().equals(block.targetSideName())) {
            block.setTargetSideName(this.firstAlternativeSide(block));
        }
    }

    private List<String> sourceSideOptions(final NoCodeBlock block) {
        return this.sideOptionsForDevice(block == null ? "" : block.deviceApiName());
    }

    private List<String> targetSideOptions(final NoCodeBlock block) {
        if (block == null) {
            return this.sideOptionsForDevice("");
        }
        final String apiName = this.usesTargetDevice(block.kind()) ? block.targetDeviceApiName() : block.deviceApiName();
        return this.sideOptionsForDevice(apiName);
    }

    private List<String> sideOptionsForDevice(final String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return List.of(DEFAULT_SIDE, "south", "west", "east", "up", "down");
        }
        for (final PythonPeripheralBinding binding : this.executionContext.peripherals()) {
            if (binding.apiName().equals(apiName)) {
                return binding.sideNames();
            }
        }
        return List.of(DEFAULT_SIDE, "south", "west", "east", "up", "down");
    }

    private Layout layout() {
        final int margin = 18;
        final int panelLeft = margin;
        final int panelTop = margin;
        final int panelWidth = this.width - margin * 2;
        final int panelHeight = this.height - margin * 2;
        final int headerHeight = 86 + this.templateRowCount() * 24;
        final int footerHeight = 38;
        final int contentTop = panelTop + headerHeight;
        final int contentHeight = panelHeight - headerHeight - footerHeight;
        final int catalogWidth = 166;
        final int programWidth = 258;
        final int gap = 8;
        final int catalogLeft = panelLeft + 8;
        final int programLeft = catalogLeft + catalogWidth + gap;
        final int detailLeft = programLeft + programWidth + gap;
        final int detailWidth = panelLeft + panelWidth - detailLeft - 8;
        return new Layout(panelLeft, panelTop, panelWidth, panelHeight, contentTop, contentHeight,
                catalogLeft, catalogWidth, programLeft, programWidth, detailLeft, detailWidth,
                Math.min(MAX_VISIBLE_ROWS, Math.max(4, (contentHeight - 104) / 22)));
    }

    private int templateRowCount() {
        return Math.max(1, (NoCodeBuilderTemplate.values().length + TEMPLATE_COLUMNS - 1) / TEMPLATE_COLUMNS);
    }

    private void drawPanel(final GuiGraphics graphics, final int left, final int top, final int width, final int height) {
        graphics.fill(left, top, left + width, top + height, PANEL_BACKGROUND);
        graphics.hLine(left, left + width - 1, top, PANEL_BORDER);
        graphics.hLine(left, left + width - 1, top + height - 1, PANEL_BORDER);
        graphics.vLine(left, top, top + height - 1, PANEL_BORDER);
        graphics.vLine(left + width - 1, top, top + height - 1, PANEL_BORDER);
    }

    private record Layout(int panelLeft,
                          int panelTop,
                          int panelWidth,
                          int panelHeight,
                          int contentTop,
                          int contentHeight,
                          int catalogLeft,
                          int catalogWidth,
                          int programLeft,
                          int programWidth,
                          int detailLeft,
                          int detailWidth,
                          int visibleRows) {
        private int panelBottom() {
            return this.panelTop + this.panelHeight;
        }

        private int contentBottom() {
            return this.contentTop + this.contentHeight;
        }

        private int programRight() {
            return this.programLeft + this.programWidth;
        }
    }
}