# Build from the repo root: docker build -f app/Dockerfile .
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
# the reactor needs every module's pom, even the ones it is not building
COPY pom.xml .
COPY messaging/pom.xml messaging/
COPY app/pom.xml app/
COPY execution-engine/pom.xml execution-engine/
RUN mvn -B -pl app -am dependency:go-offline -DexcludeGroupIds=com.neueda
COPY messaging/src messaging/src
COPY app/src app/src
RUN mvn -B -pl app -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/app/target/tech-nova.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
