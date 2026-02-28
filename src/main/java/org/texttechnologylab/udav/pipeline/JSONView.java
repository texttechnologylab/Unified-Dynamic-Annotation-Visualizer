package org.texttechnologylab.udav.pipeline;

import lombok.Getter;
import org.springframework.lang.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;


public class JSONView implements Iterable<JSONView> {

    @Getter
    private final Object node;

    public JSONView(Object node) {
        this.node = node;
    }


    /**
     * Is the current node a Map?
     */
    public boolean isMap() {
        return node instanceof Map<?, ?>;
    }

    /**
     * Is the current node a List?
     */
    public boolean isList() {
        return node instanceof List<?>;
    }

    /**
     * Is the current node a primitive or null?
     */
    public boolean isValue() {
        return !isMap() && !isList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap() {
        if (!isMap()) {
            throw new IllegalStateException("Not a JSON object: " + node);
        }
        return (Map<String, Object>) node;
    }


    @SuppressWarnings("unchecked")
    public List<Object> asList() {
        if (!isList()) {
            throw new IllegalStateException("Not a JSON array: " + node);
        }
        return (List<Object>) node;
    }


    public JSONView get(String key) {
        Map<String, Object> m = asMap();
        if (!m.containsKey(key)) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
        return new JSONView(m.get(key));
    }


    public JSONView get(int index) {
        List<Object> l = asList();
        if (index < 0 || index >= l.size()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for list of size " + l.size());
        }
        return new JSONView(l.get(index));
    }

    /**
     * Returns true iff this node is a JSON object (Map) and contains the given key.
     */
    public boolean has(String key) {
        if (key == null) return false;
        if (!isMap()) return false;
        return asMap().containsKey(key);
    }

    /**
     * Like has(String) but also checks that the value is not null.
     */
    public boolean hasNonNull(String key) {
        if (!has(key)) return false;
        return asMap().get(key) != null;
    }

    /**
     * Returns the raw node (could be Map, List, String, Number, Boolean, or null).
     */
    public Object raw() {
        return node;
    }


    /**
     * Iterable: if this is a list, iterate its elements as JSONViews, otherwise empty.
     */
    @Override
    @NonNull
    public Iterator<JSONView> iterator() {
        if (!isList()) {
            return Collections.emptyIterator();
        }
        Iterator<Object> rawIt = asList().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return rawIt.hasNext();
            }

            @Override
            public JSONView next() {
                return new JSONView(rawIt.next());
            }
        };
    }

    /**
     * Returns a Stream of JSONView elements if this node is a list, otherwise an empty.
     */
    public Stream<JSONView> stream() {
        if (!isList()) {
            return Stream.empty();
        }
        return asList().stream().map(JSONView::new);
    }


    public String toJson(boolean pretty) {
        return toJson(node, pretty, 0);
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object obj, boolean pretty, int indent) {
        StringBuilder b = new StringBuilder();
        String indentStr = pretty ? " ".repeat(indent) : "";
        String childIndentStr = pretty ? " ".repeat(indent + 2) : "";

        if (obj instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) obj;
            b.append("{");
            if (pretty && !map.isEmpty()) b.append("\n");

            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) b.append(pretty ? ",\n" : ",");
                first = false;

                if (pretty) b.append(childIndentStr);
                b.append("\"").append(escape(entry.getKey())).append("\":");
                if (pretty) b.append(" ");
                b.append(toJson(entry.getValue(), pretty, indent + 2));
            }

            if (pretty && !map.isEmpty()) {
                b.append("\n").append(indentStr);
            }
            b.append("}");
        } else if (obj instanceof List<?>) {
            List<Object> list = (List<Object>) obj;
            b.append("[");
            if (pretty && !list.isEmpty()) b.append("\n");

            boolean first = true;
            for (Object item : list) {
                if (!first) b.append(pretty ? ",\n" : ",");
                first = false;

                if (pretty) b.append(childIndentStr);
                b.append(toJson(item, pretty, indent + 2));
            }

            if (pretty && !list.isEmpty()) {
                b.append("\n").append(indentStr);
            }
            b.append("]");
        } else if (obj instanceof String) {
            b.append("\"").append(escape((String) obj)).append("\"");
        } else if (obj instanceof Number || obj instanceof Boolean) {
            b.append(obj);
        } else if (obj == null) {
            b.append("null");
        } else {
            b.append("\"").append(escape(obj.toString())).append("\"");
        }

        return b.toString();
    }


    @Override
    public String toString() {
        if (isValue() || node == null) {
            return Objects.toString(node);
        }
        return node.toString();
    }


    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        // Write control characters as \\u00XX
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
