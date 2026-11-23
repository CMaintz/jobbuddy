package com.autoapplicant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The loopback check trusts the request's remote address, which is the socket peer only while the
 * server listens on loopback and applies no forwarded headers. Settings that break either must stop
 * the desktop app from starting, whichever way they arrive (env vars override the profile file).
 */
class DesktopServerGuardTest {

    private final ApplicationContextRunner desktop = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("desktop"))
            .withUserConfiguration(ServerSettings.class, DesktopServerGuard.class);

    @Test
    void starts_on_loopback_with_forwarded_headers_ignored() {
        desktop.withPropertyValues("server.address=127.0.0.1", "server.forward-headers-strategy=none")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void refuses_to_listen_beyond_loopback() {
        desktop.withPropertyValues("server.address=0.0.0.0", "server.forward-headers-strategy=none")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage(DesktopServerGuard.LOOPBACK_ONLY));
        desktop.withPropertyValues("server.forward-headers-strategy=none")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage(DesktopServerGuard.LOOPBACK_ONLY));
    }

    @Test
    void refuses_to_take_the_client_address_from_forwarded_headers() {
        desktop.withPropertyValues("server.address=127.0.0.1", "server.forward-headers-strategy=native")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage(DesktopServerGuard.NO_FORWARDED_HEADERS));
        desktop.withPropertyValues("server.address=127.0.0.1", "server.forward-headers-strategy=framework")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage(DesktopServerGuard.NO_FORWARDED_HEADERS));
    }

    @Configuration
    @EnableConfigurationProperties(ServerProperties.class)
    static class ServerSettings {}
}
