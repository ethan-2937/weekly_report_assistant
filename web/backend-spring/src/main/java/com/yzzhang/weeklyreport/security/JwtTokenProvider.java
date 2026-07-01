package com.yzzhang.weeklyreport.security;

import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final WeeklyReportProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(WeeklyReportProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("WEEKLY_JWT_SECRET must be at least 32 bytes.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret);
    }

    public String generateToken(AuthenticatedUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(getExpiresInSeconds());
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("uid", user.getId())
            .claim("roles", user.getRoles())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact();
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validate(String token) {
        parseClaims(token);
        return true;
    }

    public long getExpiresInSeconds() {
        return properties.getAuth().getTokenExpireMinutes() * 60;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
