package com.individual.messenger.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import java.util.regex.Pattern;

/** Relay wire format only. Application destinations and authorization rules stay unchanged. */
public final class BrokerDestinationCodec implements ChannelInterceptor {
    private static final Pattern LOGICAL = Pattern.compile("^/topic/chat/([A-Za-z0-9_-]{1,100})(/read)?$");
    private static final Pattern WIRE = Pattern.compile("^/topic/chat\\.([A-Za-z0-9_-]{1,100})(\\.read)?$");
    private static final Pattern USER_LOGICAL = Pattern.compile("^/queue/((?:events|errors)-user[A-Za-z0-9_-]{1,100})$");
    private static final Pattern USER_WIRE = Pattern.compile("^/exchange/amq\\.direct/((?:events|errors)-user[A-Za-z0-9_-]{1,100})$");
    private final boolean fromBroker;
    public BrokerDestinationCodec(boolean fromBroker) { this.fromBroker = fromBroker; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        String destination = headers.getDestination();
        if (destination == null) return message;
        String translated;
        var personal = (fromBroker ? USER_WIRE : USER_LOGICAL).matcher(destination);
        var room = (fromBroker ? WIRE : LOGICAL).matcher(destination);
        if (personal.matches()) {
            // RabbitMQ /queue SEND redeclares the consumer's exclusive queue from
            // a different (system) connection. Publish through amq.direct instead;
            // SUBSCRIBE still gets its own exclusive auto-delete queue.
            translated = (fromBroker ? "/queue/" : "/exchange/amq.direct/") + personal.group(1);
            if (!fromBroker && headers.getCommand() == StompCommand.SUBSCRIBE) {
                headers.setNativeHeader("auto-delete", "true");
                headers.setNativeHeader("durable", "false");
                headers.setNativeHeader("exclusive", "true");
                for (String name : new String[]{"x-queue-name", "persistent", "x-dead-letter-exchange", "x-dead-letter-routing-key"}) {
                    headers.removeNativeHeader(name);
                }
            }
        } else if (room.matches()) {
            translated = fromBroker
                    ? "/topic/chat/" + room.group(1) + (room.group(2) == null ? "" : "/read")
                    : "/topic/chat." + room.group(1) + (room.group(2) == null ? "" : ".read");
        } else return message;
        if (!fromBroker && headers.getCommand() == null && headers.getMessageType() == SimpMessageType.MESSAGE) {
            // Server publications must remain SIMP here. The channel's framework
            // ImmutableMessageChannelInterceptor runs after this codec, regardless
            // of setLeaveMutable(true). The relay wraps a SIMP publication itself
            // and assigns its system session on a fresh mutable STOMP accessor.
            // Returning a STOMP accessor here makes it reuse already-frozen headers.
            SimpMessageHeaderAccessor publication = SimpMessageHeaderAccessor.wrap(message);
            publication.setDestination(translated);
            publication.setNativeHeader("destination", translated);
            return MessageBuilder.createMessage(message.getPayload(), publication.getMessageHeaders());
        }
        headers.setDestination(translated);
        headers.setNativeHeader("destination", translated);
        // Client frames retain STOMP command/session/subscription metadata.
        // The framework controls their final immutability boundary.
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
    }
}
