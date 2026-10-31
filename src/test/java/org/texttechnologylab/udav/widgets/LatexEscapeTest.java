package org.texttechnologylab.udav.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatexEscapeTest {

    @Test
    void backslashKeepsItsBraces() {
        assertEquals("a\\textbackslash{}b", Widget.escapeLatex("a\\b"));
    }

    @Test
    void specialCharacters() {
        assertEquals("\\{x\\} 50\\% \\$ \\& \\# a\\_b \\textasciitilde{} \\textasciicircum{} \\textless{}\\textgreater{}",
                Widget.escapeLatex("{x} 50% $ & # a_b ~ ^ <>"));
        assertEquals("", Widget.escapeLatex(null));
    }
}
