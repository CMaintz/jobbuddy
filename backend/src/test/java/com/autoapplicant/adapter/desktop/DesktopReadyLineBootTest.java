package com.autoapplicant.adapter.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The ready line from a real embedded server started the way the desktop shell starts it, on port
 * 0. The unit test hands the announcer a mocked server; only a real one shows that the line names
 * the port Tomcat bound rather than the configured one.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.DEFINED_PORT,
        properties = {
            "server.port=0",
            "app.desktop.token=ready-line-test-token-0123456789abcdef01234",
            "app.data-dir=build/desktop-ready-line-data",
            // Whichever provider the empty database settles on has a key, so the announcer
            // reaches the ready line instead of exiting the test JVM.
            "app.gemini.api-key=test-key",
            "app.openai.api-key=test-key",
            "app.enrichment.sweep.initial-delay-ms=3600000",
            "app.embedding.sweep.initial-delay-ms=3600000"
        })
@ActiveProfiles("desktop")
// The extension swaps System.out before the context starts; the announcer keeps the stream it
// was constructed with, so capturing any later would see nothing.
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers(disabledWithoutDocker = true)
class DesktopReadyLineBootTest {

    private static final Pattern READY_LINE =
            Pattern.compile("(?m)^" + Pattern.quote(DesktopReadyAnnouncer.READY_PREFIX) + "(\\d{1,5})$");

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    // The test runner's stdin is already at EOF, which the real watch takes for the shell having
    // gone and exits the JVM.
    @MockitoBean DesktopParentWatch parentWatch;

    @LocalServerPort int boundPort;

    @Test
    void the_ready_line_names_the_port_the_server_bound_when_started_on_port_zero(CapturedOutput output)
            throws IOException {
        List<Integer> announced = READY_LINE.matcher(output.getOut()).results()
                .map(m -> Integer.parseInt(m.group(1)))
                .toList();

        // Bug: reading server.port prints 0 (or 8080 from the default config), and the shell
        // loads a URL nothing listens on.
        assertThat(announced).containsExactly(boundPort);
        try (Socket connected = new Socket(InetAddress.getLoopbackAddress(), announced.get(0))) {
            assertThat(connected.isConnected()).isTrue();
        }
    }
}
