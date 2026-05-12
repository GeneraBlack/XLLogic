package de.xllogic.client.editor;

public enum TokenStyle {
    DEFAULT(0xFFD0D7DE),
    KEYWORD(0xFFF0C674),
    STRING(0xFF98C379),
    COMMENT(0xFF6A737D),
    NUMBER(0xFFD19A66),
    BUILTIN(0xFF61AFEF),
    DECORATOR(0xFFC678DD);

    private final int color;

    TokenStyle(final int color) {
        this.color = color;
    }

    public int color() {
        return this.color;
    }
}
