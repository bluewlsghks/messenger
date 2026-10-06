package com.individual.messenger;

import com.individual.messenger.dev.PublicTunnelListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MessengerApplication {
    public static void main(String[] args) {
        PublicTunnelListener tunnel = new PublicTunnelListener();
        SpringApplication application = new SpringApplication(MessengerApplication.class);
        application.addListeners(tunnel);
        try {
            application.run(args);
        } catch (RuntimeException | Error failure) {
            tunnel.close();
            throw failure;
        }
    }
}
