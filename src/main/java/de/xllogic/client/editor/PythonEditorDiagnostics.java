package de.xllogic.client.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PythonEditorDiagnostics {
    public DiagnosticReport analyze(final TextDocument document) {
        final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        final Deque<BracketState> brackets = new ArrayDeque<>();
        int indentExpectation = -1;
        int indentSourceLine = -1;

        for (int lineIndex = 0; lineIndex < document.getLineCount(); lineIndex++) {
            final String line = document.getLine(lineIndex);
            final String trimmed = line.trim();
            final boolean meaningfulLine = !trimmed.isEmpty() && !trimmed.startsWith("#");
            final int indent = leadingWhitespaceWidth(line);

            if (meaningfulLine && indentExpectation >= 0 && indent <= indentExpectation) {
                diagnostics.add(new Diagnostic(lineIndex, 0, Math.max(1, indent), Severity.ERROR,
                        "Block nach ':' muss eingerueckt sein. Erwartet mehr Einrueckung als in Zeile " + (indentSourceLine + 1) + "."));
                indentExpectation = -1;
                indentSourceLine = -1;
            } else if (meaningfulLine && indentExpectation >= 0) {
                indentExpectation = -1;
                indentSourceLine = -1;
            }

            final int initialDepth = brackets.size();
            final LineScanResult result = this.scanLine(lineIndex, line, brackets, diagnostics);
            if (meaningfulLine && result.blockOpener() && brackets.size() == initialDepth) {
                indentExpectation = indent;
                indentSourceLine = lineIndex;
            }
        }

        while (!brackets.isEmpty()) {
            final BracketState bracket = brackets.removeLast();
            diagnostics.add(new Diagnostic(bracket.line(), bracket.column(), bracket.column() + 1, Severity.ERROR,
                    "Nicht geschlossene Klammer '" + bracket.opening() + "'."));
        }

        return new DiagnosticReport(List.copyOf(diagnostics));
    }

    private LineScanResult scanLine(final int lineIndex, final String line, final Deque<BracketState> brackets, final List<Diagnostic> diagnostics) {
        char lastMeaningful = 0;
        int index = 0;
        while (index < line.length()) {
            final char current = line.charAt(index);
            if (current == '#') {
                break;
            }

            if (current == '\'' || current == '"') {
                final int end = findStringEnd(line, index, current);
                if (end < 0) {
                    diagnostics.add(new Diagnostic(lineIndex, index, Math.max(index + 1, line.length()), Severity.ERROR, "Nicht geschlossener String."));
                    break;
                }
                lastMeaningful = 's';
                index = end;
                continue;
            }

            if (isOpeningBracket(current)) {
                brackets.addLast(new BracketState(current, lineIndex, index));
                lastMeaningful = current;
            } else if (isClosingBracket(current)) {
                if (brackets.isEmpty()) {
                    diagnostics.add(new Diagnostic(lineIndex, index, index + 1, Severity.ERROR, "Unerwartete schliessende Klammer '" + current + "'."));
                } else {
                    final BracketState top = brackets.peekLast();
                    if (matches(top.opening(), current)) {
                        brackets.removeLast();
                    } else {
                        brackets.removeLast();
                        diagnostics.add(new Diagnostic(lineIndex, index, index + 1, Severity.ERROR,
                                "Klammer '" + current + "' passt nicht zu '" + top.opening() + "' aus Zeile " + (top.line() + 1) + "."));
                    }
                }
                lastMeaningful = current;
            } else if (!Character.isWhitespace(current)) {
                lastMeaningful = current;
            }
            index++;
        }
        return new LineScanResult(lastMeaningful == ':');
    }

    private static int leadingWhitespaceWidth(final String line) {
        int width = 0;
        while (width < line.length() && Character.isWhitespace(line.charAt(width))) {
            width++;
        }
        return width;
    }

    private static boolean isOpeningBracket(final char value) {
        return value == '(' || value == '[' || value == '{';
    }

    private static boolean isClosingBracket(final char value) {
        return value == ')' || value == ']' || value == '}';
    }

    private static boolean matches(final char opening, final char closing) {
        return opening == '(' && closing == ')'
                || opening == '[' && closing == ']'
                || opening == '{' && closing == '}';
    }

    private static int findStringEnd(final String line, final int start, final char delimiter) {
        int index = start + 1;
        boolean escaped = false;
        while (index < line.length()) {
            final char current = line.charAt(index);
            if (current == delimiter && !escaped) {
                return index + 1;
            }
            escaped = current == '\\' && !escaped;
            if (current != '\\') {
                escaped = false;
            }
            index++;
        }
        return -1;
    }

    public record Diagnostic(int line, int startColumn, int endColumn, Severity severity, String message) {
    }

    public record DiagnosticReport(List<Diagnostic> diagnostics) {
        public Map<Integer, List<Diagnostic>> byLine() {
            final Map<Integer, List<Diagnostic>> diagnosticsByLine = new LinkedHashMap<>();
            for (final Diagnostic diagnostic : this.diagnostics) {
                diagnosticsByLine.computeIfAbsent(diagnostic.line(), ignored -> new ArrayList<>()).add(diagnostic);
            }
            return diagnosticsByLine;
        }

        public boolean hasErrors() {
            return !this.diagnostics.isEmpty();
        }
    }

    public enum Severity {
        ERROR
    }

    private record BracketState(char opening, int line, int column) {
    }

    private record LineScanResult(boolean blockOpener) {
    }
}
