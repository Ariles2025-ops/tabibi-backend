# tabibi-backend

API de la plateforme Tabibi — **socle securite**. Architecture hexagonale, Spring Boot 3.4 / Java 21.

## Ce que fait cette premiere version

- Securite : chaque requete porte un **JWT** signe par **Keycloak** ; l'autorisation se fait par role (`PATIENT`, `MEDECIN`, `SECRETAIRE`, `ADMIN`).
- `GET /api/moi` — identite de l'utilisateur courant (protege).
- `POST /api/rendezvous` — reserve un creneau (**role PATIENT**), avec la regle metier « le creneau doit etre libre » (409 sinon).
- Documentation d'API : `/swagger-ui.html`.

Persistance : **en memoire** pour l'instant, derriere un *port* (`RendezVousRepository`).
Elle sera remplacee par un adaptateur **JPA / PostgreSQL + Liquibase** a l'etape suivante,
sans toucher au domaine. PostgreSQL est deja fourni par `docker-compose`.

## Lancer en local

```bash
docker compose up -d          # Postgres + Keycloak (realm tabibi importe)
mvn spring-boot:run           # API sur http://localhost:8080
# Keycloak : http://localhost:8081 (admin / admin)
```

## Tester

```bash
mvn test                      # tests unitaires + securite (aucun service externe requis)
```

## Structure (hexagonale)

```
rendezvous/
  domain/        entites + regles + port (RendezVousRepository)
  application/   cas d'usage (RendezVousService)
  adapter/       controleur REST (entrant) + repository en memoire (sortant)
identite/        MoiController
config/          securite (JWT + roles Keycloak)
```
