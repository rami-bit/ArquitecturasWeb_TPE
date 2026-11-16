package com.ticketya.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IdentityPropagationGatewayFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .defaultIfEmpty(new SecurityContextImpl())
                .flatMap(context -> mutateRequestAndContinue(exchange, chain, context));
    }

    private Mono<Void> mutateRequestAndContinue(ServerWebExchange exchange,
            GatewayFilterChain chain, SecurityContext context) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.remove(AUTHORIZATION_HEADER);
                });

        Authentication authentication = context.getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth
                && authentication.isAuthenticated()) {
            Jwt jwt = jwtAuth.getToken();
            String sub = jwt.getSubject();
            String role = extractSingleRole(jwt);
            if (sub != null && !sub.isBlank()) {
                requestBuilder.header(USER_ID_HEADER, sub);
            }
            if (role != null && !role.isBlank()) {
                requestBuilder.header(USER_ROLE_HEADER, role);
            }
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(requestBuilder.build())
                .build();
        return chain.filter(mutatedExchange);
    }

    private String extractSingleRole(Jwt jwt) {
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof java.util.List<?> roles && roles.size() == 1) {
            return String.valueOf(roles.get(0));
        }
        return null;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
