package com.individual.messenger.dev;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicTunnelListenerTest {
    private final PublicTunnelListener.TunnelFactory factory = mock(PublicTunnelListener.TunnelFactory.class);
    private final QuickTunnelProcess process = mock(QuickTunnelProcess.class);
    private final PublicTunnelListener listener = new PublicTunnelListener(factory);

    private StandardEnvironment environment(Map<String, Object> values) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("test", values));
        return env;
    }
    private void prepare(StandardEnvironment env) {
        listener.onApplicationEvent(new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(),
                new SpringApplication(Object.class), new String[0], env));
    }
    private void fakeTunnel() throws Exception {
        when(process.origin()).thenReturn("https://fresh-example.trycloudflare.com");
        when(factory.start(anyString(), anyInt(), any(), any())).thenReturn(process);
    }

    @Test void disabledByDefault() {
        StandardEnvironment env = environment(Map.of("app.allowed-origins", "http://localhost:8080"));
        prepare(env);
        verifyNoInteractions(factory);
        assertNull(env.getPropertySources().get("messengerPublicTunnel"));
        assertEquals("http://localhost:8080", env.getProperty("app.allowed-origins"));
    }
    @Test void explicitFalseDoesNotLaunch() {
        prepare(environment(Map.of("app.public-tunnel.enabled", "false")));
        verifyNoInteractions(factory);
    }
    @Test void configuresExactOriginsBeforeBeansAndKeepsSecrets() throws Exception {
        fakeTunnel();
        StandardEnvironment env = environment(Map.of("app.public-tunnel.enabled", "true", "server.port", "8081",
                "app.allowed-origins", "https://stale.trycloudflare.com", "app.openai.enabled", "true",
                "MONGODB_URI", "unchanged-test-value"));
        prepare(env);
        assertEquals("https://fresh-example.trycloudflare.com,http://localhost:8081,http://127.0.0.1:8081", env.getProperty("app.allowed-origins"));
        assertEquals("127.0.0.1", env.getProperty("server.address"));
        assertEquals("none", env.getProperty("server.forward-headers-strategy"));
        assertEquals("false", env.getProperty("app.openai.enabled"));
        assertEquals("unchanged-test-value", env.getProperty("MONGODB_URI"));
        verify(factory).start(eq("cloudflared"), eq(8081), any(), eq(java.time.Duration.ofSeconds(90)));
        listener.close();
        verify(process).close();
    }
    @Test void intellijUppercaseEnvironmentVariablesAreRecognized() throws Exception {
        fakeTunnel();
        StandardEnvironment env = environment(Map.of());
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env", Map.of(
                "APP_PUBLIC_TUNNEL_ENABLED", "true", "SERVER_PORT", "8081",
                "APP_PUBLIC_TUNNEL_EXECUTABLE", "C:/some path/cloudflared.exe")));
        prepare(env);
        verify(factory).start(eq("C:/some path/cloudflared.exe"), eq(8081), any(), any());
        listener.close();
    }
    @Test void rejectsRandomPortBeforeStartingTunnel() {
        assertThrows(IllegalArgumentException.class, () -> prepare(environment(Map.of(
                "app.public-tunnel.enabled", "true", "server.port", "0"))));
        verifyNoInteractions(factory);
    }
    @Test void rejectsIncompatibleHttpsOrigin() {
        assertThrows(IllegalArgumentException.class, () -> prepare(environment(Map.of(
                "app.public-tunnel.enabled", "true", "server.ssl.enabled", "true"))));
        verifyNoInteractions(factory);
    }
    @Test void rejectsUnboundedTimeout() {
        assertThrows(IllegalArgumentException.class, () -> prepare(environment(Map.of(
                "app.public-tunnel.enabled", "true", "app.public-tunnel.timeout-seconds", "301"))));
        verifyNoInteractions(factory);
    }
    @Test void startupFailureDoesNotPublishOrigins() throws Exception {
        when(factory.start(anyString(), anyInt(), any(), any())).thenThrow(new IOException("missing executable"));
        StandardEnvironment env = environment(Map.of("app.public-tunnel.enabled", "true"));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> prepare(env));
        assertTrue(failure.getMessage().contains("APP_PUBLIC_TUNNEL_ENABLED=false"));
        assertNull(env.getPropertySources().get("messengerPublicTunnel"));
    }
    @Test void contextCloseStopsOnlyOurTunnel() throws Exception {
        fakeTunnel();
        prepare(environment(Map.of("app.public-tunnel.enabled", "true")));
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            listener.onApplicationEvent(new ContextClosedEvent(context));
        }
        verify(process).close();
    }
    @Test void duplicatePreparationDoesNotLaunchTwice() throws Exception {
        fakeTunnel();
        StandardEnvironment env = environment(Map.of("app.public-tunnel.enabled", "true"));
        prepare(env);
        prepare(env);
        verify(factory, times(1)).start(anyString(), anyInt(), any(), any());
        listener.close();
    }
}
