#!/usr/bin/env python3
"""Derive le realm de production depuis infra/keycloak/tabibi-realm.json (source unique, versionnee) :
- supprime les comptes de demonstration (« users ») : ils ne doivent JAMAIS exister en production ;
- desactive le flux « direct access grants » (mot de passe envoye directement a Keycloak, reglage de dev)
  sur tous les clients ;
- remplace le domaine d'exemple tabibi.example par le domaine reel et retire les adresses localhost
  (redirectUris et webOrigins du front web).

Usage : infra/keycloak/realm-production.py [domaine]
  (sans argument, DOMAINE est lu dans .env a la racine du depot)
Sortie : infra/keycloak/production/tabibi-realm.json, monte par docker-compose.prod.yml dans Keycloak.
"""
import json
import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent.parent
SOURCE = RACINE / "infra" / "keycloak" / "tabibi-realm.json"
DESTINATION = RACINE / "infra" / "keycloak" / "production" / "tabibi-realm.json"
DOMAINE_EXEMPLE = "tabibi.example"


def domaine_depuis_env() -> str:
    env = RACINE / ".env"
    if env.exists():
        for ligne in env.read_text(encoding="utf-8").splitlines():
            if ligne.startswith("DOMAINE="):
                return ligne.split("=", 1)[1].strip().strip('"').strip("'")
    return ""


def remplacer_domaine(valeur, domaine):
    if isinstance(valeur, str):
        return valeur.replace(DOMAINE_EXEMPLE, domaine)
    if isinstance(valeur, list):
        return [remplacer_domaine(v, domaine) for v in valeur]
    if isinstance(valeur, dict):
        return {k: remplacer_domaine(v, domaine) for k, v in valeur.items()}
    return valeur


def main() -> int:
    domaine = sys.argv[1] if len(sys.argv) > 1 else domaine_depuis_env()
    if not domaine or domaine == DOMAINE_EXEMPLE:
        print("Domaine reel requis : realm-production.py <domaine> (ou DOMAINE dans .env)", file=sys.stderr)
        return 1
    realm = json.loads(SOURCE.read_text(encoding="utf-8"))
    comptes = [u.get("username") for u in realm.pop("users", [])]
    for client in realm.get("clients", []):
        client["directAccessGrantsEnabled"] = False
        for champ in ("redirectUris", "webOrigins"):
            client[champ] = [u for u in client.get(champ, []) if "localhost" not in u]
    realm = remplacer_domaine(realm, domaine)
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    DESTINATION.write_text(json.dumps(realm, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Realm de production ecrit : {DESTINATION}")
    print(f"  comptes de demonstration retires : {', '.join(comptes) or 'aucun'}")
    print(f"  direct access grants desactives sur : {', '.join(c['clientId'] for c in realm['clients'])}")
    print(f"  domaine : {domaine}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
