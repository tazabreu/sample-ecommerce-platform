package com.ecommerce.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security configuration for customer-facing service.
 * 
 * <p>Security Model:</p>
 * <ul>
 *   <li>Public endpoints: GET /api/v1/products, /api/v1/categories, /api/v1/carts/**, POST /api/v1/checkout, /actuator/health</li>
 *   <li>Manager endpoints: POST/PUT/DELETE /api/v1/products, /api/v1/categories (require ROLE_MANAGER)</li>
 *   <li>JWT-based authentication (OAuth2 Resource Server)</li>
 *   <li>Stateless sessions (no server-side session storage)</li>
 * </ul>
 * 
 * <p>For local development (dev profile), mock authentication is available at /api/v1/auth/login</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${jwt.secret:demo-secret-key-for-local-development-only-do-not-use-in-production}")
    private String jwtSecret;

    /**
     * Configure HTTP security with public and protected endpoints.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Stateless API, CSRF not needed
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - catalog browsing
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        
                        // Public endpoints - shopping cart (guest checkout)
                        .requestMatchers("/api/v1/carts/**").permitAll()
                        
                        // Public endpoints - checkout (guest checkout)
                        .requestMatchers(HttpMethod.POST, "/api/v1/checkout").permitAll()
                        
                        // Public endpoints - health checks
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        
                        // Public endpoints - mock auth (dev profile only)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        
                        // Manager endpoints - catalog management (handled by @PreAuthorize in controllers)
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").authenticated()
                        
                        // Default - require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * JWT decoder for validating JWT tokens.
     * Uses HMAC-SHA256 (HS256) with shared secret.
     *
     * @return configured JWT decoder
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // For production, use RSA public key or JWK Set URI
        // For local dev, use shared secret (HS256)
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * JWT authentication converter to extract roles from JWT claims.
     * Maps "roles" claim to Spring Security authorities with ROLE_ prefix.
     *
     * @return configured JWT authentication converter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    /**
     * Permissive security configuration for local development.
     * Only active in dev profile - allows all requests without authentication.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Profile("dev-insecure")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}

