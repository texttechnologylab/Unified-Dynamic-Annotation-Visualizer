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

        for (String shortName : candidates) {
            String physical = SourceUIMA.sanitize(tableHash + "_f_" + shortName);
            boolean exists = dsl.fetchExists(
                    DSL.selectOne()
                            .from(DSL.table(DSL.name("information_schema", "columns")))
                            .where(DSL.field(DSL.name("table_schema"), String.class).eq(schema))
                            .and(DSL.field(DSL.name("table_name"),  String.class).eq(tableHash))
                            .and(DSL.field(DSL.name("column_name"), String.class).eq(physical))
            );
            if (exists) {
                tempFeatureName = shortName;
                return DSL.field(DSL.name(schema, tableHash, physical), String.class);
            }
        }

        throw new IllegalStateException(
                "No matching feature column in " + schema + "." + tableHash +
                        " for desired '" + desiredShort + "'. Tried: " + candidates
        );
    }
}
