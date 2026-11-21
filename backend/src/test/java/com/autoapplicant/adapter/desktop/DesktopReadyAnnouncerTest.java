package com.autoapplicant.adapter.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

class DesktopReadyAnnouncerTest {

    @Test
    void announces_the_port_the_server_actually_bound() {
        // The shell starts the backend with port 0, so the configured port says nothing about
        // where the server is; only the bound one does.
        WebServer server = mock(WebServer.class);
        when(server.getPort()).thenReturn(54321);
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        when(context.getWebServer()).thenReturn(server);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new DesktopReadyAnnouncer(new PrintStream(out, true, StandardCharsets.UTF_8))
                .announce(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, null));

        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo("JOBBUDDY_DESKTOP_READY port=54321" + System.lineSeparator());
    }
}
