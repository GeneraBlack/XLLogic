package de.xllogic.client.editor;

import de.xllogic.common.device.XLDefaults;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class TextDocument {
    private static final int MAX_HISTORY = 128;

    private final List<StringBuilder> lines = new ArrayList<>();
    private final Deque<DocumentSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<DocumentSnapshot> redoStack = new ArrayDeque<>();
    private int cursorLine;
    private int cursorColumn;
    private CursorPosition selectionAnchor;

    public TextDocument(final String text) {
        this.lines.add(new StringBuilder());
        this.insertWithoutHistory(text == null ? "" : text);
        this.setCursorInternal(new CursorPosition(0, 0));
    }

    public static TextDocument starterPythonDocument() {
        return new TextDocument(XLDefaults.STARTER_SCRIPT);
    }

    public int getCursorLine() {
        return this.cursorLine;
    }

    public int getCursorColumn() {
        return this.cursorColumn;
    }

    public int getLineCount() {
        return this.lines.size();
    }

    public String getLine(final int index) {
        return this.lines.get(index).toString();
    }

    public String getText() {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < this.lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(this.lines.get(index));
        }
        return builder.toString();
    }

    public boolean hasSelection() {
        return this.selectionRange() != null;
    }

    public SelectionSegment getSelectionSegment(final int line) {
        final SelectionRange range = this.selectionRange();
        if (range == null || line < range.start().line() || line > range.end().line()) {
            return null;
        }

        if (range.start().line() == range.end().line()) {
            return new SelectionSegment(range.start().column(), range.end().column());
        }
        if (line == range.start().line()) {
            return new SelectionSegment(range.start().column(), this.lineLength(line));
        }
        if (line == range.end().line()) {
            return new SelectionSegment(0, range.end().column());
        }
        return new SelectionSegment(0, this.lineLength(line));
    }

    public String getSelectedText() {
        final SelectionRange range = this.selectionRange();
        if (range == null) {
            return "";
        }

        if (range.start().line() == range.end().line()) {
            return this.getLine(range.start().line()).substring(range.start().column(), range.end().column());
        }

        final StringBuilder builder = new StringBuilder();
        builder.append(this.getLine(range.start().line()).substring(range.start().column()));
        builder.append('\n');
        for (int line = range.start().line() + 1; line < range.end().line(); line++) {
            builder.append(this.getLine(line)).append('\n');
        }
        builder.append(this.getLine(range.end().line()), 0, range.end().column());
        return builder.toString();
    }

    public void clearSelection() {
        this.selectionAnchor = null;
    }

    public void selectAll() {
        this.selectionAnchor = new CursorPosition(0, 0);
        this.setCursorInternal(this.endOfDocument());
    }

    public void setCursor(final int line, final int column) {
        this.setCursor(line, column, false);
    }

    public void setCursor(final int line, final int column, final boolean keepSelection) {
        final CursorPosition target = this.clampPosition(new CursorPosition(line, column));
        if (keepSelection) {
            if (this.selectionAnchor == null) {
                this.selectionAnchor = this.currentPosition();
            }
        } else {
            this.selectionAnchor = null;
        }
        this.setCursorInternal(target);
        this.clearSelectionIfCollapsed();
    }

    public void insert(final char value) {
        this.insert(String.valueOf(value));
    }

    public void insert(final String value) {
        final String normalized = normalizeLineSeparators(value);
        if (normalized.isEmpty()) {
            return;
        }

        this.pushUndoSnapshot();
        this.deleteSelectionInternal();
        this.insertNormalizedInternal(normalized);
    }

    public void insertSpaces(final int count) {
        if (count <= 0) {
            return;
        }
        this.insert(" ".repeat(count));
    }

    public void insertNewLine() {
        this.pushUndoSnapshot();
        this.deleteSelectionInternal();
        this.insertNewLineInternal();
    }

    public void replaceRange(final int startLine, final int startColumn, final int endLine, final int endColumn, final String replacement) {
        final CursorPosition start = this.clampPosition(new CursorPosition(startLine, startColumn));
        final CursorPosition end = this.clampPosition(new CursorPosition(endLine, endColumn));
        final CursorPosition rangeStart = compare(start, end) <= 0 ? start : end;
        final CursorPosition rangeEnd = compare(start, end) <= 0 ? end : start;
        final String normalized = normalizeLineSeparators(replacement);
        if (compare(rangeStart, rangeEnd) == 0 && normalized.isEmpty()) {
            return;
        }

        this.pushUndoSnapshot();
        this.deleteRangeInternal(rangeStart, rangeEnd);
        this.insertNormalizedInternal(normalized);
    }

    public void deleteSelection() {
        if (!this.hasSelection()) {
            return;
        }
        this.pushUndoSnapshot();
        this.deleteSelectionInternal();
    }

    public void backspace() {
        if (this.hasSelection()) {
            this.pushUndoSnapshot();
            this.deleteSelectionInternal();
            return;
        }

        if (this.cursorColumn == 0 && this.cursorLine == 0) {
            return;
        }

        this.pushUndoSnapshot();
        if (this.cursorColumn > 0) {
            this.lines.get(this.cursorLine).deleteCharAt(this.cursorColumn - 1);
            this.cursorColumn--;
            return;
        }

        final StringBuilder previous = this.lines.get(this.cursorLine - 1);
        final String current = this.lines.remove(this.cursorLine).toString();
        this.cursorLine--;
        this.cursorColumn = previous.length();
        previous.append(current);
    }

    public void deleteForward() {
        if (this.hasSelection()) {
            this.pushUndoSnapshot();
            this.deleteSelectionInternal();
            return;
        }

        final StringBuilder line = this.lines.get(this.cursorLine);
        if (this.cursorColumn >= line.length() && this.cursorLine >= this.lines.size() - 1) {
            return;
        }

        this.pushUndoSnapshot();
        if (this.cursorColumn < line.length()) {
            line.deleteCharAt(this.cursorColumn);
            return;
        }

        line.append(this.lines.remove(this.cursorLine + 1));
    }

    public void moveLeft() {
        this.moveLeft(false);
    }

    public void moveLeft(final boolean keepSelection) {
        if (!keepSelection && this.hasSelection()) {
            this.collapseSelectionToStart();
            return;
        }

        this.prepareSelection(keepSelection);
        if (this.cursorColumn > 0) {
            this.cursorColumn--;
        } else if (this.cursorLine > 0) {
            this.cursorLine--;
            this.cursorColumn = this.lineLength(this.cursorLine);
        }
        this.clearSelectionIfCollapsed();
    }

    public void moveRight() {
        this.moveRight(false);
    }

    public void moveRight(final boolean keepSelection) {
        if (!keepSelection && this.hasSelection()) {
            this.collapseSelectionToEnd();
            return;
        }

        this.prepareSelection(keepSelection);
        final int lineLength = this.lineLength(this.cursorLine);
        if (this.cursorColumn < lineLength) {
            this.cursorColumn++;
        } else if (this.cursorLine < this.lines.size() - 1) {
            this.cursorLine++;
            this.cursorColumn = 0;
        }
        this.clearSelectionIfCollapsed();
    }

    public void moveUp() {
        this.moveUp(false);
    }

    public void moveUp(final boolean keepSelection) {
        this.prepareSelection(keepSelection);
        if (this.cursorLine > 0) {
            this.cursorLine--;
            this.cursorColumn = Math.min(this.cursorColumn, this.lineLength(this.cursorLine));
        }
        this.clearSelectionIfCollapsed();
    }

    public void moveDown() {
        this.moveDown(false);
    }

    public void moveDown(final boolean keepSelection) {
        this.prepareSelection(keepSelection);
        if (this.cursorLine < this.lines.size() - 1) {
            this.cursorLine++;
            this.cursorColumn = Math.min(this.cursorColumn, this.lineLength(this.cursorLine));
        }
        this.clearSelectionIfCollapsed();
    }

    public void moveHome() {
        this.moveHome(false);
    }

    public void moveHome(final boolean keepSelection) {
        this.prepareSelection(keepSelection);
        this.cursorColumn = 0;
        this.clearSelectionIfCollapsed();
    }

    public void moveEnd() {
        this.moveEnd(false);
    }

    public void moveEnd(final boolean keepSelection) {
        this.prepareSelection(keepSelection);
        this.cursorColumn = this.lineLength(this.cursorLine);
        this.clearSelectionIfCollapsed();
    }

    public void movePageUp(final int amount) {
        this.movePageUp(amount, false);
    }

    public void movePageUp(final int amount, final boolean keepSelection) {
        this.prepareSelection(keepSelection);
        this.cursorLine = Math.max(0, this.cursorLine - Math.max(1, amount));
        this.cursorColumn = Math.min(this.cursorColumn, this.lineLength(this.cursorLine));
        this.clearSelectionIfCollapsed();
    }

    public void movePageDown(final int amount) {
        this.movePageDown(amount, false);
    }

    public void movePageDown(final int amount, final boolean keepSelection) {
        this.prepareSelection(keepSelection);
        this.cursorLine = Math.min(this.lines.size() - 1, this.cursorLine + Math.max(1, amount));
        this.cursorColumn = Math.min(this.cursorColumn, this.lineLength(this.cursorLine));
        this.clearSelectionIfCollapsed();
    }

    public boolean undo() {
        if (this.undoStack.isEmpty()) {
            return false;
        }

        this.redoStack.addLast(this.createSnapshot());
        this.restoreSnapshot(this.undoStack.removeLast());
        return true;
    }

    public boolean redo() {
        if (this.redoStack.isEmpty()) {
            return false;
        }

        this.undoStack.addLast(this.createSnapshot());
        this.restoreSnapshot(this.redoStack.removeLast());
        return true;
    }

    private void insertWithoutHistory(final String text) {
        final String normalized = normalizeLineSeparators(text);
        this.insertNormalizedInternal(normalized);
        this.undoStack.clear();
        this.redoStack.clear();
        this.clearSelection();
    }

    private void insertNormalizedInternal(final String normalized) {
        for (int index = 0; index < normalized.length(); index++) {
            final char current = normalized.charAt(index);
            if (current == '\n') {
                this.insertRawNewLineInternal();
            } else {
                this.lines.get(this.cursorLine).insert(this.cursorColumn, current);
                this.cursorColumn++;
            }
        }
    }

    private void insertNewLineInternal() {
        final String currentLine = this.getLine(this.cursorLine);
        final String beforeCursor = currentLine.substring(0, this.cursorColumn);
        final String afterCursor = currentLine.substring(this.cursorColumn);
        String indentation = leadingWhitespace(beforeCursor);
        if (beforeCursor.stripTrailing().endsWith(":")) {
            indentation += "    ";
        }

        final StringBuilder existingLine = this.lines.get(this.cursorLine);
        existingLine.setLength(0);
        existingLine.append(beforeCursor);

        final StringBuilder newLine = new StringBuilder(indentation);
        newLine.append(afterCursor);
        this.lines.add(this.cursorLine + 1, newLine);
        this.cursorLine++;
        this.cursorColumn = indentation.length();
    }

    private void insertRawNewLineInternal() {
        final String currentLine = this.getLine(this.cursorLine);
        final String beforeCursor = currentLine.substring(0, this.cursorColumn);
        final String afterCursor = currentLine.substring(this.cursorColumn);

        final StringBuilder existingLine = this.lines.get(this.cursorLine);
        existingLine.setLength(0);
        existingLine.append(beforeCursor);

        this.lines.add(this.cursorLine + 1, new StringBuilder(afterCursor));
        this.cursorLine++;
        this.cursorColumn = 0;
    }

    private void deleteSelectionInternal() {
        final SelectionRange range = this.selectionRange();
        if (range == null) {
            return;
        }
        this.deleteRangeInternal(range.start(), range.end());
    }

    private void deleteRangeInternal(final CursorPosition start, final CursorPosition end) {
        if (compare(start, end) == 0) {
            this.setCursorInternal(start);
            this.selectionAnchor = null;
            return;
        }

        if (start.line() == end.line()) {
            this.lines.get(start.line()).delete(start.column(), end.column());
        } else {
            final StringBuilder startLine = this.lines.get(start.line());
            final String suffix = this.getLine(end.line()).substring(end.column());
            startLine.delete(start.column(), startLine.length());
            startLine.append(suffix);
            for (int line = end.line(); line > start.line(); line--) {
                this.lines.remove(line);
            }
        }

        if (this.lines.isEmpty()) {
            this.lines.add(new StringBuilder());
        }

        this.setCursorInternal(start);
        this.selectionAnchor = null;
    }

    private void prepareSelection(final boolean keepSelection) {
        if (keepSelection) {
            if (this.selectionAnchor == null) {
                this.selectionAnchor = this.currentPosition();
            }
            return;
        }
        this.selectionAnchor = null;
    }

    private void clearSelectionIfCollapsed() {
        if (this.selectionAnchor != null && compare(this.selectionAnchor, this.currentPosition()) == 0) {
            this.selectionAnchor = null;
        }
    }

    private void collapseSelectionToStart() {
        final SelectionRange range = this.selectionRange();
        if (range == null) {
            return;
        }
        this.setCursorInternal(range.start());
        this.selectionAnchor = null;
    }

    private void collapseSelectionToEnd() {
        final SelectionRange range = this.selectionRange();
        if (range == null) {
            return;
        }
        this.setCursorInternal(range.end());
        this.selectionAnchor = null;
    }

    private SelectionRange selectionRange() {
        if (this.selectionAnchor == null) {
            return null;
        }

        final CursorPosition current = this.currentPosition();
        if (compare(this.selectionAnchor, current) == 0) {
            return null;
        }

        if (compare(this.selectionAnchor, current) < 0) {
            return new SelectionRange(this.selectionAnchor, current);
        }
        return new SelectionRange(current, this.selectionAnchor);
    }

    private CursorPosition currentPosition() {
        return new CursorPosition(this.cursorLine, this.cursorColumn);
    }

    private CursorPosition endOfDocument() {
        final int line = this.lines.size() - 1;
        return new CursorPosition(line, this.lineLength(line));
    }

    private CursorPosition clampPosition(final CursorPosition position) {
        final int line = Math.max(0, Math.min(position.line(), this.lines.size() - 1));
        final int column = Math.max(0, Math.min(position.column(), this.lineLength(line)));
        return new CursorPosition(line, column);
    }

    private void setCursorInternal(final CursorPosition position) {
        this.cursorLine = position.line();
        this.cursorColumn = position.column();
    }

    private int lineLength(final int line) {
        return this.lines.get(line).length();
    }

    private void pushUndoSnapshot() {
        this.undoStack.addLast(this.createSnapshot());
        while (this.undoStack.size() > MAX_HISTORY) {
            this.undoStack.removeFirst();
        }
        this.redoStack.clear();
    }

    private DocumentSnapshot createSnapshot() {
        return new DocumentSnapshot(this.getText(), this.cursorLine, this.cursorColumn, this.selectionAnchor);
    }

    private void restoreSnapshot(final DocumentSnapshot snapshot) {
        this.lines.clear();
        this.lines.add(new StringBuilder());
        this.cursorLine = 0;
        this.cursorColumn = 0;
        this.selectionAnchor = null;
        this.insertNormalizedInternal(snapshot.text());
        this.setCursorInternal(this.clampPosition(new CursorPosition(snapshot.cursorLine(), snapshot.cursorColumn())));
        this.selectionAnchor = snapshot.selectionAnchor() == null ? null : this.clampPosition(snapshot.selectionAnchor());
    }

    private static int compare(final CursorPosition left, final CursorPosition right) {
        if (left.line() != right.line()) {
            return Integer.compare(left.line(), right.line());
        }
        return Integer.compare(left.column(), right.column());
    }

    private static String normalizeLineSeparators(final String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String leadingWhitespace(final String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }

    public record CursorPosition(int line, int column) {
    }

    public record SelectionSegment(int startColumn, int endColumn) {
    }

    private record SelectionRange(CursorPosition start, CursorPosition end) {
    }

    private record DocumentSnapshot(String text, int cursorLine, int cursorColumn, CursorPosition selectionAnchor) {
    }
}
