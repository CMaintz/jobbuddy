package com.autoapplicant.adapter.security;

import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenFilter.class);

    private final ProvisionFirebaseUserUseCase provisionUser;
    private final FirebaseAuth firebaseAuth;

    public FirebaseTokenFilter(ProvisionFirebaseUserUseCase provisionUser, FirebaseAuth firebaseAuth) {
        this.provisionUser = provisionUser;
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = header.substring(7);
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);

            if (!decoded.isEmailVerified()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"EMAIL_NOT_VERIFIED\"}");
                return;
            }

            String firebaseUid = decoded.getUid();
            String email = decoded.getEmail();
            String name = decoded.getName();

            User user = provisionUser.findOrCreate(firebaseUid, email, name);

            // Role is stored as a Firebase Custom Claim after first login.
            // Fall back to the DB role (already set by findOrCreate).
            String role = user.role().name();
            Object roleClaim = decoded.getClaims().get("role");
            if (roleClaim != null) {
                role = roleClaim.toString();
            }

            var auth = new UsernamePasswordAuthenticationToken(
                    user.id(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (FirebaseAuthException e) {
            log.debug("Firebase token verification failed: {}", e.getMessage());
            // Don't set auth — Spring Security will reject protected endpoints
        }

        filterChain.doFilter(request, response);
    }
}
