# tabibi-backend

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
  migrations dans `src/main/resources/db/changelog`.

## Lancer en local

```bash
docker compose up -d          # Postgres + Keycloak (realm tabibi importe)
mvn spring-boot:run           # API sur http://localhost:8080 (en memoire)
# Keycloak : http://localhost:8081 (admin / admin)
```

### Comptes de demonstration Keycloak (dev local uniquement)

Le realm importe (`infra/keycloak/tabibi-realm.json`) contient cinq utilisateurs aux mots de passe
simples, a ne jamais reutiliser ailleurs qu'en local :

| Utilisateur | Mot de passe | Role | Identifiant (`sub`) |
|---|---|---|---|
| `patient.demo` | `patient` | PATIENT | `11111111-1111-1111-1111-111111111111` |
| `medecin.demo` | `medecin` | MEDECIN | `00000000-0000-0000-0000-000000000001` (Dr Amina Belkacem, premier praticien de demonstration de l'annuaire en memoire) |
| `admin.demo` | `admin` | ADMIN | `33333333-3333-3333-3333-333333333333` |
| `pharmacie.demo` | `pharmacie` | PHARMACIE | `44444444-4444-4444-4444-444444444444` |
| `secretaire.demo` | `secretaire` | SECRETAIRE | `55555555-5555-5555-5555-555555555555` (a rattacher par `medecin.demo` via `POST /api/medecin/secretaires`) |

Obtenir un jeton en ligne de commande (le client public `tabibi-web` accepte le flux
« direct access grants » pour le dev local) :

```bash
curl -s -X POST http://localhost:8081/realms/tabibi/protocol/openid-connect/token \
  -d client_id=tabibi-web -d grant_type=password -d username=medecin.demo -d password=medecin \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
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
teleconsultation/ sessions video Jitsi Meet avec consentement du patient
administration/  candidatures des medecins, validation par l'administrateur, statistiques
messagerie/      conversations patient-medecin (apres un rendez-vous) et messages
avis/            avis verifies des patients (rendez-vous honore), synthese publique, moderation
dawini/          besoins de medicaments des patients et reponses des pharmacies (role PHARMACIE)
profil/          profil de l'utilisateur connecte (nom, telephone, date de naissance, wilaya, langue)
listeattente/    liste d'attente par medecin, port AlerteCreneau alerte des inscrits quand un creneau se libere
cabinet/         secretaires rattachees a un medecin : agenda, creneaux, rendez-vous honores ou annules pour lui
rappels/         rappel de rendez-vous 24 h avant (RappelService a horloge injectee, planificateur horaire, declenchement admin)
identite/        MoiController
commun/          erreurs API (GestionErreursApi), exceptions partagees, format de date
config/          securite (JWT + roles Keycloak, CORS : CorsProprietes), horloge (Clock) et planification (@EnableScheduling)
```

Le detail de chaque version est dans `docs/JOURNAL.md`.
