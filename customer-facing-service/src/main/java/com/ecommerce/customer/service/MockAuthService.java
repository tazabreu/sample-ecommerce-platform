package com.ecommerce.customer.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Mock authentication service for local development.
 * Generates JWT tokens for testing authenticated endpoints without real OAuth provider.
 * 
 * <p>WARNING: This service should ONLY be used in development environments.
 * Never deploy this to production.</p>
 * 
 * <p>Supported users:</p>
 * <ul>
 *   <li>username: "manager", password: "manager123" → ROLE_MANAGER</li>
 *   <li>username: "guest", password: "guest123" → ROLE_GUEST</li>
 * </ul>
 */
@Service
@Profile("dev")
public class MockAuthService {

    private static final Logger logger = LoggerFactory.getLogger(MockAuthService.class);

    @Value("${jwt.secret:demo-secret-key-for-local-development-only-do-not-use-in-production}")
    private String jwtSecret;

    @Value("${jwt.issuer:ecommerce-platform-dev}")
    private String jwtIssuer;

    @Value("${jwt.expiration-minutes:15}")
    private int jwtExpirationMinutes;

    /**
     * Generate JWT token for a given username and password.
     * Validates credentials and returns token with appropriate roles.
     *
     * @param username the username
     * @param password the password
     * @return JWT token
     * @throws IllegalArgumentException if credentials are invalid
     */
    public String generateToken(String username, String password) {
        logger.info("Mock authentication attempt for username: {}", username);

        // Validate credentials and get roles
        List<String> roles = validateCredentials(username, password);

        // Generate JWT token
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtExpirationMinutes, ChronoUnit.MINUTES);

        // Create secret key - use HS256 to match SecurityConfig
        SecretKey secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        String token = Jwts.builder()
                .subject(username)
                .issuer(jwtIssuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("roles", roles)
                .claim("type", "mock")
                .signWith(secretKey)
                .compact();

        logger.info("Mock JWT token generated for username: {}, roles: {}, expires: {}", 
                username, roles, expiration);

        return token;
    }

    /**
     * Validate mock credentials and return roles.
     *
     * @param username the username
     * @param password the password
     * @return list of roles
     * @throws IllegalArgumentException if credentials are invalid
     */
    private List<String> validateCredentials(String username, String password) {
        // Mock user database
        Map<String, MockUser> users = Map.of(
                "manager", new MockUser("manager", "manager123", List.of("MANAGER")),
                "guest", new MockUser("guest", "guest123", List.of("GUEST"))
        );

        MockUser user = users.get(username);
        if (user == null || !user.password.equals(password)) {
            logger.warn("Mock authentication failed for username: {}", username);
            throw new IllegalArgumentException("Invalid credentials");
        }

        return user.roles;
    }

    /**
     * Calculate token expiration in seconds.
     *
     * @return expiration in seconds
     */
    public int getExpirationSeconds() {
        return jwtExpirationMinutes * 60;
    }

    /**
     * Mock user record.
     */
    private record MockUser(String username, String password, List<String> roles) {}
}

