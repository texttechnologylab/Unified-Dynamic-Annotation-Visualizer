package org.texttechnologylab.udav.api;

import java.util.Locale;

public enum ValueMode {
    RAW,        // no transform
    SHARE,      // value / sum(value)
    MAX1,       // value / max(value)
    ZSCORE,     // (value-mean)/sd
    PER_FILE_AVG;

    public static ValueMode from(String s) {
        if (s == null) return RAW;
        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "RAW", "" -> RAW;   // treat aliases as no-op
            case "SHARE" -> SHARE;
            case "MAX1" -> MAX1;
            case "ZSCORE" -> ZSCORE;
            case "PER_FILE_AVG" -> PER_FILE_AVG;
            default -> RAW;
        };
    }
}
