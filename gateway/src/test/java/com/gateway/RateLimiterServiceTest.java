package com.gateway;

import com.gateway.ratelimit.RateLimiterService;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimiterServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RateLimiterService service;

    @BeforeEach
    void setUp() throws Exception {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        service = new RateLimiterService(redis);

        // Inject @Value fields via reflection
        setField(service, "defaultUserLimit", 10);
        setField(service, "windowSeconds", 60);
        setField(service, "endpointLimits", java.util.Map.of("/api/users", 20));
    }

    @Test
    void allowsRequestsUnderLimit() {
        when(valueOps.increment("rl:user:alice")).thenReturn(1L);
        var result = service.checkUserLimit("alice");
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(9);
    }

    @Test
    void blocksRequestsOverLimit() {
        when(valueOps.increment("rl:user:alice")).thenReturn(11L);
        var result = service.checkUserLimit("alice");
        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));
        var result = service.checkUserLimit("alice");
        assertThat(result.allowed()).isTrue(); // fail open
    }

    @Test
    void setsExpiryOnFirstIncrement() {
        when(valueOps.increment("rl:user:bob")).thenReturn(1L);
        service.checkUserLimit("bob");
        verify(redis).expire(eq("rl:user:bob"), any());
    }

    @Test
    void doesNotResetExpiryOnSubsequentIncrements() {
        when(valueOps.increment("rl:user:bob")).thenReturn(5L);
        service.checkUserLimit("bob");
        verify(redis, never()).expire(any(), any());
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        var field = RateLimiterService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
