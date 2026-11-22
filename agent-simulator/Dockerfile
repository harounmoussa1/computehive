# Faux agents pour la démo M/P (profil "demo" de docker-compose.yml) : ils se connectent à app-scheduling comme
# de vrais PC du laboratoire. Le jar est construit avant l'image : ./mvnw package -DskipTests.
FROM eclipse-temurin:17-jre

RUN useradd --system --uid 1001 --no-create-home computehive
WORKDIR /app
COPY target/agent-simulator.jar agent-simulator.jar
USER computehive

ENTRYPOINT ["java", "-jar", "/app/agent-simulator.jar"]
