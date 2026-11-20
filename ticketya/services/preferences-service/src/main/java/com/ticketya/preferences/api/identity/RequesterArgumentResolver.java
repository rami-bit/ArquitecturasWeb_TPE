package com.ticketya.preferences.api.identity;

import com.ticketya.preferences.api.error.ApiProblemException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Único punto de resolución de identidad para toda la API de preferencias: sin
 * {@code X-User-Id} UUID o con un rol distinto de CLIENT responde 403.
 */
@Component
public class RequesterArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentRequester.class)
                && Requester.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        UUID userId = parseUserId(webRequest.getHeader(USER_HEADER));
        String role = webRequest.getHeader(ROLE_HEADER);
        if (role == null || !"CLIENT".equals(role)) {
            throw ApiProblemException.forbidden("The X-User-Role header must be CLIENT");
        }
        return new Requester(userId, role);
    }

    private static UUID parseUserId(String rawUserId) {
        if (rawUserId != null && !rawUserId.isBlank()) {
            try {
                return UUID.fromString(rawUserId.trim());
            } catch (IllegalArgumentException invalid) {
                // fall through to the forbidden error
            }
        }
        throw ApiProblemException.forbidden("A valid X-User-Id header is required");
    }
}
