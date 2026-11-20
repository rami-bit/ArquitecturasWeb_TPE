package com.ticketya.gateway.config;

import com.ticketya.gateway.security.ProblemJsonAccessDeniedHandler;
import com.ticketya.gateway.security.ProblemJsonAuthenticationEntryPoint;
import com.ticketya.gateway.tracing.AuthenticatedIdentityCaptureFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter) {

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/events").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/events/{id}").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/inventory/events/{eventId}/availability")
                        .permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        // E2-03: sólo el personal de acceso escanea; ADMIN puede auditar los intentos.
                        .pathMatchers(HttpMethod.POST, "/api/access/events/{eventId}/validations")
                        .hasRole("ACCESS_STAFF")
                        .pathMatchers(HttpMethod.GET, "/api/access/events/{eventId}/scans")
                        .hasAnyRole("ACCESS_STAFF", "ADMIN")
                        .pathMatchers("/api/access/**").hasAnyRole("ACCESS_STAFF", "ADMIN")
                        // E2-04: reportes para organizadores (ownership en Catalog) y ADMIN.
                        .pathMatchers("/api/reports/**").hasAnyRole("ORGANIZER", "ADMIN")
                        // E2-02: la bitácora centralizada es exclusiva de ADMIN.
                        .pathMatchers("/api/audit/**").hasRole("ADMIN")
                        // PDF-F10: preferencias y recomendaciones son sólo del cliente autenticado.
                        .pathMatchers("/api/customers/**").hasRole("CLIENT")
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(new ProblemJsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new ProblemJsonAccessDeniedHandler()))
                .exceptionHandling(handling -> handling
                        .accessDeniedHandler(new ProblemJsonAccessDeniedHandler()))
                .addFilterAfter(new AuthenticatedIdentityCaptureFilter(), SecurityWebFiltersOrder.AUTHENTICATION);

        return http.build();
    }
}
