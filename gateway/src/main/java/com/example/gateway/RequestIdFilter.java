package com.example.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {
    public static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        String finalRequestId = requestId;
        exchange.getResponse().getHeaders().set(HEADER, finalRequestId);
        return chain.filter(exchange.mutate().request(builder -> builder.header(HEADER, finalRequestId)).build());
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
