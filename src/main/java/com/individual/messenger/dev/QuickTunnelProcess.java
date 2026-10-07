package com.individual.messenger.dev;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Owns just one development tunnel. No shell, installation, or global process termination. */
public final class QuickTunnelProcess implements AutoCloseable {
    private static final Pattern ORIGIN = Pattern.compile(
            "(?<![\\w./-])https://[a-z0-9]+(?:-[a-z0-9]+)*\\.trycloudflare\\.com(?=[\\s|]|$)");
    private final Process process;
    private final Path runDirectory;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread shutdownHook;
    private String origin;
    private Thread outputThread;
    private Process guardian;

    private QuickTunnelProcess(Process process, Path runDirectory) {
        this.process = process;
        this.runDirectory = runDirectory;
        this.shutdownHook = new Thread(this::close, "messenger-tunnel-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (RuntimeException failure) {
            process.destroyForcibly();
            throw failure;
        }
    }

    public static QuickTunnelProcess start(String executable, int port, Path root, Duration timeout)
            throws IOException, InterruptedException {
        return startCommand(List.of(resolveExecutable(executable)), port, root, timeout);
    }

    // A command prefix allows offline tests to substitute a tiny Java process for cloudflared.
    static QuickTunnelProcess startCommand(List<String> executable, int port, Path root, Duration timeout)
            throws IOException, InterruptedException {
        if (port < 1 || port > 65535 || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("A fixed port (1..65535) and positive timeout are required.");
        }
        // Do not expose an unrelated application already using this port.
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        }
        Path parent = root.toAbsolutePath().normalize().resolve(".public");
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "idea-");
        Path config = directory.resolve("quick.yml");
        Files.writeString(config, "{}\n", StandardCharsets.US_ASCII);
        List<String> command = new ArrayList<>(executable);
        command.addAll(List.of("tunnel", "--config", config.toString(), "--no-autoupdate",
                "--protocol", "http2", "--url", "http://127.0.0.1:" + port));
        System.out.println("[Public tunnel] Local logs: " + directory.resolve("tunnel.log"));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        QuickTunnelProcess tunnel = new QuickTunnelProcess(process, directory);
        try { tunnel.startGuardian(); }
        catch(IOException | InterruptedException | RuntimeException failure) {tunnel.close();throw failure;}
        CompletableFuture<String> issued = new CompletableFuture<>();
        Thread reader = new Thread(() -> {
            try (var input = process.inputReader(StandardCharsets.UTF_8);
                 BufferedWriter log = Files.newBufferedWriter(directory.resolve("tunnel.log"), StandardCharsets.UTF_8)) {
                String line;
                while ((line = input.readLine()) != null) {
                    log.write(line);
                    log.newLine();
                    log.flush();
                    var match = ORIGIN.matcher(line);
                    if (match.find()) issued.complete(match.group());
                }
                issued.completeExceptionally(new IOException("cloudflared exited before issuing a URL."));
            } catch (IOException failure) {
                issued.completeExceptionally(failure);
                if (!tunnel.closed.get()) {
                    System.err.println("[Public tunnel] Log reader stopped; closing the tunnel. See " + directory);
                    tunnel.close();
                }
            }
        }, "messenger-tunnel-output");
        tunnel.outputThread = reader;
        reader.setDaemon(true);
        reader.start();
        try {
            tunnel.origin = issued.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!process.isAlive()) throw new IOException("cloudflared stopped. See " + directory);
            Files.writeString(directory.resolve("url.txt"), tunnel.origin + "\n", StandardCharsets.US_ASCII);
            process.onExit().thenRun(() -> {
                if (!tunnel.closed.get()) {
                    System.err.println("[Public tunnel] Tunnel exited; the public URL is no longer available. "
                            + "Restart Messenger to create a new URL. Logs: " + directory);
                    tunnel.close();
                } else {
                    tunnel.deleteUrl();
                }
            });
            return tunnel;
        } catch (ExecutionException | TimeoutException failure) {
            tunnel.close();
            throw new IOException("No Quick Tunnel URL was issued. See " + directory
                    + ". Check cloudflared and outbound HTTPS/TCP 7844 access.", failure);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            tunnel.close();
            throw failure;
        }
    }

    static String resolveExecutable(String configured) {
        if (configured == null || configured.isBlank()) throw new IllegalArgumentException("cloudflared executable is empty.");
        if (!configured.equals("cloudflared")) return configured; // Explicit path, passed as one argument.
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return configured;
        // IntelliJ may still have its old PATH after winget installation.
        for (String variable : List.of("LOCALAPPDATA", "ProgramFiles")) {
            String base = System.getenv(variable);
            if (base == null) continue;
            for (String relative : List.of("Microsoft/WinGet/Links/cloudflared.exe", "WinGet/Links/cloudflared.exe",
                    "cloudflared/cloudflared.exe", "Cloudflare/cloudflared.exe")) {
                Path candidate = Path.of(base).resolve(relative);
                if (Files.isRegularFile(candidate)) return candidate.toString();
            }
            for (String relative : List.of("Microsoft/WinGet/Packages", "WinGet/Packages")) {
                Path packages = Path.of(base).resolve(relative);
                if (!Files.isDirectory(packages)) continue;
                try (var directories = Files.newDirectoryStream(packages, "Cloudflare.cloudflared_*")) {
                    for (Path directory : directories) {
                        try (var files = Files.walk(directory, 3)) {
                            var found = files.filter(Files::isRegularFile)
                                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("cloudflared.exe")).findFirst();
                            if (found.isPresent()) return found.get().toString();
                        }
                    }
                } catch (IOException | SecurityException ignored) { /* Fall back to PATH. */ }
            }
        }
        return configured;
    }

    private void startGuardian() throws IOException,InterruptedException {
        String relative=TunnelGuardian.class.getName().replace('.', '/')+".class";
        Path classes=runDirectory.resolve("guardian"),target=classes.resolve(relative);
        Files.createDirectories(target.getParent());
        try(var input=TunnelGuardian.class.getResourceAsStream("/"+relative)) {
            if(input==null)throw new IOException("Tunnel guardian class is unavailable");
            Files.copy(input,target);
        }
        var parent=ProcessHandle.current();
        var parentStart=parent.info().startInstant().orElseThrow(()->new IOException("Parent start time unavailable"));
        var childStart=process.info().startInstant().orElseThrow(()->new IOException("Tunnel start time unavailable"));
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Path ready=runDirectory.resolve("guardian.ready");
        guardian=new ProcessBuilder(executable,"-cp",classes.toString(),TunnelGuardian.class.getName(),
                Long.toString(parent.pid()),parentStart.toString(),Long.toString(process.pid()),childStart.toString(),
                runDirectory.resolve("url.txt").toString(),ready.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(runDirectory.resolve("guardian.log").toFile()).start();
        long deadline=System.nanoTime()+5_000_000_000L;
        while(!Files.exists(ready) && guardian.isAlive() && System.nanoTime()<deadline)Thread.sleep(20);
        if(!Files.exists(ready))throw new IOException("Tunnel guardian could not start. See guardian.log");
    }

    public String origin() { return origin; }
    public boolean isAlive() { return process.isAlive(); }
    Path runDirectory() { return runDirectory; }

    private void deleteUrl() {
        try { Files.deleteIfExists(runDirectory.resolve("url.txt")); }
        catch (IOException ignored) { /* Only a local convenience file, not a source of authorization. */ }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
            }
            if (outputThread != null && Thread.currentThread() != outputThread) outputThread.join(1000);
            if(guardian!=null && !guardian.waitFor(2,TimeUnit.SECONDS))guardian.destroy();
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            deleteUrl();
            if (Thread.currentThread() != shutdownHook) {
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException ignored) { /* JVM shutdown already in progress. */ }
            }
        }
    }
}
