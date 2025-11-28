package org.texttechnologylab.udav.generators.sources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.texttechnologylab.udav.pipeline.JSONView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class SourceJson extends Source {

    public static final String SOURCE_FILES_PATH = "sourcefilesJSON";

    protected final String singleFileName;
    protected final JSONView singleFileJSONView;
    protected final Map<String, JSONView> filenameToJsonView; // TODO: Use or remove

    public SourceJson(String filepath) throws IOException {
        this.singleFileName = SOURCE_FILES_PATH + "/" + filepath.trim();
        this.singleFileJSONView = readJsonViewFromFile(singleFileName);
        this.filenameToJsonView = null;
    }
    public SourceJson(String normalizedFilepath, JSONView jsonView) {
        this.singleFileName = normalizedFilepath;
        this.singleFileJSONView = jsonView;
        this.filenameToJsonView = null;
    }

    public static List<Map<String, Object>> generateFlatKeysMap(Map<String, Object> keysMapRoot) {
        return null;
    }

    private static JSONView readJsonViewFromFile(String path) throws IOException {
        try (InputStream in = SourceJson.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("File not found: " + path);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(in);

            if (!rootNode.isObject()) {
                throw new IllegalArgumentException("Invalid JSON.");
            }

            Map<String, Object> rootMap = mapper.convertValue(rootNode, new TypeReference<>() {
            });
            return new JSONView(rootMap);
        }
    }
}
