package com.autoapplicant.adapter.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleaningServiceTest {

    private final TextCleaningService cleaner = new TextCleaningService();

    private static final String PAGE = """
            <html><head><title>Platform Engineer | Netcompany</title></head>
            <body>
              <nav><a href="/">Home</a><a href="/jobs">Jobs</a></nav>
              <h1>Platform Engineer</h1>
              <p>We are hiring a platform engineer for our Copenhagen office.</p>
              <h2>Krav</h2>
              <ul><li>5 års erfaring med Kubernetes</li><li>Dansk på professionelt niveau</li></ul>
              <p>Har du spørgsmål, så kontakt Marie på 12345678.</p>
              <footer>© 2026 Netcompany. All rights reserved.</footer>
            </body></html>
            """;

    @Test
    void the_postings_own_line_structure_survives_the_cleanup() {
        String text = cleaner.clean(PAGE);

        // Each block gets its own line — the enrichment pass addresses boilerplate by
        // line number, and a single flattened line would make that meaningless.
        assertThat(text.lines()).contains(
                "Platform Engineer",
                "Krav",
                "5 års erfaring med Kubernetes",
                "Dansk på professionelt niveau");
    }

    @Test
    void page_chrome_goes_and_the_posting_stays() {
        String text = cleaner.clean(PAGE);

        assertThat(text).doesNotContain("Home", "All rights reserved");
        assertThat(text).contains("Copenhagen office", "kontakt Marie på 12345678");
    }

    @Test
    void runs_of_blank_lines_from_empty_wrappers_are_collapsed() {
        String text = cleaner.clean("<div><div><div><p>One</p></div></div></div><div></div><p>Two</p>");

        assertThat(text).doesNotContain("\n\n\n");
        assertThat(text.lines()).containsExactly("One", "Two");
    }

    @Test
    void the_title_is_taken_from_the_page_and_stripped_of_the_site_name() {
        assertThat(cleaner.extractTitle(PAGE)).isEqualTo("Platform Engineer");
    }

    @Test
    void nothing_in_means_nothing_out() {
        assertThat(cleaner.clean(null)).isNull();
        assertThat(cleaner.clean("")).isEmpty();
    }
}
