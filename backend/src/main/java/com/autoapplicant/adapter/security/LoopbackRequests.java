package com.autoapplicant.adapter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Whether a request came from this PC. */
public final class LoopbackRequests {

    private LoopbackRequests() {}

    // The remote address is the socket peer only while forwarded-header handling is off;
    // DesktopServerGuard refuses to start otherwise, so a remote client cannot claim to be local.
    public static boolean isFromLoopback(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
