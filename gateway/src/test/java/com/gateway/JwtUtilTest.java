package com.gateway;

import com.gateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    @Test
    void generatesValidToken() {
        String token = jwtUtil.generateToken("user123", "ADMIN");
        assertThat(token).isNotBlank();
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void extractsUserIdFromToken() {
        String token = jwtUtil.generateToken("user123", "USER");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user123");
    }

    @Test
    void extractsClaimsFromToken() {
        String token = jwtUtil.generateToken("user456", "ADMIN");
        Claims claims = jwtUtil.validateAndExtract(token);
        assertThat(claims.get("userId", String.class)).isEqualTo("user456");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JwtUtil shortLived = new JwtUtil();
        ReflectionTestUtils.setField(shortLived, "secret",
                "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm");
        ReflectionTestUtils.setField(shortLived, "expirationMs", 1L); // 1ms TTL
        String token = shortLived.generateToken("user789", "USER");
        Thread.sleep(10);
        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtUtil.generateToken("user123", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtUtil.isValid("not.a.jwt")).isFalse();
        assertThat(jwtUtil.isValid("")).isFalse();
        assertThat(jwtUtil.isValid("Bearer token")).isFalse();
    }
}
