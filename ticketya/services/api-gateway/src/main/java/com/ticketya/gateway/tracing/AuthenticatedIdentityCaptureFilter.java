package com.ticketya.gateway.tracing;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Se registra dentro de la cadena de seguridad, después de la autenticación y
 * antes de la autorización: copia el {@code sub} y el rol del JWT validado a
 * atributos del exchange para que {@link AccessLogWebFilter} registre el
 * usuario incluso cuando la autorización responde 403.
 */
public class AuthenticatedIdentityCaptureFilter implements WebFilter {
    public static final String USER_ID_ATTRIBUTE = AuthenticatedIdentityCaptureFilter.class.getName() + ".userId";
    public static final String USER_ROLE_ATTRIBUTE = AuthenticatedIdentityCaptureFilter.class.getName() + ".role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .doOnNext(context -> {
                    if (context.getAuthentication() instanceof JwtAuthenticationToken token
                            && token.isAuthenticated()) {
                        Jwt jwt = token.getToken();
                        exchange.getAttributes().put(USER_ID_ATTRIBUTE, jwt.getSubject());
                        Object roles = jwt.getClaim("roles");
                        if (roles instanceof List<?> list && list.size() == 1) {
                            exchange.getAttributes().put(USER_ROLE_ATTRIBUTE, String.valueOf(list.get(0)));
                        }
                    }
                })
                .then(chain.filter(exchange));
    }
}
