package com.interview.platform.security;

import com.interview.platform.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTokenMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        String normalized = secret == null || secret.length() < 32
                ? "change-this-development-secret-to-at-least-32-characters"
                : secret;
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .claim("roles", user.getRoles())
                .claim("activeWorkspace", user.getActiveWorkspace())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return claims(token).getSubject();
    }

    public boolean isValid(String token, String email) {
        Claims claims = claims(token);
        return email.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
