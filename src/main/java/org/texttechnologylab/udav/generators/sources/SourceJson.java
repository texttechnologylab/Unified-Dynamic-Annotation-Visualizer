package org.texttechnologylab.udav.generators.sources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.texttechnologylab.udav.pipeline.JSONView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
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

    public List<Map<String, Object>> generateFlatKeysMap(Map<String, Object> keysMapRoot) {
        ArrayList<Map<String, Object>> flatKeysMap = new ArrayList<>();
        if (keysMapRoot == null) return flatKeysMap;
        generateFlatKeysMapRecursive(keysMapRoot, singleFileJSONView, flatKeysMap);
        return flatKeysMap;
    }
    private void generateFlatKeysMapRecursive(Map<String, Object> keysMapCurrentPosition, JSONView currentPosition, ArrayList<Map<String, Object>> flatKeysMap) {
        for (Map.Entry<String, Object> entry : keysMapCurrentPosition.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (String.class.equals(value.getClass())) {
                List valueNode = (List) currentPosition.get(key).getNode();
                String stringValue = ((String) value).trim();
                int valueNodeSize = valueNode.size();
                if (stringValue.contains("@")) {
                    String[] split = stringValue.split("@");
                    for (int i = 0; i < valueNodeSize; i++) {
                        Map<String, Object> innerMap;
                        if (flatKeysMap.size() < i + 1) {
                            innerMap = new HashMap<>();
                            flatKeysMap.add(innerMap);
                        } else {
                            innerMap = flatKeysMap.get(i);
                        }
                        Map<String, Object> innerValueMap = (Map<String, Object>) innerMap.get(split[0]);
                        if (innerValueMap == null) {
                            innerValueMap = new HashMap<>();
                            innerMap.put(split[0], innerValueMap);
                        }
                        innerValueMap.put(split[1], valueNode.get(i));
                    }
                } else {
                    for (int i = 0; i < valueNodeSize; i++) {
                        Map<String, Object> innerMap;
                        if (flatKeysMap.size() < i + 1) {
                            innerMap = new HashMap<>();
                            flatKeysMap.add(innerMap);
                        } else {
                            innerMap = flatKeysMap.get(i);
                        }
                        innerMap.put(stringValue, valueNode.get(i));
                    }
                }
            } else if (value instanceof List) {
                List valueNode = (List) currentPosition.get(key).getNode();
                List listValue = (List) value;
                int valueNodeSize = valueNode.size();
                int listSize = listValue.size();
                for (int listIndex = 0; listIndex < listSize; listIndex++) {
                    String stringValue = (String) listValue.get(listIndex);
                    if (stringValue.contains("@")) {
                        String[] split = stringValue.split("@");
                        for (int i = 0; i < valueNodeSize; i++) {
                            Map<String, Object> innerMap;
                            if (flatKeysMap.size() < i + 1) {
                                innerMap = new HashMap<>();
                                flatKeysMap.add(innerMap);
                            } else {
                                innerMap = flatKeysMap.get(i);
                            }
                            Map<String, Object> innerValueMap = (Map<String, Object>) innerMap.get(split[0]);
                            if (innerValueMap == null) {
                                innerValueMap = new HashMap<>();
                                innerMap.put(split[0], innerValueMap);
                            }
                            List subListValueNode = (List) valueNode.get(i);
                            innerValueMap.put(split[1], subListValueNode.get(listIndex));
                        }
                    } else {
                        for (int i = 0; i < valueNodeSize; i++) {
                            Map<String, Object> innerMap;
                            if (flatKeysMap.size() < i + 1) {
                                innerMap = new HashMap<>();
                                flatKeysMap.add(innerMap);
                            } else {
                                innerMap = flatKeysMap.get(i);
                            }
                            List subListValueNode = (List) valueNode.get(i);
                            innerMap.put(stringValue, subListValueNode.get(listIndex));
                        }
                    }
                }
            } else if (value instanceof Map) {
                generateFlatKeysMapRecursive((Map<String, Object>) value, currentPosition.get(key), flatKeysMap);
            }
        }
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
