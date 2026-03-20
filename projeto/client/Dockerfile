# Estágio de build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn -q package -DskipTests

# Estágio de execução
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/bot-client-1.0-SNAPSHOT.jar app.jar

ENV SERVER_ADDR=tcp://server:5555
ENV BOT_NAME=bot1
ENV CHANNEL_NAME=geral

CMD ["java", "-jar", "app.jar"]