package org.texttechnologylab.udav.database;

/**
 * Maps domain table and field names to database-compliant identifiers
 * by normalizing case and replacing illegal characters.
 */
public class NameMapper {

    /**
     * Map a field name to its corresponding database column name.
     * <ul>
     *   <li>Converts the field name to uppercase.</li>
     *   <li>Replaces ':' characters with underscores.</li>
     *   <li>Prefixes the normalized field name with the uppercase table name and an underscore.</li>
     * </ul>
     *
     * @param table the domain table name
     * @param field the original field name
     * @return the database column name
     */
    public static String mapField(String table, String field) {
        String normalized = field.toUpperCase().replace(":", "_");
        return table.toUpperCase() + "_" + normalized;
    }

    /**
     * Map a domain table name to its database table identifier by converting to uppercase.
     *
     * @param name the original table name
     * @return the database table name
     */
    public static String mapTable(String name) {
        return name.toUpperCase();
    }
}
