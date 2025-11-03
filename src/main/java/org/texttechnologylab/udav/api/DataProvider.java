package org.texttechnologylab.udav.api;

import java.util.Map;

public interface DataProvider {
    Map<String, Object> lookup(String type, String id);
}
