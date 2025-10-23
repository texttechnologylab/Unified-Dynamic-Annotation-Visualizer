package org.texttechnologylab.udav.sources;

import java.util.Map;

public class AnnotationFeatures {
    public static Map<String, String> featureNames_default(Map<String, String> featureNames) {
        featureNames.put("xmi:id", "xmi:id");
        featureNames.put("sofa", "sofa");
        featureNames.put("value", "value");
        featureNames.put("begin", "begin");
        featureNames.put("end", "end");
        featureNames.put("PosValue", "PosValue");
        featureNames.put("coarseValue", "coarseValue");
        return featureNames;
    }
}
