package com.autoapplicant.config;

/**
 * Where user uploads live on disk and the URL path they are served under. The web layer serves
 * this directory and the PDF renderer reads from it, so the two must not drift apart.
 */
public final class UploadsLocation {

    /** Local directory holding uploads, relative to the working directory. */
    public static final String DIRECTORY = "uploads";

    /** URL path prefix uploads are served under, with its trailing slash. */
    public static final String URL_PREFIX = "/" + DIRECTORY + "/";

    private UploadsLocation() {}
}
