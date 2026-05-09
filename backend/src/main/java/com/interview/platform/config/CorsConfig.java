package com.interview.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin-patterns}")
    private String allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = buildCorsConfiguration();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = parseAllowedOriginPatterns(allowedOriginPatterns);

                registry.addMapping("/api/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-Request-Id")
                        .exposedHeaders("X-Request-Id", "Retry-After")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    private CorsConfiguration buildCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(parseAllowedOriginPatterns(allowedOriginPatterns)));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return configuration;
    }

    static String[] parseAllowedOriginPatterns(String originPatterns) {
        return Arrays.stream(originPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
