package org.texttechnologylab.udav.api.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.api.service.BrowserExportService;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/batch")
public class BrowserExportController {

    private final BrowserExportService exportService;
    private final ObjectMapper mapper;

    public BrowserExportController(BrowserExportService exportService, ObjectMapper mapper) {
        this.exportService = exportService;
        this.mapper = mapper;
    }

    @PostMapping("/export/{format}")
    public ResponseEntity<byte[]> exportSingle(
            @PathVariable String format,
            @RequestBody WidgetExportRequest request
    ) throws Exception {
        if (request == null || request.pipeline == null || request.pipeline.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pipeline must not be empty");
        }
        if (request.widget == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "widget must not be empty");
        }

        BrowserExportService.WidgetExportResult result = exportService.exportWidget(
                request.pipeline,
                request.widget.toSelection(),
                format,
                request.bulk
        );

        if (result.hasError()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, result.error);
        }

        if (result.files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Export produced no files.");
        }

        if (result.files.size() == 1) {
            BrowserExportService.ExportedFile file = result.files.getFirst();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, file.contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.name + "\"")
                    .body(file.content);
        }

        byte[] zip = zipFiles(result.files, null, null);
        String filename = safeFilename(result.widget.title != null ? result.widget.title : result.widget.id) + "-export.zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(zip);
    }

    @PostMapping("/export/pipeline/{format}")
    public ResponseEntity<byte[]> exportPipeline(
            @PathVariable String format,
            @RequestBody PipelineExportRequest request
    ) throws Exception {
        if (request == null || request.pipeline == null || request.pipeline.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pipeline must not be empty");
        }

        return exportPipelineInternal(request.pipeline, format, request.bulk);
    }

    @GetMapping("/export/pipeline/{pipelineId}/{format}")
    public ResponseEntity<byte[]> exportPipelineByPath(
            @PathVariable String pipelineId,
            @PathVariable String format,
            @RequestParam(value = "bulk", defaultValue = "true") boolean bulk
    ) throws Exception {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pipelineId must not be empty");
        }
        return exportPipelineInternal(pipelineId, format, bulk);
    }

    private ResponseEntity<byte[]> exportPipelineInternal(String pipelineId, String format, boolean bulk) throws Exception {
        BrowserExportService.PipelineExportResult result = exportService.exportPipeline(pipelineId, format, bulk);
        byte[] zip = zipFiles(result.files, result.failures, pipelineId);
        String filename = safeFilename(pipelineId) + "-" + safeFilename(format) + "-exports.zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(zip);
    }

    private byte[] zipFiles(
            List<BrowserExportService.ExportedFile> files,
            List<BrowserExportService.ExportFailure> failures,
            String pipelineId
    ) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            int index = 0;
            for (BrowserExportService.ExportedFile file : files) {
                String entryName = String.format(
                        Locale.ROOT,
                        "%03d-%s",
                        index++,
                        safeFilename(file.name)
                );
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(file.content);
                zip.closeEntry();
            }

            if (failures != null && !failures.isEmpty()) {
                zip.putNextEntry(new ZipEntry("_errors.json"));
                zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(failures));
                zip.closeEntry();
            }

            if (pipelineId != null) {
                zip.putNextEntry(new ZipEntry("_summary.json"));
                zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                        "pipeline", pipelineId,
                        "exported", files.size(),
                        "failed", failures == null ? 0 : failures.size(),
                        "generatedAt", java.time.Instant.now().toString()
                )));
                zip.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private static String safeFilename(String input) {
        if (input == null || input.isBlank()) {
            return "export";
        }
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static final class WidgetExportRequest {
        public String pipeline;
        public WidgetRequest widget;
        public boolean bulk;
    }

    public static final class PipelineExportRequest {
        public String pipeline;
        public boolean bulk;
    }

    public static final class WidgetRequest {
        public String id;
        public String type;
        public String title;
        public String generatorId;
        public GeneratorRequest generator;

        BrowserExportService.WidgetSelection toSelection() {
            BrowserExportService.WidgetSelection selection = new BrowserExportService.WidgetSelection();
            selection.id = id;
            selection.type = type;
            selection.title = title;
            selection.generatorId = generatorId != null
                    ? generatorId
                    : (generator != null ? generator.id : null);
            return selection;
        }
    }

    public static final class GeneratorRequest {
        public String id;
    }
}



