# Journal des fonctionnalites

## v0.1.0 — Socle securite
- Resource server JWT (Keycloak), roles PATIENT/MEDECIN/SECRETAIRE/ADMIN.
- Fonctionnalite « reserver un rendez-vous » (regle : creneau libre), adaptateur en memoire.
- Tests : unitaire (service) + securite web (401 sans jeton, 200 avec, role requis).
- Infra : docker-compose (PostgreSQL + Keycloak), CI GitHub Actions.

## A suivre
- Persistance JPA + Liquibase (1re migration : utilisateur, rendez-vous) sur PostgreSQL.
- Module annuaire (medecins, specialites, wilayas) + recherche.

## v0.2.0 — Persistance
- Adaptateur JPA/PostgreSQL derriere le port RendezVousRepository (profil `postgres`).
- Liquibase : 1re migration (tables utilisateur, rendez_vous + index).
- Test d'integration Testcontainers (Docker/CI).
- L'adaptateur en memoire reste actif hors profil postgres (dev/tests).

## v0.3.0 — Annuaire
- Recherche publique de praticiens : GET /api/medecins?specialite=&wilaya=&q= (sans connexion).
- Deux adaptateurs (memoire seeded / JPA + requete JPQL), Liquibase 002 (table medecin + index).
- Securite : GET annuaire en acces libre ; le reste reste protege.
- Tests : filtres (specialite/wilaya/texte) + acces public sans jeton.

## v0.4.0 — Creneaux
- Disponibilites d'un medecin, sans connexion : GET /api/medecins/{id} (fiche, 404 si inconnu)
  et GET /api/medecins/{id}/creneaux (creneaux encore disponibles, du plus proche au plus lointain).
- Module creneaux (Creneau, port CreneauRepository, CreneauService) ; port MedecinRepository enrichi de parId.
- Adaptateurs : en memoire (praticiens de demonstration a identifiants fixes, 4 creneaux futurs de 20 min
  chacun) et JPA ; Liquibase 003 (table creneau + index medecin/debut).
- Tests : creneaux d'un praticien seede, medecin/identifiant inconnu, acces public sans jeton (fiche et creneaux).
