package org.texttechnologylab.udav.generators;

import org.junit.jupiter.api.Test;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.SourceJson;
import org.texttechnologylab.udav.pipeline.JSONView;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CategoryNumber and TextFormatting fed from a JSON source, without a database: the source is a
 * JSONView, and only the setup steps run (writeToDB is what needs the DB).
 */
class JsonBackedGeneratorsTest {

    private static JSONView generatorConfig(String type, String id, Map<String, Object> settings) {
        return new JSONView(Map.of("id", id, "type", type, "settings", settings, "extends", List.of()));
    }

    private static final JSONView SOURCE_ENTRY = new JSONView(Map.of("id", "Source-json", "uri", "cats.json", "settings", Map.of()));

    private static CategoryNumber categoryNumber(Map<String, Object> settings, Object document) throws Exception {
        CategoryNumber g = new CategoryNumber("CategoryNumber-json", generatorConfig("CategoryNumber", "CategoryNumber-json", settings),
                SOURCE_ENTRY, GeneratorSettings.fromConfig(SOURCE_ENTRY), null);
        g.setSource(new SourceJson("sourcefilesJSON/cats.json", new JSONView(document)));
        g.setup_step1();
        g.setup_step2();
        return g;
    }

    private static TextFormatting textFormatting(Map<String, Object> settings, Object document) throws Exception {
        TextFormatting g = new TextFormatting("TextFormatting-json", generatorConfig("TextFormatting", "TextFormatting-json", settings),
                SOURCE_ENTRY, GeneratorSettings.fromConfig(SOURCE_ENTRY), null);
        g.setSource(new SourceJson("sourcefilesJSON/text.json", new JSONView(document)));
        g.setup_step1();
        g.setup_step2();
        return g;
    }

    @Test
    void categoryNumbersFromNestedFileMap() throws Exception {
        CategoryNumber g = categoryNumber(Map.of("colors", Map.of("NOUN", "#4e79a7")),
                Map.of("doc-1", Map.of("NOUN", 3, "VERB", 1.5), "doc-2", Map.of("NOUN", 2)));

        Map<String, Map<String, Double>> perFile = g.categoryNumbersPerFile();
        assertEquals(Map.of("NOUN", 3.0, "VERB", 1.5), perFile.get("doc-1"));
        assertEquals(Map.of("NOUN", 2.0), perFile.get("doc-2"));
        assertEquals(Color.decode("#4e79a7"), g.colorFor("NOUN"), "explicit colour wins");
        assertNotNull(g.colorFor("VERB"), "unlisted categories get a palette colour");
    }

    @Test
    void categoryNumbersFromFlatMapUseTheSourceFileAsFileLabel() throws Exception {
        CategoryNumber g = categoryNumber(Map.of(), Map.of("NOUN", 3, "VERB", 1));
        assertEquals(Map.of("cats.json", Map.of("NOUN", 3.0, "VERB", 1.0)), g.categoryNumbersPerFile());
    }

    @Test
    void categoryNumbersFromRowsWithKeysMappingLikeMapCoordinates() throws Exception {
        CategoryNumber g = categoryNumber(
                Map.of("keys", Map.of("category", "pos", "number", "count", "file", "doc")),
                List.of(Map.of("pos", "NOUN", "count", 5, "doc", "a"),
                        Map.of("pos", "NOUN", "count", 2, "doc", "a"),
                        Map.of("pos", "VERB", "count", 1, "doc", "b")));
        assertEquals(Map.of("NOUN", 7.0), g.categoryNumbersPerFile().get("a"), "rows of one category are summed");
        assertEquals(Map.of("VERB", 1.0), g.categoryNumbersPerFile().get("b"));
    }

    @Test
    void categoryNumbersRespectCategoryAndFileFilters() throws Exception {
        CategoryNumber g = categoryNumber(
                Map.of("categoriesBlacklist", List.of("verb"), "filesWhitelist", List.of("doc-1")),
                Map.of("doc-1", Map.of("NOUN", 3, "VERB", 1), "doc-2", Map.of("NOUN", 2)));
        assertEquals(Map.of("doc-1", Map.of("NOUN", 3.0)), g.categoryNumbersPerFile());
    }

    @Test
    void textFormattingBuildsOneLayerPerTypeWithStylesAndColours() throws Exception {
        TextFormatting g = textFormatting(
                Map.of("styles", Map.of("POS", "underline", "NamedEntity", "highlight"),
                        "style", "bold", "type", "Lemma",
                        "colors", Map.of("POS", Map.of("NOUN", "#ff0000"), "VERB", "#00ff00")),
                Map.of("text", "Dogs bark loud.",
                        "segments", List.of(
                                Map.of("begin", 5, "end", 9, "category", "VERB", "type", "POS"),
                                Map.of("begin", 0, "end", 4, "category", "NOUN", "type", "POS"),
                                Map.of("begin", 0, "end", 4, "category", "ANIMAL", "type", "NamedEntity"),
                                Map.of("begin", 10, "end", 14, "category", "VERB"))));

        assertEquals("Dogs bark loud.", g.getText());
        Map<String, List<int[]>> layers = g.segmentsByTypeForTest();
        assertEquals(3, layers.size());
        assertArrayEquals(new int[]{0, 4}, layers.get("POS").get(0), "segments are sorted by begin");
        assertArrayEquals(new int[]{5, 9}, layers.get("POS").get(1));
        assertEquals(1, layers.get("Lemma").size(), "untyped segments fall back to the type setting");

        assertEquals("underline", g.layerForTest("POS").get("style"));
        assertEquals("highlight", g.layerForTest("NamedEntity").get("style"));
        assertEquals("bold", g.layerForTest("Lemma").get("style"));

        @SuppressWarnings("unchecked") Map<String, Color> posColors = (Map<String, Color>) g.layerForTest("POS").get("colors");
        assertEquals(Color.RED, posColors.get("NOUN"), "per-layer colour");
        assertEquals(Color.GREEN, posColors.get("VERB"), "colour for all layers");
        @SuppressWarnings("unchecked") Map<String, Color> lemmaColors = (Map<String, Color>) g.layerForTest("Lemma").get("colors");
        assertEquals(Color.GREEN, lemmaColors.get("VERB"));
        @SuppressWarnings("unchecked") Map<String, Color> neColors = (Map<String, Color>) g.layerForTest("NamedEntity").get("colors");
        assertNotNull(neColors.get("ANIMAL"), "palette colour when nothing explicit is given");
    }

    @Test
    void textFormattingHonoursKeyRenaming() throws Exception {
        TextFormatting g = textFormatting(
                Map.of("textKey", "body", "segmentsKey", "spans",
                        "keys", Map.of("begin", "start", "end", "stop", "category", "label")),
                Map.of("body", "abc def", "spans", List.of(Map.of("start", 4, "stop", 7, "label", "X"))));
        assertEquals("abc def", g.getText());
        assertArrayEquals(new int[]{4, 7}, g.segmentsByTypeForTest().get("annotation").get(0));
    }

    @Test
    void textFormattingRejectsDocumentsWithoutText() {
        assertThrows(IllegalArgumentException.class, () -> textFormatting(Map.of(), List.of(1, 2, 3)));
        assertThrows(IllegalArgumentException.class, () -> textFormatting(Map.of(), Map.of("segments", List.of())));
    }
}
