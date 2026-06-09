package com.aims.assembly.config.jwt;

import com.aims.assembly.dto.auth.TokenResponse;
import com.aims.assembly.mapper.TokenMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public TokenResponse.TokenDTO generateTokens(Authentication authentication) {
        String email = authentication.getName();
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        Long userId = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            userId = jwtPrincipal.userId();
        }

        Date now = new Date();
        String accessToken = createToken(email, role, userId, now, jwtProperties.getExpiration().getAccess());
        String refreshToken = createToken(email, role, userId, now, jwtProperties.getExpiration().getRefresh());

        return TokenMapper.toTokenDTO(
                accessToken,
                refreshToken,
                jwtProperties.getExpiration().getAccess() / 1000,
                "Bearer"
        );
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException exception) {
            log.warn("JWT token is expired: {}", exception.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("JWT token is invalid: {}", exception.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return getExpirationFromToken(token).before(new Date());
        } catch (ExpiredJwtException exception) {
            return true;
        }
    }

    public static String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(JwtConstants.HEADER_STRING);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(JwtConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(JwtConstants.TOKEN_PREFIX.length());
        }
        return null;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String email = claims.getSubject();
        String role = claims.get(JwtConstants.ROLE_CLAIM, String.class);
        Long userId = claims.get(JwtConstants.USER_ID_CLAIM, Long.class);

        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        JwtPrincipal principal = new JwtPrincipal(userId, email, role);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(JwtConstants.ROLE_CLAIM, String.class);
    }

    public Long extractUserId(String token) {
        return parseClaims(token).get(JwtConstants.USER_ID_CLAIM, Long.class);
    }

    public Claims extractAllClaims(String token) throws ExpiredJwtException {
        return parseClaims(token);
    }

    public Authentication extractAuthentication(HttpServletRequest request) {
        String accessToken = resolveToken(request);
        if (accessToken == null || !validateToken(accessToken)) {
            return null;
        }
        return getAuthentication(accessToken);
    }

    public TokenResponse.TokenDTO refreshAccessToken(String refreshToken) {
        if (!validateToken(refreshToken)) {
            return null;
        }

        Claims claims = parseClaims(refreshToken);
        String email = claims.getSubject();
        String role = claims.get(JwtConstants.ROLE_CLAIM, String.class);
        Long userId = claims.get(JwtConstants.USER_ID_CLAIM, Long.class);

        Date now = new Date();
        String newAccessToken = createToken(email, role, userId, now, jwtProperties.getExpiration().getAccess());

        return TokenMapper.toTokenDTO(
                newAccessToken,
                refreshToken,
                jwtProperties.getExpiration().getAccess() / 1000,
                "Bearer"
        );
    }

    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    private String createToken(String email, String role, Long userId, Date issuedAt, long expirationMillis) {
        return Jwts.builder()
                .subject(email)
                .claim(JwtConstants.ROLE_CLAIM, role)
                .claim(JwtConstants.USER_ID_CLAIM, userId)
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + expirationMillis))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record JwtPrincipal(Long userId, String email, String role) {

        public User toSpringUser() {
            return new User(email, "", List.of(new SimpleGrantedAuthority(role)));
        }
    }
}
