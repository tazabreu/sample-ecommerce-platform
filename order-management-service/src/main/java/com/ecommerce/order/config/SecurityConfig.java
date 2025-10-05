package com.ecommerce.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security configuration for order management service.
 * 
 * <p>Security Model:</p>
 * <ul>
 *   <li>Public endpoints: GET /api/v1/orders/{orderNumber} (guest order lookup)</li>
 *   <li>Manager endpoints: GET /api/v1/orders (list all), POST /api/v1/orders/{orderNumber}/cancel, POST /api/v1/orders/{orderNumber}/fulfill (require ROLE_MANAGER)</li>
 *   <li>JWT-based authentication (OAuth2 Resource Server)</li>
 *   <li>Stateless sessions (no server-side session storage)</li>
 *   <li>CSRF disabled (stateless API)</li>
 * </ul>
 * 
 * <p>Profiles:</p>
 * <ul>
 *   <li>test: Permissive security for integration tests</li>
 *   <li>dev: JWT enabled but can be disabled via property</li>
 *   <li>stg/prod: Full JWT security enforcement</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret:demo-secret-key-for-local-development-only-do-not-use-in-production}")
    private String jwtSecret;

    @Value("${jwt.issuer:ecommerce-platform-dev}")
    private String jwtIssuer;

    @Value("${app.security.jwt.enabled:true}")
    private boolean jwtEnabled;

    // Removed permissive test SecurityFilterChain; tests should run with security enabled

    /**
     * Production security configuration with JWT authentication.
     * Active in dev, stg, prod profiles.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        if (!jwtEnabled) {
            // Dev mode with JWT disabled - permit all
            http.authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        } else {
            // Production mode with JWT enforcement
            http
                .authorizeHttpRequests(auth -> auth
                    // Public endpoint - guest order lookup by order number
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").permitAll()
                    
                    // Public health and metrics
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll()
                    
                    // Swagger/OpenAPI (dev only, should be disabled in prod via properties)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    
                    // Manager-only order management endpoints
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasRole("MANAGER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/cancel").hasRole("MANAGER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/fulfill").hasRole("MANAGER")
                    
                    // Default - require authentication
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                );
        }

        return http.build();
    }

    /**
     * JWT decoder for validating JWT tokens.
     * Uses HMAC-SHA256 (HS256) with shared secret.
     * 
     * <p>Development: Uses shared secret with issuer validation</p>
     * <p>Production: Should use RSA public key or JWK Set URI from identity provider</p>
     *
     * @return configured JWT decoder with issuer validation
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        
        // Add issuer validation for security
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(jwtIssuer);
        decoder.setJwtValidator(withIssuer);
        
        return decoder;
    }

    /**
     * JWT authentication converter to extract roles from JWT claims.
     * Maps "roles" claim to Spring Security authorities with ROLE_ prefix.
     * 
     * <p>Example JWT payload:</p>
     * <pre>
     * {
     *   "sub": "manager@example.com",
     *   "roles": ["MANAGER"],
     *   "exp": 1234567890
     * }
     * </pre>
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
}