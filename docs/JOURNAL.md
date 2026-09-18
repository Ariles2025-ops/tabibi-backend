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

## v0.6.0 — Ordonnances
- POST /api/ordonnances (MEDECIN, 201) : redaction d'une ordonnance pour un patient (rendez-vous facultatif),
  au moins une ligne { medicament, posologie, duree } sinon 400 ; code de verification de 8 caracteres
  (SecureRandom, sans O/0/I/1 pour eviter les confusions), statut EMISE.
- GET /api/ordonnances/mes (PATIENT) : mes ordonnances, les plus recentes d'abord.
- GET /api/ordonnances/{id} (PATIENT ou MEDECIN) : accessible au patient destinataire et au medecin auteur ;
  403 sinon, 404 si absente.
- GET /api/ordonnances/verifier/{code} (public, sans jeton) : { valide, emiseLe, statut }, sans aucune donnee
  personnelle ; le code est accepte en minuscules ; 404 si le code est inconnu.
- Module ordonnances : Ordonnance (record immuable), LigneOrdonnance, StatutOrdonnance, CodeVerification,
  ResultatVerification, port OrdonnanceRepository (enregistrer, parId, parPatient, parMedecin, parCode),
  OrdonnanceService ; exceptions OrdonnanceInvalide (400) et OrdonnanceIntrouvable (404) dans GestionErreursApi.
- Adaptateurs : en memoire et JPA (lignes stockees en JSON dans lignes_json via l'ObjectMapper de Spring) ;
  Liquibase 005 (table ordonnance, code unique, index patient et medecin).
- Tests : code de 8 caracteres et statut EMISE, refus sans ligne / sans medicament / sans patient, codes distincts,
  tri par date, acces patient / medecin / tiers, verification (code connu, minuscules, annulee, inconnu),
  codec JSON ; web 401 / 403 / 201, 400, 200 / 403 / 404 et verification publique 200 / 404.

## v0.7.0 — Espace medecin
- GET /api/medecin/rendezvous (MEDECIN) : agenda du medecin connecte, tous statuts, du plus proche au plus lointain.
- POST /api/rendezvous/{id}/honorer (MEDECIN, 200) : le patient est venu, statut HONORE ; 404 si inconnu,
  403 si le rendez-vous est dans l'agenda d'un autre medecin, 409 s'il n'est pas confirme (annule, deja honore).
- POST /api/medecin/creneaux (MEDECIN, 201, body { debut, dureeMinutes }) : ouvre un creneau disponible ;
  400 si le debut n'est pas dans le futur ou si la duree sort de 5..120 minutes.
- GET /api/medecin/ordonnances (MEDECIN) : ordonnances redigees par le medecin, les plus recentes d'abord.
- Domaine : RendezVous.honorer() (uniquement depuis CONFIRME) et estAvec(medecinId) ; exception commune
  TransitionInvalide (409) ; CreneauInvalide (400) ; CreneauService.ouvrir ; RendezVousService.agendaDuMedecin
  et honorer ; OrdonnanceService.ordonnancesDuMedecin.
- Persistance : RendezVousRepository.parMedecin (memoire + JPA findByMedecinIdOrderByDebut).
- API : la vue d'un rendez-vous expose desormais patientId (utile a l'agenda du medecin) ; le sujet du jeton
  vaut identifiant du patient ou du medecin selon le role.
- Tests : honorer un rendez-vous confirme, refus si annule / deja honore / autre medecin / inconnu, agenda trie
  tous statuts, creneau ouvert / passe / duree hors bornes / bornes acceptees, ordonnances du medecin ;
  web 401 / 403 PATIENT / 200 MEDECIN pour l'agenda, 200 / 403 / 409 pour honorer, 401 / 403 / 201 / 400
  pour l'ouverture d'un creneau, 200 / 403 pour les ordonnances du medecin.

## v0.8.0 — Notifications
- Boite de reception de l'utilisateur connecte, quel que soit son role (401 sans jeton) :
  GET /api/notifications/mes (les plus recentes d'abord), GET /api/notifications/non-lues/nombre ({ nombre }),
  POST /api/notifications/{id}/lue (403 si adressee a un autre utilisateur, 404 si inconnue),
  POST /api/notifications/toutes-lues ({ nombre } de notifications passees a lues).
- Module notifications : Notification (record immuable, marquerLue() en copie), CanalNotification (INTERNE seul
  realise ; SMS et EMAIL reserves), port NotificationRepository (enregistrer, parId, parDestinataire, nombreNonLues),
  port Notifieur (notifier(destinataire, sujet, message)) : point d'extension des canaux, un adaptateur SMS / e-mail
  s'y branchera sans toucher au domaine ; NotificationService ; NotifieurInterne (@Component) qui depose la
  notification et la trace dans le journal (identifiant, destinataire et sujet seulement : le message peut porter des
  informations de sante) ; exception NotificationIntrouvable (404) dans GestionErreursApi.
- Branchement : RendezVousService recoit un Notifieur ; a la reservation (horaire libre ou creneau) le patient est
  prevenu (« Rendez-vous confirme », avec la date) et le medecin aussi (« Nouveau rendez-vous ») ; a l'annulation
  effective le medecin est prevenu (« Rendez-vous annule ») ; une seconde annulation ne previent personne.
- Commun : FormatDate.lisible(instant) presente les dates des messages a l'heure d'Algerie (07/12/2026 a 10:00).
- Persistance : adaptateur en memoire et adaptateur JPA ; Liquibase 007 (table notification, index destinataire +
  date de creation).
- Tests : service (liste triee par destinataire, nombre de non lues, marquer lue et idempotence, refus d'un tiers,
  introuvable, tout marquer lu sans toucher les autres), notifieur interne, format de date, rendez-vous (faux
  Notifieur : qui est prevenu, de quoi, et pas en cas d'echec) ; web 401 sans jeton, 200 PATIENT et MEDECIN,
  compteur, 200 / 403 / 404 pour marquer lue, 200 pour tout marquer lu.
