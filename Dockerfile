FROM maven:3.9.14-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY backend/pom.xml .
COPY backend/src ./src
RUN mvn -B -ntp clean package

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S stockmentor \
    && adduser -S stockmentor -G stockmentor

WORKDIR /app
COPY --from=build /workspace/target/stockmentor-0.0.1-SNAPSHOT.jar app.jar

USER stockmentor
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
