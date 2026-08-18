package com.example.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfiguration {
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-Client-Id"))
                .filter(value -> !value.isBlank())
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var address = exchange.getRequest().getRemoteAddress();
                    return address == null ? "unknown" : address.getAddress().getHostAddress();
                }));
    }
}
