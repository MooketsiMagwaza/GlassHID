package com.nido.a05sinput;

/** Converts printable US-layout characters into USB HID keyboard usages. */
final class KeyStroke {
    final int modifier;
    final int usage;

    private KeyStroke(int modifier, int usage) {
        this.modifier = modifier;
        this.usage = usage;
    }

    static KeyStroke forChar(char c) {
        if (c >= 'a' && c <= 'z') return new KeyStroke(0, 0x04 + c - 'a');
        if (c >= 'A' && c <= 'Z') return new KeyStroke(0x02, 0x04 + c - 'A');
        if (c >= '1' && c <= '9') return new KeyStroke(0, 0x1E + c - '1');
        if (c == '0') return new KeyStroke(0, 0x27);
        switch (c) {
            case '\n': return new KeyStroke(0, 0x28);
            case '\t': return new KeyStroke(0, 0x2B);
            case ' ': return new KeyStroke(0, 0x2C);
            case '-': return new KeyStroke(0, 0x2D);
            case '_': return new KeyStroke(0x02, 0x2D);
            case '=': return new KeyStroke(0, 0x2E);
            case '+': return new KeyStroke(0x02, 0x2E);
            case '[': return new KeyStroke(0, 0x2F);
            case '{': return new KeyStroke(0x02, 0x2F);
            case ']': return new KeyStroke(0, 0x30);
            case '}': return new KeyStroke(0x02, 0x30);
            case '\\': return new KeyStroke(0, 0x31);
            case '|': return new KeyStroke(0x02, 0x31);
            case ';': return new KeyStroke(0, 0x33);
            case ':': return new KeyStroke(0x02, 0x33);
            case '\'': return new KeyStroke(0, 0x34);
            case '"': return new KeyStroke(0x02, 0x34);
            case '`': return new KeyStroke(0, 0x35);
            case '~': return new KeyStroke(0x02, 0x35);
            case ',': return new KeyStroke(0, 0x36);
            case '<': return new KeyStroke(0x02, 0x36);
            case '.': return new KeyStroke(0, 0x37);
            case '>': return new KeyStroke(0x02, 0x37);
            case '/': return new KeyStroke(0, 0x38);
            case '?': return new KeyStroke(0x02, 0x38);
            case '!': return new KeyStroke(0x02, 0x1E);
            case '@': return new KeyStroke(0x02, 0x1F);
            case '#': return new KeyStroke(0x02, 0x20);
            case '$': return new KeyStroke(0x02, 0x21);
            case '%': return new KeyStroke(0x02, 0x22);
            case '^': return new KeyStroke(0x02, 0x23);
            case '&': return new KeyStroke(0x02, 0x24);
            case '*': return new KeyStroke(0x02, 0x25);
            case '(': return new KeyStroke(0x02, 0x26);
            case ')': return new KeyStroke(0x02, 0x27);
            default: return null;
        }
    }
}
