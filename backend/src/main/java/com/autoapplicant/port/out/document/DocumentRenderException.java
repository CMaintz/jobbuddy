package com.autoapplicant.port.out.document;

/**
 * Rendering a document failed. {@link #diagnostics()} carries the layout engine's own error
 * output (file, line, column and hints), empty when it never got that far.
 */
public class DocumentRenderException extends RuntimeException {

    private final String diagnostics;

    public DocumentRenderException(String message, String diagnostics) {
        super(message);
        this.diagnostics = diagnostics == null ? "" : diagnostics;
    }

    public DocumentRenderException(String message, Throwable cause) {
        super(message, cause);
        this.diagnostics = "";
    }

    public String diagnostics() {
        return diagnostics;
    }
}
