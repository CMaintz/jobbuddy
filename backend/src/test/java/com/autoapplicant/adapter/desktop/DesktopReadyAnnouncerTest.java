package com.autoapplicant.adapter.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.ai.EmbeddingKeyGuard.MissingKey;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

class DesktopReadyAnnouncerTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final List<Integer> exits = new ArrayList<>();

    private void announce(Optional<MissingKey> missingKey) {
        WebServer server = mock(WebServer.class);
        when(server.getPort()).thenReturn(54321);
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        when(context.getWebServer()).thenReturn(server);
        new DesktopReadyAnnouncer(new PrintStream(out, true, StandardCharsets.UTF_8), () -> missingKey, exits::add)
                .announce(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, null));
    }

    @Test
    void announces_the_port_the_server_actually_bound() {
        // The shell starts the backend with port 0, so the configured port says nothing about
        // where the server is; only the bound one does.
        announce(Optional.empty());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo("JOBBUDDY_DESKTOP_READY port=54321" + System.lineSeparator());
        assertThat(exits).isEmpty();
    }

    @Test
    void without_the_embeddings_key_it_says_which_one_instead_of_ready_and_stops() {
        // Ready would load an app whose search and matching fail on every call; the shell turns
        // this line into a plain Danish message naming the provider.
        announce(Optional.of(new MissingKey("gemini", "GEMINI_API_KEY")));

        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo("JOBBUDDY_DESKTOP_ERROR embedding-key-missing provider=gemini" + System.lineSeparator());
        assertThat(exits).containsExactly(1);
    }
}
