# Image de production de l'API Tabibi.
# Etape 1 : construction du jar avec Maven (les tests tournent en CI, pas ici).
# Etape 2 : execution sur un JRE minimal, avec un utilisateur sans privilege.
# Construire : docker build -t tabibi-backend .   Lancer : voir docker-compose.prod.yml

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Les dependances d'abord : cette couche est reutilisee tant que le pom.xml ne change pas.
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:24-jre-alpine
RUN addgroup -S tabibi && adduser -S tabibi -G tabibi
WORKDIR /app
COPY --from=build --chown=tabibi:tabibi /build/target/*.jar /app/app.jar
USER tabibi
EXPOSE 8080
# La JVM se dimensionne sur la memoire du conteneur.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
# Sante de l'API (endpoint public, sans detail) ; wget vient de busybox (image alpine).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
