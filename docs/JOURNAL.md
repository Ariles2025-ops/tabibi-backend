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

## v0.5.0 — Gestion des rendez-vous
- POST /api/creneaux/{id}/reserver (PATIENT, 201) : le rendez-vous est cree sur l'horaire du creneau,
  qui cesse d'etre propose ; 409 s'il est deja pris, 404 s'il n'existe pas.
- GET /api/rendezvous/mes (PATIENT) : mes rendez-vous, tous statuts, du plus proche au plus lointain.
- POST /api/rendezvous/{id}/annuler (PATIENT, 200) : statut ANNULE et creneau remis a disposition ;
  403 si le rendez-vous est a un autre patient, 404 s'il n'existe pas ; annuler deux fois ne relibere
  pas un creneau repris entre-temps.
- Domaine : RendezVous porte le creneau reserve (creneauId, optionnel) ; Creneau.reserver()/liberer()
  (copies immuables) ; exceptions CreneauIntrouvable, RendezVousIntrouvable, AccesRefuse.
- Erreurs : conseil global GestionErreursApi (corps { "erreur": "..." }) : 404 introuvable, 409 conflit,
  403 acces refuse ; l'ancien @ExceptionHandler du controleur y est deplace.
- Persistance : RendezVousRepository.parPatient (memoire + JPA), Liquibase 004 (colonne rendez_vous.creneau_id).
- Tests : reservation (creneau consomme), double reservation, creneau inconnu, liste par patient, annulation
  (creneau libere, refus si autre patient, idempotence) ; web 401/200/403 par role et codes 201/409/404/403.

## v0.5.1 — Correctif demarrage
- Hors profil `postgres`, l'auto-configuration DataSource/JPA/Liquibase est exclue : l'API demarre sans base (memoire). Le profil `postgres` la reactive.
