package org.texttechnologylab.udav.db;

/** Central place for naming DB objects that are used across repositories/importers. */
public final class SchemaObjectNames {
    private SchemaObjectNames() {}

    public static final String TABLE_JSON_DATA = "json_data";
    public static final String COL_JSON_DATA_SOURCEFILE_NAME = "sourcefile_name";
    public static final String COL_JSON_DATA_JSON = "json";

    public static final String TABLE_PIPELINE = "pipeline";
    public static final String COL_PIPELINE_ID = "pipeline_id";
    public static final String COL_PIPELINE_NAME = "pipeline_name";
    public static final String COL_PIPELINE_JSON = "json";

    public static final String TABLE_PIPELINE_LOCKS = "pipeline_locks";
    public static final String COL_PIPELINE_LOCKS_PIPELINE_ID = "pipeline_id";
    public static final String COL_PIPELINE_LOCKS_LOCKED_AT = "locked_at";
}
