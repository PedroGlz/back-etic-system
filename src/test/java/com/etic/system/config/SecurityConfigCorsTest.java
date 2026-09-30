package com.etic.system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigCorsTest {

    @Test
    void allowsCredentialedLocalPortalOriginsWithoutWildcard() throws Exception {
        CorsConfigurationSource source = new SecurityConfig()
            .corsConfigurationSource("https://apps.etic-system.online");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertTrue(cors.getAllowCredentials());
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cors.getAllowedHeaders().contains("Content-Type"));
        assertEquals("http://localhost:4200", cors.checkOrigin("http://localhost:4200"));
        assertEquals("http://localhost:4300", cors.checkOrigin("http://localhost:4300"));
        assertEquals("http://127.0.0.1:4300", cors.checkOrigin("http://127.0.0.1:4300"));
        assertEquals("https://apps.etic-system.online", cors.checkOrigin("https://apps.etic-system.online"));
        assertFalse(cors.getAllowedOrigins().contains("*"));

        assertPreflightAllowed(cors, "http://localhost:4200");
        assertPreflightAllowed(cors, "http://localhost:4300");
    }

    private void assertPreflightAllowed(CorsConfiguration cors, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(new DefaultCorsProcessor().processRequest(cors, request, response));
        assertEquals(200, response.getStatus());
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }
}
