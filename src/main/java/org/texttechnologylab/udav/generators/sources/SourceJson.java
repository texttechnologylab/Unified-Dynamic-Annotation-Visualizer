package org.texttechnologylab.udav.generators.sources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.db.SchemaObjectNames;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
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

    public SourceJson(String filepath, DBAccess dbAccess) throws IOException, SQLException {
        this.singleFileName = SOURCE_FILES_PATH + "/" + filepath.trim();
        this.singleFileJSONView = readJsonViewFromDB(singleFileName, dbAccess);
        this.filenameToJsonView = null;
    }
    public SourceJson(String normalizedFilepath, JSONView jsonView) {
        this.singleFileName = normalizedFilepath;
        this.singleFileJSONView = jsonView;
        this.filenameToJsonView = null;
    }

    public List<Map<String, Object>> generateKeysMap(GeneratorSettings settings) {

        Map<String, Object> map_keysMap = (Map<String, Object>) settings.getMapSettingOrDefault("keysMap", null);
        List<Map<String, Object>> keysMap;
        if (map_keysMap == null) {
            map_keysMap = (Map<String, Object>) settings.getMapSettingOrDefault("keys", null);
            keysMap = generateFlatKeys(map_keysMap);
        } else {
            keysMap = generateFlatKeysMap(map_keysMap);
        }
        addFixedKeysToKeysMap(keysMap, (Map<String, Object>) settings.getMapSettingOrDefault("fixedKeys", null));

        return keysMap;
    }

    private void addFixedKeysToKeysMap(List<Map<String, Object>> keysMap, Map<String, Object> fixedKeys) {
        if (fixedKeys == null) return;
        for (Map<String, Object> m : keysMap) {
            m.putAll(fixedKeys);
        }
    }

    private List<Map<String, Object>> generateFlatKeys(Map<String, Object> keysRoot) {
        ArrayList<Map<String, Object>> flatKeysMap = new ArrayList<>();
        if (keysRoot == null) return flatKeysMap;
        List nodes = (List) singleFileJSONView.getNode();
        for (Object m : nodes) {
            HashMap<String, Object> currentMap = new HashMap<>();
            Map<String, Object> map = (Map<String, Object>) m;
            for (Map.Entry<String, Object> entry : keysRoot.entrySet()) {
                String key = entry.getKey();
                if (entry.getValue() instanceof List) {
                    List valueList = (List) entry.getValue();
                    int i = 0;
                    HashMap<String, Object> innerMap = new HashMap<>();
                    for (Object e : valueList) {
                        String value = (String) e;
                        Object foundVal = map.get(value);
                        innerMap.put(Integer.toString(i), foundVal);
                        i++;
                    }
                    currentMap.put(key, innerMap);
                } else {
                    String value = (String) entry.getValue();
                    Object foundVal = map.get(value);
                    currentMap.put(key, foundVal);
                }
            }
            flatKeysMap.add(currentMap);
        }
        return flatKeysMap;
    }

    private List<Map<String, Object>> generateFlatKeysMap(Map<String, Object> keysMapRoot) {
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

    private static JSONView readJsonViewFromDB(String path, DBAccess dbAccess) throws IOException, SQLException {
        if (dbAccess == null) {
            throw new IllegalArgumentException("dbAccess must not be null");
        }

        String sourceFileName = path.trim();
        int slashIdx = sourceFileName.lastIndexOf('/');
        if (slashIdx >= 0 && slashIdx < sourceFileName.length() - 1) {
            sourceFileName = sourceFileName.substring(slashIdx + 1);
        }

        String json = readJsonString(dbAccess, sourceFileName);
        if (json == null && !"public".equalsIgnoreCase(dbAccess.getSchema())) {
            json = readJsonString(dbAccess, "public", sourceFileName);
        }
        if (json == null) {
            throw new IllegalArgumentException("JSON source not found in DB table \"" + SchemaObjectNames.TABLE_JSON_DATA + "\": " + sourceFileName);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(json);

        Object rootValue;
        if (rootNode.isObject()) {
            rootValue = mapper.convertValue(rootNode, new TypeReference<Map<String, Object>>() {});
        } else if (rootNode.isArray()) {
            rootValue = mapper.convertValue(rootNode, new TypeReference<List<Object>>() {});
        } else {
            rootValue = mapper.convertValue(rootNode, Object.class);
        }
        return new JSONView(rootValue);
    }

    private static String readJsonString(DBAccess dbAccess, String sourceFileName) throws SQLException {
        return readJsonString(dbAccess, dbAccess.getSchema(), sourceFileName);
    }

    private static String readJsonString(DBAccess dbAccess, String schema, String sourceFileName) throws SQLException {
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);
            Table<?> table = DSL.table(DSL.name(schema, SchemaObjectNames.TABLE_JSON_DATA));
            Field<String> fName = DSL.field(DSL.name(schema, SchemaObjectNames.TABLE_JSON_DATA, SchemaObjectNames.COL_JSON_DATA_SOURCEFILE_NAME), String.class);
            Field<String> fJson = DSL.field(DSL.name(schema, SchemaObjectNames.TABLE_JSON_DATA, SchemaObjectNames.COL_JSON_DATA_JSON), String.class);
            return dsl.select(fJson).from(table).where(fName.eq(sourceFileName)).fetchOne(fJson);
        } catch (org.jooq.exception.DataAccessException e) {
            return null;
        }
    }
}
