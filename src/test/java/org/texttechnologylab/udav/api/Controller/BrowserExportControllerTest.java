package org.texttechnologylab.udav.api.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.texttechnologylab.udav.api.service.BrowserExportService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowserExportController.class)
class BrowserExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrowserExportService browserExportService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        BrowserExportService browserExportService() {
            return org.mockito.Mockito.mock(BrowserExportService.class);
        }
    }

    @Test
    void exportSingle_returnsDirectFile_whenServiceReturnsOneFile() throws Exception {
        BrowserExportService.WidgetSelection selection = new BrowserExportService.WidgetSelection();
        selection.id = "PieChart-aqlay35";
        selection.title = "My Pie";

        BrowserExportService.ExportedFile file = new BrowserExportService.ExportedFile(
                "my-pie.svg",
                "image/svg+xml",
                "<svg/>".getBytes(StandardCharsets.UTF_8)
        );

        BrowserExportService.WidgetExportResult serviceResult = BrowserExportService.WidgetExportResult.success(
                selection,
                List.of(file)
        );

        when(browserExportService.exportWidget(anyString(), any(), anyString(), anyBoolean()))
                .thenReturn(serviceResult);

        String body = """
                {
                  "pipeline": "0c1953d4-843b-4de4-a44e-1c607ed5a584",
                  "widget": {
                    "id": "PieChart-aqlay35",
                    "type": "PieChart",
                    "generator": {"id": "CategoryNumber-esl7guq"}
                  }
                }
                """;

        mockMvc.perform(post("/api/batch/export/svg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"my-pie.svg\""));
    }

    @Test
    void exportPipeline_returnsZip_withSummaryAndErrors_whenServiceProvidesFailures() throws Exception {
        BrowserExportService.WidgetSelection selection = new BrowserExportService.WidgetSelection();
        selection.id = "BarChart-cspd570";
        selection.title = "Bar Chart";
        selection.type = "BarChart";
        selection.generatorId = "CategoryNumber-eky02g0";

        BrowserExportService.ExportedFile file = new BrowserExportService.ExportedFile(
                "bar-chart.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        BrowserExportService.ExportFailure failure = new BrowserExportService.ExportFailure(selection, "timeout");

        BrowserExportService.PipelineExportResult serviceResult = new BrowserExportService.PipelineExportResult(
                "0c1953d4-843b-4de4-a44e-1c607ed5a584",
                "png",
                List.of(file),
                List.of(failure)
        );

        when(browserExportService.exportPipeline(anyString(), anyString(), anyBoolean()))
                .thenReturn(serviceResult);

        byte[] response = mockMvc.perform(post("/api/batch/export/pipeline/png")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"pipeline\":\"0c1953d4-843b-4de4-a44e-1c607ed5a584\"," +
                                "\"bulk\":false" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        boolean hasSummary = false;
        boolean hasErrors = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("_summary.json".equals(entry.getName())) {
                    hasSummary = true;
                }
                if ("_errors.json".equals(entry.getName())) {
                    hasErrors = true;
                }
            }
        }

        org.junit.jupiter.api.Assertions.assertTrue(hasSummary);
        org.junit.jupiter.api.Assertions.assertTrue(hasErrors);
    }

    @Test
    void exportSingle_returnsBadRequest_whenPipelineMissing() throws Exception {
        mockMvc.perform(post("/api/batch/export/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"widget\":{\"id\":\"x\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPipelineByPath_defaultsBulkToTrue() throws Exception {
        BrowserExportService.ExportedFile file = new BrowserExportService.ExportedFile(
                "pie.svg",
                "image/svg+xml",
                "<svg/>".getBytes(StandardCharsets.UTF_8)
        );

        BrowserExportService.PipelineExportResult serviceResult = new BrowserExportService.PipelineExportResult(
                "0c1953d4-843b-4de4-a44e-1c607ed5a584",
                "svg",
                List.of(file),
                List.of()
        );

        when(browserExportService.exportPipeline(anyString(), anyString(), anyBoolean()))
                .thenReturn(serviceResult);

        mockMvc.perform(get("/api/batch/export/pipeline/0c1953d4-843b-4de4-a44e-1c607ed5a584/svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"0c1953d4-843b-4de4-a44e-1c607ed5a584-svg-exports.zip\""));

        verify(browserExportService).exportPipeline(
                eq("0c1953d4-843b-4de4-a44e-1c607ed5a584"), eq("svg"), eq(true));
    }

    @Test
    void exportPipelineByPath_respectsExplicitBulkFalse() throws Exception {
        BrowserExportService.ExportedFile file = new BrowserExportService.ExportedFile(
                "pie.svg",
                "image/svg+xml",
                "<svg/>".getBytes(StandardCharsets.UTF_8)
        );

        BrowserExportService.PipelineExportResult serviceResult = new BrowserExportService.PipelineExportResult(
                "0c1953d4-843b-4de4-a44e-1c607ed5a584",
                "svg",
                List.of(file),
                List.of()
        );

        when(browserExportService.exportPipeline(anyString(), anyString(), anyBoolean()))
                .thenReturn(serviceResult);

        mockMvc.perform(get("/api/batch/export/pipeline/0c1953d4-843b-4de4-a44e-1c607ed5a584/svg")
                        .param("bulk", "false"))
                .andExpect(status().isOk());

        verify(browserExportService).exportPipeline(
                eq("0c1953d4-843b-4de4-a44e-1c607ed5a584"), eq("svg"), eq(false));
    }
}

