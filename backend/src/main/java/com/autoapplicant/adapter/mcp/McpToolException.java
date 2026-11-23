package com.autoapplicant.adapter.mcp;

/** A tool call the model got wrong. Its message goes back to the model, so it names no secrets. */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message);
    }
}
