package com.sitegenius.hrms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // FIX 1: "/user" MUST be included in enableSimpleBroker so that
        // convertAndSendToUser() can route to /user/{username}/queue/...
        // Without this, user-specific destinations are silently dropped.
        registry.enableSimpleBroker("/topic", "/queue", "/user");

        registry.setApplicationDestinationPrefixes("/app");

        // This prefix is used by convertAndSendToUser() internally —
        // it transforms /user/{name}/queue/x → /queue/x for that user.
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                // TODO for production: replace with your actual domain
                // .setAllowedOrigins("https://hrms-fe-ten.vercel.app")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}