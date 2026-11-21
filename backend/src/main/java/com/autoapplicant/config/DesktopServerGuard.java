package com.autoapplicant.config;

import java.net.InetAddress;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties.ForwardHeadersStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the desktop app on server settings that would let another machine in.
 * DesktopTokenFilter's loopback check reads the request's remote address, which is the socket
 * peer only while the server listens on loopback alone and applies no forwarded headers. With
 * forwarded headers on, Tomcat's RemoteIpValve trusts private ranges such as 192.168/16, so a LAN
 * host sending {@code X-Forwarded-For: 127.0.0.1} would pass as local. Environment variables
 * (SERVER_ADDRESS, SERVER_FORWARD_HEADERS_STRATEGY) override application-desktop.yml, so the
 * settings the app actually got are checked, before the server binds its port.
 */
@Component
@Profile("desktop")
public class DesktopServerGuard {

    static final String LOOPBACK_ONLY = "The desktop profile must listen on loopback only:"
            + " server.address (SERVER_ADDRESS) has to be 127.0.0.1";
    static final String NO_FORWARDED_HEADERS = "The desktop profile must ignore forwarded headers:"
            + " server.forward-headers-strategy (SERVER_FORWARD_HEADERS_STRATEGY) has to be none";

    public DesktopServerGuard(ServerProperties server) {
        InetAddress address = server.getAddress();
        if (address == null || !address.isLoopbackAddress()) {
            throw new IllegalStateException(LOOPBACK_ONLY);
        }
        if (server.getForwardHeadersStrategy() != ForwardHeadersStrategy.NONE) {
            throw new IllegalStateException(NO_FORWARDED_HEADERS);
        }
    }
}
