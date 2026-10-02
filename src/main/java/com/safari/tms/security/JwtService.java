package com.safari.tms.security;

import com.safari.tms.config.AppProperties;
import com.safari.tms.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Issues and validates the HS256 tokens used for stateless authentication. */
@Service
public class JwtService {

    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] secretBytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "safari.jwt.secret must be at least 32 characters for HS256 signing");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(props.getJwt().getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(props.getJwt().getIssuer())
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long getExpirationMinutes() {
        return props.getJwt().getExpirationMinutes();
    }

    /** Returns the token subject (email), or {@code null} when the token is absent or invalid. */
    public String extractSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
