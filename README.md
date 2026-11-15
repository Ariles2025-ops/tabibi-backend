# tabibi-backend

[![ci](https://github.com/<org>/tabibi-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/<org>/tabibi-backend/actions/workflows/ci.yml)
(remplacer `<org>` par l'organisation GitHub qui heberge le depot)

API de la plateforme Tabibi (prise de rendez-vous medicaux en Algerie). Architecture hexagonale,
Spring Boot 3.4 / Java 21, securite JWT / Keycloak. Les donnees manipulees sont des donnees de
patients : la securite prime (autorisation par role cote serveur, regles de proprietaire dans les
cas d'usage, aucune donnee personnelle dans les reponses publiques ni dans les journaux).

## Securite

- Chaque requete porte un **JWT** signe par **Keycloak** (realm `tabibi`) ; l'autorisation se fait par
  role (`PATIENT`, `MEDECIN`, `SECRETAIRE`, `ADMIN`, `PHARMACIE`) dans `SecurityConfig` (dont le verrou
  `/api/admin/**` reserve au role ADMIN) puis par `@PreAuthorize` sur chaque endpoint ; `KeycloakRoleConverter`
  traduit tout role du realm en autorite `ROLE_*`.
- Le sujet du jeton (`sub`) est l'identifiant de l'utilisateur : patient, medecin, secretaire ou pharmacie selon le role.
- Les erreurs metier sont traduites par `GestionErreursApi` en `{ "erreur": "..." }` :
  400 (contenu invalide), 403 (acces refuse a une ressource d'un autre utilisateur),
  404 (introuvable), 409 (conflit avec l'etat courant).
- **Journal des acces** : chaque requete `/api/**` est tracee (qui, quoi, quand, resultat, IP tronquee, duree ;
  jamais le corps ni les parametres) par le filtre `FiltreAudit`, consultable par l'administrateur
  (`GET /api/admin/audit`) ; voir la section « Journal des acces ».
- **CORS** : le front web appelle l'API depuis une autre origine ; seules les origines listees dans
  `tabibi.cors.origines` (`CorsProprietes`, variable `TABIBI_CORS_ORIGINES`) sont acceptees, sur `/api/**`,
  methodes GET/POST/PUT/DELETE/OPTIONS, en-tetes `Authorization` et `Content-Type`, sans cookies
  (`credentials: false`), preflight garde 1 h. Une autre origine est refusee (403) avant d'atteindre l'API.

## Configuration

Toute la configuration passe par des variables d'environnement, avec des valeurs par defaut pour le
poste de developpement (`src/main/resources/application.yml`) :

| Variable | Defaut | Role |
|---|---|---|
| `TABIBI_KEYCLOAK_ISSUER` | `http://localhost:8081/realms/tabibi` | emetteur des jetons (realm Keycloak) |
| `TABIBI_CORS_ORIGINES` | `http://localhost:4200` | origines autorisees a appeler l'API depuis un navigateur, separees par des virgules (ex. `https://tabibi.example,https://www.tabibi.example`) |
| `TABIBI_TELECONSULTATION_BASE_URL` | `https://meet.jit.si` | instance Jitsi Meet des teleconsultations |
| `TABIBI_RAPPELS_ACTIFS` | `true` | `false` coupe le planificateur des rappels |
| `SPRING_PROFILES_ACTIVE` | (aucun : en memoire) | `postgres` active JPA / Liquibase sur PostgreSQL |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/tabibi` / `tabibi` / `tabibi` | base PostgreSQL (profil `postgres`) |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native` (profil `postgres`) | prise en compte des en-tetes `X-Forwarded-*` du reverse proxy ; `none` en acces direct |

## Endpoints

Sans jeton :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/medecins?specialite=&wilaya=&q=` | recherche de praticiens |
| GET | `/api/medecins/{id}` | fiche d'un praticien (404 si inconnu) |
| GET | `/api/medecins/{id}/creneaux` | creneaux encore disponibles |
| GET | `/api/ordonnances/verifier/{code}` | verification d'une ordonnance par un pharmacien, sans donnee personnelle |
| GET | `/api/medecins/{id}/avis` | avis publies sur un praticien `{ moyenne, nombre, avis: [{ id, note, commentaire, deposeLe }] }`, anonymises |

Tout utilisateur authentifie :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/moi` | identite portee par le jeton |
| GET | `/api/moi/profil` | mon profil `{ utilisateurId, nomComplet, telephone, dateNaissance, wilayaCode, langue, misAJourLe }` (404 tant qu'il n'est pas renseigne) |
| PUT | `/api/moi/profil` | renseigne ou remplace mon profil `{ nomComplet, telephone, dateNaissance, wilayaCode, langue }` (400 si invalide : nom 2..120, telephone algerien, date de naissance passee, langue fr / ar / kab / en) |
| GET | `/api/notifications/mes` | mes notifications, les plus recentes d'abord |
| GET | `/api/notifications/non-lues/nombre` | `{ "nombre": n }` |
| POST | `/api/notifications/{id}/lue` | marque une notification lue (403 si elle n'est pas a moi, 404 si inconnue) |
| POST | `/api/notifications/toutes-lues` | `{ "nombre": n }` notifications passees a lues |

Role PATIENT :

| Methode | Chemin | Description |
|---|---|---|
| POST | `/api/rendezvous` | reserve un horaire libre `{ medecinId, debut }` (201, 409 si pris) |
| POST | `/api/creneaux/{id}/reserver` | reserve un creneau de l'agenda (201, 404, 409) |
| GET | `/api/rendezvous/mes` | mes rendez-vous, du plus proche au plus lointain |
| POST | `/api/rendezvous/{id}/annuler` | annule mon rendez-vous, le creneau est remis a disposition |
| GET | `/api/ordonnances/mes` | mes ordonnances, les plus recentes d'abord |
| GET | `/api/teleconsultations/mes` | mes teleconsultations, les plus recentes d'abord |
| POST | `/api/teleconsultations/{id}/consentir` | consentement explicite : le lien de salle m'est remis a partir de la |
| POST | `/api/conversations` | ouvre une conversation `{ medecinId }` avec un medecin deja consulte (201, 200 si elle existe, 403 sans rendez-vous commun) |
| POST | `/api/avis` | depose un avis `{ rendezVousId, note, commentaire }` sur un rendez-vous honore (201, 400, 404, 403, 409 si non honore ou deja note) |
| GET | `/api/avis/mes` | mes avis, tous statuts, les plus recents d'abord |
| POST | `/api/dawini/besoins` | publie un besoin de medicament `{ medicament, wilayaCode, commune, precision }` (201, 400) |
| GET | `/api/dawini/besoins/mes` | mes besoins, tous statuts, les plus recents d'abord, avec `nombreReponses` |
| POST | `/api/dawini/besoins/{id}/cloturer` | cloture mon besoin (404, 403, 409 si deja cloture) |
| POST | `/api/medecins/{id}/liste-attente` | m'inscrit sur la liste d'attente d'un medecin (201, 409 si deja inscrit) : je suis prevenu des qu'un creneau se libere chez lui |
| GET | `/api/liste-attente/mes` | mes inscriptions en liste d'attente, les plus anciennes d'abord |
| POST | `/api/liste-attente/{id}/retirer` | me retire d'une liste d'attente (204 sans corps, 404, 403 si l'inscription est a un autre patient) |

Role MEDECIN :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/medecin/rendezvous` | mon agenda, tous statuts |
| POST | `/api/rendezvous/{id}/honorer` | le patient est venu (409 si le rendez-vous n'est pas confirme) |
| POST | `/api/medecin/creneaux` | ouvre un creneau `{ debut, dureeMinutes }` (201, 400) |
| POST | `/api/ordonnances` | redige une ordonnance (201, 400) |
| GET | `/api/medecin/ordonnances` | ordonnances que j'ai redigees |
| POST | `/api/medecin/teleconsultations` | planifie une teleconsultation `{ rendezVousId }` sur un rendez-vous confirme (201, 404, 403, 409) |
| GET | `/api/medecin/teleconsultations` | mes teleconsultations, les plus recentes d'abord |
| POST | `/api/teleconsultations/{id}/demarrer` | ouvre la session (409 sans consentement du patient) |
| POST | `/api/teleconsultations/{id}/terminer` | clot la session (409 si elle n'est pas en cours) |
| POST | `/api/teleconsultations/{id}/annuler` | annule une teleconsultation planifiee (409 sinon) |
| POST | `/api/medecin/candidature` | depose ma candidature a l'annuaire `{ nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville, numeroOrdre, telephone }` (201, 400, 409) |
| GET | `/api/medecin/candidature` | ma derniere candidature (404 si aucune) |
| POST | `/api/avis/{id}/signaler` | signale a l'administrateur un avis publie qui me concerne (403 sinon, 409 s'il n'est pas publie) |
| GET | `/api/medecin/liste-attente` | ma liste d'attente : patients inscrits, les plus anciens d'abord |
| POST | `/api/medecin/rendezvous/{id}/annuler` | annule un rendez-vous confirme de mon agenda : creneau remis a disposition, patient prevenu (403 si autre medecin, 409 si annule ou honore) |
| POST | `/api/medecin/secretaires` | rattache une secretaire a mon cabinet `{ secretaireId }` (201, 400 si moi-meme, 409 si deja rattachee) ; elle est prevenue |
| GET | `/api/medecin/secretaires` | secretaires rattachees a mon cabinet `[{ id, medecinId, secretaireId, creeLe }]` |
| POST | `/api/medecin/secretaires/{id}/retirer` | retire une secretaire de mon cabinet (204 sans corps, 404, 403 si le rattachement est a un autre medecin) |

Role SECRETAIRE (rattachee au cabinet du medecin par celui-ci, 403 sinon) :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/secretaire/medecins` | cabinets auxquels je suis rattachee `[{ id, medecinId, secretaireId, creeLe }]` |
| GET | `/api/secretaire/medecins/{medecinId}/rendezvous` | agenda du medecin, tous statuts (vue des rendez-vous) |
| POST | `/api/secretaire/medecins/{medecinId}/creneaux` | ouvre un creneau `{ debut, dureeMinutes }` dans son agenda (201, 400) |
| POST | `/api/secretaire/rendezvous/{id}/honorer` | le patient est venu (404, 409 si non confirme) |
| POST | `/api/secretaire/rendezvous/{id}/annuler` | annule pour le cabinet : creneau remis a disposition, patient prevenu (404, 409) |

Role PHARMACIE (Dawini) :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/dawini/besoins?wilaya=16` | besoins ouverts de la wilaya, les plus recents d'abord, sans `patientId` (400 sans wilaya) |
| POST | `/api/dawini/besoins/{id}/reponses` | repond `{ nomPharmacie, disponible, prixDa, commentaire }` (201, 400, 404, 409 si cloture ou deja repondu) ; le patient est prevenu |

PATIENT (proprietaire, 403 sinon) ou PHARMACIE :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/dawini/besoins/{id}/reponses` | reponses des pharmacies a un besoin, les plus anciennes d'abord (404) |

PATIENT ou MEDECIN (regle de proprietaire, 403 sinon) :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/ordonnances/{id}` | une ordonnance, pour son patient ou son medecin auteur |
| GET | `/api/teleconsultations/{id}` | une teleconsultation, pour son patient ou son medecin |
| GET | `/api/conversations` | mes conversations, la plus recente activite d'abord, avec `nonLus` |
| GET | `/api/conversations/{id}/messages` | messages du plus ancien au plus recent ; les messages recus sont marques lus |
| POST | `/api/conversations/{id}/messages` | envoie `{ contenu }` (201, 400 si vide ou > 2000 caracteres) ; l'autre participant est prevenu |

Role ADMIN (`/api/admin/**` est aussi verrouille par chemin dans `SecurityConfig`) :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/admin/candidatures?statut=EN_ATTENTE` | candidatures, statut optionnel, les plus anciennes d'abord |
| POST | `/api/admin/candidatures/{id}/valider` | valide : le medecin est publie dans l'annuaire et prevenu (404, 409) |
| POST | `/api/admin/candidatures/{id}/refuser` | refuse avec `{ motif }` : le medecin est prevenu du motif (400, 404, 409) |
| GET | `/api/admin/statistiques` | `{ candidaturesEnAttente, candidaturesValidees, candidaturesRefusees }` |
| GET | `/api/admin/avis?statut=SIGNALE` | avis, statut optionnel (PUBLIE, SIGNALE, MASQUE), les plus anciens d'abord, avec patientId et rendezVousId |
| POST | `/api/admin/avis/{id}/masquer` | retire un avis de la vue publique (404, 409 si deja masque) |
| POST | `/api/admin/avis/{id}/retablir` | remet un avis en ligne (404, 409 si deja publie) |
| POST | `/api/admin/rappels/executer` | declenche manuellement les rappels de rendez-vous des 24 prochaines heures : `{ "nombre": n }` |
| GET | `/api/admin/audit?limite=100` | journal des acces, les plus recents d'abord `[{ id, sujet, methode, chemin, statut, adresseIp, horodatage, dureeMs }]` (`limite` entre 1 et 1000, 100 par defaut) |
| GET | `/api/admin/audit/sujet/{id}?limite=100` | les acces d'un utilisateur (sujet de son jeton), les plus recents d'abord |

Documentation d'API : `/swagger-ui.html`.

## Teleconsultation

Les sessions video reposent sur **Jitsi Meet** (open source, gratuit, auto-hebergeable, aucun SDK
proprietaire) : chaque teleconsultation recoit une salle au nom non devinable (`tabibi-` + 32
caracteres hexadecimaux tires par `SecureRandom`). Le lien vaut `base-url/salleId` et n'est remis
qu'au medecin, et au patient **apres son consentement explicite** (`lienSalle` vaut `null` sinon) ;
le medecin ne peut pas demarrer la session sans ce consentement.

Configuration (`application.yml`) :

```yaml
tabibi:
  teleconsultation:
    base-url: ${TABIBI_TELECONSULTATION_BASE_URL:https://meet.jit.si}   # instance publique, remplacable par la votre
```

## Rappels de rendez-vous

Chaque rendez-vous **confirme** qui commence dans les 24 prochaines heures vaut un rappel au patient
(« Rappel de rendez-vous »), envoye **une seule fois** : le rendez-vous est marque a l'envoi
(`rendez_vous.rappel_envoye_le`). Le planificateur `PlanificateurRappels` (`@Scheduled`, toutes les heures)
appelle `RappelService.executer()`, qui lit l'heure sur l'horloge injectee (`Clock`, bean de `HorlogeConfig`,
UTC ; fixe dans les tests). L'administrateur peut declencher l'envoi a la main
(`POST /api/admin/rappels/executer`).

Configuration (`application.yml`) :

```yaml
tabibi:
  rappels:
    actifs: ${TABIBI_RAPPELS_ACTIFS:true}   # false coupe le planificateur (tests, instances multiples) ; l'appel manuel reste possible
```

## Journal des acces

Exigence de sante : savoir qui a accede a quoi. Le filtre servlet `FiltreAudit` (module `audit`,
enregistre par `AuditConfig` juste apres la chaine de Spring Security) trace chaque requete `/api/**`
(jamais `/actuator/**`) dans une entree `EntreeAudit` : `sujet` (identifiant porte par le jeton, `null`
pour un appel public sans jeton), `methode`, `chemin` (sans la chaine de requete), `statut` HTTP,
`adresseIp`, `horodatage` (reception de la requete) et `dureeMs`.

- **Minimisation** : le corps et les parametres de requete ne sont jamais lus ni journalises (ils
  peuvent porter des donnees de sante) ; l'adresse IP est tronquee (`AdresseIp` : IPv4 au dernier
  octet, `192.168.1.37` devient `192.168.1.0` ; IPv6 aux 64 premiers bits) ; le chemin est coupe a 512 caracteres.
- **Jamais bloquant** : si le journal est indisponible, l'entree est perdue et un avertissement est
  journalise, la requete aboutit normalement.
- **Perimetre** : le filtre tourne apres la securite ; les requetes refusees par Spring Security
  lui-meme (401 sans jeton ou jeton invalide, 403 du verrou `/api/admin/**`) ne sont pas des acces et
  n'y figurent pas ; un refus de `@PreAuthorize` est trace avec le statut 403, une erreur imprevue avec 500.
- **Stockage** : en memoire, journal borne aux 10 000 dernieres entrees (dev/tests) ; sous PostgreSQL,
  table `journal_acces` (Liquibase 016, index sur `horodatage` et `(sujet, horodatage)`), sans purge
  automatique : prevoir une retention (par exemple une suppression periodique des entrees de plus d'un an).
- **Derriere un reverse proxy**, l'adresse vue par le serveur est celle du proxy : le profil `postgres`
  active la prise en compte des en-tetes `X-Forwarded-*` (`server.forward-headers-strategy=native`, en
  provenance des proxys internes seulement ; `SERVER_FORWARD_HEADERS_STRATEGY=none` en acces direct).

## Notifications

Les cas d'usage previennent les utilisateurs par le port `Notifieur` (module `notifications`) :
a la reservation d'un rendez-vous, le patient (« Rendez-vous confirme ») et le medecin (« Nouveau
rendez-vous ») ; a l'annulation, le medecin (« Rendez-vous annule ») ; a chaque message de la
messagerie, l'autre participant (« Nouveau message », sans le contenu) ; a chaque reponse d'une
pharmacie sur Dawini, le patient (« Reponse d'une pharmacie », sans detail) ; a chaque creneau libere chez un
medecin (ouverture d'un creneau, annulation d'un rendez-vous qui remet son creneau a disposition), chaque patient
inscrit sur sa liste d'attente (« Creneau disponible », port `AlerteCreneau` du module `listeattente`, realise par
`ListeAttenteService`) ; a l'annulation par le cabinet (medecin ou secretaire), le patient (« Rendez-vous annule par le
cabinet ») ; au rattachement d'une secretaire et a son retrait, la secretaire ; la veille d'un rendez-vous confirme, le
patient (« Rappel de rendez-vous », une seule fois). Aujourd'hui l'adaptateur
`NotifieurInterne` depose une notification dans la boite de reception de l'application (canal
`INTERNE`) ; un adaptateur SMS ou e-mail (Brevo, fournisseur SMS) pourra s'y brancher sans toucher
au domaine. Les messages ne sont jamais journalises.

## Persistance

Deux adaptateurs derriere chaque port de persistance :

- **en memoire** (profil par defaut, dev et tests) : aucune base requise, praticiens et creneaux de
  demonstration a identifiants fixes ;
- **JPA / PostgreSQL + Liquibase** (profil `postgres`) : `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`,
  migrations dans `src/main/resources/db/changelog` ; base lue dans `SPRING_DATASOURCE_URL` / `_USERNAME` /
  `_PASSWORD` (defaut : le PostgreSQL de `docker-compose.yml`).

## Lancer en local

```bash
docker compose up -d          # Postgres + Keycloak (realm tabibi importe)
mvn spring-boot:run           # API sur http://localhost:8080 (en memoire)
# Keycloak : http://localhost:8081 (admin / admin)
```

`docker-compose.yml` contient aussi, en commentaire, le service `backend` (l'API en conteneur, image construite
par `docker build -t tabibi-backend .`) pour tester localement la configuration de production.

### Comptes de demonstration Keycloak (dev local uniquement)

Le realm importe (`infra/keycloak/tabibi-realm.json`) contient cinq utilisateurs aux mots de passe
simples, a ne jamais reutiliser ailleurs qu'en local. **Ils ne doivent JAMAIS exister en production** :
le realm de production est derive par `infra/keycloak/realm-production.py`, qui les retire (voir
« Securite Keycloak ») ; si une instance en a herite, les supprimer dans la console (Users) ou par
`kcadm.sh delete users/<id> -r tabibi`. Les mots de passe sont importes haches (PBKDF2-SHA512,
`secretData` / `credentialData`) : la politique de mot de passe du realm, qui les refuserait en clair a
l'import, ne s'applique qu'aux changements de mot de passe ; `RealmKeycloakTest` verifie que les
empreintes correspondent bien aux mots de passe ci-dessous.

| Utilisateur | Mot de passe | Role | Identifiant (`sub`) |
|---|---|---|---|
| `patient.demo` | `patient` | PATIENT | `11111111-1111-1111-1111-111111111111` |
| `medecin.demo` | `medecin` | MEDECIN | `00000000-0000-0000-0000-000000000001` (Dr Amina Belkacem, premier praticien de demonstration de l'annuaire en memoire) |
| `admin.demo` | `admin` | ADMIN | `33333333-3333-3333-3333-333333333333` |
| `pharmacie.demo` | `pharmacie` | PHARMACIE | `44444444-4444-4444-4444-444444444444` |
| `secretaire.demo` | `secretaire` | SECRETAIRE | `55555555-5555-5555-5555-555555555555` (a rattacher par `medecin.demo` via `POST /api/medecin/secretaires`) |

Obtenir un jeton en ligne de commande (le client public `tabibi-web` accepte le flux
« direct access grants » **pour le dev local seulement** : `directAccessGrantsEnabled` est un reglage de
developpement, desactive en production par `realm-production.py` ; les applications passent par le flux
Authorization Code + PKCE) :

```bash
curl -s -X POST http://localhost:8081/realms/tabibi/protocol/openid-connect/token \
  -d client_id=tabibi-web -d grant_type=password -d username=medecin.demo -d password=medecin \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
```

## Deploiement

Tout est conteneurise : `Dockerfile` (image de l'API) et `docker-compose.prod.yml` (PostgreSQL, Keycloak, API,
front web, Caddy). Seuls les ports 80/443 de Caddy sont exposes ; les autres services ne se parlent que sur le
reseau interne `interne`, les donnees vivent dans des volumes nommes (`tabibi-pg`, `caddy-data`, `caddy-config`).

### Image de l'API

`Dockerfile` multi-etapes : construction du jar avec `maven:3.9-eclipse-temurin-21` (`mvn -B -q -DskipTests package`,
les tests tournent en CI), execution sur `eclipse-temurin:21-jre-alpine` avec un utilisateur sans privilege (`tabibi`),
port 8080, `HEALTHCHECK` sur `/actuator/health`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`, JVM dimensionnee sur la
memoire du conteneur (`-XX:MaxRAMPercentage=75.0`). Le contexte de construction est reduit par `.dockerignore`.

```bash
docker build -t tabibi-backend .
docker run --rm -p 8080:8080 tabibi-backend        # profil en memoire, Keycloak attendu sur localhost:8081
```

L'image officielle est publiee sur GitHub Container Registry par la CI (`.github/workflows/ci.yml`, job
`image`) a chaque push sur `main`, une fois le job `verify` (`mvn -B verify`) vert : `ghcr.io/<org>/tabibi-backend:latest`
et `ghcr.io/<org>/tabibi-backend:sha-<commit>` (tag fige, a preferer pour un deploiement reproductible :
`BACKEND_TAG=sha-xxxxxxx` dans `.env`). Un paquet GHCR est prive par defaut : le rendre public dans les reglages
du paquet, ou `docker login ghcr.io` sur le serveur avec un jeton `read:packages`. Dependabot
(`.github/dependabot.yml`) propose chaque semaine les mises a jour Maven, GitHub Actions et images Docker.

### Orchestration (`docker-compose.prod.yml`)

| Service | Image | Role |
|---|---|---|
| `postgres` | `postgres:16-alpine` | bases `tabibi` (donnees de patients) et `keycloak` (role dedie, cree par `infra/postgres/init/01-keycloak.sh` a la premiere initialisation du volume), `healthcheck` `pg_isready` |
| `keycloak` | `quay.io/keycloak/keycloak:26.0` | `start --import-realm` (mode production) du realm `infra/keycloak/production/tabibi-realm.json` genere par `realm-production.py`, `KC_DB=postgres`, `KC_HOSTNAME=https://auth.<domaine>`, `KC_HTTP_ENABLED=true` et `KC_PROXY_HEADERS=xforwarded` derriere Caddy, administrateur initial par variables (compte temporaire : creer un administrateur permanent puis le supprimer) |
| `backend` | `ghcr.io/<org>/tabibi-backend:latest` | `SPRING_PROFILES_ACTIVE=postgres`, `SPRING_DATASOURCE_*`, `TABIBI_KEYCLOAK_ISSUER=https://auth.<domaine>/realms/tabibi` (emetteur attendu), cles de signature lues en interne (`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak:8080/...`), `TABIBI_CORS_ORIGINES=https://<domaine>`, `SERVER_FORWARD_HEADERS_STRATEGY=native` ; demarre une fois PostgreSQL sain |
| `web` | `ghcr.io/<org>/tabibi-web:latest` | front Angular (depot `tabibi-web`) |
| `caddy` | `caddy:2-alpine` | reverse proxy, certificats Let's Encrypt automatiques (`infra/caddy/Caddyfile`) : `<domaine>` -> `web:80`, `api.<domaine>` -> `backend:8080`, `auth.<domaine>` -> `keycloak:8080` ; HSTS, `nosniff` |

Les trois noms DNS (`<domaine>`, `api.<domaine>`, `auth.<domaine>`) doivent pointer vers le serveur avant le
premier demarrage (obtention des certificats).

```bash
cp .env.example .env            # puis renseigner DOMAINE, ACME_EMAIL, ORG_GITHUB et les secrets (openssl rand -base64 32)
infra/keycloak/realm-production.py   # realm de production : sans comptes de demo, sans mot de passe direct, domaine reel
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps                       # backend « healthy » apres une minute environ
curl -s https://api.<domaine>/actuator/health                      # {"status":"UP"}
curl -s https://api.<domaine>/actuator/health/readiness            # sonde de disponibilite
curl -s https://auth.<domaine>/realms/tabibi | head -c 200         # realm importe
docker compose -f docker-compose.prod.yml logs -f backend          # journal de l'API
```

Mise a jour : `docker compose -f docker-compose.prod.yml pull backend web && docker compose -f docker-compose.prod.yml up -d`
(les migrations Liquibase s'appliquent au demarrage de l'API).

### Sauvegardes

`infra/sauvegarde/pg_dump.sh [repertoire]` sauvegarde les bases `tabibi` et `keycloak` (`pg_dump` au format custom,
compresse, un fichier horodate par base dans `/var/backups/tabibi` par defaut, repertoire en `chmod 700`) et supprime
les fichiers de plus de 14 jours (`RETENTION_JOURS`). A planifier chaque nuit :

```
0 3 * * * /opt/tabibi/tabibi-backend/infra/sauvegarde/pg_dump.sh >> /var/log/tabibi-sauvegarde.log 2>&1
```

Restauration : `docker compose -f docker-compose.prod.yml exec -T postgres pg_restore -U tabibi -d tabibi --clean --if-exists < tabibi-<horodatage>.dump`.
Les sauvegardes contiennent des donnees de sante : copie chiffree hors du serveur, acces restreint.

## Securite Keycloak

Le realm `tabibi` (`infra/keycloak/tabibi-realm.json`, verifie par `RealmKeycloakTest`) est durci :

- **Force brute** : `bruteForceProtected` (5 echecs, attente croissante de 60 s, 15 min au plus, verrouillage
  temporaire jamais permanent).
- **Mots de passe** : `length(10) and digits(1) and lowerCase(1) and upperCase(1) and notUsername`.
- **MFA** : politique OTP `totp` (6 chiffres, 30 s), disponible pour tout utilisateur (console du compte, « Signing in »).
  Pour l'**imposer aux roles ADMIN et MEDECIN** : console d'administration, Authentication > Flows, dupliquer le flux
  `browser`, dans le sous-flux « Browser - Conditional OTP » ajouter la condition « Condition - user role » (role
  `ADMIN`, puis une seconde pour `MEDECIN`, ou un role composite) et passer « OTP Form » a *Required*, puis
  Action > *Bind flow* > Browser flow : a la prochaine connexion, l'utilisateur concerne recoit l'action requise
  « Configure OTP » et enregistre son application d'authentification. Pour un compte donne : Users > compte >
  « Required user actions » > *Configure OTP*.
- **Sessions et jetons** : jeton d'acces de 5 min (`accessTokenLifespan: 300`), session inactive close apres 30 min
  (`ssoSessionIdleTimeout: 1800`), 10 h au plus, pas de « se souvenir de moi », `sslRequired: external` (HTTPS
  obligatoire hors reseau local).
- **Clients** : `tabibi-web` (public, Authorization Code + PKCE S256, `redirectUris` et `webOrigins` explicites :
  `http://localhost:4200` et `https://tabibi.example`, jamais `*`) et `tabibi-mobile` (public, PKCE S256, redirection
  `dz.tabibi.app:/oauthredirect`, sans mot de passe direct). `directAccessGrantsEnabled: true` sur `tabibi-web` est
  un reglage de dev (jeton par `curl`), a desactiver en production.
- **Production** : `infra/keycloak/realm-production.py [domaine]` ecrit `infra/keycloak/production/tabibi-realm.json`
  (ignore par git) a partir du realm de dev : comptes de demonstration retires, `directAccessGrantsEnabled` a `false`
  sur tous les clients, `tabibi.example` remplace par le domaine reel et adresses `localhost` retirees ; c'est ce
  fichier que `docker-compose.prod.yml` monte dans Keycloak. Le realm n'est importe qu'au premier demarrage
  (base vide) : ensuite, les reglages se changent dans la console.
- Le meme realm est copie dans le depot `tabibi-infra-docs` (`infra/keycloak/tabibi-realm.json`).

## Tester

```bash
mvn test                      # tests unitaires + web (aucun service externe requis)
mvn verify -Dit.docker=true   # + test d'integration PostgreSQL (Testcontainers, Docker requis)
```

## Structure (hexagonale)

```
<module>/
  domain/        entites, regles, exceptions metier et ports (interfaces)
  application/   cas d'usage (services)
  adapter/       controleur REST (entrant), repositories en memoire et JPA (sortants)

annuaire/        praticiens, recherche publique
creneaux/        disponibilites et ouverture de creneaux
rendezvous/      reservation, annulation, agenda, rendez-vous honores
ordonnances/     redaction, consultation, verification publique par code
notifications/   boite de reception, port Notifieur et notifieur interne
teleconsultation/ sessions video Jitsi Meet avec consentement du patient
administration/  candidatures des medecins, validation par l'administrateur, statistiques
messagerie/      conversations patient-medecin (apres un rendez-vous) et messages
avis/            avis verifies des patients (rendez-vous honore), synthese publique, moderation
dawini/          besoins de medicaments des patients et reponses des pharmacies (role PHARMACIE)
profil/          profil de l'utilisateur connecte (nom, telephone, date de naissance, wilaya, langue)
listeattente/    liste d'attente par medecin, port AlerteCreneau alerte des inscrits quand un creneau se libere
cabinet/         secretaires rattachees a un medecin : agenda, creneaux, rendez-vous honores ou annules pour lui
rappels/         rappel de rendez-vous 24 h avant (RappelService a horloge injectee, planificateur horaire, declenchement admin)
audit/           journal des acces : EntreeAudit, AdresseIp (troncature), port AuditRepository, FiltreAudit (servlet, apres la securite), AuditConfig, consultation ADMIN
identite/        MoiController
commun/          erreurs API (GestionErreursApi), exceptions partagees, format de date
config/          securite (JWT + roles Keycloak, CORS : CorsProprietes), horloge (Clock) et planification (@EnableScheduling)
```

Le detail de chaque version est dans `docs/JOURNAL.md`.
