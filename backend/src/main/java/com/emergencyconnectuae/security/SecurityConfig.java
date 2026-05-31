// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.time.Duration;

/**
 * Stateless OAuth2 resource server (SRS 3.2 / 10). The JWT is validated against
 * the Supabase JWKS endpoint; our two custom filters then enforce rate limiting,
 * the Redis session, MFA and the IP blacklist.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    private final JwtValidationFilter jwtValidationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtValidationFilter jwtValidationFilter, RateLimitFilter rateLimitFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtValidationFilter = jwtValidationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        IpAddressMatcher loopbackV4 = new IpAddressMatcher("127.0.0.1/32");
        IpAddressMatcher loopbackV6 = new IpAddressMatcher("::1");

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())              // X-Content-Type-Options: nosniff
                .frameOptions(frame -> frame.deny())                        // X-Frame-Options: DENY
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Internal endpoints restricted to localhost CIDR (SRS 10.4).
                .requestMatchers("/api/v1/health", "/actuator/**").access((authn, ctx) -> {
                    var req = ctx.getRequest();
                    boolean local = loopbackV4.matches(req) || loopbackV6.matches(req);
                    return new org.springframework.security.authorization.AuthorizationDecision(local);
                })
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(authenticationEntryPoint)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            // RateLimitFilter first; JwtValidationFilter after the bearer token is validated
            // (and therefore before the AuthorizationFilter that enforces the URL rules).
            .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(jwtValidationFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("ROLE_");
        authorities.setAuthoritiesClaimName("role");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Supabase signs access tokens with ES256 (asymmetric EC keys served via
        // JWKS). NimbusJwtDecoder defaults to RS256 only, so without these the
        // EC key never matches and every token is rejected with 401. RS256 is
        // also accepted for forward-compatibility if Supabase rotates key types.
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> withClockSkew = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofMinutes(15))
        );
        jwtDecoder.setJwtValidator(withClockSkew);
        return jwtDecoder;
    }
}
