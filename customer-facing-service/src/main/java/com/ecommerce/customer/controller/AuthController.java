package com.ecommerce.customer.controller;

import com.ecommerce.customer.service.MockAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mock authentication controller for local development.
 * Provides JWT token generation endpoint for testing authenticated endpoints.
 * 
 * <p>WARNING: This controller should ONLY be active in development environments.
 * Never deploy this to production.</p>
 * 
 * <p>Endpoint:</p>
 * <ul>
 *   <li>POST /api/v1/auth/login - Generate JWT token</li>
 * </ul>
 * 
 * <p>Example request:</p>
 * <pre>
 * {
 *   "username": "manager",
 *   "password": "manager123"
 * }
 * </pre>
 * 
 * <p>Example response:</p>
 * <pre>
 * {
 *   "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 900
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile("dev")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final MockAuthService mockAuthService;

    public AuthController(MockAuthService mockAuthService) {
        this.mockAuthService = mockAuthService;
    }

    /**
     * Mock login endpoint that generates JWT token.
     * Only active in dev profile.
     *
     * @param request login request with username and password
     * @return login response with JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("Mock login request for username: {}", request.username());

        try {
            String token = mockAuthService.generateToken(request.username(), request.password());
            int expiresIn = mockAuthService.getExpirationSeconds();

            LoginResponse response = new LoginResponse(
                    token,
                    "Bearer",
                    expiresIn
            );

            logger.info("Mock login successful for username: {}", request.username());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Mock login failed for username: {}, error: {}", 
                    request.username(), ex.getMessage());
            
            return ResponseEntity.status(401).body(
                    new LoginResponse(null, null, 0)
            );
        }
    }

    /**
     * Login request DTO.
     *
     * @param username the username
     * @param password the password
     */
    public record LoginRequest(String username, String password) {}

    /**
     * Login response DTO.
     *
     * @param accessToken the JWT access token
     * @param tokenType the token type (always "Bearer")
     * @param expiresIn token expiration in seconds
     */
    public record LoginResponse(String accessToken, String tokenType, int expiresIn) {}
}


