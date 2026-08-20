FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app

ENV SERVER_PORT=8080
COPY --from=build /workspace/target/library-system-0.0.1-SNAPSHOT.jar /app/library-system.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/library-system.jar"]
