# Build without DB credentials, API tokens, or files from the developer's PC.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN bash ./gradlew bootJar --no-daemon --max-workers=2 \
    '-Dorg.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=256m' \
    && mkdir /out \
    && bash -c 'set -e; jars=(); for f in build/libs/*.jar; do [[ "$f" == *-plain.jar ]] || jars+=("$f"); done; [[ ${#jars[@]} -eq 1 ]]; cp "${jars[0]}" /out/messenger.jar'

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 10001 messenger \
    && useradd --system --uid 10001 --gid messenger --no-create-home messenger
WORKDIR /app
COPY --from=build /out/messenger.jar /app/messenger.jar
COPY scripts/start-render.sh /app/start-render.sh
RUN chmod 0555 /app/start-render.sh && chmod 0444 /app/messenger.jar
# Free instance: 512 MB TOTAL RAM, not 512 MB Java heap. Leave room for native memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=10.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["sh", "/app/start-render.sh"]
