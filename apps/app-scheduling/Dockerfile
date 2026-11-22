# ComputeHive M/P — app-scheduling : processus extrait (scheduling, porte gRPC des agents, checkpoints).
# Le jar est construit avant l'image : ./mvnw package -DskipTests, puis docker compose up --build.
FROM eclipse-temurin:17-jre

RUN useradd --system --uid 1001 --no-create-home computehive
WORKDIR /app
COPY target/app-scheduling.jar app.jar
USER computehive

EXPOSE 8081 9090
ENV SPRING_PROFILES_ACTIVE=json
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
