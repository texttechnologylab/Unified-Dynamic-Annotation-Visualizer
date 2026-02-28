package org.texttechnologylab.udav.generators.sources;

import lombok.Getter;
import org.texttechnologylab.udav.generators.Generator;

import java.util.List;


@Getter
public class SourceDerived extends Source {

    private List<Generator> sourceGenerators;

    public SourceDerived(List<Generator> sourceGenerators) {
        this.sourceGenerators = sourceGenerators;
    }
}
