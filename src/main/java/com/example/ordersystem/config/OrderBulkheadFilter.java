package com.example.ordersystem.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

@Component
public class OrderBulkheadFilter extends OncePerRequestFilter {
    private final Semaphore permits;
    private final Counter rejected;

    public OrderBulkheadFilter(@Value("${order.bulkhead.max-concurrent:100}") int maxConcurrent,
            MeterRegistry registry) {
        permits = new Semaphore(maxConcurrent, true);
        rejected = registry.counter("order.bulkhead.rejected");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/orders".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            rejected.increment();
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Order service is busy; retry later\"}");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }
}
