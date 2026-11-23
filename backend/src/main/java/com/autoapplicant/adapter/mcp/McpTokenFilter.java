package com.autoapplicant.adapter.mcp;

import com.autoapplicant.adapter.security.LoopbackRequests;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets a running chat turn's {@code claude} into {@code /mcp}: from loopback, with that turn's
 * bearer token. It signs the request in with the MCP role only, which no other endpoint accepts,
 * and hands the grant on so every tool reads as the turn's user.
 */
public class McpTokenFilter extends OncePerRequestFilter {

    public static final String PATH = "/mcp";
    static final String GRANT_ATTRIBUTE = McpTokenFilter.class.getName() + ".grant";
    public static final String ROLE = "MCP";
    private static final String BEARER = "Bearer ";

    private final McpGrants grants;

    public McpTokenFilter(McpGrants grants) {
        this.grants = grants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<McpGrant> grant = LoopbackRequests.isFromLoopback(request)
                ? grants.find(bearer(request))
                : Optional.empty();
        if (grant.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute(GRANT_ATTRIBUTE, grant.get());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                grant.get().userId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        chain.doFilter(request, response);
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
    }
}
