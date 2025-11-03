package org.texttechnologylab.udav.generators;

// TODO: Needed? Delete class if not
public abstract class Generator implements GeneratorInterface {
    protected final String id;

    public Generator(String id) {
        this.id = id;
    }
}
