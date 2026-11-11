package com.ticketya.purchase;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class PurchaseTracingFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "ticketya.correlationId";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value=request.getHeader("X-Correlation-Id");
        if(value==null||value.isBlank()) value=UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE,value); response.setHeader("X-Correlation-Id",value);
        chain.doFilter(request,response);
    }
}
