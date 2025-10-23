package org.texttechnologylab.udav.pipeline;

public interface PipelineProcessor {
    /**
     * Process a pipeline and return the generated schema JSON to persist.
     */
    void process(String pipelineId) throws Exception;
}
