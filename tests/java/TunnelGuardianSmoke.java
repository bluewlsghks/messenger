package com.individual.messenger.dev;
import java.nio.file.*;
import java.time.*;
import java.net.ServerSocket;
import java.util.*;

/** Tests forced parent death with fake Java processes, never real Cloudflare or another user's process. */
public final class TunnelGuardianSmoke {
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("child")) {
            List<String> values=List.of(args);Path config=Path.of(values.get(values.indexOf("--config")+1));
            Files.writeString(config.getParent().resolve("child.pid"),Long.toString(ProcessHandle.current().pid()));
            System.out.println("| https://guardian-test.trycloudflare.com |");Thread.sleep(60000);return;
        }
        if(args.length>0 && args[0].equals("parent")) {
            int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
            try(var tunnel=QuickTunnelProcess.startCommand(command("child"),port,Path.of(args[1]),Duration.ofSeconds(5))) {
                Files.writeString(Path.of(args[1],"parent.ready"),tunnel.runDirectory().toString());Thread.sleep(60000);
            }return;
        }
        Path root=Files.createTempDirectory("messenger guardian ");
        Process parent=new ProcessBuilder(command("parent",root.toString())).redirectErrorStream(true)
                .redirectOutput(root.resolve("parent.log").toFile()).start();
        try {
            long deadline=System.nanoTime()+10_000_000_000L;
            while(!Files.exists(root.resolve("parent.ready")) && parent.isAlive() && System.nanoTime()<deadline)Thread.sleep(50);
            if(!Files.exists(root.resolve("parent.ready")))throw new AssertionError(Files.readString(root.resolve("parent.log")));
            Path directory=Path.of(Files.readString(root.resolve("parent.ready")));
            long child=Long.parseLong(Files.readString(directory.resolve("child.pid")));
            parent.destroyForcibly();parent.waitFor();
            deadline=System.nanoTime()+8_000_000_000L;
            while(Files.exists(directory.resolve("url.txt")) && System.nanoTime()<deadline)Thread.sleep(50);
            if(Files.exists(directory.resolve("url.txt")))throw new AssertionError("Guardian did not invalidate URL after parent kill");
            // Linux containers may retain an already-dead orphan as a zombie until init reaps it.
            boolean zombie=false;Path stat=Path.of("/proc",Long.toString(child),"stat");
            if(Files.exists(stat)){String text=Files.readString(stat);zombie=text.substring(text.lastIndexOf(')')+2).startsWith("Z");}
            if(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false) && !zombie)throw new AssertionError("Owned tunnel remains running");
            System.out.println("PASS: forced parent termination stops its owned child and invalidates only its URL");
        }finally{if(parent.isAlive())parent.destroyForcibly();}
    }
    private static List<String> command(String... args) {
        List<String> command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",
                System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-cp",System.getProperty("java.class.path"),TunnelGuardianSmoke.class.getName()));
        command.addAll(List.of(args));return command;
    }
}
