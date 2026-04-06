package org.texttechnologylab.udav.generators.sources;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SourceJsonN extends SourceJson implements SourceN {

    private final Map<String, Source> subSources;

    public SourceJsonN(String filepath) throws IOException {
        super(filepath);
        this.subSources = new LinkedHashMap<>();
        Map<String, Object> map = singleFileJSONView.asMap();
        for (String key : map.keySet()) subSources.put(key, new SourceJson(singleFileName, singleFileJSONView.get(key)));
    }

    @Override
    public Map<String, Source> getSubSourcesIdToObjectMap() {
        return subSources;
    }
}
