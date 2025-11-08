package com.hotel.gateway.filter;

import com.hotel.gateway.config.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().toString();

            System.out.println("=".repeat(100));
            System.out.println("🔐 GATEWAY - INCOMING REQUEST");
            System.out.println("   📍 Path: " + path);
            System.out.println("   🚀 Method: " + request.getMethod());
            System.out.println("   📄 Content-Type: " + request.getHeaders().getContentType());
            System.out.println("   📏 Content-Length: " + request.getHeaders().getContentLength());
            System.out.println("   🌐 Headers:");
            request.getHeaders().forEach((key, value) ->
                    System.out.println("      " + key + ": " + value)
            );

            // Для POST/PUT запросов логируем тело
            if ((request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT)
                    && request.getHeaders().getContentLength() > 0) {

                return DataBufferUtils.join(request.getBody())
                        .flatMap(dataBuffer -> {
                            // Читаем тело запроса
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            String body = new String(bytes, StandardCharsets.UTF_8);
                            System.out.println("   📦 REQUEST BODY:");
                            System.out.println("      " + body);

                            // Пропускаем публичные endpoints
                            if (path.equals("/api/user/register") || path.equals("/api/user/auth")) {
                                System.out.println("✅ PUBLIC ENDPOINT - No JWT check: " + path);

                                // Восстанавливаем тело и передаем дальше
                                ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                                    }
                                };
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            }

                            // Проверяем JWT для защищенных endpoints
                            String authHeader = request.getHeaders().getFirst("Authorization");

                            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                                System.out.println("❌ NO TOKEN - Returning 401");
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                return exchange.getResponse().setComplete();
                            }

                            String token = authHeader.substring(7);

                            try {
                                if (jwtUtil.isTokenExpired(token)) {
                                    System.out.println("❌ EXPIRED TOKEN");
                                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return exchange.getResponse().setComplete();
                                }

                                String username = jwtUtil.extractUsername(token);
                                String role = jwtUtil.extractRole(token);

                                System.out.println("✅ VALID TOKEN - User: " + username + ", Role: " + role);

                                if (username == null || role == null) {
                                    System.out.println("❌ INVALID TOKEN - missing data");
                                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return exchange.getResponse().setComplete();
                                }

                                // Создаем модифицированный запрос с телом и новыми заголовками
                                ServerHttpRequest modifiedRequest = new ServerHttpRequestDecorator(request) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                                    }

                                    @Override
                                    public HttpHeaders getHeaders() {
                                        HttpHeaders headers = new HttpHeaders();
                                        headers.putAll(request.getHeaders());
                                        headers.set("X-User-Name", username);
                                        headers.set("X-User-Role", role);
                                        return headers;
                                    }
                                };

                                System.out.println("➡️  FORWARDING TO BACKEND:");
                                System.out.println("   👤 X-User-Name: " + username);
                                System.out.println("   🎭 X-User-Role: " + role);
                                System.out.println("   📦 Body preserved: " + (bytes.length > 0 ? "YES" : "NO"));
                                System.out.println("=".repeat(100));

                                return chain.filter(exchange.mutate().request(modifiedRequest).build());

                            } catch (Exception e) {
                                System.out.println("❌ JWT ERROR: " + e.getMessage());
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                return exchange.getResponse().setComplete();
                            }
                        });
            } else {
                // Для запросов без тела
                System.out.println("   📦 REQUEST BODY: NO BODY (GET/DELETE or empty)");

                // Пропускаем публичные endpoints
                if (path.equals("/api/user/register") || path.equals("/api/user/auth")) {
                    System.out.println("✅ PUBLIC ENDPOINT - No JWT check: " + path);
                    return chain.filter(exchange);
                }

                String authHeader = request.getHeaders().getFirst("Authorization");

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    System.out.println("❌ NO TOKEN - Returning 401");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                String token = authHeader.substring(7);

                try {
                    if (jwtUtil.isTokenExpired(token)) {
                        System.out.println("❌ EXPIRED TOKEN");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    String username = jwtUtil.extractUsername(token);
                    String role = jwtUtil.extractRole(token);

                    System.out.println("✅ VALID TOKEN - User: " + username + ", Role: " + role);

                    if (username == null || role == null) {
                        System.out.println("❌ INVALID TOKEN - missing data");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    // Добавляем заголовки для запросов без тела
                    ServerHttpRequest modifiedRequest = request.mutate()
                            .header("X-User-Name", username)
                            .header("X-User-Role", role)
                            .build();

                    System.out.println("➡️  FORWARDING TO BACKEND:");
                    System.out.println("   👤 X-User-Name: " + username);
                    System.out.println("   🎭 X-User-Role: " + role);
                    System.out.println("=".repeat(100));

                    return chain.filter(exchange.mutate().request(modifiedRequest).build());

                } catch (Exception e) {
                    System.out.println("❌ JWT ERROR: " + e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }
        };
    }

    public static class Config {
        // Конфигурация
    }
}