package com.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "services")
@Getter
@Setter
public class ServiceRegistryConfig {

    private Map<String, ServiceConfig> services = new HashMap<>();

    @Getter
    @Setter
    public static class ServiceConfig {
        private String url;
        private String pathPrefix;
    }

    /**
     * Finds the downstream service config based on a request path.
     * Returns null if no matching service found.
     */
    public Map.Entry<String, ServiceConfig> resolveService(String path) {
        return services.entrySet().stream()
                .filter(e -> path.startsWith(e.getValue().getPathPrefix()))
                .findFirst()
                .orElse(null);
    }
}
