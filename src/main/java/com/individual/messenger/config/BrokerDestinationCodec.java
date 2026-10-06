package com.individual.messenger.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import java.util.regex.Pattern;

/** Relay wire format only. Application destinations and authorization rules stay unchanged. */
public final class BrokerDestinationCodec implements ChannelInterceptor {
    private static final Pattern LOGICAL = Pattern.compile("^/topic/chat/([A-Za-z0-9_-]{1,100})(/read)?$");
    private static final Pattern WIRE = Pattern.compile("^/topic/chat\\.([A-Za-z0-9_-]{1,100})(\\.read)?$");
    private final boolean fromBroker;
    public BrokerDestinationCodec(boolean fromBroker) { this.fromBroker = fromBroker; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        String destination = headers.getDestination();
        if (destination == null) return message;
        var match = (fromBroker ? WIRE : LOGICAL).matcher(destination);
        if (!match.matches()) return message;
        String translated = fromBroker
                ? "/topic/chat/" + match.group(1) + (match.group(2) == null ? "" : "/read")
                : "/topic/chat." + match.group(1) + (match.group(2) == null ? "" : ".read");
        if (!fromBroker && headers.getCommand() == null && headers.getMessageType() == SimpMessageType.MESSAGE) {
            headers.updateStompCommandAsClientMessage();
        }
        headers.setDestination(translated);
        headers.setNativeHeader("destination", translated);
        return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
    }
}
