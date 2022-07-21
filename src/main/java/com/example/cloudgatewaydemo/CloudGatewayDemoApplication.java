package com.example.cloudgatewaydemo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
public class CloudGatewayDemoApplication {

    @Autowired
    private CustomerFilter filter;

    public static void main(String[] args) {
        SpringApplication.run(CloudGatewayDemoApplication.class, args);
    }

    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder) {

        return builder.routes()
                .route(p -> p.path("/**")
                        .filters(f -> f.filter(filter))
                        .uri("no://op"))
                .build();
    }
}

@Component
class CustomerFilter implements GatewayFilter, Ordered {

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        routeToUrlInRequestHeader(exchange);
        return chain.filter(exchange)
                .then(copyCorrIdFromRequestToResponseHeaderAfterRouting(exchange));
    }

    // WISH THERE WAS A OUT-OF-BOX FILTER THAT ROUTED TO URL SPECIFIED IN REQUEST HEADER
    private void routeToUrlInRequestHeader(ServerWebExchange exchange) {
        Optional<String> first = exchange.getRequest().getHeaders().get("x-forward-to").stream().findFirst();
        first.ifPresent(c -> {
            try {
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(c));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        });
    }

    // POST ROUTE FILTER -- WISH THERE WAS A OUT-OF-BO FILTER TO COPY REQUEST HEADER TO RESPONSE HEADER
    private Mono<Void> copyCorrIdFromRequestToResponseHeaderAfterRouting(ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> {
            List<String> list = new ArrayList<>();
            Optional<String> corrId = exchange.getRequest()
                    .getHeaders()
                    .get("correlationId")
                    .stream()
                    .findFirst();
            if (corrId.isPresent()) {
                list.add(corrId.get());
                exchange.getResponse()
                        .getHeaders()
                        .put("correlationId", list);
            }
        });
    }
}