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
    @Value("${app.broker.relay.enabled:false}") private boolean relay;
    @Value("${app.broker.relay.host:127.0.0.1}") private String host;
    @Value("${app.broker.relay.port:61613}") private int port;
    @Value("${app.broker.relay.login:}") private String login;
    @Value("${app.broker.relay.password:}") private String password;
    @Value("${app.broker.relay.virtual-host:/}") private String virtualHost;
    private final StompSecurityInterceptor security;
    private final String[] allowedOrigins;
    public WebSocketConfig(StompSecurityInterceptor security,
            @Value("${app.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}") String[] allowedOrigins) {
        this.security = security;
        this.allowedOrigins = allowedOrigins;
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        if(relay) {
            config.configureBrokerChannel().interceptors(new BrokerDestinationCodec(false));
            if(login.isBlank() || password.isBlank())throw new IllegalArgumentException("Broker relay credentials must be configured explicitly");
            var relayRegistration=config.enableStompBrokerRelay("/topic","/queue","/exchange").setRelayHost(host).setRelayPort(port)
                    .setClientLogin(login).setClientPasscode(password).setSystemLogin(login).setSystemPasscode(password)
                    .setVirtualHost(virtualHost).setSystemHeartbeatSendInterval(10000).setSystemHeartbeatReceiveInterval(10000);
            relayRegistration.setUserDestinationBroadcast("/topic/messenger-unresolved-users");
            relayRegistration.setUserRegistryBroadcast("/topic/messenger-user-registry");
        } else config.enableSimpleBroker("/topic","/sub","/queue");
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
        registration.taskExecutor(new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor())
                .corePoolSize(4).maxPoolSize(16).queueCapacity(2000);
        registration.interceptors(security);
        if (relay) registration.interceptors(new BrokerDestinationCodec(false));
    }
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor())
                .corePoolSize(4).maxPoolSize(16).queueCapacity(2000);
        if (relay) registration.interceptors(new BrokerDestinationCodec(true));
        registration.interceptors(security.outbound());
    }
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(32 * 1024).setSendBufferSizeLimit(512 * 1024).setSendTimeLimit(15000);
    }
}
