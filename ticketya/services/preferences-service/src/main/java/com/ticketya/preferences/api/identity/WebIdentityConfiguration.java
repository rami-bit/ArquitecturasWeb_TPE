package com.ticketya.preferences.api.identity;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registra el resolver de identidad derivada del Gateway. */
@Configuration
public class WebIdentityConfiguration implements WebMvcConfigurer {

    private final RequesterArgumentResolver requesterResolver;

    public WebIdentityConfiguration(RequesterArgumentResolver requesterResolver) {
        this.requesterResolver = requesterResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(requesterResolver);
    }
}
