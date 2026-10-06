package com.individual.messenger.dev;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Offline subprocess regression checks. Never contacts Cloudflare or launches the real app. */
public final class QuickTunnelSmoke {
    private static int passed;
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--fake")) { fake(args); return; }
        Path root = Files.createTempDirectory("messenger tunnel smoke ");
        try {
            try (QuickTunnelProcess tunnel = start(root.resolve("success"), "good", Duration.ofSeconds(5))) {
                check(tunnel.origin().equals("https://test-dev-123.trycloudflare.com"), "extract exact HTTPS origin");
                check(tunnel.isAlive(), "owned process runs");
                check(Files.readString(tunnel.runDirectory().resolve("quick.yml")).equals("{}\n"), "isolated config");
                check(Files.readString(tunnel.runDirectory().resolve("url.txt")).trim().equals(tunnel.origin()), "URL file");
                Path url = tunnel.runDirectory().resolve("url.txt");
                tunnel.close();
                check(!tunnel.isAlive(), "close stops owned process");
                check(!Files.exists(url), "close removes stale URL");
                tunnel.close();
                check(!tunnel.isAlive(), "close is idempotent");
            }
            expectFailure(root.resolve("timeout"), "silent", Duration.ofMillis(700), "timeout cleans child");
            expectFailure(root.resolve("exit"), "exit", Duration.ofSeconds(5), "early exit fails cleanly");
            expectFailure(root.resolve("spoof"), "spoof", Duration.ofMillis(700), "reject deceptive hostname");
            expectFailure(root.resolve("http"), "http", Duration.ofMillis(700), "reject non-HTTPS origin");
            try (ServerSocket occupied = new ServerSocket(0)) {
                try {
                    QuickTunnelProcess.startCommand(command("good"), occupied.getLocalPort(), root.resolve("busy"), Duration.ofSeconds(1));
                    throw new AssertionError("occupied port accepted");
                } catch (IOException expected) { check(true, "occupied port is not exposed"); }
            }
            try {
                QuickTunnelProcess.startCommand(command("good"), 0, root, Duration.ofSeconds(1));
                throw new AssertionError("random port accepted");
            } catch (IllegalArgumentException expected) { check(true, "random port rejected"); }
            try {
                QuickTunnelProcess.start(root.resolve("missing-cloudflared").toString(), freePort(), root.resolve("missing"), Duration.ofSeconds(1));
                throw new AssertionError("missing binary accepted");
            } catch (IOException expected) { check(true, "missing binary fails clearly"); }
            try (QuickTunnelProcess tunnel = start(root.resolve("late-exit"), "late-exit", Duration.ofSeconds(5))) {
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while ((tunnel.isAlive() || Files.exists(tunnel.runDirectory().resolve("url.txt"))) && System.nanoTime() < deadline) Thread.sleep(20);
                check(!tunnel.isAlive() && !Files.exists(tunnel.runDirectory().resolve("url.txt")), "runtime exit removes stale URL");
            }
            Path interruptedRoot = root.resolve("interrupt");
            AtomicReference<Throwable> result = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try (var ignored = start(interruptedRoot, "silent", Duration.ofSeconds(10))) {
                    result.set(new AssertionError("interrupted startup completed"));
                } catch (Throwable failure) { result.set(failure); }
            });
            worker.start();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!hasPid(interruptedRoot) && System.nanoTime() < deadline) Thread.sleep(20);
            worker.interrupt();
            worker.join(6000);
            check(!worker.isAlive() && result.get() instanceof InterruptedException, "startup interruption propagated");
            check(childrenStopped(interruptedRoot), "interrupted child stopped");
            System.out.println("PASS: " + passed + " offline public-tunnel checks (no network).");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    private static List<String> command(String mode) {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        return List.of(executable, "-cp", System.getProperty("java.class.path"), QuickTunnelSmoke.class.getName(), "--fake", mode);
    }
    private static QuickTunnelProcess start(Path root, String mode, Duration timeout) throws Exception {
        return QuickTunnelProcess.startCommand(command(mode), freePort(), root, timeout);
    }
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
    private static void expectFailure(Path root, String mode, Duration timeout, String name) throws Exception {
        try (var ignored = start(root, mode, timeout)) { throw new AssertionError(name); }
        catch (IOException expected) { check(childrenStopped(root), name); }
    }
    private static boolean hasPid(Path root) throws IOException {
        if (!Files.exists(root)) return false;
        try (var files = Files.walk(root)) { return files.anyMatch(p -> p.getFileName().toString().equals("fake.pid")); }
    }
    private static boolean childrenStopped(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(p -> p.getFileName().toString().equals("fake.pid")).toList()) {
                long pid = Long.parseLong(Files.readString(path));
                if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) return false;
            }
        }
        return true;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
        System.out.println("PASS " + message);
    }
    private static void fake(String[] args) throws Exception {
        List<String> values = List.of(args);
        Path config = Path.of(values.get(values.indexOf("--config") + 1));
        Files.writeString(config.getParent().resolve("fake.pid"), Long.toString(ProcessHandle.current().pid()));
        if (!Files.readString(config).equals("{}\n") || !values.contains("--no-autoupdate") || !values.contains("http2")
                || !values.get(values.indexOf("--url") + 1).startsWith("http://127.0.0.1:")) System.exit(4);
        switch (args[1]) {
            case "exit" -> System.exit(3);
            case "spoof" -> System.out.println("https://good.trycloudflare.com.evil.example");
            case "http" -> System.out.println("http://good.trycloudflare.com");
            case "silent" -> { }
            default -> System.err.println("| https://test-dev-123.trycloudflare.com |");
        }
        Thread.sleep(args[1].equals("late-exit") ? 600 : 60_000);
    }
}
