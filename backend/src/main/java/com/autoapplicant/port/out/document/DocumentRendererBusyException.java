package com.autoapplicant.port.out.document;

/**
 * Every renderer slot stayed taken for as long as a render may wait. Nothing is wrong with the
 * document; the same request is likely to succeed a moment later.
 */
public class DocumentRendererBusyException extends DocumentRenderException {

    /** The {@code code} a client matches to show "busy, try again" rather than a failure. */
    public static final String CODE = "RENDERER_BUSY";

    public DocumentRendererBusyException(String message) {
        super(message, "");
    }
}
