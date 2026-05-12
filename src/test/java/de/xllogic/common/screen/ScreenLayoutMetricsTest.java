package de.xllogic.common.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScreenLayoutMetricsTest {
    @Test
    void expandsSurfaceWidthByMeaningfulPanelAreaPerAdditionalScreen() {
        assertEquals(72, ScreenLayoutMetrics.surfaceWidthUnits(1));
        assertEquals(181, ScreenLayoutMetrics.surfaceWidthUnits(2));
        assertEquals(290, ScreenLayoutMetrics.surfaceWidthUnits(3));
    }

    @Test
    void scalesLineBudgetAndFieldCapacityWithAdditionalRows() {
        assertEquals(6, ScreenLayoutMetrics.lineBudget(1));
        assertEquals(16, ScreenLayoutMetrics.lineBudget(2));
        assertEquals(26, ScreenLayoutMetrics.lineBudget(3));
        assertEquals(56, ScreenLayoutMetrics.lineBudget(6));
        assertEquals(2, ScreenLayoutMetrics.fieldLimit(1));
        assertEquals(12, ScreenLayoutMetrics.fieldLimit(2));
        assertEquals(22, ScreenLayoutMetrics.fieldLimit(3));
        assertEquals(52, ScreenLayoutMetrics.fieldLimit(6));
        assertEquals(1, ScreenLayoutMetrics.tableRowLimit(1));
        assertEquals(11, ScreenLayoutMetrics.tableRowLimit(2));
        assertEquals(21, ScreenLayoutMetrics.tableRowLimit(3));
        assertEquals(51, ScreenLayoutMetrics.tableRowLimit(6));
    }

    @Test
    void increasesVisibleTableColumnsForWiderMultiscreens() {
        assertEquals(3, ScreenLayoutMetrics.tableColumnLimit(1));
        assertEquals(9, ScreenLayoutMetrics.tableColumnLimit(2));
        assertEquals(15, ScreenLayoutMetrics.tableColumnLimit(3));
        assertEquals(33, ScreenLayoutMetrics.tableColumnLimit(6));
        assertEquals(45, ScreenLayoutMetrics.tableColumnLimit(8));
        assertEquals(ScreenLayoutMetrics.tableColumnLimit(3), ScreenLayoutMetrics.focusedTableColumnLimit(3));
        assertEquals(ScreenLayoutMetrics.tableColumnLimit(8), ScreenLayoutMetrics.focusedTableColumnLimit(8));
        assertTrue(ScreenLayoutMetrics.tableColumnLimit(3) > ScreenLayoutMetrics.tableColumnLimit(1));
        assertTrue(ScreenLayoutMetrics.tableColumnLimit(8) > ScreenLayoutMetrics.tableColumnLimit(6));
    }
}