plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.5"
    java
}
group = "com.individual"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21
repositories { mavenCentral() }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.projectreactor.netty:reactor-netty")
    implementation("nl.martijndwars:web-push:5.1.2") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "org.asynchttpclient", module = "async-http-client")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bitbucket.b_c:jose4j:0.9.7")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework:spring-messaging")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    // Preserve REST/STOMP wire compatibility while moving the platform to Boot 4.
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("com.openai:openai-java:4.16.1")
    // Browser libraries are served locally from the jar; the workspace needs no CDN or npm build.
    runtimeOnly("org.webjars.npm:stomp__stompjs:7.3.0") { isTransitive = false }
    runtimeOnly("org.webjars.npm:sockjs-client:1.6.1") { isTransitive = false }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
}
tasks.withType<Test> { useJUnitPlatform() }

// Audit the resolved runtime graph rather than only the versions written in this build file.
tasks.register("dependencyCoordinates") {
    val output = layout.buildDirectory.file("security/dependencies.json")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val coordinates = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }.distinctBy { "${it.group}:${it.name}:${it.version}" }
            .sortedBy { "${it.group}:${it.name}" }
            .map { "{\"ecosystem\":\"Maven\",\"name\":\"${it.group}:${it.name}\",\"version\":\"${it.version}\"}" }
        output.get().asFile.apply { parentFile.mkdirs(); writeText(coordinates.joinToString(",\n", "[\n", "\n]")) }
    }
}
