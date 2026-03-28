package com.gateway.config;

import com.gateway.filter.GatewayFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public FilterRegistrationBean<GatewayFilter> gatewayFilterRegistration(GatewayFilter gatewayFilter) {
        FilterRegistrationBean<GatewayFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(gatewayFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
