# tabibi-backend

API de la plateforme Tabibi (prise de rendez-vous medicaux en Algerie). Architecture hexagonale,
Spring Boot 3.4 / Java 21, securite JWT / Keycloak. Les donnees manipulees sont des donnees de
patients : la securite prime (autorisation par role cote serveur, regles de proprietaire dans les
cas d'usage, aucune donnee personnelle dans les reponses publiques ni dans les journaux).

## Securite

- Chaque requete porte un **JWT** signe par **Keycloak** (realm `tabibi`) ; l'autorisation se fait par
  role (`PATIENT`, `MEDECIN`, `SECRETAIRE`, `ADMIN`) dans `SecurityConfig` puis par `@PreAuthorize`.
- Le sujet du jeton (`sub`) est l'identifiant de l'utilisateur : patient ou medecin selon le role.
- Les erreurs metier sont traduites par `GestionErreursApi` en `{ "erreur": "..." }` :
  400 (contenu invalide), 403 (acces refuse a une ressource d'un autre utilisateur),
  404 (introuvable), 409 (conflit avec l'etat courant).

## Endpoints

Sans jeton :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/medecins?specialite=&wilaya=&q=` | recherche de praticiens |
| GET | `/api/medecins/{id}` | fiche d'un praticien (404 si inconnu) |
| GET | `/api/medecins/{id}/creneaux` | creneaux encore disponibles |
| GET | `/api/ordonnances/verifier/{code}` | verification d'une ordonnance par un pharmacien, sans donnee personnelle |

Tout utilisateur authentifie :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/moi` | identite portee par le jeton |
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

Role MEDECIN :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/medecin/rendezvous` | mon agenda, tous statuts |
| POST | `/api/rendezvous/{id}/honorer` | le patient est venu (409 si le rendez-vous n'est pas confirme) |
| POST | `/api/medecin/creneaux` | ouvre un creneau `{ debut, dureeMinutes }` (201, 400) |
| POST | `/api/ordonnances` | redige une ordonnance (201, 400) |
| GET | `/api/medecin/ordonnances` | ordonnances que j'ai redigees |

PATIENT ou MEDECIN (regle de proprietaire, 403 sinon) :

| Methode | Chemin | Description |
|---|---|---|
| GET | `/api/ordonnances/{id}` | une ordonnance, pour son patient ou son medecin auteur |

Documentation d'API : `/swagger-ui.html`.

## Notifications

Les cas d'usage previennent les utilisateurs par le port `Notifieur` (module `notifications`) :
a la reservation d'un rendez-vous, le patient (« Rendez-vous confirme ») et le medecin (« Nouveau
rendez-vous ») ; a l'annulation, le medecin (« Rendez-vous annule »). Aujourd'hui l'adaptateur
`NotifieurInterne` depose une notification dans la boite de reception de l'application (canal
`INTERNE`) ; un adaptateur SMS ou e-mail (Brevo, fournisseur SMS) pourra s'y brancher sans toucher
au domaine. Les messages ne sont jamais journalises.

## Persistance

Deux adaptateurs derriere chaque port de persistance :

- **en memoire** (profil par defaut, dev et tests) : aucune base requise, praticiens et creneaux de
  demonstration a identifiants fixes ;
- **JPA / PostgreSQL + Liquibase** (profil `postgres`) : `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`,
  migrations dans `src/main/resources/db/changelog`.

## Lancer en local

```bash
docker compose up -d          # Postgres + Keycloak (realm tabibi importe)
mvn spring-boot:run           # API sur http://localhost:8080 (en memoire)
# Keycloak : http://localhost:8081 (admin / admin)
```

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
identite/        MoiController
commun/          erreurs API (GestionErreursApi), exceptions partagees, format de date
config/          securite (JWT + roles Keycloak)
```

Le detail de chaque version est dans `docs/JOURNAL.md`.
