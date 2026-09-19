#!/usr/bin/env bash
# Sauvegarde quotidienne des bases Tabibi (tabibi : donnees de patients ; keycloak : identites) depuis
# le conteneur postgres de docker-compose.prod.yml : un fichier horodate par base (format custom de
# pg_dump, compresse), puis rotation : les fichiers de plus de RETENTION_JOURS jours (14) sont supprimes.
#
# Usage : infra/sauvegarde/pg_dump.sh [repertoire]      (defaut : /var/backups/tabibi)
# Cron, tous les jours a 3 h (le depot est suppose dans /opt/tabibi/tabibi-backend) :
#   0 3 * * * /opt/tabibi/tabibi-backend/infra/sauvegarde/pg_dump.sh >> /var/log/tabibi-sauvegarde.log 2>&1
# Restauration d'une base (ici tabibi) :
#   docker compose -f docker-compose.prod.yml exec -T postgres pg_restore -U tabibi -d tabibi --clean --if-exists < tabibi-<horodatage>.dump
# Les sauvegardes contiennent des donnees de sante : repertoire reserve a root (chmod 700), copie
# chiffree hors du serveur.
set -euo pipefail

REPERTOIRE="${1:-${SAUVEGARDES_DIR:-/var/backups/tabibi}}"
RETENTION_JOURS="${RETENTION_JOURS:-14}"
PROJET="$(cd "$(dirname "$0")/../.." && pwd)"
HORODATAGE="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$REPERTOIRE"
chmod 700 "$REPERTOIRE"

for base in tabibi keycloak; do
  fichier="$REPERTOIRE/$base-$HORODATAGE.dump"
  docker compose -f "$PROJET/docker-compose.prod.yml" exec -T postgres \
    pg_dump -U tabibi -d "$base" --format=custom --compress=6 > "$fichier"
  echo "$(date -Is) sauvegarde de $base : $fichier ($(du -h "$fichier" | cut -f1))"
done

find "$REPERTOIRE" -name '*.dump' -type f -mtime "+$RETENTION_JOURS" -print -delete | sed 's/^/supprime : /'
