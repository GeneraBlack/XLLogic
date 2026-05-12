package de.xllogic.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.xllogic.client.screen.PythonComputerScreen;
import de.xllogic.common.block.ScreenBlock;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.ScreenBlockEntity;
import de.xllogic.common.screen.ScreenLayoutMetrics;
import de.xllogic.runtime.ComputerOutputEntry;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public final class ScreenBlockEntityRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
    private static final String SCREEN_TITLE_PREFIX = "Screen ";
    private static final float SCREEN_SCALE = ScreenLayoutMetrics.SCREEN_SCALE;
    private static final float PANEL_Z_OFFSET = 0.448F;
    private static final float PANEL_Y_OFFSET = 0.5F;
    private static final float SURFACE_FILL_Z = 0.0F;
    private static final float SURFACE_BORDER_Z = 0.012F;
    private static final float CONTENT_FILL_Z = 0.028F;
    private static final float CONTENT_BORDER_Z = 0.04F;
    private static final float ACCENT_Z = 0.052F;
    private static final float FOOTER_Z = 0.058F;
    private static final float LED_BEZEL_Z = 0.07F;
    private static final float LED_CORE_Z = 0.082F;
    private static final float TEXT_Z_OFFSET = 0.12F;
    private static final float CONTENT_MARGIN = ScreenLayoutMetrics.CONTENT_MARGIN_UNITS;
    private static final float ENTRY_GAP = 4.0F;
    private static final float PAGE_FOOTER_HEIGHT = 12.0F;
    private static final float BOX_BORDER = 1.0F;
    private static final float STATUS_LED_SIZE = 10.0F;
    private static final float STATUS_LED_INSET = 2.0F;
    private static final float STATUS_LED_RIGHT_MARGIN = 3.0F;
    private static final float STATUS_LED_TOP_MARGIN = 2.0F;
    private static final long RUNNING_PULSE_PERIOD_MILLIS = 1400L;
    private static final long ERROR_BLINK_PERIOD_MILLIS = 180L;
    private static final int SCREEN_BACKGROUND = 0xCC0B0F14;
    private static final int SCREEN_BORDER = 0xAA2F3A4A;
    private static final int CARD_BACKGROUND = 0x5522384D;
    private static final int TABLE_HEADER_BACKGROUND = 0x22334A60;
    private static final int TITLE_COLOR = 0xFFE6EDF3;
    private static final int INFO_COLOR = 0xFFD8E0E8;
    private static final int DIM_COLOR = 0xFF8B949E;
    private static final int LED_BEZEL_COLOR = 0xFF1B1F24;
    private static final int WARN_COLOR = 0xFFF2CC60;
    private static final int OK_COLOR = 0xFF56D364;
    private static final int ERROR_COLOR = 0xFFF85149;
    private static final int PLAN_COLOR = 0xFF79C0FF;
    private static final int GLYPH_BACKGROUND = 0x00000000;

    public ScreenBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final ScreenBlockEntity screen, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("client.render.screenBlockEntity");
        try {
            final Minecraft minecraft = Minecraft.getInstance();
            final Font font = minecraft.font;
            if (font == null) {
                return;
            }
            if (minecraft.screen instanceof PythonComputerScreen) {
                return;
            }

            if (!screen.isController()) {
                if (screen.hasLoadedControllerScreen()) {
                    return;
                }
                this.renderFollowerFallback(screen, poseStack, bufferSource, packedLight, packedOverlay, font);
                return;
            }

            poseStack.pushPose();
            this.applyScreenTransform(screen, poseStack);
            poseStack.scale(SCREEN_SCALE, -SCREEN_SCALE, SCREEN_SCALE);

            final ScreenSurface surface = this.surface(screen, font);
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final RenderContext context = new RenderContext(font, matrix, pose, bufferSource, packedLight, packedOverlay);

            this.renderSurfaceBox(context, surface.left(), surface.top(), surface.right(), surface.bottom(), SCREEN_BACKGROUND, SCREEN_BORDER);
            this.renderStatusLed(screen, surface, poseStack, bufferSource, packedOverlay);

            if (!screen.hasLinkedComputer()) {
                this.renderFallback(surface, context,
                    SCREEN_TITLE_PREFIX + screen.getEndpointName(), "UNLINKED", "Run discovery from a computer over connected network cables.");
                poseStack.popPose();
                return;
            }

            final ComputerBlockEntity linkedComputer = screen.resolveLinkedComputer();
            if (linkedComputer == null) {
                this.renderFallback(surface, context,
                        "Computer " + screen.getLinkedComputerPos().toShortString(), "OFFLINE", "Target computer is not available.");
                poseStack.popPose();
                return;
            }

            this.renderRuntime(surface, screen, linkedComputer.getRuntimeState(), context);
            poseStack.popPose();
        } finally {
            XLRuntimeDebugger.endSection("client.render.screenBlockEntity", debugStartedAt);
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private void renderRuntime(final ScreenSurface surface, final ScreenBlockEntity screen, final ComputerRuntimeSnapshot runtimeState, final RenderContext context) {
        final Font font = context.font();
        final List<ComputerOutputEntry> displayEntries = screen.resolveDisplayOutputEntries(runtimeState);
        float y = surface.top() + CONTENT_MARGIN;
        y = this.drawCenteredText(context, SCREEN_TITLE_PREFIX + screen.getEndpointName(), y, TITLE_COLOR);
        if (!screen.isSoloScreen()) {
            y = this.drawCenteredText(context, "Panel " + screen.multiblockSummary(), y, PLAN_COLOR);
        }
        y = this.drawCenteredText(context, "Computer " + screen.getLinkedComputerPos().toShortString(), y, DIM_COLOR);
        y += 2.0F;
        y = this.renderStatusSummary(surface, runtimeState, y, context);
        y += ENTRY_GAP;

        final float availableHeight = surface.bottom() - y - CONTENT_MARGIN;
        final FocusedOutput focusedOutput = this.resolveFocusedOutput(displayEntries, screen);
        if (focusedOutput != null) {
            this.renderFocusedOutput(surface, screen, focusedOutput, y, availableHeight, context);
            return;
        }

        final OutputPage outputPage = this.selectOutputPage(displayEntries, screen, font, availableHeight);
        if (outputPage.slices().isEmpty()) {
            this.renderLineEntry(surface.left() + CONTENT_MARGIN, y, surface.contentWidth(), ComputerOutputEntry.info("No output."), context);
            return;
        }

        for (final OutputSlice slice : outputPage.slices()) {
            if (y >= surface.bottom() - CONTENT_MARGIN) {
                break;
            }
            y += this.renderOutputEntry(surface, slice, y, context) + ENTRY_GAP;
        }

        if (outputPage.multiPage()) {
            this.renderPageFooter(surface, outputPage, context);
        }
    }

    private void renderFocusedOutput(final ScreenSurface surface, final ScreenBlockEntity screen, final FocusedOutput focusedOutput,
                                     final float top, final float availableHeight, final RenderContext context) {
        final float contentHeight = Math.max(1.0F, availableHeight - PAGE_FOOTER_HEIGHT);
        final OutputSlice focusedSlice = this.buildFocusedSlice(focusedOutput, screen, context.font(), contentHeight);
        this.renderOutputEntry(surface, focusedSlice, top, context);
        this.renderFocusFooter(surface, focusedOutput, focusedSlice, context);
    }

    private void renderFollowerFallback(final ScreenBlockEntity screen, final PoseStack poseStack, final MultiBufferSource bufferSource,
                                        final int packedLight, final int packedOverlay, final Font font) {
        poseStack.pushPose();
        this.applyScreenTransform(screen.getBlockState().getValue(ScreenBlock.FACING), 1, 1, poseStack);
        poseStack.scale(SCREEN_SCALE, -SCREEN_SCALE, SCREEN_SCALE);

        final ScreenSurface surface = this.surface(1, 1, font);
        final PoseStack.Pose pose = poseStack.last();
        final Matrix4f matrix = pose.pose();
        final RenderContext context = new RenderContext(font, matrix, pose, bufferSource, packedLight, packedOverlay);

        this.renderSurfaceBox(context, surface.left(), surface.top(), surface.right(), surface.bottom(), SCREEN_BACKGROUND, SCREEN_BORDER);
        this.renderStatusLed(screen, surface, poseStack, bufferSource, packedOverlay);
        this.renderFallback(surface, context,
            SCREEN_TITLE_PREFIX + screen.getEndpointName(),
                "FOLLOWER",
                "Controller " + screen.getControllerPos().toShortString() + " for " + screen.multiblockSummary() + " is not loaded. Move closer or load that chunk.",
                WARN_COLOR);
        poseStack.popPose();
    }

    private void renderFallback(final ScreenSurface surface, final RenderContext context, final String title, final String status, final String text) {
        this.renderFallback(surface, context, title, status, text, ERROR_COLOR);
    }

    private void renderFallback(final ScreenSurface surface, final RenderContext context, final String title, final String status, final String text, final int accentColor) {
        final Font font = context.font();
        final float boxLeft = surface.left() + CONTENT_MARGIN;
        final float boxRight = surface.right() - CONTENT_MARGIN;
        final float boxTop = surface.top() + 16.0F;
        final float boxBottom = surface.bottom() - 10.0F;
        this.renderContentBox(context, boxLeft, boxTop, boxRight, boxBottom, 0x2222384D, accentColor);

        float y = boxTop + 6.0F;
        y = this.drawCenteredText(context, title, y, TITLE_COLOR);
        y = this.drawCenteredText(context, status, y + 2.0F, accentColor);

        final List<String> wrapped = wrapLines(font, text, (int) (boxRight - boxLeft - 8.0F), 3);
        for (final String line : wrapped) {
            y = this.drawCenteredText(context, line, y + 1.0F, DIM_COLOR);
        }
    }

    private float renderStatusSummary(final ScreenSurface surface, final ComputerRuntimeSnapshot runtimeState, final float top, final RenderContext context) {
        final Font font = context.font();
        final float height = font.lineHeight + 8.0F;
        final float left = surface.left() + CONTENT_MARGIN;
        final float right = surface.right() - CONTENT_MARGIN;
        final int accent = runtimeStatusColor(runtimeState);
        this.renderContentBox(context, left, top, right, top + height, 0x3322384D, accent);

        final String status = runtimeStatusLabel(runtimeState);
        final float tagWidth = font.width(status) + 10.0F;
        this.renderRect(context, left + 2.0F, top + 2.0F, left + 2.0F + tagWidth, top + 2.0F + font.lineHeight + 2.0F, ACCENT_Z, outputTagBackground(accent));
        this.drawText(context, status, left + 6.0F, top + 4.0F, TITLE_COLOR);

        final String summary = fitToWidth(font, abbreviate(runtimeState.summary(), 96), (int) (right - left - tagWidth - 12.0F));
        this.drawText(context, summary, left + tagWidth + 6.0F, top + 4.0F, INFO_COLOR);
        return height;
    }

    private float renderOutputEntry(final ScreenSurface surface, final OutputSlice slice, final float top, final RenderContext context) {
        final float left = surface.left() + CONTENT_MARGIN;
        final float width = surface.contentWidth();
        if (slice.tableSlice() != null) {
            return this.renderTableEntry(slice, left, top, width, context);
        }
        if (slice.fieldSlice() != null) {
            return this.renderFieldEntry(slice, left, top, width, context);
        }
        return this.renderLineEntry(left, top, width, slice.entry(), context);
    }

    private float renderLineEntry(final float left, final float top, final float width, final ComputerOutputEntry entry, final RenderContext context) {
        final Font font = context.font();
        final float height = font.lineHeight + 6.0F;
        final float right = left + width;
        this.renderContentBox(context, left, top, right, top + height, 0x22111827, outputAccentColor(entry));

        final String label = entry.displayLabel();
        final float labelWidth = font.width(label) + 8.0F;
        this.renderRect(context, left + 2.0F, top + 2.0F, left + 2.0F + labelWidth, top + 2.0F + font.lineHeight + 2.0F, ACCENT_Z, outputTagBackground(entry));
        this.drawText(context, label, left + 6.0F, top + 4.0F, TITLE_COLOR);

        final String text = fitToWidth(font, nonBlank(entry.text(), entry.summaryLine()), (int) (width - labelWidth - 10.0F));
        this.drawText(context, text, left + labelWidth + 6.0F, top + 4.0F, outputTextColor(entry));
        return height;
    }

    private float renderFieldEntry(final OutputSlice slice, final float left, final float top, final float width, final RenderContext context) {
        final Font font = context.font();
        final ComputerOutputEntry entry = slice.entry();
        final FieldSlice fieldSlice = slice.fieldSlice();
        final List<ComputerOutputEntry.OutputField> fields = entry.fields();
        final List<ComputerOutputEntry.OutputField> visibleFields = fieldSlice.visibleFields(fields);
        final float height = this.measureFieldEntryHeight(font, entry, fieldSlice);
        final float right = left + width;
        final int accent = outputAccentColor(entry);

        this.renderContentBox(context, left, top, right, top + height, CARD_BACKGROUND, accent);

        float y = top + 4.0F;
        y = this.renderCardHeader(entry, left + 4.0F, right - 4.0F, y, accent, context);
        if (!entry.text().isBlank()) {
            this.drawText(context, fitToWidth(font, entry.text(), (int) (width - 8.0F)), left + 4.0F, y, outputTextColor(entry));
            y += font.lineHeight + 2.0F;
        }

        final int keyWidth = this.measureFieldKeyWidth(font, visibleFields, (int) Math.max(28.0F, width / 2.0F));
        for (final ComputerOutputEntry.OutputField field : visibleFields) {
            this.drawText(context, fitToWidth(font, field.key(), keyWidth), left + 4.0F, y, DIM_COLOR);
            this.drawText(context, fitToWidth(font, field.value(), (int) (width - keyWidth - 14.0F)), left + 8.0F + keyWidth, y, outputTextColor(entry));
            y += font.lineHeight + 1.0F;
        }

        if (fieldSlice.continued()) {
            this.drawText(context, fieldSlice.summary(), left + 4.0F, y, DIM_COLOR);
        }
        return height;
    }

    private float renderTableEntry(final OutputSlice slice, final float left, final float top, final float width, final RenderContext context) {
        final Font font = context.font();
        final ComputerOutputEntry entry = slice.entry();
        final TableSlice tableSlice = slice.tableSlice();
        final ComputerOutputEntry.TableData tableData = entry.tableData();
        final List<String> allColumns = this.tableColumns(tableData);
        final List<String> visibleColumns = allColumns.subList(tableSlice.columnStart(), tableSlice.columnEnd());
        final float height = this.measureTableEntryHeight(font, entry, tableSlice);
        final float right = left + width;
        final int accent = outputAccentColor(entry);

        this.renderContentBox(context, left, top, right, top + height, CARD_BACKGROUND, accent);

        float y = top + 4.0F;
        y = this.renderCardHeader(entry, left + 4.0F, right - 4.0F, y, accent, context);
        if (!entry.text().isBlank()) {
            this.drawText(context, fitToWidth(font, entry.text(), (int) (width - 8.0F)), left + 4.0F, y, outputTextColor(entry));
            y += font.lineHeight + 2.0F;
        }

        final float columnWidth = Math.max(ScreenLayoutMetrics.TABLE_MIN_COLUMN_WIDTH_UNITS,
            (width - ScreenLayoutMetrics.TABLE_COLUMN_PADDING_UNITS) / Math.max(1, visibleColumns.size()));
        this.renderTableHeader(left, right, y, columnWidth, visibleColumns, context);
        y += font.lineHeight + 3.0F;

        y = this.renderTableRows(tableData.rows(), tableSlice, left, y, columnWidth, context);

        if (tableSlice.continued()) {
            this.drawText(context, tableSlice.summary(), left + 4.0F, y, DIM_COLOR);
        }
        return height;
    }

    private float renderCardHeader(final ComputerOutputEntry entry, final float left, final float right, final float top, final int accent, final RenderContext context) {
        final Font font = context.font();
        final String label = entry.displayLabel();
        final float labelWidth = font.width(label) + 8.0F;
        this.renderRect(context, left, top - 1.0F, left + labelWidth, top + font.lineHeight + 1.0F, ACCENT_Z, outputTagBackground(accent));
        this.drawText(context, label, left + 4.0F, top, TITLE_COLOR);

        final String title = fitToWidth(font, nonBlank(entry.title(), entry.displayLabel()), (int) (right - left - labelWidth - 6.0F));
        this.drawText(context, title, left + labelWidth + 6.0F, top, outputTextColor(entry));
        return top + font.lineHeight + 3.0F;
    }

    private void renderTableHeader(final float left, final float right, final float top, final float columnWidth, final List<String> columns, final RenderContext context) {
        final Font font = context.font();
        this.renderRect(context, left + 3.0F, top - 1.0F, right - 3.0F, top + font.lineHeight + 2.0F, ACCENT_Z, TABLE_HEADER_BACKGROUND);

        float x = left + 4.0F;
        for (final String column : columns) {
            this.drawText(context, fitToWidth(font, column, (int) (columnWidth - 4.0F)), x, top, TITLE_COLOR);
            x += columnWidth;
        }
    }

    private float renderTableRows(final List<List<String>> rows, final TableSlice tableSlice, final float left, final float top, final float columnWidth,
                                  final RenderContext context) {
        final Font font = context.font();
        float y = top;
        for (int rowIndex = tableSlice.rowStart(); rowIndex < tableSlice.rowEnd(); rowIndex++) {
            final List<String> row = rows.get(rowIndex);
            float x = left + 4.0F;
            for (int columnIndex = tableSlice.columnStart(); columnIndex < tableSlice.columnEnd(); columnIndex++) {
                final String cell = columnIndex < row.size() ? row.get(columnIndex) : "";
                this.drawText(context, fitToWidth(font, cell, (int) (columnWidth - 4.0F)), x, y, INFO_COLOR);
                x += columnWidth;
            }
            y += font.lineHeight + 1.0F;
        }
        return y;
    }

    private FocusedOutput resolveFocusedOutput(final List<ComputerOutputEntry> entries, final ScreenBlockEntity screen) {
        if (!screen.hasFocusedOutput()) {
            return null;
        }

        final List<ComputerOutputEntry> focusableEntries = focusableEntries(entries);
        if (focusableEntries.isEmpty()) {
            return null;
        }

        final int clampedCursor = Math.min(screen.getFocusEntryCursor(), focusableEntries.size() - 1);
        if (clampedCursor < 0) {
            return null;
        }

        return new FocusedOutput(
                focusableEntries.get(clampedCursor),
                clampedCursor,
                focusableEntries.size(),
                screen.getFocusFieldOffset(),
                screen.getFocusRowOffset(),
                screen.getFocusColumnOffset());
    }

    private OutputPage selectOutputPage(final List<ComputerOutputEntry> entries, final ScreenBlockEntity screen, final Font font, final float availableHeight) {
        final OutputPage initialPage = this.buildOutputPage(entries, screen, font, availableHeight);
        if (!initialPage.multiPage()) {
            return initialPage;
        }

        return this.buildOutputPage(entries, screen, font, Math.max(1.0F, availableHeight - PAGE_FOOTER_HEIGHT));
    }

    private OutputPage buildOutputPage(final List<ComputerOutputEntry> entries, final ScreenBlockEntity screen, final Font font, final float availableHeight) {
        final List<OutputSlice> slices = this.expandOutputSlices(entries, screen);
        if (slices.isEmpty() || availableHeight <= 0.0F) {
            return OutputPage.empty();
        }

        final ArrayList<List<OutputSlice>> pages = new ArrayList<>();
        final ArrayList<OutputSlice> currentPage = new ArrayList<>();
        float usedHeight = 0.0F;
        for (final OutputSlice slice : slices) {
            final float sliceHeight = this.measureOutputSliceHeight(slice, font);
            final float nextHeight = currentPage.isEmpty() ? sliceHeight : usedHeight + ENTRY_GAP + sliceHeight;
            if (!currentPage.isEmpty() && nextHeight > availableHeight) {
                pages.add(reverseCopy(currentPage));
                currentPage.clear();
                usedHeight = 0.0F;
            }

            currentPage.add(slice);
            usedHeight = currentPage.size() == 1 ? sliceHeight : usedHeight + ENTRY_GAP + sliceHeight;
        }

        if (!currentPage.isEmpty()) {
            pages.add(reverseCopy(currentPage));
        }

        if (pages.isEmpty()) {
            return OutputPage.empty();
        }

        final int effectivePageIndex = Math.min(screen.getPageCursor(), pages.size() - 1);
        return new OutputPage(pages.get(effectivePageIndex), effectivePageIndex, pages.size());
    }

    private List<OutputSlice> expandOutputSlices(final List<ComputerOutputEntry> entries, final ScreenBlockEntity screen) {
        if (entries.isEmpty()) {
            return List.of();
        }

        final ArrayList<OutputSlice> slices = new ArrayList<>();
        for (int index = entries.size() - 1; index >= 0; index--) {
            slices.addAll(this.expandOutputEntry(entries.get(index), screen));
        }
        return List.copyOf(slices);
    }

    private List<ComputerOutputEntry> focusableEntries(final List<ComputerOutputEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        final ArrayList<ComputerOutputEntry> focusableEntries = new ArrayList<>();
        for (int index = entries.size() - 1; index >= 0; index--) {
            final ComputerOutputEntry entry = entries.get(index);
            if (isFocusableEntry(entry)) {
                focusableEntries.add(entry);
            }
        }
        return List.copyOf(focusableEntries);
    }

    private List<OutputSlice> expandOutputEntry(final ComputerOutputEntry entry, final ScreenBlockEntity screen) {
        if (entry.tableKind()) {
            return this.expandTableSlices(entry, screen);
        }
        if (entry.keyValueKind() || entry.planCardKind()) {
            return this.expandFieldSlices(entry, screen);
        }
        return List.of(OutputSlice.line(entry));
    }

    private List<OutputSlice> expandFieldSlices(final ComputerOutputEntry entry, final ScreenBlockEntity screen) {
        final List<ComputerOutputEntry.OutputField> fields = entry.fields();
        final int pageSize = Math.max(1, fieldLimit(screen));
        if (fields.isEmpty()) {
            return List.of(OutputSlice.field(entry, new FieldSlice(0, 0, 0)));
        }

        final ArrayList<OutputSlice> slices = new ArrayList<>();
        for (int start = 0; start < fields.size(); start += pageSize) {
            final int end = Math.min(fields.size(), start + pageSize);
            slices.add(OutputSlice.field(entry, new FieldSlice(start, end, fields.size())));
        }
        return List.copyOf(slices);
    }

    private OutputSlice buildFocusedSlice(final FocusedOutput focusedOutput, final ScreenBlockEntity screen, final Font font, final float availableHeight) {
        final ComputerOutputEntry entry = focusedOutput.entry();
        if (entry.tableKind()) {
            return OutputSlice.table(entry, this.buildFocusedTableSlice(focusedOutput, screen, font, availableHeight));
        }
        if (entry.keyValueKind() || entry.planCardKind()) {
            return OutputSlice.field(entry, this.buildFocusedFieldSlice(focusedOutput, font, availableHeight));
        }
        return OutputSlice.line(entry);
    }

    private FieldSlice buildFocusedFieldSlice(final FocusedOutput focusedOutput, final Font font, final float availableHeight) {
        final ComputerOutputEntry entry = focusedOutput.entry();
        final List<ComputerOutputEntry.OutputField> fields = entry.fields();
        if (fields.isEmpty()) {
            return new FieldSlice(0, 0, 0);
        }

        int visibleCount = 1;
        for (int candidateCount = 1; candidateCount <= fields.size(); candidateCount++) {
            final int candidateStart = clamp(focusedOutput.fieldOffset(), 0, Math.max(0, fields.size() - candidateCount));
            final FieldSlice candidateSlice = new FieldSlice(candidateStart, Math.min(fields.size(), candidateStart + candidateCount), fields.size());
            if (this.measureFieldEntryHeight(font, entry, candidateSlice) <= availableHeight) {
                visibleCount = candidateCount;
            } else {
                break;
            }
        }

        final int start = clamp(focusedOutput.fieldOffset(), 0, Math.max(0, fields.size() - visibleCount));
        return new FieldSlice(start, Math.min(fields.size(), start + visibleCount), fields.size());
    }

    private TableSlice buildFocusedTableSlice(final FocusedOutput focusedOutput, final ScreenBlockEntity screen, final Font font, final float availableHeight) {
        final ComputerOutputEntry entry = focusedOutput.entry();
        final ComputerOutputEntry.TableData tableData = entry.tableData();
        final List<String> columns = this.tableColumns(tableData);
        final int rowCount = tableData.rows().size();
        final int visibleColumns = Math.max(1, Math.min(columns.size(), focusedTableColumnLimit(screen)));
        final int columnStart = clamp(focusedOutput.columnOffset(), 0, Math.max(0, columns.size() - visibleColumns));

        if (rowCount == 0) {
            return new TableSlice(columnStart, Math.min(columns.size(), columnStart + visibleColumns), columns.size(), 0, 0, 0);
        }

        int visibleRows = 1;
        for (int candidateRows = 1; candidateRows <= rowCount; candidateRows++) {
            final int rowStart = clamp(focusedOutput.rowOffset(), 0, Math.max(0, rowCount - candidateRows));
            final TableSlice candidateSlice = new TableSlice(
                    columnStart,
                    Math.min(columns.size(), columnStart + visibleColumns),
                    columns.size(),
                    rowStart,
                    Math.min(rowCount, rowStart + candidateRows),
                    rowCount);
            if (this.measureTableEntryHeight(font, entry, candidateSlice) <= availableHeight) {
                visibleRows = candidateRows;
            } else {
                break;
            }
        }

        final int rowStart = clamp(focusedOutput.rowOffset(), 0, Math.max(0, rowCount - visibleRows));
        return new TableSlice(
                columnStart,
                Math.min(columns.size(), columnStart + visibleColumns),
                columns.size(),
                rowStart,
                Math.min(rowCount, rowStart + visibleRows),
                rowCount);
    }

    private List<OutputSlice> expandTableSlices(final ComputerOutputEntry entry, final ScreenBlockEntity screen) {
        final ComputerOutputEntry.TableData tableData = entry.tableData();
        final List<String> columns = this.tableColumns(tableData);
        final int rowCount = tableData.rows().size();
        final int columnPageSize = Math.max(1, tableColumnLimit(screen));
        final int rowPageSize = Math.max(1, tableRowLimit(screen));
        final ArrayList<OutputSlice> slices = new ArrayList<>();

        for (int columnStart = 0; columnStart < columns.size(); columnStart += columnPageSize) {
            final int columnEnd = Math.min(columns.size(), columnStart + columnPageSize);
            if (rowCount == 0) {
                slices.add(OutputSlice.table(entry, new TableSlice(columnStart, columnEnd, columns.size(), 0, 0, 0)));
                continue;
            }

            for (int rowStart = 0; rowStart < rowCount; rowStart += rowPageSize) {
                final int rowEnd = Math.min(rowCount, rowStart + rowPageSize);
                slices.add(OutputSlice.table(entry, new TableSlice(columnStart, columnEnd, columns.size(), rowStart, rowEnd, rowCount)));
            }
        }

        return List.copyOf(slices);
    }

    private List<String> tableColumns(final ComputerOutputEntry.TableData tableData) {
        if (!tableData.columns().isEmpty()) {
            return tableData.columns();
        }

        final int width = Math.max(1, widestRowWidth(tableData.rows()));
        final ArrayList<String> columns = new ArrayList<>(width);
        for (int index = 0; index < width; index++) {
            columns.add(width == 1 ? "Value" : "Value " + (index + 1));
        }
        return List.copyOf(columns);
    }

    private float measureOutputSliceHeight(final OutputSlice slice, final Font font) {
        if (slice.tableSlice() != null) {
            return this.measureTableEntryHeight(font, slice.entry(), slice.tableSlice());
        }
        if (slice.fieldSlice() != null) {
            return this.measureFieldEntryHeight(font, slice.entry(), slice.fieldSlice());
        }
        return font.lineHeight + 6.0F;
    }

    private float measureFieldEntryHeight(final Font font, final ComputerOutputEntry entry, final FieldSlice fieldSlice) {
        float height = font.lineHeight + 8.0F;
        if (!entry.text().isBlank()) {
            height += font.lineHeight + 2.0F;
        }
        height += fieldSlice.visibleCount() * (font.lineHeight + 1.0F);
        if (fieldSlice.continued()) {
            height += font.lineHeight + 1.0F;
        }
        return Math.max(height + 4.0F, font.lineHeight + 10.0F);
    }

    private float measureTableEntryHeight(final Font font, final ComputerOutputEntry entry, final TableSlice tableSlice) {
        float height = font.lineHeight + 8.0F;
        if (!entry.text().isBlank()) {
            height += font.lineHeight + 2.0F;
        }
        height += font.lineHeight + 3.0F;
        height += tableSlice.visibleRowCount() * (font.lineHeight + 1.0F);
        if (tableSlice.continued()) {
            height += font.lineHeight + 1.0F;
        }
        return Math.max(height + 4.0F, font.lineHeight + 12.0F);
    }

    private void renderPageFooter(final ScreenSurface surface, final OutputPage outputPage, final RenderContext context) {
        final float top = surface.bottom() - CONTENT_MARGIN - PAGE_FOOTER_HEIGHT;
        final float bottom = surface.bottom() - CONTENT_MARGIN;
        this.renderRect(context, surface.left() + CONTENT_MARGIN, top, surface.right() - CONTENT_MARGIN, bottom, FOOTER_Z, 0x22111827);
        this.drawCenteredText(context, "Page " + outputPage.displayPageNumber() + "/" + outputPage.totalPages(), top + 1.0F, DIM_COLOR);
    }

    private void renderFocusFooter(final ScreenSurface surface, final FocusedOutput focusedOutput, final OutputSlice focusedSlice, final RenderContext context) {
        final float top = surface.bottom() - CONTENT_MARGIN - PAGE_FOOTER_HEIGHT;
        final float bottom = surface.bottom() - CONTENT_MARGIN;
        final String detailSummary;
        if (focusedSlice.tableSlice() != null) {
            detailSummary = focusedSlice.tableSlice().summary();
        } else if (focusedSlice.fieldSlice() != null) {
            detailSummary = focusedSlice.fieldSlice().summary();
        } else {
            detailSummary = "";
        }
        final String footer = detailSummary.isBlank()
                ? "Focus " + focusedOutput.entry().displayLabel() + " " + focusedOutput.displayIndex() + "/" + focusedOutput.totalFocusable()
                : "Focus " + focusedOutput.entry().displayLabel() + " " + focusedOutput.displayIndex() + "/" + focusedOutput.totalFocusable() + " | " + detailSummary;
        this.renderRect(context, surface.left() + CONTENT_MARGIN, top, surface.right() - CONTENT_MARGIN, bottom, FOOTER_Z, 0x3322384D);
        this.drawCenteredText(context, fitToWidth(context.font(), footer, (int) (surface.contentWidth() - 4.0F)), top + 1.0F, PLAN_COLOR);
    }

    private static List<OutputSlice> reverseCopy(final List<OutputSlice> slices) {
        final ArrayList<OutputSlice> reversed = new ArrayList<>(slices);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static int widestRowWidth(final List<List<String>> rows) {
        int width = 0;
        for (final List<String> row : rows) {
            width = Math.max(width, row.size());
        }
        return width;
    }

    private static boolean isFocusableEntry(final ComputerOutputEntry entry) {
        return entry != null && (entry.tableKind() || entry.keyValueKind() || entry.planCardKind());
    }

    private static int focusedTableColumnLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.focusedTableColumnLimit(screen.getSpanX());
    }

    private static int clamp(final int value, final int minValue, final int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private int measureFieldKeyWidth(final Font font, final List<ComputerOutputEntry.OutputField> fields, final int maxWidth) {
        int width = 28;
        for (final ComputerOutputEntry.OutputField field : fields) {
            width = Math.max(width, font.width(field.key()));
        }
        return Math.min(width, Math.max(28, maxWidth));
    }

    private void applyScreenTransform(final ScreenBlockEntity screen, final PoseStack poseStack) {
        this.applyScreenTransform(screen.getBlockState().getValue(ScreenBlock.FACING), screen.getSpanX(), screen.getSpanY(), poseStack);
    }

    private void applyScreenTransform(final Direction facing, final int spanX, final int spanY, final PoseStack poseStack) {
        final double horizontalCenterOffset = Math.max(0, spanX - 1) * 0.5D;
        final double verticalCenterOffset = Math.max(0, spanY - 1) * 0.5D;
        final double multiblockLift = Math.max(0, spanY - 1) * 0.04D;
        poseStack.translate(0.5D, PANEL_Y_OFFSET, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(horizontalCenterOffset, verticalCenterOffset + multiblockLift, PANEL_Z_OFFSET);
    }

    private ScreenSurface surface(final ScreenBlockEntity screen, final Font font) {
        return this.surface(screen.getSpanX(), screen.getSpanY(), font);
    }

    private ScreenSurface surface(final int spanX, final int spanY, final Font font) {
        final int lineBudget = lineBudget(spanY);
        final float width = ScreenLayoutMetrics.surfaceWidthUnits(spanX);
        final float top = this.startY(font, lineBudget) - 4.0F;
        final float bottom = this.maxPanelHeight(font, lineBudget) + 4.0F;
        return new ScreenSurface(-width / 2.0F, top, width, bottom - top);
    }

    private float drawCenteredText(final RenderContext context, final String text, final float y, final int color) {
        final String safeText = text == null ? "" : text;
        final float x = -context.font().width(safeText) / 2.0F;
        context.font().drawInBatch(safeText, x, y, color, false, textMatrix(context), context.bufferSource(), Font.DisplayMode.POLYGON_OFFSET, GLYPH_BACKGROUND, context.packedLight());
        this.flushBufferSource(context.bufferSource());
        return y + context.font().lineHeight + 1.0F;
    }

    private void drawText(final RenderContext context, final String text, final float x, final float y, final int color) {
        final String safeText = text == null ? "" : text;
        context.font().drawInBatch(safeText, x, y, color, false, textMatrix(context), context.bufferSource(), Font.DisplayMode.POLYGON_OFFSET, GLYPH_BACKGROUND, context.packedLight());
        this.flushBufferSource(context.bufferSource());
    }

    private static Matrix4f textMatrix(final RenderContext context) {
        return new Matrix4f(context.matrix()).translate(0.0F, 0.0F, TEXT_Z_OFFSET);
    }

    private void flushBufferSource(final MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource bufferSourceImpl) {
            bufferSourceImpl.endBatch();
        }
    }

    private void renderStatusLed(final ScreenBlockEntity screen, final ScreenSurface surface, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedOverlay) {
        final StatusLed led = this.resolveStatusLed(screen);
        final float right = surface.right() - STATUS_LED_RIGHT_MARGIN;
        final float left = right - STATUS_LED_SIZE;
        final float top = surface.top() + 4.0F + STATUS_LED_TOP_MARGIN;
        final float bottom = top + STATUS_LED_SIZE;
        final PoseStack.Pose pose = poseStack.last();

        this.renderLedQuad(bufferSource, pose, new LedQuad(left, top, right, bottom, LED_BEZEL_COLOR), LED_BEZEL_Z, packedOverlay);
        this.renderLedQuad(bufferSource, pose, new LedQuad(left + STATUS_LED_INSET, top + STATUS_LED_INSET, right - STATUS_LED_INSET, bottom - STATUS_LED_INSET, led.color()), LED_CORE_Z, packedOverlay);
    }

    private StatusLed resolveStatusLed(final ScreenBlockEntity screen) {
        final ScreenBlockEntity.LedAnimationState animationState;
        if (!screen.hasLinkedComputer()) {
            animationState = screen.observeLedMode("unlinked");
            return new StatusLed(animateLedColor(animationState, DIM_COLOR));
        }

        final ComputerBlockEntity linkedComputer = screen.resolveLinkedComputer();
        if (linkedComputer == null) {
            animationState = screen.observeLedMode("offline");
            return new StatusLed(animateLedColor(animationState, WARN_COLOR));
        }

        final ComputerRuntimeSnapshot runtimeState = linkedComputer.getRuntimeState();
        if (runtimeState.running()) {
            animationState = screen.observeLedMode("running");
            return new StatusLed(animateLedColor(animationState, PLAN_COLOR));
        }
        if (runtimeState.neverExecuted()) {
            animationState = screen.observeLedMode("idle");
            return new StatusLed(animateLedColor(animationState, DIM_COLOR));
        }
        if (runtimeState.stopped()) {
            animationState = screen.observeLedMode("stopped");
            return new StatusLed(animateLedColor(animationState, WARN_COLOR));
        }
        animationState = screen.observeLedMode(runtimeState.success() ? "ok" : "error");
        return new StatusLed(animateLedColor(animationState, runtimeState.success() ? OK_COLOR : ERROR_COLOR));
    }

    private void renderSurfaceBox(final RenderContext context, final float left, final float top, final float right, final float bottom,
                                  final int fillColor, final int borderColor) {
        this.renderRect(context, left, top, right, bottom, SURFACE_FILL_Z, fillColor);
        this.renderRect(context, left, top, right, top + BOX_BORDER, SURFACE_BORDER_Z, borderColor);
        this.renderRect(context, left, bottom - BOX_BORDER, right, bottom, SURFACE_BORDER_Z, borderColor);
        this.renderRect(context, left, top + BOX_BORDER, left + BOX_BORDER, bottom - BOX_BORDER, SURFACE_BORDER_Z, borderColor);
        this.renderRect(context, right - BOX_BORDER, top + BOX_BORDER, right, bottom - BOX_BORDER, SURFACE_BORDER_Z, borderColor);
    }

    private void renderContentBox(final RenderContext context, final float left, final float top, final float right, final float bottom,
                                  final int fillColor, final int borderColor) {
        this.renderRect(context, left, top, right, bottom, CONTENT_FILL_Z, fillColor);
        this.renderRect(context, left, top, right, top + BOX_BORDER, CONTENT_BORDER_Z, borderColor);
        this.renderRect(context, left, bottom - BOX_BORDER, right, bottom, CONTENT_BORDER_Z, borderColor);
        this.renderRect(context, left, top + BOX_BORDER, left + BOX_BORDER, bottom - BOX_BORDER, CONTENT_BORDER_Z, borderColor);
        this.renderRect(context, right - BOX_BORDER, top + BOX_BORDER, right, bottom - BOX_BORDER, CONTENT_BORDER_Z, borderColor);
    }

    private void renderRect(final RenderContext context, final float left, final float top, final float right, final float bottom, final int color) {
        this.renderRect(context, left, top, right, bottom, CONTENT_FILL_Z, color);
    }

    private void renderRect(final RenderContext context, final float left, final float top, final float right, final float bottom, final float z, final int color) {
        RenderQuadHelper.drawSolidQuad(context.bufferSource(), context.pose(), left, top, right, bottom, z, color, LightTexture.FULL_BRIGHT, context.packedOverlay());
    }

    private void renderLedQuad(final MultiBufferSource bufferSource, final PoseStack.Pose pose, final LedQuad quad, final float z, final int packedOverlay) {
        RenderQuadHelper.drawSolidQuad(bufferSource, pose, quad.left(), quad.top(), quad.right(), quad.bottom(), z, quad.color(), LightTexture.FULL_BRIGHT, packedOverlay);
    }

    private float startY(final Font font, final int lineCount) {
        final float contentHeight = lineCount * (font.lineHeight + 2) - 2.0F;
        return -contentHeight / 2.0F;
    }

    private float maxPanelHeight(final Font font, final int lineBudget) {
        return this.startY(font, lineBudget) + lineBudget * (font.lineHeight + 2);
    }

    private static int lineBudget(final ScreenBlockEntity screen) {
        return lineBudget(screen.getSpanY());
    }

    private static int lineBudget(final int spanY) {
        return ScreenLayoutMetrics.lineBudget(spanY);
    }

    private static int fieldLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.fieldLimit(screen.getSpanY());
    }

    private static int tableRowLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.tableRowLimit(screen.getSpanY());
    }

    private static int tableColumnLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.tableColumnLimit(screen.getSpanX());
    }

    private static String runtimeStatusLabel(final ComputerRuntimeSnapshot runtimeState) {
        if (runtimeState.running()) {
            return "RUNNING";
        }
        if (runtimeState.neverExecuted()) {
            return "IDLE";
        }
        if (runtimeState.stopped()) {
            return "STOPPED";
        }
        if (runtimeState.success()) {
            return "OK";
        }
        return "ERROR";
    }

    private static int runtimeStatusColor(final ComputerRuntimeSnapshot runtimeState) {
        if (runtimeState.running()) {
            return PLAN_COLOR;
        }
        if (runtimeState.neverExecuted()) {
            return DIM_COLOR;
        }
        if (runtimeState.stopped()) {
            return WARN_COLOR;
        }
        return runtimeState.success() ? OK_COLOR : ERROR_COLOR;
    }

    private static int outputAccentColor(final ComputerOutputEntry outputEntry) {
        if (outputEntry.errorTone()) {
            return ERROR_COLOR;
        }
        if (outputEntry.okTone()) {
            return OK_COLOR;
        }
        if (outputEntry.planChannel()) {
            return PLAN_COLOR;
        }
        return INFO_COLOR;
    }

    private static int outputTagBackground(final ComputerOutputEntry outputEntry) {
        return outputTagBackground(outputAccentColor(outputEntry));
    }

    private static int outputTagBackground(final int accentColor) {
        final int red = accentColor >>> 16 & 0xFF;
        final int green = accentColor >>> 8 & 0xFF;
        final int blue = accentColor & 0xFF;
        return 0x66000000 | red << 16 | green << 8 | blue;
    }

    private static int outputTextColor(final ComputerOutputEntry outputEntry) {
        if (outputEntry.errorTone()) {
            return ERROR_COLOR;
        }
        if (outputEntry.okTone()) {
            return OK_COLOR;
        }
        if (outputEntry.planChannel()) {
            return PLAN_COLOR;
        }
        return INFO_COLOR;
    }

    private static int animateLedColor(final ScreenBlockEntity.LedAnimationState animationState, final int baseColor) {
        return switch (animationState.mode()) {
            case "running" -> scaleColor(baseColor, runningPulseIntensity(animationState.elapsedMillis()));
            case "error" -> animationState.errorBlinkActive()
                    ? scaleColor(baseColor, blinkingErrorIntensity(animationState.elapsedMillis()))
                    : baseColor;
            case "idle", "unlinked" -> scaleColor(baseColor, 0.75F);
            case "offline" -> scaleColor(baseColor, 0.9F);
            default -> baseColor;
        };
    }

    private static float runningPulseIntensity(final long elapsedMillis) {
        final float phase = (elapsedMillis % RUNNING_PULSE_PERIOD_MILLIS) / (float) RUNNING_PULSE_PERIOD_MILLIS;
        final float wave = 0.5F + 0.5F * (float) Math.sin(phase * ((float) Math.PI * 2.0F));
        return 0.45F + 0.55F * wave;
    }

    private static float blinkingErrorIntensity(final long elapsedMillis) {
        final boolean ledOn = (elapsedMillis / ERROR_BLINK_PERIOD_MILLIS) % 2L == 0L;
        return ledOn ? 1.0F : 0.18F;
    }

    private static int scaleColor(final int color, final float intensity) {
        final float clampedIntensity = Math.max(0.0F, Math.min(1.0F, intensity));
        final int alpha = color >>> 24 & 0xFF;
        final int red = Math.round((color >>> 16 & 0xFF) * clampedIntensity);
        final int green = Math.round((color >>> 8 & 0xFF) * clampedIntensity);
        final int blue = Math.round((color & 0xFF) * clampedIntensity);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static String nonBlank(final String preferred, final String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String abbreviate(final String text, final int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String fitToWidth(final Font font, final String text, final int maxWidth) {
        if (text == null || text.isBlank() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }

        final String ellipsis = "...";
        final int prefixWidth = Math.max(0, maxWidth - font.width(ellipsis));
        if (prefixWidth == 0) {
            return ellipsis;
        }
        return font.plainSubstrByWidth(text, prefixWidth) + ellipsis;
    }

    private static List<String> wrapLines(final Font font, final String text, final int maxWidth, final int maxLines) {
        final ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String remaining = text.strip();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            String current = font.plainSubstrByWidth(remaining, maxWidth);
            if (current.isEmpty()) {
                break;
            }
            if (current.length() < remaining.length()) {
                final int lastSpace = current.lastIndexOf(' ');
                if (lastSpace > 10) {
                    current = current.substring(0, lastSpace);
                }
            }
            lines.add(current.stripTrailing());
            remaining = remaining.substring(Math.min(remaining.length(), current.length())).stripLeading();
        }

        if (!remaining.isEmpty() && !lines.isEmpty()) {
            final int lastIndex = lines.size() - 1;
            lines.set(lastIndex, fitToWidth(font, lines.get(lastIndex) + " ...", maxWidth));
        }
        return List.copyOf(lines);
    }

    private record StatusLed(int color) {
    }

    private record LedQuad(float left, float top, float right, float bottom, int color) {
    }

    private record FocusedOutput(ComputerOutputEntry entry, int focusCursor, int totalFocusable, int fieldOffset, int rowOffset, int columnOffset) {
        private int displayIndex() {
            return this.focusCursor + 1;
        }
    }

    private record OutputPage(List<OutputSlice> slices, int pageIndex, int totalPages) {
        private static OutputPage empty() {
            return new OutputPage(List.of(), 0, 0);
        }

        private boolean multiPage() {
            return this.totalPages > 1;
        }

        private int displayPageNumber() {
            return this.totalPages == 0 ? 0 : this.pageIndex + 1;
        }
    }

    private record OutputSlice(ComputerOutputEntry entry, FieldSlice fieldSlice, TableSlice tableSlice) {
        private static OutputSlice line(final ComputerOutputEntry entry) {
            return new OutputSlice(entry, null, null);
        }

        private static OutputSlice field(final ComputerOutputEntry entry, final FieldSlice fieldSlice) {
            return new OutputSlice(entry, fieldSlice, null);
        }

        private static OutputSlice table(final ComputerOutputEntry entry, final TableSlice tableSlice) {
            return new OutputSlice(entry, null, tableSlice);
        }
    }

    private record FieldSlice(int startInclusive, int endExclusive, int totalCount) {
        private int visibleCount() {
            return Math.max(0, this.endExclusive - this.startInclusive);
        }

        private boolean hasPrevious() {
            return this.startInclusive > 0;
        }

        private boolean hasNext() {
            return this.endExclusive < this.totalCount;
        }

        private boolean continued() {
            return this.hasPrevious() || this.hasNext();
        }

        private List<ComputerOutputEntry.OutputField> visibleFields(final List<ComputerOutputEntry.OutputField> fields) {
            if (fields.isEmpty() || this.visibleCount() == 0) {
                return List.of();
            }
            return fields.subList(this.startInclusive, this.endExclusive);
        }

        private String summary() {
            if (!this.continued() || this.totalCount == 0) {
                return "";
            }
            return "Fields " + (this.startInclusive + 1) + "-" + this.endExclusive + "/" + this.totalCount;
        }
    }

    private record TableSlice(int columnStart, int columnEnd, int columnTotal, int rowStart, int rowEnd, int rowTotal) {
        private int visibleRowCount() {
            return Math.max(0, this.rowEnd - this.rowStart);
        }

        private boolean hasPreviousColumns() {
            return this.columnStart > 0;
        }

        private boolean hasNextColumns() {
            return this.columnEnd < this.columnTotal;
        }

        private boolean hasPreviousRows() {
            return this.rowStart > 0;
        }

        private boolean hasNextRows() {
            return this.rowEnd < this.rowTotal;
        }

        private boolean continued() {
            return this.hasPreviousColumns() || this.hasNextColumns() || this.hasPreviousRows() || this.hasNextRows();
        }

        private String summary() {
            if (!this.continued()) {
                return "";
            }

            final StringBuilder builder = new StringBuilder();
            if (this.columnTotal > 0 && (this.hasPreviousColumns() || this.hasNextColumns())) {
                builder.append("Cols ").append(this.columnStart + 1).append("-").append(this.columnEnd).append("/").append(this.columnTotal);
            }
            if (this.rowTotal > 0 && (this.hasPreviousRows() || this.hasNextRows())) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append("Rows ").append(this.rowStart + 1).append("-").append(this.rowEnd).append("/").append(this.rowTotal);
            }
            return builder.toString();
        }
    }

    private record RenderContext(Font font, Matrix4f matrix, PoseStack.Pose pose, MultiBufferSource bufferSource,
                                 int packedLight, int packedOverlay) {
    }

    private record ScreenSurface(float left, float top, float width, float height) {
        private float right() {
            return this.left + this.width;
        }

        private float bottom() {
            return this.top + this.height;
        }

        private float contentWidth() {
            return this.width - CONTENT_MARGIN * 2.0F;
        }
    }
}