package com.example.ordersystem.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {
    private final String instanceId;

    public InstanceHeaderFilter(@Value("${INSTANCE_ID:${HOSTNAME:local-order-service}}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Order-Instance", instanceId);
        filterChain.doFilter(request, response);
    }
}
