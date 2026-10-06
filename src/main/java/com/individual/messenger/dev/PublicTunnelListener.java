package com.individual.messenger.dev;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Opt-in launcher listener; runs after Boot has loaded application.yml and environment variables. */
public final class PublicTunnelListener implements ApplicationListener<ApplicationEvent>, Ordered, AutoCloseable {
    @FunctionalInterface
    interface TunnelFactory {
        QuickTunnelProcess start(String executable, int port, Path root, Duration timeout)
                throws IOException, InterruptedException;
    }
    private final TunnelFactory factory;
    private QuickTunnelProcess tunnel;

    public PublicTunnelListener() { this(QuickTunnelProcess::start); }
    PublicTunnelListener(TunnelFactory factory) { this.factory = factory; }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
            start(prepared.getEnvironment());
        } else if (event instanceof ApplicationReadyEvent ready && tunnel != null) {
            if (!tunnel.isAlive()) {
                System.err.println("[Public tunnel] Messenger started locally, but the tunnel has stopped. Restart to retry.");
                return;
            }
            String context = ready.getApplicationContext().getEnvironment()
                    .getProperty("server.servlet.context-path", "");
            if (context.equals("/")) context = "";
            System.out.println("\n[Public tunnel] MESSENGER STARTED — public login URL: " + tunnel.origin() + context + "/login");
            System.out.println("[Public tunnel] Remote access has not been verified. Stop Messenger to close this tunnel.\n");
        } else if (event instanceof ContextClosedEvent || event instanceof ApplicationFailedEvent) {
            close();
        }
    }

    private void start(ConfigurableEnvironment environment) {
        if (tunnel != null || !environment.getProperty("app.public-tunnel.enabled", Boolean.class, false)) return;
        int port = environment.getProperty("server.port", Integer.class, 8080);
        int seconds = environment.getProperty("app.public-tunnel.timeout-seconds", Integer.class, 90);
        if (port < 1 || port > 65535 || seconds < 1 || seconds > 300) {
            throw new IllegalArgumentException("Public tunnel requires server.port 1..65535 and timeout-seconds 1..300.");
        }
        if (environment.getProperty("server.ssl.enabled", Boolean.class, false)) {
            throw new IllegalArgumentException("Public tunnel expects local HTTP. Disable server.ssl.enabled for this development run.");
        }
        if ("none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", ""))) {
            throw new IllegalArgumentException("Public tunnel requires the Messenger web server.");
        }
        System.out.println("[Public tunnel] DEVELOPMENT PREVIEW: anyone with the URL can reach login/registration. "
                + "Use test accounts and a development database. HTTP traffic passes through Cloudflare.");
        try {
            tunnel = factory.start(environment.getProperty("app.public-tunnel.executable", "cloudflared"),
                    port, Path.of(System.getProperty("user.dir")), Duration.ofSeconds(seconds));
            // Replace stale manual origins BEFORE Security/WebSocket beans are constructed.
            environment.getPropertySources().addFirst(new MapPropertySource("messengerPublicTunnel", Map.of(
                    "app.allowed-origins", tunnel.origin() + ",http://localhost:" + port + ",http://127.0.0.1:" + port,
                    "server.address", "127.0.0.1",
                    "server.forward-headers-strategy", "none",
                    "app.openai.enabled", "false")));
            System.out.println("[Public tunnel] URL ISSUED (app is still starting): " + tunnel.origin());
        } catch (InterruptedException interrupted) {
            close();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Public tunnel startup interrupted.", interrupted);
        } catch (IOException | RuntimeException failure) {
            close();
            throw new IllegalStateException("Public tunnel could not start. Check cloudflared installation/PATH, "
                    + "APP_PUBLIC_TUNNEL_EXECUTABLE, free server port and .public logs. "
                    + "Set APP_PUBLIC_TUNNEL_ENABLED=false to run locally.", failure);
        }
    }

    @Override
    public void close() {
        if (tunnel != null) tunnel.close();
    }
}
