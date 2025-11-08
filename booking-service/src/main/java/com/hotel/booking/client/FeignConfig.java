package com.hotel.booking.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new FeignRequestInterceptor();
    }

    public static class FeignRequestInterceptor implements RequestInterceptor {

        @Override
        public void apply(RequestTemplate template) {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // Передаем заголовок Authorization
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null) {
                    template.header("Authorization", authHeader);
                    System.out.println("🔐 Feign Client - Forwarding Authorization header");
                }

                // Передаем пользовательские заголовки
                String userName = request.getHeader("X-User-Name");
                String userRole = request.getHeader("X-User-Role");

                if (userName != null) {
                    template.header("X-User-Name", userName);
                }
                if (userRole != null) {
                    template.header("X-User-Role", userRole);
                }

                System.out.println("🔄 Feign Client - Headers forwarded to hotel-service");
                System.out.println("   - X-User-Name: " + userName);
                System.out.println("   - X-User-Role: " + userRole);
            } else {
                System.out.println("⚠️ Feign Client - No request attributes found");
            }
        }
    }
}