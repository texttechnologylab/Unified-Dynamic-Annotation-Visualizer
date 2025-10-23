package org.texttechnologylab.udav.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.service.SourceBuildService;

/**
 * Default processor: loads the pipeline, runs it.
 */
@Component
public class DefaultPipelineProcessor implements PipelineProcessor {

    private final SourceBuildService sourceBuildService;

    private final Logger logger = LoggerFactory.getLogger(DefaultPipelineProcessor.class);

    public DefaultPipelineProcessor(SourceBuildService sourceBuildService) {
        this.sourceBuildService = sourceBuildService;
    }

    @Override
    public void process(String pipelineId) {
        logger.info("Processing pipeline " + pipelineId + " by running a source build.");
        sourceBuildService.startBuild(pipelineId, pipelineId);
    }
}
