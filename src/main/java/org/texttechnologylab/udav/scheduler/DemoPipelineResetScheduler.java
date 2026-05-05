package org.texttechnologylab.udav.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.service.PipelineService;

import java.io.InputStream;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.pipeline-reset.enabled", havingValue = "true")
public class DemoPipelineResetScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoPipelineResetScheduler.class);

    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${app.pipeline-reset.demo-file:pipelines/demo.json}")
    private String demoFile;

    public DemoPipelineResetScheduler(PipelineService pipelineService,
                                      ObjectMapper objectMapper,
                                      ResourceLoader resourceLoader) {
        this.pipelineService = pipelineService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Scheduled(cron = "${app.pipeline-reset.cron:0 0 2 * * *}")
    public void resetPipelinesDaily() {
        try {
            List<String> pipelineIds = pipelineService.listAllIds();
            for (String pipelineId : pipelineIds) {
                pipelineService.delete(pipelineId);
            }

            JsonNode demoPipeline = loadDemoPipeline();
            String recreatedId = pipelineService.create(demoPipeline);

            LOGGER.info("Daily reset completed. Deleted {} pipeline(s), recreated demo pipeline '{}'.", pipelineIds.size(), recreatedId);
        } catch (Exception e) {
            LOGGER.error("Daily pipeline reset failed: {}", e.getMessage(), e);
        }
    }

    private JsonNode loadDemoPipeline() throws Exception {
        Resource resource = resolveResource();
        if (!resource.exists()) {
            throw new IllegalStateException("Demo pipeline resource not found: " + demoFile);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            if (root.has("pipelines")) {
                JsonNode pipelines = root.get("pipelines");
                if (!pipelines.isArray() || pipelines.isEmpty()) {
                    throw new IllegalArgumentException("Demo file has invalid 'pipelines' array: " + demoFile);
                }
                return pipelines.get(0);
            }
            if (!root.isObject()) {
                throw new IllegalArgumentException("Demo file root must be a JSON object: " + demoFile);
            }
            return root;
        }
    }

    private Resource resolveResource() {
        if (demoFile.startsWith("classpath:") || demoFile.startsWith("file:")) {
            return resourceLoader.getResource(demoFile);
        }
        return resourceLoader.getResource("classpath:" + demoFile);
    }
}
