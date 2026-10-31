package org.texttechnologylab.udav.widgets.svgtolatex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SVG posted to /api/convertions/tikz comes from the client, so entities must not pull local
 * files into the generated document.
 */
class VecTikZConverterXmlTest {

    private static final String SECRET = "xxeSecret4711";

    @TempDir
    Path dir;

    private String secretUri() throws Exception {
        return Files.writeString(dir.resolve("secret.txt"), SECRET).toUri().toString();
    }

    private static String svg(String doctype, String body) {
        return "<?xml version=\"1.0\"?>" + doctype
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\">" + body + "</svg>";
    }

    @Test
    void externalEntityIsNotResolved() throws Exception {
        String tex = new VecTikZConverter().convert(svg(
                "<!DOCTYPE svg [<!ENTITY x SYSTEM \"" + secretUri() + "\">]>",
                "<text x=\"10\" y=\"20\">visible&x;</text>"));

        assertTrue(tex.contains("visible"));
        assertFalse(tex.contains(SECRET));
    }

    @Test
    void externalParameterEntityIsNotLoaded() throws Exception {
        String dtd = Files.writeString(dir.resolve("defs.dtd"), "<!ENTITY y \"" + SECRET + "\">").toUri().toString();

        String tex = new VecTikZConverter().convert(svg(
                "<!DOCTYPE svg [<!ENTITY % defs SYSTEM \"" + dtd + "\"> %defs;]>",
                "<text x=\"10\" y=\"20\">visible&y;</text>"));

        assertTrue(tex.contains("visible"));
        assertFalse(tex.contains(SECRET));
    }

    @Test
    void internalEntitiesStillWork() throws Exception {
        String tex = new VecTikZConverter().convert(svg(
                "<!DOCTYPE svg [<!ENTITY label \"Hello\">]>",
                "<text x=\"10\" y=\"20\">&label;</text>"));

        assertTrue(tex.contains("Hello"));
    }

    @Test
    void referencedDocumentIsParsedTheSameWay() throws Exception {
        Files.writeString(dir.resolve("other.svg"), svg(
                "<!DOCTYPE svg [<!ENTITY x SYSTEM \"" + secretUri() + "\">]>",
                "<g id=\"g1\"><text x=\"10\" y=\"20\">visible&x;</text></g>"));

        String tex = new VecTikZConverter().convert(svg("", "<use href=\"other.svg#g1\"/>"), dir);

        assertTrue(tex.contains("visible"));
        assertFalse(tex.contains(SECRET));
    }
}
