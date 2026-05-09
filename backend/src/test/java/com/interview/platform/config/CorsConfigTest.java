package com.interview.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.POST;

class CorsConfigTest {

    @Test
    void corsConfigurationAllowsProductionFrontendPreflightWithAuthHeaders() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOriginPatterns",
                "https://interview-platform-frontend-iv6x.onrender.com,http://localhost:5173,https://*.onrender.com");
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest(OPTIONS.name(), "/api/auth/login");
        request.addHeader(ORIGIN, "https://interview-platform-frontend-iv6x.onrender.com");
        request.addHeader(ACCESS_CONTROL_REQUEST_METHOD, POST.name());

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://interview-platform-frontend-iv6x.onrender.com"))
                .isEqualTo("https://interview-platform-frontend-iv6x.onrender.com");
        assertThat(configuration.checkHttpMethod(POST)).contains(POST);
        assertThat(configuration.checkHttpMethod(OPTIONS)).contains(OPTIONS);
        assertThat(configuration.checkHeaders(List.of("Authorization", "Content-Type")))
                .containsExactly("Authorization", "Content-Type");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
