package org.texttechnologylab.udav.generators;

import lombok.AccessLevel;
import lombok.Getter;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.SourceUIMA;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;

public abstract class GeneratorUIMA extends Generator {
    @Getter(AccessLevel.NONE)
    protected String tempFeatureName;

    public GeneratorUIMA(String id, JSONView configGenerator, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
    }

    /** Return the feature field <schema>.<hash>.<hash>_f_<short> by trying candidates until one exists. */
    protected org.jooq.Field<String> resolveFeatureField(org.jooq.DSLContext dsl,
                                                       String schema,
                                                       String tableHash,
                                                       String desiredShort,
                                                       java.util.List<String> extraCandidates) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();

        if (desiredShort != null && !desiredShort.isBlank()) candidates.add(desiredShort.trim());

        if (((SourceUIMA) source).getAnnotationType() == SourceUIMA.AnnotationType.POS) {
            // common POS feature short names
            candidates.add("coarseValue");
            candidates.add("posValue");
            candidates.add("value");
        } else {
            // NE / Lemma etc.
            candidates.add("value");
            candidates.add("identifier");
            candidates.add("label");
            candidates.add("lemmaValue");
        }

        if (extraCandidates != null) {
            for (String c : extraCandidates) if (c != null && !c.isBlank()) candidates.add(c.trim());
        }

        // Importer writes feature columns as "<tableHash>_f_<sanitizedShortName>_<8-hex-of-feature-FQN>"
        // (see JooqDatabaseWriter.featColName). Fetch the table's columns once and match in Java so we
        // don't have to deal with LIKE-escaping the underscores in the prefix.
        org.jooq.Field<String> COL = DSL.field(DSL.name("column_name"), String.class);
        java.util.List<String> columns = dsl.select(COL)
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq(schema))
                .and(DSL.field(DSL.name("table_name"), String.class).eq(tableHash))
                .fetch(COL);
        java.util.regex.Pattern hex8 = java.util.regex.Pattern.compile("[0-9a-f]{8}");
        for (String shortName : candidates) {
            String prefix = SourceUIMA.sanitize(tableHash + "_f_" + shortName) + "_";
            for (String col : columns) {
                if (col.startsWith(prefix) && hex8.matcher(col.substring(prefix.length())).matches()) {
                    tempFeatureName = shortName;
                    return DSL.field(DSL.name(schema, tableHash, col), String.class);
                }
            }
        }

        throw new IllegalStateException(
                "No matching feature column in " + schema + "." + tableHash +
                        " for desired '" + desiredShort + "'. Tried: " + candidates
        );
    }
}
