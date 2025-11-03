package org.texttechnologylab.udav.pipeline;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PipelineNode {
    private final PipelineNodeType type;
    private final Map<String, PipelineNode> dependencies;
    private final Map<String, PipelineNode> children;
    private final JSONView config;

    public PipelineNode(PipelineNodeType type, Map<String, PipelineNode> dependencies, JSONView config) {
        this.type = type;
        this.dependencies = dependencies;
        this.config = config;
        this.children = new HashMap<>();
        for (PipelineNode dependency : dependencies.values()) {
            dependency.children.put(config.get("id").toString(), this);
        }
    }
}
