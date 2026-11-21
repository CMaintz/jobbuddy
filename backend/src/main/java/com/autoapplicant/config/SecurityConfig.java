package com.autoapplicant.config;

import com.autoapplicant.adapter.security.AiRateLimitFilter;
import com.autoapplicant.adapter.security.DesktopToken;
import com.autoapplicant.adapter.security.DesktopTokenFilter;
import com.autoapplicant.adapter.security.FirebaseTokenFilter;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Two ways in, one per deployment. Hosted: Firebase ID tokens, any number of users. Desktop
 * profile: one local user on the user's own PC, signed in by the desktop shell's per-launch token,
 * with no Firebase project at all. Both are stateless APIs with the same authorization rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] HOSTED_PUBLIC_PATHS = {
        "/api/v1/auth/linkedin",
        "/actuator/health",
        "/uploads/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    // The CORS source is injected by name: Spring MVC's HandlerMappingIntrospector is a
    // CorsConfigurationSource too.
    @Bean
    @Profile("!desktop")
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            FirebaseTokenFilter firebaseTokenFilter,
            AiRateLimitFilter aiRateLimitFilter,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource hostedCors)
            throws Exception {
        return statelessApi(http, hostedCors, HOSTED_PUBLIC_PATHS)
                .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(aiRateLimitFilter, FirebaseTokenFilter.class)
                .build();
    }

    @Bean
    @Profile("!desktop")
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOriginsRaw) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(allowedOriginsRaw.split(",")));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        config.setAllowCredentials(true);
        return forAllPaths(config);
    }

    /**
     * Nothing is public here, uploads and health included: the only legitimate caller is the
     * shell, and it always has the token.
     */
    @Bean
    @Profile("desktop")
    public SecurityFilterChain desktopFilterChain(
            HttpSecurity http,
            ProvisionLocalUserUseCase localUser,
            AiRateLimitFilter aiRateLimitFilter,
            @Value("${app.desktop.token:}") String token,
            @Value("${app.desktop.shell-origin}") String shellOrigin)
            throws Exception {
        // Not a bean: Spring Boot registers every Filter bean with the servlet container too,
        // which would run it a second time outside this chain.
        DesktopTokenFilter tokenFilter = new DesktopTokenFilter(DesktopToken.of(token), localUser);
        return statelessApi(http, desktopCors(shellOrigin))
                // Ahead of CORS, so a request from another machine is refused before anything,
                // a preflight included, answers it.
                .addFilterBefore(tokenFilter, CorsFilter.class)
                // After CORS, so a 429 carries Access-Control-Allow-Origin and the shell can read
                // RATE_LIMIT_EXCEEDED instead of seeing an opaque network error.
                .addFilterAfter(aiRateLimitFilter, CorsFilter.class)
                .build();
    }

    /** Creates the desktop's local user before the server takes its first request. */
    @Bean
    @Profile("desktop")
    public SmartInitializingSingleton provisionLocalUserAtStartup(
            ProvisionLocalUserUseCase localUser) {
        return localUser::provisionLocalUser;
    }

    // CSRF is off because both modes send their credential in a header a browser never attaches
    // on its own, so a forged cross-site request carries none.
    private static HttpSecurity statelessApi(HttpSecurity http,
                                             CorsConfigurationSource cors,
                                             String... publicPaths)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated();
                });
    }

    /** Exactly the shell's origin: no patterns, and no credentials since it sends no cookies. */
    private static CorsConfigurationSource desktopCors(String shellOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(shellOrigin));
        config.setAllowedHeaders(List.of(
                DesktopTokenFilter.TOKEN_HEADER, "Content-Type", "X-Requested-With", "Accept"));
        return forAllPaths(config);
    }

    private static CorsConfigurationSource forAllPaths(CorsConfiguration config) {
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
