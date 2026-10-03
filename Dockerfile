# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Use the project's Maven Wrapper (currently Maven 3.9.16) and cache downloads.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S stock && adduser -S -G stock stock
WORKDIR /app
COPY --from=build --chown=stock:stock /workspace/target/stock-0.0.1-SNAPSHOT.jar app.jar
USER stock
EXPOSE 8080
# Alpine provides wget; this endpoint does not call external financial APIs.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/hello || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
