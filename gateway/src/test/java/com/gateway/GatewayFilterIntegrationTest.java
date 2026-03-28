package com.gateway;

import com.gateway.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GatewayFilterIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean  RestTemplate restTemplate;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = jwtUtil.generateToken("test-user", "USER");
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void requestWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithValidTokenIsProxied() throws Exception {
        // Mock the downstream service response
        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("{\"users\":[]}".getBytes()));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void correlationIdIsForwardedFromRequest() throws Exception {
        String correlationId = "test-correlation-123";
        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("{}".getBytes()));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + validToken)
                        .header("X-Correlation-ID", correlationId))
                .andExpect(header().string("X-Correlation-ID", correlationId));
    }

    @Test
    void authEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/unknown-service/foo")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }
}
