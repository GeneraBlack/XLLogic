package de.xllogic.client.screen;

import de.xllogic.common.network.payload.OpenEndpointNamingPayload;
import de.xllogic.common.network.payload.SaveEndpointNamingPayload;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EndpointNamingScreen extends Screen {
    private static final int FIELD_WIDTH = 180;
    private static final int FIELD_HEIGHT = 20;
    private static final int LABEL_WIDTH = 98;
    private static final int ROW_SPACING = 24;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int PANEL_BACKGROUND = 0xE60B0F14;
    private static final int PANEL_BORDER = 0xAA2F3A4A;
    private static final int TITLE_COLOR = 0xFFE6EDF3;
    private static final int INFO_COLOR = 0xFF8B949E;

    private OpenEndpointNamingPayload payload;
    private EditBox endpointNameBox;
    private final Map<Direction, EditBox> sideBoxes = new EnumMap<>(Direction.class);

    public EndpointNamingScreen(final OpenEndpointNamingPayload payload) {
        super(Component.literal("Endpoint Config"));
        this.payload = payload;
    }

    public boolean isBoundTo(final BlockPos pos) {
        return this.payload.endpointPos().equals(pos);
    }

    public void applyPayload(final OpenEndpointNamingPayload payload) {
        this.payload = payload;
        if (this.endpointNameBox != null) {
            this.endpointNameBox.setValue(payload.endpointName());
        }
        for (final Direction direction : Direction.values()) {
            final EditBox box = this.sideBoxes.get(direction);
            if (box != null) {
                box.setValue(payload.sideAlias(direction));
            }
        }
    }

    @Override
    protected void init() {
        final String endpointNameValue = this.endpointNameBox == null ? this.payload.endpointName() : this.endpointNameBox.getValue();
        final Map<Direction, String> sideValues = new EnumMap<>(Direction.class);
        for (final Direction direction : Direction.values()) {
            final EditBox existingBox = this.sideBoxes.get(direction);
            sideValues.put(direction, existingBox == null ? this.payload.sideAlias(direction) : existingBox.getValue());
        }

        this.clearWidgets();
        this.sideBoxes.clear();

        final int panelWidth = LABEL_WIDTH + FIELD_WIDTH + 42;
        final int panelHeight = this.payload.supportsSideNaming() ? 278 : 142;
        final int left = (this.width - panelWidth) / 2;
        final int top = Math.max(18, (this.height - panelHeight) / 2);
        final int fieldX = left + LABEL_WIDTH + 20;

        int rowY = top + 58;
        this.endpointNameBox = new EditBox(this.font, fieldX, rowY, FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Endpoint name"));
        this.endpointNameBox.setMaxLength(MAX_NAME_LENGTH);
        this.endpointNameBox.setValue(endpointNameValue);
        this.addRenderableWidget(this.endpointNameBox);
        rowY += ROW_SPACING;

        if (this.payload.supportsSideNaming()) {
            for (final Direction direction : Direction.values()) {
                final EditBox box = new EditBox(this.font, fieldX, rowY, FIELD_WIDTH, FIELD_HEIGHT, Component.literal(direction.getSerializedName() + " alias"));
                box.setMaxLength(MAX_NAME_LENGTH);
                box.setValue(sideValues.getOrDefault(direction, ""));
                this.sideBoxes.put(direction, box);
                this.addRenderableWidget(box);
                rowY += ROW_SPACING;
            }
        }

        final int buttonY = top + panelHeight - 30;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
                .bounds(left + 20, buttonY, 96, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(left + panelWidth - 116, buttonY, 96, 20)
                .build());
        this.setInitialFocus(this.endpointNameBox);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            this.save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        final int panelWidth = LABEL_WIDTH + FIELD_WIDTH + 42;
        final int panelHeight = this.payload.supportsSideNaming() ? 278 : 142;
        final int left = (this.width - panelWidth) / 2;
        final int top = Math.max(18, (this.height - panelHeight) / 2);

        graphics.fill(0, 0, this.width, this.height, 0xB0080B10);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BACKGROUND);
        graphics.hLine(left, left + panelWidth - 1, top, PANEL_BORDER);
        graphics.hLine(left, left + panelWidth - 1, top + panelHeight - 1, PANEL_BORDER);
        graphics.vLine(left, top, top + panelHeight - 1, PANEL_BORDER);
        graphics.vLine(left + panelWidth - 1, top, top + panelHeight - 1, PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TITLE_COLOR);
        graphics.drawCenteredString(this.font, this.payload.endpointType(), this.width / 2, top + 28, INFO_COLOR);
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.payload.summary(), panelWidth - 20), this.width / 2, top + 40, INFO_COLOR);

        final int labelX = left + 16;
        int rowY = top + 64;
        graphics.drawString(this.font, Component.literal("Endpoint"), labelX, rowY + 6, TITLE_COLOR, false);
        rowY += ROW_SPACING;
        if (this.payload.supportsSideNaming()) {
            for (final Direction direction : Direction.values()) {
                graphics.drawString(this.font, Component.literal(capitalize(direction.getSerializedName()) + " alias"), labelX, rowY + 6, TITLE_COLOR, false);
                rowY += ROW_SPACING;
            }
            graphics.drawCenteredString(this.font, "Blank keeps the canonical side name. Saved names are normalized to lowercase_with_underscores.", this.width / 2, top + panelHeight - 48, INFO_COLOR);
        } else {
            graphics.drawCenteredString(this.font, "Saved names are normalized to lowercase_with_underscores.", this.width / 2, top + panelHeight - 48, INFO_COLOR);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        PacketDistributor.sendToServer(new SaveEndpointNamingPayload(
                this.payload.endpointPos(),
                this.endpointNameBox.getValue(),
                value(Direction.DOWN),
                value(Direction.UP),
                value(Direction.NORTH),
                value(Direction.SOUTH),
                value(Direction.WEST),
                value(Direction.EAST)
        ));
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private String value(final Direction direction) {
        final EditBox box = this.sideBoxes.get(direction);
        return box == null ? "" : box.getValue();
    }

    private static String capitalize(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}