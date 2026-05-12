package de.xllogic.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PythonSyntaxHighlighter {
    private static final Set<String> KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def", "del",
            "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "match", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True",
            "try", "while", "with", "yield"
    );

    private static final Set<String> BUILTINS = Set.of(
            "abs", "all", "any", "bool", "dict", "enumerate", "float", "int", "len", "list", "max", "min",
            "print", "range", "set", "sorted", "str", "sum", "tuple", "zip"
    );

    public static Set<String> keywords() {
        return KEYWORDS;
    }

    public static Set<String> builtins() {
        return BUILTINS;
    }

    public List<SyntaxToken> highlight(final String line) {
        final List<SyntaxToken> tokens = new ArrayList<>();
        int index = 0;

        while (index < line.length()) {
            final TokenMatch match = this.matchToken(line, index);
            if (match == null) {
                index++;
            } else {
                if (match.token() != null) {
                    tokens.add(match.token());
                }
                index = match.nextIndex();
                if (match.stopAfterMatch()) {
                    break;
                }
            }
        }

        return tokens;
    }

    private TokenMatch matchToken(final String line, final int index) {
        final char current = line.charAt(index);
        if (current == '#') {
            return new TokenMatch(new SyntaxToken(index, line.length(), TokenStyle.COMMENT), line.length(), true);
        }
        if (current == '"' || current == '\'') {
            final int end = findStringEnd(line, index, current);
            return new TokenMatch(new SyntaxToken(index, end, TokenStyle.STRING), end, false);
        }
        if (current == '@' && index + 1 < line.length() && isIdentifierStart(line.charAt(index + 1))) {
            final int end = findIdentifierEnd(line, index + 1);
            return new TokenMatch(new SyntaxToken(index, end, TokenStyle.DECORATOR), end, false);
        }
        if (Character.isDigit(current)) {
            final int end = findNumberEnd(line, index);
            return new TokenMatch(new SyntaxToken(index, end, TokenStyle.NUMBER), end, false);
        }
        if (isIdentifierStart(current)) {
            final int end = findIdentifierEnd(line, index);
            final TokenStyle style = identifierStyle(line.substring(index, end));
            final SyntaxToken token = style == null ? null : new SyntaxToken(index, end, style);
            return new TokenMatch(token, end, false);
        }
        return null;
    }

    private static TokenStyle identifierStyle(final String identifier) {
        if (KEYWORDS.contains(identifier)) {
            return TokenStyle.KEYWORD;
        }
        if (BUILTINS.contains(identifier)) {
            return TokenStyle.BUILTIN;
        }
        return null;
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
        return line.length();
    }

    private static int findNumberEnd(final String line, final int start) {
        int index = start + 1;
        while (index < line.length()) {
            final char current = line.charAt(index);
            if (!Character.isDigit(current) && current != '.' && current != '_') {
                break;
            }
            index++;
        }
        return index;
    }

    private static int findIdentifierEnd(final String line, final int start) {
        int index = start + 1;
        while (index < line.length()) {
            final char current = line.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '_') {
                break;
            }
            index++;
        }
        return index;
    }

    private static boolean isIdentifierStart(final char value) {
        return Character.isLetter(value) || value == '_';
    }

    private record TokenMatch(SyntaxToken token, int nextIndex, boolean stopAfterMatch) {
    }
}
