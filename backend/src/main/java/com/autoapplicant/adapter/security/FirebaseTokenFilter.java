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
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenFilter.class);

    private final ProvisionFirebaseUserUseCase provisionUser;
    private final FirebaseAuth firebaseAuth;
    // A DeferredResult response is written on a second (ASYNC) dispatch, which this filter skips.
    // SecurityContextHolderFilter reloads the context from this request attribute on that
    // dispatch; without the save it finds nothing and the finished result is answered with 403.
    // Saving beats re-running the filter on the async dispatch: no second token verification or
    // user lookup, and a token that expires while the AI call runs cannot lose the result.
    private final SecurityContextRepository contextRepository =
            new RequestAttributeSecurityContextRepository();

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

            // Authorities come from the DB role only. The token's "role" custom claim can be
            // stale (a demoted admin keeps it until the token refreshes), so it is ignored.
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user.id(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);

        } catch (FirebaseAuthException e) {
            log.debug("Firebase token verification failed: {}", e.getMessage());
            // Don't set auth - Spring Security will reject protected endpoints
        }

        filterChain.doFilter(request, response);
    }
}
