package com.individual.messenger.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class LayerStructureTest {
    private static final Path ROOT = Path.of("src/main/java/com/individual/messenger");
    private void rejectsImports(String directory, String... dependencies) throws Exception {
        try (var files = Files.walk(ROOT.resolve(directory))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String dependency : dependencies)
                    assertFalse(source.contains("import " + dependency), path + " -> " + dependency);
            }
        }
    }
    @Test void servicesAndDtosDoNotDependOnControllers() throws Exception {
        rejectsImports("service", "com.individual.messenger.controller.");
        rejectsImports("dto", "com.individual.messenger.controller.", "com.individual.messenger.service.", "com.individual.messenger.repository.");
    }
    @Test void controllersDoNotAccessPersistenceDirectly() throws Exception {
        rejectsImports("controller", "org.springframework.data.mongodb.", "com.individual.messenger.repository.");
    }
    @Test void repositoriesDoNotDependOnApplicationOrHttpLayers() throws Exception {
        rejectsImports("repository", "com.individual.messenger.service.", "com.individual.messenger.controller.");
    }
    @Test void requiredEntrypointsAndDomainIdentitiesRemain() {
        for (String name : new String[]{"User", "Message", "Room", "ReadCursor"})
            assertTrue(Files.exists(ROOT.resolve("domain/" + name + ".java")));
        for (String path : new String[]{"src/main/resources/application.yml", "src/main/resources/templates/workspace.html", "scripts/Start-Public.ps1", "gradle/wrapper/gradle-wrapper.jar"})
            assertTrue(Files.exists(Path.of(path)), path);
    }
}
