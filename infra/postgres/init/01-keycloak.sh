#!/bin/sh
# Premiere initialisation du volume PostgreSQL (docker-compose.prod.yml) : role et base dedies a
# Keycloak, separes des donnees de patients (base tabibi). Execute une seule fois par l'image
# postgres, a la creation du volume ; KEYCLOAK_DB_PASSWORD vient de l'environnement du conteneur.
set -e
psql -v ON_ERROR_STOP=1 -v mdp="$KEYCLOAK_DB_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE keycloak LOGIN PASSWORD :'mdp';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL
