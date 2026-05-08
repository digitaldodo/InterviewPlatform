package com.interview.platform.security;

import com.interview.platform.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.Date;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long accessTokenMinutes;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.jwt.issuer:interviewprep-api}") String issuer
    ) {
        String normalized = secret == null || secret.length() < 32
                ? "change-this-development-secret-to-at-least-32-characters"
                : secret;
        if (normalized.equals("change-this-development-secret-to-at-least-32-characters")) {
            log.warn("JWT secret is using the insecure development fallback. Configure JWT_SECRET before production deployment.");
        }
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.issuer = issuer == null || issuer.isBlank() ? "interviewprep-api" : issuer.trim();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .claim("roles", user.getRoles())
                .claim("activeWorkspace", user.getActiveWorkspace())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(accessTokenMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return claims(token).getPayload().getSubject();
    }

    public boolean isValid(String token, String email) {
        Claims claims = claims(token).getPayload();
        return email.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    private Jws<Claims> claims(String token) {
        Jws<Claims> parsed = Jwts.parser().requireIssuer(issuer).verifyWith(key).build().parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("Token subject is missing");
        }
        return parsed;
    }
}
