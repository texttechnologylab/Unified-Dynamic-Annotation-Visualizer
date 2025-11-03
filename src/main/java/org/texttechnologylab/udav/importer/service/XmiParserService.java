package org.texttechnologylab.udav.importer.service;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.texttechnologylab.udav.importer.EntityRecord;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for parsing XMI files into EntityRecord instances.
 */
@Service
public class XmiParserService {
    private final NameSanitizer nameSanitizer;
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    /**
     * Construct service with a NameSanitizer.
     *
     * @param nameSanitizer utility for sanitizing tag and attribute names
     */
    public XmiParserService(NameSanitizer nameSanitizer) {
        this.nameSanitizer = nameSanitizer;
    }

    /**
     * Parse the given XMI file into a list of EntityRecord objects.
     * <ul>
     *   <li>Builds a DOM Document from the file.</li>
     *   <li>Iterates element children of the document root.</li>
     *   <li>Converts tag names to class names via NameSanitizer.</li>
     *   <li>Includes the filename as an attribute "filename".</li>
     *   <li>Sanitizes each XML attribute name and collects its value.</li>
     *   <li>Creates and returns one EntityRecord per element.</li>
     * </ul>
     *
     * @param file Path to the XMI file
     * @return list of parsed EntityRecord objects
     * @throws Exception if parsing or I/O fails
     */
    public List<EntityRecord> parse(Path file) throws Exception {
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file.toFile());
        NodeList nodes = document.getDocumentElement().getChildNodes();
        List<EntityRecord> records = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) node;
            String entity = nameSanitizer.toClassName(element.getTagName());
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("filename", file.getFileName().toString());
            NamedNodeMap rawAttributes = element.getAttributes();
            for (int j = 0; j < rawAttributes.getLength(); j++) {
                String raw = rawAttributes.item(j).getNodeName();
                String col = nameSanitizer.sanitize(raw);
                attributes.put(col, element.getAttribute(raw));
            }
            records.add(new EntityRecord(entity, attributes));
        }
        return records;
    }
}
