package org.texttechnologylab.udav.importer.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility service for sanitizing (cleaning) identifiers to conform to Java and SQL naming rules.
 */
@Service
public class NameSanitizer {
    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while"
    ));
    private static final Set<String> SQL_RESERVED = Set.of(
            "null", "select", "from", "table", "order", "group", "by", "user", "timestamp", "value"
    );

    /**
     * Sanitize an arbitrary string into a valid identifier:
     * <ul>
     *   <li>Replace non-alphanumeric and non-underscore characters with underscores.</li>
     *   <li>Prefix with underscore if empty or starting with digit.</
     * </ul>
     *
     * @param name original string
     * @return sanitized identifier
     */
    public String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    /**
     * Convert a tag (possibly namespaced by colon) into a Java class name:
     * <ul>
     *   <li>Special-case "cas:NULL" to "CAS".</li>
     *   <li>Split on colon, take last segment.</li>
     *   <li>Capitalize first character.</li>
     * </ul>
     *
     * @param tag namespaced tag string
     * @return valid Java class name
     */
    public String toClassName(String tag) {
        if ("cas:NULL".equals(tag)) {
            return "CAS";
        }
        String[] parts = tag.split(":");
        String base = parts[parts.length - 1];
        return base.substring(0, 1).toUpperCase() + base.substring(1);
    }
}
