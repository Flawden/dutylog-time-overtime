# Многоступенчатая сборка: сначала собираем jar, потом запускаем его в лёгком JRE-образе.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
COPY --from=build /app/target/shift-calendar-*.jar /app/shift-calendar.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/shift-calendar.jar"]
