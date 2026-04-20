FROM gradle:8.14.3-jdk21-jammy AS builder
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY src src

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]