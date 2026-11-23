package com.autoapplicant.adapter.security;

import com.autoapplicant.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Signs a user in for one request, including its ASYNC dispatch. The principal is the user's id
 * (SecurityContextHelper and AiRateLimitFilter read it as a UUID), and the only authority is the
 * user's role from the database.
 */
final class RequestSignIn {

    // A DeferredResult response is written on a second (ASYNC) dispatch, which the auth filters
    // skip. SecurityContextHolderFilter reloads the context from this request attribute on that
    // dispatch; without the save it finds nothing and the finished result is answered with 403.
    // Saving beats re-running a filter on the async dispatch: no second token check or user
    // lookup, and a token that expires while the AI call runs cannot lose the result.
    private static final SecurityContextRepository CONTEXTS =
            new RequestAttributeSecurityContextRepository();

    private RequestSignIn() {}

    static void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.id(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        CONTEXTS.saveContext(context, request, response);
    }
}
