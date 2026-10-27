FROM eclipse-temurin:21.0.12.1_1-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --console=plain

COPY src src
RUN ./gradlew bootJar --no-daemon --console=plain -x test

FROM eclipse-temurin:21.0.12.1_1-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar

USER app

EXPOSE 8085

ENV SERVER_PORT=8085
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O - "http://127.0.0.1:${SERVER_PORT}/health" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
