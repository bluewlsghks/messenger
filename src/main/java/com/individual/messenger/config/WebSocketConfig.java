package com.individual.messenger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub"); // 援щ룆(prefix)
        config.setApplicationDestinationPrefixes("/pub"); // 諛쒗뻾(prefix)
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ??JWT 湲곕컲 ?묒냽 ?덉슜 (CORS ?덉슜)
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}


