package com.campusfix.common.security;

import com.campusfix.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Creates and reads the JSON Web Tokens used to prove who is calling.
 *
 * <p>A JWT is three base64 pieces joined by dots: a header, the claims, and a
 * signature. The claims are readable by anyone — they are encoded, not encrypted
 * — so nothing secret goes in them. What the signature guarantees is that the
 * claims were not changed after the server issued them. Edit the role from
 * STUDENT to ADMIN and the signature stops matching, so the token is rejected.
 *
 * <p>This is what makes the API stateless: no session is stored on the server,
 * because every request carries a token the server can verify on its own.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiry;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofMinutes(properties.expiryMinutes());
    }

    /**
     * The user id is the subject. The role and department ride along so that
     * authorising a request needs no database lookup at all.
     */
    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .claim("departmentId", user.getDepartment() == null ? null : user.getDepartment().getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(signingKey)
                .compact();
    }

    public long expirySeconds() {
        return expiry.toSeconds();
    }

    /**
     * Returns the claims only if the signature checks out and the token has not
     * expired. An invalid token is not an error worth a stack trace — it is an
     * ordinary event on a public API — so it comes back as an empty Optional.
     */
    public Optional<Claims> readClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
