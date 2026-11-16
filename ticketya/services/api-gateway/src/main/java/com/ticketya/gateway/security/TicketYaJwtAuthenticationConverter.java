package com.ticketya.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class TicketYaJwtAuthenticationConverter
        implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final List<String> ALLOWED_ROLES =
            List.of("CLIENT", "ORGANIZER", "ADMIN", "ACCESS_STAFF");
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        if (jwt.getExpiresAt() == null) {
            return Mono.error(new BadCredentialsException("Missing exp claim"));
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return Mono.error(new BadCredentialsException("Missing sub claim"));
        }
        if (!isUuid(sub)) {
            return Mono.error(new BadCredentialsException("sub claim must be a valid UUID"));
        }

        Object rolesClaim = jwt.getClaim("roles");
        if (!(rolesClaim instanceof List<?> roles)) {
            return Mono.error(new BadCredentialsException("roles claim must be an array"));
        }
        if (roles.size() != 1) {
            return Mono.error(new BadCredentialsException(
                    "roles claim must contain exactly one value"));
        }
        String role = String.valueOf(roles.get(0));
        if (!ALLOWED_ROLES.contains(role)) {
            return Mono.error(new BadCredentialsException("role not allowed: " + role));
        }

        Collection<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, sub));
    }

    private boolean isUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
