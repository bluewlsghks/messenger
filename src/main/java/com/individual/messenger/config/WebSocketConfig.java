package com.individual.messenger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub"); // 구독(prefix)
        config.setApplicationDestinationPrefixes("/pub"); // 발행(prefix)
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ JWT 기반 접속 허용 (CORS 허용)
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
