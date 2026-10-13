package com.liveseat.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class JwtSupport {
    private JwtSupport() {
    }

    public static String create(String secret, JwtPayload payload, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(payload.userId().toString())
                .claim("username", payload.username())
                .claim("role", payload.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key(secret))
                .compact();
    }

    public static JwtPayload parse(String secret, String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtPayload(Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

