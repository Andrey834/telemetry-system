package ru.telemetry.query.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    // Отдаётся как CorsConfigurationSource (не CorsWebFilter) и подключается в SecurityConfig
    // через .cors(...) — так Spring Security сам корректно пропускает CORS-preflight (OPTIONS)
    // до проверки JWT, а не после.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration devicesConfig = new CorsConfiguration();
        devicesConfig.setAllowedOrigins(allowedOrigins);
        devicesConfig.setAllowedMethods(List.of("GET", "POST"));
        devicesConfig.setAllowedHeaders(List.of("*"));

        CorsConfiguration authConfig = new CorsConfiguration();
        authConfig.setAllowedOrigins(allowedOrigins);
        authConfig.setAllowedMethods(List.of("POST"));
        authConfig.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/devices/**", devicesConfig);
        source.registerCorsConfiguration("/auth/**", authConfig);

        return source;
    }
}
