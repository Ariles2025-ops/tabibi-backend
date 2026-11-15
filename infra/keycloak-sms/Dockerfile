# syntax=docker/dockerfile:1.7
# Keycloak de Tabibi : extension de connexion par numero et code SMS (reprise de Sel3a) et theme de connexion Tabibi.
# Build depuis ce dossier : docker build infra/keycloak-sms
ARG KEYCLOAK_VERSION=26.0

FROM maven:3.9-eclipse-temurin-21 AS extension
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=extension /src/target/tabibi-keycloak.jar /opt/keycloak/providers/tabibi-keycloak.jar
COPY theme/tabibi /opt/keycloak/themes/tabibi
ENV TZ=Africa/Algiers
