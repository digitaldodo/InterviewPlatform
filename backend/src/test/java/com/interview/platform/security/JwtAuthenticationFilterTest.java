package com.interview.platform.security;

import com.interview.platform.model.User;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validJwtAuthenticatesUserEvenWhenEmailVerificationIsPending() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        UserDetails principal = new UserPrincipal(user("user-1", false, List.of("INTERVIEWEE", "INTERVIEWER")));

        when(jwtService.extractEmail("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(jwtService.isValid("valid-token", "user@example.com")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("user@example.com", authentication.getName());
        assertEquals(2, authentication.getAuthorities().size());
    }

    @Test
    void invalidJwtDoesNotAuthenticateButAllowsChainToReachSecurityEntryPoint() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        when(jwtService.extractEmail("expired-token")).thenThrow(new RuntimeException("expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions/meeting-providers");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
    }

    private User user(String id, boolean verified, List<String> roles) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setName("User");
        user.setRoles(roles);
        user.setCreatedAt(Instant.now());
        user.setIsVerified(verified);
        return user;
    }
}
