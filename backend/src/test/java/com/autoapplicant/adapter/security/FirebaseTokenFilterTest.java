package com.autoapplicant.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authorities must follow the DB role, never a possibly stale "role" claim on the token. */
class FirebaseTokenFilterTest {

    private static final String ID_TOKEN = "id-token";
    private static final String FIREBASE_UID = "firebase-uid";
    private static final String EMAIL = "alice@example.com";

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
    private final ProvisionFirebaseUserUseCase provisionUser = mock(ProvisionFirebaseUserUseCase.class);
    private final FirebaseTokenFilter filter = new FirebaseTokenFilter(provisionUser, firebaseAuth);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void a_demoted_admin_with_a_stale_admin_claim_gets_only_the_db_role() throws Exception {
        givenToken(Map.of("role", "ADMIN"), UserRole.USER);

        filter.doFilter(bearerRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(authorities()).containsExactly("ROLE_USER");
    }

    @Test
    void an_admin_in_the_db_is_admin_without_any_claim() throws Exception {
        givenToken(Map.of(), UserRole.ADMIN);

        filter.doFilter(bearerRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(authorities()).containsExactly("ROLE_ADMIN");
    }

    private void givenToken(Map<String, Object> claims, UserRole dbRole) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getUid()).thenReturn(FIREBASE_UID);
        when(token.getEmail()).thenReturn(EMAIL);
        when(token.getClaims()).thenReturn(claims);
        when(firebaseAuth.verifyIdToken(ID_TOKEN)).thenReturn(token);
        User user = new User(UUID.randomUUID(), EMAIL, null, null, FIREBASE_UID, dbRole,
                true, true, Instant.now(), Instant.now());
        when(provisionUser.findOrCreate(FIREBASE_UID, EMAIL, null)).thenReturn(user);
    }

    private static MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + ID_TOKEN);
        return request;
    }

    private static List<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
