package com.individual.messenger.dev;

import java.nio.file.*;
import java.time.Instant;

/** Standalone child watchdog; deliberately depends only on the JRE, including from a Boot fat JAR. */
public final class TunnelGuardian {
    private TunnelGuardian() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=6)return;
        long parentId=Long.parseLong(args[0]), childId=Long.parseLong(args[2]);
        Instant parentStart=Instant.parse(args[1]), childStart=Instant.parse(args[3]);
        Path url=Path.of(args[4]).toAbsolutePath().normalize(),ready=Path.of(args[5]).toAbsolutePath().normalize();
        if(!url.getFileName().toString().equals("url.txt") || !url.getParent().equals(ready.getParent()))return;
        Files.writeString(ready,"ready");
        try {
            while(same(childId,childStart)) {
                if(!same(parentId,parentStart)) {
                    ProcessHandle.of(childId).filter(process->same(childId,childStart)).ifPresent(ProcessHandle::destroy);
                    long deadline=System.nanoTime()+2_000_000_000L;
                    while(same(childId,childStart) && System.nanoTime()<deadline)Thread.sleep(50);
                    ProcessHandle.of(childId).filter(process->same(childId,childStart)).ifPresent(ProcessHandle::destroyForcibly);
                    Files.deleteIfExists(url);
                    return;
                }
                Thread.sleep(500);
            }
            Files.deleteIfExists(url);
        } finally {Files.deleteIfExists(ready);}
    }
    static boolean same(long pid,Instant started) {
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                .flatMap(process->process.info().startInstant()).filter(started::equals).isPresent();
    }
}
