package com.autoapplicant.port.out.web;

import java.util.Optional;

/** Fetches the visible text of a public web page (SSRF-guarded). Empty on any failure. */
public interface WebPageFetchPort {
    Optional<String> fetchText(String url);
}
