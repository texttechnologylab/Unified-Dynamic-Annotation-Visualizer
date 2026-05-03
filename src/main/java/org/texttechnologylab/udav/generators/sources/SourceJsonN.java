package org.texttechnologylab.udav.generators.sources;

import org.texttechnologylab.udav.sources.DBAccess;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SourceJsonN extends SourceJson implements SourceN {

    private final Map<String, Source> subSources;

    public SourceJsonN(String filepath, DBAccess dbAccess) throws IOException, SQLException {
        super(filepath, dbAccess);
        this.subSources = new LinkedHashMap<>();
        Map<String, Object> map = singleFileJSONView.asMap();
        for (String key : map.keySet()) subSources.put(key, new SourceJson(singleFileName, singleFileJSONView.get(key)));
    }

    @Override
    public Map<String, Source> getSubSourcesIdToObjectMap() {
        return subSources;
    }
}
