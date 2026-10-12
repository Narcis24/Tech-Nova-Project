# Build from the repo root: docker build -f execution-engine/Dockerfile .

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# the reactor needs every module's pom, even the ones it is not building
COPY pom.xml .
COPY messaging/pom.xml messaging/
COPY app/pom.xml app/
COPY execution-engine/pom.xml execution-engine/
RUN mvn -B -pl execution-engine -am dependency:go-offline -DexcludeGroupIds=com.neueda

COPY messaging/src messaging/src
COPY execution-engine/src execution-engine/src

RUN mvn -B -q -pl execution-engine -am -Dmaven.test.skip=true package


# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S engine && adduser -S engine -G engine

WORKDIR /opt/execution-engine

COPY --from=build /build/execution-engine/target/execution-engine.jar app.jar

USER engine

ENV SERVER_PORT=8082 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]