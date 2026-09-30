# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B -q -Dmaven.test.skip=true package


# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S engine && adduser -S engine -G engine

WORKDIR /opt/execution-engine

COPY --from=build /build/target/execution-engine.jar app.jar

USER engine

ENV SERVER_PORT=8082 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]