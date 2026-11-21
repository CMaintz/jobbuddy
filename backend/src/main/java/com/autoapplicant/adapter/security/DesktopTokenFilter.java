package com.autoapplicant.adapter.security;

import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs the desktop shell in as the local user. Both checks are required. Loopback alone is not
 * enough: every process on the PC, and every web page open in the user's browser, can reach
 * 127.0.0.1. The token alone is not enough if the port is ever reachable from outside the PC.
 */
public class DesktopTokenFilter extends OncePerRequestFilter {

    /** A header of its own, so it can never be confused with a Firebase bearer token. */
    public static final String TOKEN_HEADER = "X-JobBuddy-Token";

    private final DesktopToken token;
    private final ProvisionLocalUserUseCase localUser;

    public DesktopTokenFilter(DesktopToken token, ProvisionLocalUserUseCase localUser) {
        this.token = token;
        this.localUser = localUser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (!LoopbackRequests.isFromLoopback(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (token.matches(request.getHeader(TOKEN_HEADER))) {
            RequestSignIn.signIn(localUser.provisionLocalUser(), request, response);
        }
        filterChain.doFilter(request, response);
    }
}
