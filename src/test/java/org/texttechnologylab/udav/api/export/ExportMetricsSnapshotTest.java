package org.texttechnologylab.udav.api.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the X-UDAV-Export-Metrics header contract that BatchExportEvaluationIT parses. The JSON
 * is hand-rolled, so it is worth pinning that it parses and that every field the harness reads
 * is present.
 */
class ExportMetricsSnapshotTest {

    @Test
    void headerJsonParsesAndCarriesEveryFieldTheHarnessReads() throws Exception {
        ExportMetricsSnapshot snapshot = new ExportMetricsSnapshot(
                1234, 120, 890, 3100, 3050, 210, 900, 1800,
                123_456_789L, 987_654_321L, 45, 3, 5_000_000L, -1,
                38, 2, 4, true).withZipBytes(2_100_000L);

        JsonNode node = new ObjectMapper().readTree(snapshot.toJson());

        for (String field : new String[]{
                "wallMs", "browserInitMs", "pageReadyMs", "exportPhaseMs", "widgetSpanMs",
                "widgetMedianMs", "jvmCpuMs", "browserCpuMs", "heapPeakBytes", "allocatedBytes",
                "gcPauseMs", "gcCount", "outputBytes", "zipBytes", "widgetsOk", "widgetsFailed",
                "concurrency", "sessionReused"}) {
            assertTrue(node.has(field), "missing field: " + field);
        }

        assertEquals(1234, node.get("wallMs").asLong());
        assertEquals(987_654_321L, node.get("allocatedBytes").asLong());
        // withZipBytes must not disturb the other fields.
        assertEquals(2_100_000L, node.get("zipBytes").asLong());
        assertEquals(38, node.get("widgetsOk").asInt());
        assertTrue(node.get("sessionReused").asBoolean());
    }
}
