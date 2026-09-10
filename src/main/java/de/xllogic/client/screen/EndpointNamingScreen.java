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

        final boolean compact = this.payload.supportsSideNaming() && this.height < 320;
        final int panelWidth = compact ? 340 : (LABEL_WIDTH + FIELD_WIDTH + 42);
        final int panelHeight = this.payload.supportsSideNaming() ? (compact ? 212 : 292) : 142;
        final int left = (this.width - panelWidth) / 2;
        final int top = Math.max(8, (this.height - panelHeight) / 2);

        if (compact) {
            final int epFieldX = left + 68;
            final int epFieldWidth = panelWidth - 84;
            this.endpointNameBox = new EditBox(this.font, epFieldX, top + 56, epFieldWidth, FIELD_HEIGHT, Component.literal("Endpoint name"));
            this.endpointNameBox.setMaxLength(MAX_NAME_LENGTH);
            this.endpointNameBox.setValue(endpointNameValue);
            this.addRenderableWidget(this.endpointNameBox);

            final Direction[] col1 = { Direction.DOWN, Direction.UP, Direction.NORTH };
            final Direction[] col2 = { Direction.SOUTH, Direction.WEST, Direction.EAST };
            final int col1X = left + 56;
            final int col2X = left + 224;
            final int colFieldWidth = 98;

            int sideRowY = top + 56 + ROW_SPACING;
            for (int i = 0; i < 3; i++) {
                final Direction d1 = col1[i];
                final EditBox box1 = new EditBox(this.font, col1X, sideRowY, colFieldWidth, FIELD_HEIGHT, Component.literal(d1.getSerializedName() + " alias"));
                box1.setMaxLength(MAX_NAME_LENGTH);
                box1.setValue(sideValues.getOrDefault(d1, ""));
                this.sideBoxes.put(d1, box1);
                this.addRenderableWidget(box1);

                final Direction d2 = col2[i];
                final EditBox box2 = new EditBox(this.font, col2X, sideRowY, colFieldWidth, FIELD_HEIGHT, Component.literal(d2.getSerializedName() + " alias"));
                box2.setMaxLength(MAX_NAME_LENGTH);
                box2.setValue(sideValues.getOrDefault(d2, ""));
                this.sideBoxes.put(d2, box2);
                this.addRenderableWidget(box2);

                sideRowY += ROW_SPACING;
            }
        } else {
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
        }

        final int buttonY = top + panelHeight - 28;
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
        final boolean compact = this.payload.supportsSideNaming() && this.height < 320;
        final int panelWidth = compact ? 340 : (LABEL_WIDTH + FIELD_WIDTH + 42);
        final int panelHeight = this.payload.supportsSideNaming() ? (compact ? 212 : 292) : 142;
        final int left = (this.width - panelWidth) / 2;
        final int top = Math.max(8, (this.height - panelHeight) / 2);

        graphics.fill(0, 0, this.width, this.height, 0xB0080B10);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BACKGROUND);
        graphics.hLine(left, left + panelWidth - 1, top, PANEL_BORDER);
        graphics.hLine(left, left + panelWidth - 1, top + panelHeight - 1, PANEL_BORDER);
        graphics.vLine(left, top, top + panelHeight - 1, PANEL_BORDER);
        graphics.vLine(left + panelWidth - 1, top, top + panelHeight - 1, PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TITLE_COLOR);
        graphics.drawCenteredString(this.font, formatEndpointType(this.payload.endpointType()), this.width / 2, top + 26, INFO_COLOR);

        final String displaySummary = extractSummary(this.payload.summary(), panelWidth - 24);
        graphics.drawCenteredString(this.font, displaySummary, this.width / 2, top + 38, INFO_COLOR);

        final int labelVerticalOffset = (FIELD_HEIGHT - 8) / 2;
        if (compact) {
            final int epFieldX = left + 68;
            final Component endpointLabel = Component.literal("Endpoint");
            graphics.drawString(this.font, endpointLabel, epFieldX - 8 - this.font.width(endpointLabel), top + 56 + labelVerticalOffset, TITLE_COLOR, false);

            final Direction[] col1 = { Direction.DOWN, Direction.UP, Direction.NORTH };
            final Direction[] col2 = { Direction.SOUTH, Direction.WEST, Direction.EAST };
            final int col1X = left + 56;
            final int col2X = left + 224;
            int sideRowY = top + 56 + ROW_SPACING;
            for (int i = 0; i < 3; i++) {
                final Component l1 = Component.literal(capitalize(col1[i].getSerializedName()));
                graphics.drawString(this.font, l1, col1X - 6 - this.font.width(l1), sideRowY + labelVerticalOffset, TITLE_COLOR, false);

                final Component l2 = Component.literal(capitalize(col2[i].getSerializedName()));
                graphics.drawString(this.font, l2, col2X - 6 - this.font.width(l2), sideRowY + labelVerticalOffset, TITLE_COLOR, false);

                sideRowY += ROW_SPACING;
            }
            graphics.drawCenteredString(this.font, "Blank keeps canonical side names.", this.width / 2, top + panelHeight - 50, INFO_COLOR);
            graphics.drawCenteredString(this.font, "Saved names are normalized to lowercase_with_underscores.", this.width / 2, top + panelHeight - 40, INFO_COLOR);
        } else {
            final int fieldX = left + LABEL_WIDTH + 20;
            final int labelRight = fieldX - 8;
            int rowY = top + 58;
            final Component endpointLabel = Component.literal("Endpoint");
            graphics.drawString(this.font, endpointLabel, labelRight - this.font.width(endpointLabel), rowY + labelVerticalOffset, TITLE_COLOR, false);
            rowY += ROW_SPACING;
            if (this.payload.supportsSideNaming()) {
                for (final Direction direction : Direction.values()) {
                    final Component sideLabel = Component.literal(capitalize(direction.getSerializedName()) + " alias");
                    graphics.drawString(this.font, sideLabel, labelRight - this.font.width(sideLabel), rowY + labelVerticalOffset, TITLE_COLOR, false);
                    rowY += ROW_SPACING;
                }
                graphics.drawCenteredString(this.font, "Blank keeps canonical side names.", this.width / 2, top + panelHeight - 54, INFO_COLOR);
                graphics.drawCenteredString(this.font, "Saved names are normalized to lowercase_with_underscores.", this.width / 2, top + panelHeight - 42, INFO_COLOR);
            } else {
                graphics.drawCenteredString(this.font, "Saved names are normalized to lowercase_with_underscores.", this.width / 2, top + panelHeight - 48, INFO_COLOR);
            }
        }

        // Skip Screen.render here; it would repaint the vanilla blurred/menu background over this custom UI.
        // Instead, only render the widgets (EditBoxes, Buttons).
        for (final net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
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

    private static String formatEndpointType(final String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "";
        }
        final String[] parts = rawType.split("_");
        final StringBuilder result = new StringBuilder();
        for (final String part : parts) {
            if (result.length() > 0) {
                result.append(' ');
            }
            if (part.equalsIgnoreCase("io")) {
                result.append("I/O");
            } else if (part.equalsIgnoreCase("cpu")) {
                result.append("CPU");
            } else if (part.equalsIgnoreCase("xlapi")) {
                result.append("XLAPI");
            } else {
                result.append(capitalize(part));
            }
        }
        return result.toString();
    }

    private static String extractSummary(final String rawSummary, final int maxWidth) {
        if (rawSummary == null || rawSummary.isBlank()) {
            return "";
        }
        final String[] segments = rawSummary.split(" \\| ");
        if (segments.length <= 1) {
            return rawSummary.trim();
        }
        // Skip the first segment ("Endpoint: name") and show the key status info
        return segments[1].trim();
    }
}