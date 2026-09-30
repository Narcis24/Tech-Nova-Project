# Execution engine image: builds the Spring Boot jar with Maven, then runs
# it on a slim JRE. Two stages keep the JDK, Maven and the build cache out of the
# final image.

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they're cached across source-only changes.
COPY pom.xml .
# Not quiet on purpose: on a first build this step downloads for several
# minutes, and the download log shows it's making progress.
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S engine && adduser -S engine -G engine
WORKDIR /opt/execution-engine
COPY --from=build /build/target/execution-engine.jar app.jar
USER engine

# Kafka comes from KAFKA_BOOTSTRAP_SERVERS and the simulated market's
# timing from ENGINE_* (see src/main/resources/application.yml);
# docker-compose.yml points it at the kafka service.
ENV SERVER_PORT=8082 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
