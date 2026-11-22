# ComputeHive M/P — app-main : processus principal (jobs, machines, quotas, API des utilisateurs).
# Le jar est construit avant l'image : ./mvnw package -DskipTests, puis docker compose up --build.
FROM eclipse-temurin:17-jre

RUN useradd --system --uid 1001 --no-create-home computehive
WORKDIR /app
COPY target/app-main.jar app.jar
USER computehive

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=json
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
