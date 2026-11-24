# syntax=docker/dockerfile:1
# Multi-stage image for SiteLog (used by Render and by docker-compose.yml).
# Stage 1 builds the jar with the Maven wrapper on a JDK; stage 2 runs it on a JRE only.
# Tests are not run here: CI (./mvnw verify with Testcontainers) runs them on every push.

# ---------- build ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Wrapper and pom first: the dependency layer is rebuilt only when pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Package, then split the jar into layers (libraries change less often than application classes).
RUN ./mvnw -B -q -DskipTests package \
    && cp target/sitelog-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- runtime ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Unprivileged user: the process cannot modify the image or the JRE.
RUN addgroup -S sitelog && adduser -S -G sitelog sitelog

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER sitelog

# Render injects PORT; the app listens on ${PORT:8080} (server.port in application.yml).
ENV PORT=8080
# Render free plan: 512 MB and 0.1 CPU. Heap sized from the container limit; C1-only JIT and
# SerialGC cut startup at 0.1 CPU from ~350 s to ~170 s (measured with docker run --cpus=0.1
# --memory=512m) at the cost of peak throughput, which this low-traffic app does not need.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC"
EXPOSE 8080

# Used by docker compose; Render uses its own health check on the same path.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -q -O /dev/null "http://localhost:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
