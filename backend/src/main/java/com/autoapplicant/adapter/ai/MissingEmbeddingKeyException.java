package com.autoapplicant.adapter.ai;

/** The stored embedding provider has no API key in this process's environment. */
public class MissingEmbeddingKeyException extends IllegalStateException {

    private final transient EmbeddingKeyGuard.MissingKey missing;

    public MissingEmbeddingKeyException(EmbeddingKeyGuard.MissingKey missing) {
        super("Embeddings are set to " + missing.provider() + ", but " + missing.envVar()
                + " is not set. Set it, or nothing can be embedded or searched.");
        this.missing = missing;
    }

    public EmbeddingKeyGuard.MissingKey missing() {
        return missing;
    }
}
