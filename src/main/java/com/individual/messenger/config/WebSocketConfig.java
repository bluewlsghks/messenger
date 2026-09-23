package com.individual.messenger.config;

import com.individual.messenger.security.StompSecurityInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompSecurityInterceptor security;
    private final String[] allowedOrigins;
    public WebSocketConfig(StompSecurityInterceptor security,
            @Value("${app.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}") String[] allowedOrigins) {
        this.security = security;
        this.allowedOrigins = allowedOrigins;
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub", "/queue");
        config.setApplicationDestinationPrefixes("/pub");
        config.setUserDestinationPrefix("/user");
        config.setPreservePublishOrder(true);
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp").setAllowedOrigins(allowedOrigins).withSockJS();
    }
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(security);
    }
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(security.outbound());
    }
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(32 * 1024).setSendBufferSizeLimit(512 * 1024).setSendTimeLimit(15000);
    }
}
