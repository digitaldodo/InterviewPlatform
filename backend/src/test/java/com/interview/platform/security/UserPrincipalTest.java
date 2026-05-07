package com.interview.platform.security;

import com.interview.platform.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrincipalTest {

    @Test
    void mapsMultiRoleUsersToSpringRoleAuthoritiesWithoutDoublePrefixing() {
        User user = new User();
        user.setEmail("multi@example.com");
        user.setCreatedAt(Instant.now());
        user.setIsVerified(false);
        user.setRoles(List.of("interviewee", "ROLE_INTERVIEWER", "INTERVIEWER"));

        UserPrincipal principal = new UserPrincipal(user);

        Set<String> authorities = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_INTERVIEWEE", "ROLE_INTERVIEWER"), authorities);
        assertTrue(principal.isEnabled());
    }
}
