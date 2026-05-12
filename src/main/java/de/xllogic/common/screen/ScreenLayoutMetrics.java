package de.xllogic.common.screen;

public final class ScreenLayoutMetrics {
    public static final float SCREEN_SCALE = 0.0092F;
    public static final int CONTENT_MARGIN_UNITS = 4;
    public static final int TABLE_COLUMN_PADDING_UNITS = 10;
    public static final int TABLE_MIN_COLUMN_WIDTH_UNITS = 18;
    private static final int BASE_SURFACE_UNITS = 72;
    private static final int EXTRA_SURFACE_UNITS_PER_PANEL = Math.round(1.0F / SCREEN_SCALE);
    private static final int BASE_RENDER_LINES = 6;
    private static final int EXTRA_RENDER_LINES_PER_PANEL_ROW = 10;
    private static final int MAX_RENDER_LINES = 48;
    private static final int MAX_VISIBLE_TABLE_COLUMNS = 32;
        private static final int RENDER_LINE_CAP_START_ROW = 5;
        private static final int TABLE_COLUMN_CAP_START_SPAN = 5;
        private static final int EXTRA_VISIBLE_TABLE_COLUMNS_PER_PANEL =
            (EXTRA_SURFACE_UNITS_PER_PANEL + TABLE_MIN_COLUMN_WIDTH_UNITS - 1) / TABLE_MIN_COLUMN_WIDTH_UNITS;

    private ScreenLayoutMetrics() {
    }

    public static int surfaceWidthUnits(final int spanX) {
        return BASE_SURFACE_UNITS + Math.max(0, spanX - 1) * EXTRA_SURFACE_UNITS_PER_PANEL;
    }

    public static int lineBudget(final int spanY) {
        final int derivedBudget = BASE_RENDER_LINES + Math.max(0, spanY - 1) * EXTRA_RENDER_LINES_PER_PANEL_ROW;
        return Math.min(renderLineCap(spanY), derivedBudget);
    }

    public static int fieldLimit(final int spanY) {
        return Math.max(2, lineBudget(spanY) - 4);
    }

    public static int tableRowLimit(final int spanY) {
        return Math.max(1, lineBudget(spanY) - 5);
    }

    public static int tableColumnLimit(final int spanX) {
        final int contentWidth = Math.max(1, surfaceWidthUnits(spanX) - CONTENT_MARGIN_UNITS * 2);
        final int derivedLimit = Math.max(1, (contentWidth - TABLE_COLUMN_PADDING_UNITS) / TABLE_MIN_COLUMN_WIDTH_UNITS);
        return Math.min(tableColumnCap(spanX), Math.max(3, derivedLimit));
    }

    public static int focusedTableColumnLimit(final int spanX) {
        return tableColumnLimit(spanX);
    }

    private static int renderLineCap(final int spanY) {
        return MAX_RENDER_LINES + Math.max(0, spanY - RENDER_LINE_CAP_START_ROW) * EXTRA_RENDER_LINES_PER_PANEL_ROW;
    }

    private static int tableColumnCap(final int spanX) {
        return MAX_VISIBLE_TABLE_COLUMNS + Math.max(0, spanX - TABLE_COLUMN_CAP_START_SPAN) * EXTRA_VISIBLE_TABLE_COLUMNS_PER_PANEL;
    }
}