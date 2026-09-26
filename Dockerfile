# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ENV PORT=8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
COPY --from=build /app/target/url-shortener.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/health" || exit 1
RUN apt-get update && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
