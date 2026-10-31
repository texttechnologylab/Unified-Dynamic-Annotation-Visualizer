package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.sources.SourceBuildOps;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * A pipeline id is also the name of its schema, which a delete drops and a build replaces. Ids
 * naming the app, UIMA or PostgreSQL schemas are refused before the database is touched.
 */
class ReservedSchemaTest {

    private static final String APP_SCHEMA = "udav_app";

    private final DataSource dataSource = mock(DataSource.class);
    private final SourceBuildService builds = mock(SourceBuildService.class);
    private final PipelineService service = new PipelineService(builds, dataSource, new ObjectMapper());

    ReservedSchemaTest() {
        ReflectionTestUtils.setField(service, "schema", APP_SCHEMA);
    }

    @Test
    void reservedNames() {
        for (String name : new String[]{"public", "PUBLIC", " public ", APP_SCHEMA, "information_schema", "pg_catalog", "pg_toast"}) {
            assertTrue(DBConstants.isReservedSchema(name, APP_SCHEMA), name);
        }
        for (String name : new String[]{"e0000001-0000-4000-8000-000000000001", "public_data", "pgdata", "main"}) {
            assertFalse(DBConstants.isReservedSchema(name, APP_SCHEMA), name);
        }
    }

    @Test
    void deleteRefusesReservedIds() throws Exception {
        for (String id : new String[]{"public", " public ", APP_SCHEMA, "information_schema", "pg_catalog"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.delete(id), id);
            assertEquals(BAD_REQUEST, e.getStatusCode(), id);
        }
        verify(dataSource, never()).getConnection();
    }

    @Test
    void createRefusesReservedIds() throws Exception {
        for (String id : new String[]{"public", APP_SCHEMA, "information_schema", "pg_toast"}) {
            ObjectNode json = new ObjectMapper().createObjectNode().put("id", id).put("name", "Reserved");
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.create(json), id);
            assertEquals(BAD_REQUEST, e.getStatusCode(), id);
        }
        verify(dataSource, never()).getConnection();
        verifyNoInteractions(builds);
    }

    @Test
    void ordinaryIdsStillReachTheDatabase() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("no database in this test"));

        assertThrows(RuntimeException.class, () -> service.delete("e0000001-0000-4000-8000-000000000001"));
        verify(dataSource).getConnection();
    }

    @Test
    void buildRefusesSystemSchemas() {
        SourceBuildService build = new SourceBuildService(dataSource, mock(SourceBuildOps.class));
        ReflectionTestUtils.setField(build, "appDbSchema", APP_SCHEMA);

        for (String schema : new String[]{"information_schema", "pg_catalog"}) {
            assertThrows(RuntimeException.class, () -> build.startBuild(schema, schema), schema);
        }
        verifyNoInteractions(dataSource);
    }
}
