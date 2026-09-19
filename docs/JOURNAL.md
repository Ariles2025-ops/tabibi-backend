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

## v0.9.0 — Teleconsultation
- Choix : salle video Jitsi Meet (open source, gratuit, auto-hebergeable, aucun SDK proprietaire) ; nom de salle
  non devinable (tabibi- + 32 caracteres hexadecimaux tires par SecureRandom) ; consentement explicite du patient
  obligatoire avant tout acces au lien (donnees de sante). URL de base configurable :
  tabibi.teleconsultation.base-url (defaut https://meet.jit.si), lien = base-url/salleId.
- POST /api/medecin/teleconsultations (MEDECIN, 201, body { rendezVousId }) : planifie une teleconsultation sur un
  rendez-vous confirme du medecin ; 404 si le rendez-vous est inconnu, 403 s'il est avec un autre medecin, 409 s'il
  n'est pas confirme ou si une teleconsultation non annulee existe deja ; le patient est prevenu
  (« Teleconsultation proposee »).
- GET /api/medecin/teleconsultations (MEDECIN) et GET /api/teleconsultations/mes (PATIENT) : les plus recentes d'abord.
- GET /api/teleconsultations/{id} (PATIENT ou MEDECIN) : pour le patient destinataire ou le medecin, 403 sinon, 404 si absente.
- POST /api/teleconsultations/{id}/consentir (PATIENT, 200) : idempotent ; 409 si terminee ou annulee.
- POST /api/teleconsultations/{id}/demarrer (MEDECIN, 200) : exige PLANIFIEE et le consentement (409 sinon) ; le patient
  est prevenu (« Teleconsultation demarree »). POST .../terminer (exige EN_COURS) et POST .../annuler (exige PLANIFIEE).
- Vue { id, rendezVousId, patientId, medecinId, statut, consentementPatientLe, lienSalle, creeLe, demarreeLe, termineeLe } :
  lienSalle est renseigne pour le medecin toujours, pour le patient uniquement apres consentement, null sinon.
- Module teleconsultation : Teleconsultation (entite : planifier, consentir, demarrer, terminer, annuler, appartientA,
  estAvec, patientAConsenti, peutAccederALaSalle), StatutTeleconsultation, GenerateurSalle (expose en bean par
  TeleconsultationConfig, le domaine reste sans annotation Spring), port TeleconsultationRepository (enregistrer, parId,
  parPatient, parMedecin, parRendezVous = la derniere non annulee), TeleconsultationService (depend du port
  RendezVousRepository du module rendezvous, du Notifieur et de la base-url), TeleconsultationIntrouvable (404).
- Persistance : adaptateur en memoire et JPA (findFirstByRendezVousIdAndStatutNotOrderByCreeLeDesc) ; Liquibase 006
  (table teleconsultation, salle_id unique, index patient, medecin, rendez-vous), inseree avant 007 dans le master.
- Tests : domaine (transitions, idempotence du consentement, refus sans consentement, acces a la salle), generateur
  (format, unicite), service (planifier ok / 404 / 403 / 409 x2, replanification apres annulation, lien cache puis visible,
  lien du medecin et du tiers, detail, consentement reserve au patient, demarrer sans consentement 409, terminer,
  annuler, listes triees, notifications) ; web 401 / 403 par role, 201 / 404 / 403 / 409 pour planifier, 200 pour
  consentir / demarrer / terminer / annuler, 409 sans consentement, lienSalle null pour un patient sans consentement.

## v0.10.0 — Administration
- POST /api/medecin/candidature (MEDECIN, 201, body { nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr,
  ville, numeroOrdre, telephone }) : depot d'une candidature a l'annuaire ; 400 si nom, specialite, wilaya ou numero
  d'ordre manque ; 409 si une candidature en attente ou validee existe deja (une refusee peut etre redeposee).
- GET /api/medecin/candidature (MEDECIN) : derniere candidature du medecin connecte, 404 s'il n'en a depose aucune.
- GET /api/admin/candidatures?statut= (ADMIN, statut optionnel) : les plus anciennes d'abord.
- POST /api/admin/candidatures/{id}/valider (ADMIN, 200) : statut VALIDEE, le medecin est publie dans l'annuaire
  (fiche sous l'identifiant de son jeton) et prevenu (« Candidature validee ») ; 404 si inconnue, 409 si deja traitee.
- POST /api/admin/candidatures/{id}/refuser (ADMIN, 200, body { motif }) : statut REFUSEE, le medecin est prevenu avec
  le motif (« Candidature refusee ») ; 400 sans motif, 404, 409.
- GET /api/admin/statistiques (ADMIN) : { candidaturesEnAttente, candidaturesValidees, candidaturesRefusees }.
- Vue { id, medecinId, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr, ville, numeroOrdre, telephone,
  statut, motifRefus, deposeeLe, traiteeLe }.
- Securite : SecurityConfig verrouille /api/admin/** au role ADMIN avant anyRequest (defense en profondeur, en plus du
  @PreAuthorize des controleurs).
- Module administration : CandidatureMedecin (record immuable : deposer, valider, refuser, estEnAttente),
  DemandeCandidature, StatutCandidature, StatistiquesAdministration, port CandidatureRepository (enregistrer, parId,
  derniereDuMedecin, lister(statut optionnel), compter), AdministrationService ; exceptions CandidatureIntrouvable (404)
  et CandidatureInvalide (400) dans GestionErreursApi ; le doublon de candidature est un conflit (TransitionInvalide, 409).
- Annuaire : nouveau port MedecinRepository.enregistrer(Medecin) (memoire : les praticiens de demonstration restent
  seedes, la recherche est triee par nom comme en JPA ; JPA : MedecinEntity.de + save) ; les libelles facultatifs absents
  (specialiteFr, wilayaFr) sont remplaces par le code correspondant a la publication, l'annuaire les exigeant.
- Persistance : adaptateur en memoire (ordre de depot conserve) et JPA ; Liquibase 008 (table candidature_medecin,
  index medecin_id et statut).
- Keycloak : trois comptes de demonstration dans infra/keycloak/tabibi-realm.json (patient.demo / patient,
  medecin.demo / medecin avec l'identifiant du premier praticien de demonstration 00000000-0000-0000-0000-000000000001,
  admin.demo / admin), mots de passe non temporaires et direct access grants actives sur tabibi-web pour obtenir un jeton
  en ligne de commande en dev local uniquement.
- Tests : domaine (depot et nettoyage des champs, donnees obligatoires, valider / refuser depuis EN_ATTENTE seulement,
  motif obligatoire), service (depot, incomplete, doublon 409 en attente et validee, redepot apres refus, validation qui
  publie dans l'annuaire et previent, libelles completes, refus avec motif et notification, refus sans motif 400,
  liste triee et filtree, statistiques) ; web 401 sans jeton, 403 PATIENT et MEDECIN sur /api/admin/**, 200 ADMIN,
  MEDECIN 201 / 200 / 404, 409 doublon, 400 candidature incomplete et refus sans motif ; SecuriteWebTest complete
  (/api/admin/** refuse a un MEDECIN et a un PATIENT, 401 sans jeton).

## v0.11.0 — Messagerie
- POST /api/conversations (PATIENT, body { medecinId }) : ouvre la conversation du patient avec un medecin qu'il a deja
  consulte (au moins un rendez-vous, quel qu'en soit le statut) : 201 si elle est creee, 200 si elle existait deja
  (une seule par couple patient / medecin) ; 403 sans rendez-vous commun, 400 sans medecin.
- GET /api/conversations (PATIENT ou MEDECIN) : mes conversations, la plus recente activite d'abord, chacune avec le
  nombre de messages que je n'ai pas encore lus (nonLus).
- GET /api/conversations/{id}/messages (participant) : messages du plus ancien au plus recent ; les messages de l'autre
  participant sont marques lus par cette lecture (luLe) ; 404 si inconnue, 403 pour un tiers.
- POST /api/conversations/{id}/messages (participant, 201, body { contenu }) : contenu obligatoire, non blanc, au plus
  2000 caracteres (400 sinon) ; la conversation est reactivee (dernierMessageLe) et l'autre participant est prevenu
  (« Nouveau message », « Vous avez recu un nouveau message. » : jamais le contenu, qui peut porter des donnees de sante).
- Vues : ConversationVue { id, patientId, medecinId, creeLe, dernierMessageLe, nonLus } et
  MessageVue { id, conversationId, auteurId, contenu, envoyeLe, luLe }.
- Module messagerie : Conversation (record immuable : ouvrir, avecDernierMessageLe, participe, autreParticipant ;
  dernierMessageLe vaut la date d'ouverture tant qu'aucun message n'est envoye, pour un tri sans valeur absente),
  Message (record immuable : envoyer avec validation, marquerLu en copie idempotente, estLu, estDe), ResultatOuverture,
  ConversationAvecNonLus, ports ConversationRepository (enregistrer, parId, parParticipants, parParticipant) et
  MessageRepository (enregistrer, parConversation, nonLus, marquerLus), MessagerieService (depend du port
  RendezVousRepository du module rendezvous et du Notifieur) ; exceptions ConversationIntrouvable (404) et
  MessageInvalide (400) dans GestionErreursApi.
- Persistance : adaptateurs en memoire (messages tries du plus ancien au plus recent, ordre d'envoi conserve a date
  egale) et JPA (findByPatientIdOrMedecinIdOrderByDernierMessageLeDesc, countByConversationIdAndAuteurIdNotAndLuLeIsNull) ;
  Liquibase 009 (table conversation : unique (patient_id, medecin_id), index patient et medecin ; table message :
  index (conversation_id, envoye_le)).
- Tests : domaine (contenu strippe, blanc refuse, borne 2000, marquage lu en copie et idempotent, participants),
  service (ouverture refusee sans rendez-vous ou avec un autre medecin, medecin absent 400, creation malgre un rendez-vous
  annule puis reutilisation, liste triee par activite avec non lus par lecteur, lecture qui marque lus les messages de
  l'autre seulement, envoi qui reactive la conversation et previent l'autre sans le contenu, tiers 403, inconnue 404,
  vide / trop long 400 sans rien enregistrer) ; web 401 sans jeton, 403 MEDECIN a l'ouverture et ADMIN sur les routes
  participant, 201 / 200 / 403 / 400 a l'ouverture, 200 liste et messages, 201 envoi, 404 / 403 / 400.

## v0.12.0 — Avis
- POST /api/avis (PATIENT, 201, body { rendezVousId, note, commentaire }) : avis verifie, depose par le patient sur le
  medecin d'un de ses rendez-vous honores ; 400 si le rendez-vous manque, si la note sort de 1..5 ou si le commentaire
  depasse 500 caracteres (facultatif, espaces autour retires) ; 404 rendez-vous inconnu, 403 rendez-vous d'un autre
  patient, 409 rendez-vous non honore ou deja note (un seul avis par rendez-vous).
- GET /api/avis/mes (PATIENT) : mes avis, tous statuts, les plus recents d'abord.
- GET /api/medecins/{id}/avis (public, sans jeton : couvert par le permitAll GET /api/medecins/** de SecurityConfig) :
  { moyenne, nombre, avis: [{ id, note, commentaire, deposeLe }] } ; seuls les avis publies comptent (ni signales, ni
  masques), moyenne arrondie a une decimale et nulle sans avis ; anonymise : ni patientId, ni rendezVousId.
- POST /api/avis/{id}/signaler (MEDECIN, 200) : le medecin concerne signale un avis publie a l'administrateur (403 pour
  un autre medecin, 404, 409 s'il n'est pas publie) ; l'avis sort de la vue publique.
- GET /api/admin/avis?statut= (ADMIN, statut optionnel) : les plus anciens d'abord, vue complete AvisAdminVue
  { id, rendezVousId, patientId, medecinId, note, commentaire, statut, deposeLe } ; POST /api/admin/avis/{id}/masquer
  (409 si deja masque) et POST /api/admin/avis/{id}/retablir (409 si deja publie), 404 si inconnu.
- Vues : AvisVue { id, rendezVousId, medecinId, note, commentaire, statut, deposeLe } pour le patient et le medecin
  (sans patientId), AvisPublicVue anonyme, AvisAdminVue complete.
- Module avis : Avis (record immuable : deposer avec validation, signaler PUBLIE -> SIGNALE, masquer PUBLIE / SIGNALE
  -> MASQUE, retablir -> PUBLIE, estPublie, estDe, concerne), StatutAvis, SyntheseAvis (moyenne, nombre, avis), port
  AvisRepository (enregistrer, parId, parRendezVous, parPatient, publiesPourMedecin, parStatut, tous), AvisService
  (depend du port RendezVousRepository du module rendezvous) ; exceptions AvisIntrouvable (404) et AvisInvalide (400)
  dans GestionErreursApi ; les transitions interdites et le doublon sont des conflits (TransitionInvalide, 409).
- Persistance : adaptateur en memoire (ordre de depot conserve a date egale) et JPA (findByMedecinIdAndStatutOrderByDeposeLeDesc,
  findByStatutOrderByDeposeLeAsc, findAllByOrderByDeposeLeAsc) ; Liquibase 010 (table avis : rendez_vous_id unique,
  index (medecin_id, statut) et patient_id, commentaire varchar(500)).
- Tests : domaine (depot et nettoyage du commentaire, commentaire facultatif, note 1..5, borne 500, transitions signaler /
  masquer / retablir en copie, synthese arrondie et synthese vide), service (depot ok, note hors bornes, commentaire trop
  long, rendez-vous absent / inconnu / d'un autre / non honore, doublon, mes avis tries, synthese excluant signales et
  masques, retablissement, signalement par le bon medecin / par un autre / inconnu, masquer / retablir, liste admin
  triee et filtree) ; web 401 sans jeton, PATIENT 201 / 400 / 409 / 404 / 403, public 200 sans jeton et anonymise,
  moyenne nulle sans avis, MEDECIN signaler 200 / 403 / 404, ADMIN liste / masquer / retablir 200 / 409 / 404,
  PATIENT et MEDECIN 403 sur /api/admin/avis.

## v0.13.0 — Dawini
- Nouveau role Keycloak PHARMACIE (roles du realm, traduit en ROLE_PHARMACIE par KeycloakRoleConverter sans changement)
  et compte de demonstration pharmacie.demo / pharmacie (44444444-4444-4444-4444-444444444444) dans
  infra/keycloak/tabibi-realm.json ; README (roles, comptes de demo).
- POST /api/dawini/besoins (PATIENT, 201, body { medicament, wilayaCode, commune, precision }) : publie un besoin de
  medicament ouvert ; 400 si le medicament ou la wilaya manque, ou si une donnee est trop longue (medicament 200,
  wilaya 4, commune 120, precision 500 caracteres) ; espaces autour retires, facultatifs blancs effaces.
- GET /api/dawini/besoins/mes (PATIENT) : mes besoins, tous statuts, les plus recents d'abord, avec nombreReponses.
- POST /api/dawini/besoins/{id}/cloturer (PATIENT, 200) : 404 si inconnu, 403 s'il est a un autre patient, 409 si deja
  cloture ; un besoin cloture n'est plus propose aux pharmacies et n'accepte plus de reponse.
- GET /api/dawini/besoins?wilaya=16 (PHARMACIE) : besoins ouverts de la wilaya, les plus recents d'abord, patientId
  null (jamais expose aux pharmacies) ; 400 sans wilaya (BesoinInvalide, corps { erreur }).
- POST /api/dawini/besoins/{id}/reponses (PHARMACIE, 201, body { nomPharmacie, disponible, prixDa, commentaire }) :
  400 si le nom ou la disponibilite manque, prix negatif ou commentaire > 500 ; 404 ; 409 si le besoin est cloture ou si
  cette pharmacie a deja repondu (une reponse par pharmacie et par besoin) ; le patient est prevenu (« Reponse d'une
  pharmacie », « Une pharmacie a repondu a votre demande de medicament. »).
- GET /api/dawini/besoins/{id}/reponses (PATIENT proprietaire, 403 sinon, ou PHARMACIE) : les plus anciennes d'abord ;
  le controleur choisit reponsesPourPatient ou reponsesPourPharmacie selon le role porte par le jeton (Authentication).
- Vues : BesoinVue { id, patientId, medicament, wilayaCode, commune, precision, statut, publieLe, clotureLe,
  nombreReponses } et ReponseVue { id, besoinId, pharmacieId, nomPharmacie, disponible, prixDa, commentaire, repondueLe }.
- Module dawini : BesoinMedicament (record immuable : publier avec validation, cloturer en copie datee, estOuvert,
  estDe), DemandeBesoin, StatutBesoin, ReponsePharmacie (record immuable : repondre avec validation), DemandeReponse,
  ports BesoinRepository (enregistrer, parId, parPatient, ouvertsParWilaya) et ReponseRepository (enregistrer,
  parBesoin, parBesoinEtPharmacie, compterParBesoin), DawiniService (Notifieur) ; exceptions BesoinIntrouvable (404),
  BesoinInvalide et ReponseInvalide (400) dans GestionErreursApi ; cloture double, besoin cloture et double reponse
  sont des conflits (TransitionInvalide, 409).
- Persistance : adaptateurs en memoire (ordre de publication conserve a date egale) et JPA
  (findByWilayaCodeAndStatutOrderByPublieLeDesc, findByBesoinIdAndPharmacieId, countByBesoinId) ; Liquibase 011
  (table besoin_medicament : index patient_id et (wilaya_code, statut) ; table reponse_pharmacie : unique
  (besoin_id, pharmacie_id), index besoin_id).
- Tests : domaine (publication et nettoyage, obligatoires, longueurs bornees, cloture en copie et unique ; reponse
  complete, prix et commentaire facultatifs, nom et disponibilite obligatoires, prix negatif, bornes), service
  (publication, validation, liste triee, cloture 403 / 409 / 404, besoins ouverts par wilaya excluant clotures et autres
  wilayas, wilaya obligatoire, reponse ok + notification sans detail, besoin cloture 409, double reponse 409, reponse
  invalide / besoin inconnu sans rien enregistrer, reponses pour patient tiers 403 et pharmacie) ; web 401 sans jeton,
  PATIENT 201 / 400 / 200 / 409 / 403 / 404, PHARMACIE 200 / 201 / 400 sans wilaya / 409 / 404, MEDECIN 403 sur les
  routes PHARMACIE et PATIENT 403 sur les routes PHARMACIE, patientId absent de la vue pharmacie, reponses selon le role.

## v0.14.0 — Profil
- GET /api/moi/profil (tout utilisateur authentifie) : profil de l'utilisateur connecte, identifie par le sujet de
  son jeton ; 404 (ProfilIntrouvable, corps { erreur }) tant qu'il ne l'a jamais renseigne.
- PUT /api/moi/profil (tout utilisateur authentifie, 200, body { nomComplet, telephone, dateNaissance, wilayaCode,
  langue }) : renseigne ou remplace le profil (un seul par utilisateur, misAJourLe = date de l'appel) ; 400 si une
  regle n'est pas respectee.
- Vue ProfilVue { utilisateurId, nomComplet, telephone, dateNaissance (yyyy-MM-dd), wilayaCode, langue, misAJourLe }.
- Domaine : Profil (record immuable, renseigner avec validation) : nom complet obligatoire de 2 a 120 caracteres ;
  telephone facultatif, chiffres seulement une fois les espaces retires, 9 a 10 chiffres commencant par 0 (mobile
  0550123456 ou fixe 021123456) ; date de naissance facultative, dans le passe a l'heure d'Algerie et posterieure a
  1900 ; wilaya facultative (au plus 4 caracteres) ; langue facultative parmi fr, ar, kab, en (fr par defaut, casse
  ignoree) ; les espaces autour sont retires, les facultatifs blancs effaces. DemandeProfil, port ProfilRepository
  (parUtilisateur, enregistrer = creation ou remplacement), ProfilService (monProfil, enregistrer) ; exceptions
  ProfilInvalide (400) et ProfilIntrouvable (404) dans GestionErreursApi.
- Persistance : adaptateur en memoire (un profil par utilisateur) et JPA (cle = identifiant de l'utilisateur, save
  insere ou met a jour) ; Liquibase 012 (table profil : utilisateur_id cle primaire, nom_complet varchar(120),
  telephone varchar(20), date_naissance date, wilaya_code varchar(4), langue varchar(3), mis_a_jour_le).
- Tests : domaine (profil complet, nettoyage, nom obligatoire et bornes 2 / 120, telephones mobiles et fixes acceptes,
  formats refuses, date de naissance hier / aujourd'hui / demain a l'heure d'Algerie, 1900 refuse et 1901 accepte,
  wilaya bornee, langues et defaut fr, utilisateur et demande obligatoires), service (vide avant, creation datee,
  remplacement sans doublon, un profil par utilisateur, refus sans toucher au profil existant) ; web 401 sans jeton,
  404 sans profil, PUT 200 puis GET 200, accessible a un medecin, 400 invalide.

## v0.15.0 — Liste d'attente
- POST /api/medecins/{id}/liste-attente (PATIENT, 201) : inscription du patient sur la liste d'attente d'un medecin
  (une seule par couple patient / medecin, 409 sinon) ; GET /api/medecins/** reste public mais ce POST exige un
  jeton (le permitAll de SecurityConfig ne porte que sur GET, verifie par le test web).
- GET /api/liste-attente/mes (PATIENT) : mes inscriptions, les plus anciennes d'abord.
- POST /api/liste-attente/{id}/retirer (PATIENT, 204 sans corps) : 404 si inconnue, 403 si elle est a un autre patient.
- GET /api/medecin/liste-attente (MEDECIN) : sa liste d'attente, les plus anciens inscrits d'abord.
- Vue InscriptionVue { id, patientId, medecinId, inscritLe }.
- Alerte de creneau libere : port de domaine AlerteCreneau (creneauLibere(medecinId, debut)) realise par
  ListeAttenteService, qui previent chaque patient inscrit sur la liste du medecin (« Creneau disponible », « Un creneau
  vient de se liberer chez votre medecin le <date>. Reservez vite. »). Branchement : CreneauService.ouvrir l'appelle
  apres enregistrement du creneau ; RendezVousService.annuler l'appelle quand un creneau de l'agenda est effectivement
  remis a disposition (jamais pour un rendez-vous pris hors agenda, ni pour une seconde annulation). Sans cycle Spring :
  listeattente.application ne depend que de ListeAttenteRepository et du Notifieur.
- Module listeattente : InscriptionAttente (record immuable : inscrire, estDe, concerne), port ListeAttenteRepository
  (enregistrer, parId, parPatientEtMedecin, parPatient, parMedecin, supprimer), ListeAttenteService (inscrire,
  mesInscriptions, retirer, listeDuMedecin, creneauLibere) ; exception InscriptionIntrouvable (404) dans
  GestionErreursApi ; le doublon est un conflit (TransitionInvalide, 409).
- Persistance : adaptateur en memoire (ordre d'inscription conserve a date egale) et JPA (findByPatientIdAndMedecinId,
  findByPatientIdOrderByInscritLeAsc, findByMedecinIdOrderByInscritLeAsc, deleteById) ; Liquibase 013 (table
  liste_attente : unique (patient_id, medecin_id), index medecin_id).
- Tests : service (inscription, doublon 409, plusieurs medecins, listes triees par patient et par medecin, retrait puis
  reinscription, retrait 403 / 404, alerte qui previent chaque inscrit du medecin et personne d'autre, medecin sans
  liste, patient retire non prevenu), CreneauServiceTest (alerte a l'ouverture, aucune si le creneau est refuse),
  RendezVousServiceTest (alerte a l'annulation qui libere le creneau ; aucune a la reservation, pour un rendez-vous
  hors agenda, pour une annulation refusee ou une seconde annulation) avec une fausse AlerteCreneau ; web 401 sans
  jeton sur le POST d'inscription malgre le GET public, 403 par role, 201 / 409, 200 liste, 204 sans corps, 403 / 404
  au retrait, 200 / 403 / 401 pour la liste du medecin.

## v0.16.0 — Cabinet
- POST /api/medecin/secretaires (MEDECIN, 201, body { secretaireId }) : rattache une secretaire au cabinet du medecin
  connecte (une seule fois par couple, 409 sinon ; 400 si la secretaire manque ou si le medecin se designe lui-meme) ;
  la secretaire est prevenue (« Rattachement a un cabinet »). GET /api/medecin/secretaires : ses secretaires, les plus
  anciens rattachements d'abord. POST /api/medecin/secretaires/{id}/retirer (204 sans corps ; 404, 403 si le
  rattachement est a un autre medecin) ; la secretaire est prevenue (« Rattachement retire »).
- POST /api/medecin/rendezvous/{id}/annuler (MEDECIN, 200) : le medecin annule lui-meme un rendez-vous confirme de son
  agenda (RendezVousService.annulerParCabinet) : creneau remis a disposition (liste d'attente alertee), patient prevenu
  (« Rendez-vous annule par le cabinet ») ; 403 si autre medecin, 409 si deja annule ou honore, 404 si inconnu.
- SECRETAIRE : GET /api/secretaire/medecins (ses cabinets) ; GET /api/secretaire/medecins/{medecinId}/rendezvous (agenda,
  vue RendezVousVue du module rendezvous, rendue publique) ; POST /api/secretaire/medecins/{medecinId}/creneaux (201,
  body { debut, dureeMinutes }, vue Creneau du module creneaux, 400 si invalide) ; POST /api/secretaire/rendezvous/{id}/honorer
  (200, 409 si non confirme) ; POST /api/secretaire/rendezvous/{id}/annuler (200, 409). Chaque action exige que la
  secretaire soit rattachee au medecin vise ou au medecin du rendez-vous (AccesRefuse, 403) ; 404 si le rendez-vous
  n'existe pas.
- Vue RattachementVue { id, medecinId, secretaireId, creeLe }.
- Domaine rendezvous : RendezVous.annulerParCabinet() (uniquement depuis CONFIRME, a la difference de l'annulation par
  le patient qui reste idempotente) ; RendezVousService.parId(id) (404) et annulerParCabinet(medecinId, id).
- Module cabinet : Rattachement (record immuable : rattacher avec validation, concerneMedecin, concerneSecretaire), port
  RattachementRepository (enregistrer, parId, parMedecinEtSecretaire, parMedecin, parSecretaire, supprimer),
  CabinetService (RattachementRepository, RendezVousService, CreneauService, Notifieur : rattacher, secretairesDuMedecin,
  retirer, medecinsDeLaSecretaire, verifierAcces, agendaPour, ouvrirCreneauPour, honorerPour, annulerPour) ; exceptions
  RattachementIntrouvable (404) et CabinetInvalide (400) dans GestionErreursApi ; le doublon est un conflit (409).
- Persistance : adaptateur en memoire (ordre de rattachement conserve a date egale) et JPA (findByMedecinIdAndSecretaireId,
  findByMedecinIdOrderByCreeLeAsc, findBySecretaireIdOrderByCreeLeAsc, deleteById) ; Liquibase 014 (table
  rattachement_secretaire : unique (medecin_id, secretaire_id), index secretaire_id).
- Keycloak : compte de demonstration secretaire.demo / secretaire (55555555-5555-5555-5555-555555555555, role SECRETAIRE)
  dans infra/keycloak/tabibi-realm.json ; README (endpoints MEDECIN et SECRETAIRE, comptes de demo, notifications, structure).
- Tests : CabinetServiceTest (rattachement et notification, doublon 409, soi-meme et sans secretaire 400, plusieurs
  cabinets tries, retrait et notification puis acces refuse, retrait 403 / 404, acces refuse 403 pour une secretaire non
  rattachee, agenda, ouverture de creneau avec alerte et refus 403 / 400, honorer 200 / 409 / 403 / 404, annulation par
  le cabinet qui libere le creneau, alerte la liste et previent le patient, refus 403 / 409 / 404),
  RendezVousServiceTest (parId, annulerParCabinet : creneau libere, alerte, patient prevenu ; refus si annule, honore,
  autre medecin, inconnu), CabinetWebTest (401 sur toutes les routes, PATIENT 403, MEDECIN et SECRETAIRE croises 403,
  201 / 400 / 409 au rattachement, 200 liste, 204 sans corps au retrait, 404 / 403, SECRETAIRE 200 cabinets, agenda
  200 / 403, creneau 201 / 400 / 403, honorer 200 / 409, annuler 200 / 409 / 403 / 404), RendezVousWebTest
  (POST /api/medecin/rendezvous/{id}/annuler : 200, 401, PATIENT 403, 409 / 403 / 404).

## v0.17.0 — Rappels
- Un rappel de rendez-vous, une seule fois, par rendez-vous confirme dont le debut est dans les 24 prochaines heures :
  le patient est prevenu (« Rappel de rendez-vous », « Votre rendez-vous du <date> est demain. Pensez a vous presenter
  10 minutes en avance. ») et le rendez-vous est marque (rappelEnvoyeLe = heure de l'execution). Les rendez-vous
  annules ou honores, passes, plus lointains ou deja rappeles sont ignores ; fenetre [maintenant, maintenant + 24 h[.
- RappelService (module rappels, application : RendezVousRepository, Notifieur, java.time.Clock) : int executer()
  renvoie le nombre de rappels envoyes ; l'horloge est un bean de config/HorlogeConfig (Clock.systemUTC), remplacee par
  Clock.fixed dans les tests.
- PlanificateurRappels (module rappels, adapter : @Component, @Scheduled cron "0 0 * * * *", toutes les heures) appelle
  executer() et journalise le seul nombre envoye ; conditionne par tabibi.rappels.actifs (matchIfMissing = true) ;
  application.yml : tabibi.rappels.actifs: ${TABIBI_RAPPELS_ACTIFS:true}. config/PlanificationConfig porte
  @EnableScheduling, dans une configuration a part que les @WebMvcTest (qui n'importent que SecurityConfig) ne chargent
  pas.
- POST /api/admin/rappels/executer (ADMIN, 200) : declenchement manuel, corps { nombre }.
- Domaine rendezvous : RendezVous.rappelEnvoyeLe (nullable) + marquerRappelEnvoye(Instant) + rappelEnvoye() ; nouveau
  constructeur a 7 parametres, les constructeurs existants sont conserves ; port RendezVousRepository.
  confirmesSansRappelEntre(de, a) (memoire : filtre et tri par debut ; JPA : requete derivee
  findByStatutAndRappelEnvoyeLeIsNullAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut, le statut de l'entite etant
  un enum en chaine) ; entite JPA mise a jour ; Liquibase 015 (colonne rendez_vous.rappel_envoye_le timestamptz nullable).
- README (endpoint ADMIN, section Rappels et propriete tabibi.rappels.actifs, notifications, structure).
- Tests : RappelServiceTest a horloge fixe (rendez-vous dans 2 h rappele et marque avec le message attendu, dans 30 h
  ignore, bornes de la fenetre : a l'instant inclus, 23 h 59 inclus, 24 h exclu, passe exclu ; deja rappele ignore et
  date intacte ; annule et honore ignores ; deux executions n'envoient qu'une fois ; rien a envoyer), RappelWebTest
  (401 sans jeton, PATIENT et MEDECIN 403, ADMIN 200 { nombre }).

## v0.18.0 — CORS
- Le front web (Angular, http://localhost:4200 en dev) appelle l'API depuis le navigateur : sans CORS tout appel est
  bloque. SecurityConfig ajoute http.cors(...) avec un CorsConfigurationSource (bean corsConfigurationSource) sur
  /api/** : origines de la propriete tabibi.cors.origines, methodes GET/POST/PUT/DELETE/OPTIONS, en-tetes Authorization
  et Content-Type, credentials false (API sans etat, jeton en en-tete), max-age 3600. Une origine ou une methode non
  autorisee est refusee (403, sans en-tete Access-Control-Allow-Origin) avant d'atteindre l'API.
- config/CorsProprietes : liste d'origines lue par @Value("${tabibi.cors.origines:http://localhost:4200}") (valeur par
  defaut robuste : la propriete est facultative), blancs et entrees vides retires ; enregistree par @Import depuis
  SecurityConfig, donc presente dans les @WebMvcTest existants qui n'importent que SecurityConfig, sans rien changer
  a ces tests. application.yml : tabibi.cors.origines: ${TABIBI_CORS_ORIGINES:http://localhost:4200} (liste separee
  par des virgules).
- README : section Configuration (variables d'environnement, dont TABIBI_CORS_ORIGINES), CORS dans Securite, structure.
- Tests : CorsWebTest (@WebMvcTest sur AnnuaireController, public : preflight OPTIONS depuis http://localhost:4200
  avec Access-Control-Request-Method GET repond 200 avec Access-Control-Allow-Origin, Allow-Methods, Allow-Headers
  Authorization, Max-Age 3600 et sans Allow-Credentials, sans atteindre le controleur ; origine inconnue et methode
  PATCH refusees 403 sans en-tete ; requete simple GET depuis le front avec l'en-tete, depuis une origine inconnue
  refusee), CorsProprietesTest (nettoyage des blancs et entrees vides, liste vide sans origine, configuration CORS
  construite par SecurityConfig : chemin /api/** seul, origines, methodes, en-tetes, credentials false, max-age).

## v0.19.0 — Journal des acces
- Exigence de sante : tracer qui accede a quoi. Module audit : EntreeAudit (record : id, sujet nullable si anonyme,
  methode, chemin, statut, adresseIp, horodatage, dureeMs ; nouvelle(...), anonyme()), AdresseIp.tronquer (IPv4 au
  dernier octet, IPv6 aux 64 premiers bits, IPv4 projetee traitee comme une IPv4, null pour toute valeur qui n'est
  pas une adresse, sans resolution DNS), port AuditRepository (enregistrer, recents(limite), parSujet(sujet, limite),
  les plus recentes d'abord), AuditService (enregistrer, recents, parSujet ; limite ramenee entre 1 et 1000 par
  borner()).
- FiltreAudit (adapter, OncePerRequestFilter) : journalise chaque requete /api/** (jamais /actuator/**) apres la
  chaine de Spring Security ; sujet lu dans le SecurityContext (null si anonyme ou si le nom n'est pas un UUID),
  chemin sans chaine de requete abrege a 512 caracteres, statut de la reponse (ou celui que produira une erreur
  remontee : 403 pour AccessDeniedException, 401 pour AuthenticationException, 500 sinon, en remontant les causes),
  IP tronquee ; le corps et les parametres ne sont jamais lus ; un echec d'ecriture est avale (warn sans donnee de
  la requete) et ne fait jamais echouer la requete. Enregistre par AuditConfig (@Configuration a part, non importee
  par les @WebMvcTest) via FilterRegistrationBean a l'ordre SecurityProperties.DEFAULT_FILTER_ORDER + 1 ; le filtre
  n'est pas un @Component, les slices web existantes ne le chargent pas. Les requetes refusees par la securite
  elle-meme (401, 403 du verrou /api/admin/**) ne sont pas journalisees comme acces (documente).
- GET /api/admin/audit?limite=100 et GET /api/admin/audit/sujet/{id}?limite=100 (ADMIN) : vue EntreeAuditVue
  { id, sujet, methode, chemin, statut, adresseIp, horodatage, dureeMs }.
- Persistance : EnMemoireAuditRepository borne (10 000 entrees par defaut, capacite parametrable pour les tests ; a
  date egale la derniere enregistree vient en premier) et JpaAuditRepository (Pageable, findAllByOrderByHorodatageDesc,
  findBySujetOrderByHorodatageDesc) ; Liquibase 016 (table journal_acces, index horodatage et (sujet, horodatage)).
- README (Securite, endpoints ADMIN, section Journal des acces, structure).
- Tests : AdresseIpTest (IPv4, IPv6, zone, IPv4 projetee, valeurs absentes ou invalides sans exception),
  AuditServiceTest (entree nouvelle et anonyme, validations, ordre le plus recent d'abord et a date egale, limites,
  par sujet, bornes 1..1000, journal en memoire borne et capacite), FiltreAuditTest (MockHttpServletRequest/Response
  et faux repository : entree complete avec sujet et IP tronquee, sans jeton, anonyme Spring, sujet non UUID, corps
  et parametres jamais lus grace a un wrapper qui echoue a la lecture et requete transmise telle quelle, supervision
  et hors API ignores, journal en panne avale, refus @PreAuthorize journalise 403 et remonte, 401 et 500, chemin
  abrege), AuditWebTest (401 ; PATIENT et MEDECIN 403 ; ADMIN 200 avec la vue complete, limite par defaut 100 et
  transmise, sujet nul pour un acces anonyme, acces d'un utilisateur).

## v0.20.0 — Image de production et orchestration
- Dockerfile multi-etapes : construction du jar avec maven:3.9-eclipse-temurin-21 (couche dependency:go-offline
  reutilisee tant que le pom ne change pas, puis mvn -B -q -DskipTests package), execution sur
  eclipse-temurin:21-jre-alpine avec un utilisateur sans privilege (tabibi), EXPOSE 8080, HEALTHCHECK wget sur
  /actuator/health, JAVA_TOOL_OPTIONS -XX:MaxRAMPercentage=75.0, ENTRYPOINT ["java","-jar","/app/app.jar"] ;
  .dockerignore (target, .git, .github, docs, infra, compose, .env, *.md).
- docker-compose.prod.yml : postgres (16-alpine, volume tabibi-pg, healthcheck pg_isready, script
  infra/postgres/init/01-keycloak.sh qui cree le role et la base keycloak a la premiere initialisation), keycloak
  (26.0, start --import-realm, KC_DB=postgres vers la base keycloak dediee, KC_HOSTNAME=https://auth.<domaine>,
  KC_HTTP_ENABLED=true, KC_PROXY_HEADERS=xforwarded, KC_HEALTH_ENABLED, administrateur par variables, realm monte
  depuis infra/keycloak), backend (ghcr.io/<org>/tabibi-backend:<tag>, SPRING_PROFILES_ACTIVE=postgres,
  SPRING_DATASOURCE_*, TABIBI_KEYCLOAK_ISSUER public + cles lues en interne par
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI, TABIBI_CORS_ORIGINES, TABIBI_TELECONSULTATION_BASE_URL,
  SERVER_FORWARD_HEADERS_STRATEGY=native ; depends_on postgres sain), web (ghcr.io/<org>/tabibi-web:<tag>, depot
  tabibi-web), caddy (2-alpine, seuls ports exposes 80/443 + 443/udp, volumes caddy-data et caddy-config,
  infra/caddy/Caddyfile : <domaine> -> web:80, api.<domaine> -> backend:8080, auth.<domaine> -> keycloak:8080, HSTS,
  nosniff, Referrer-Policy, en-tete Server retire) ; reseau interne ; .env.example documente (DOMAINE, ACME_EMAIL,
  ORG_GITHUB, BACKEND_TAG, WEB_TAG, POSTGRES_PASSWORD, KEYCLOAK_DB_PASSWORD, KEYCLOAK_ADMIN_USERNAME/PASSWORD,
  TABIBI_TELECONSULTATION_BASE_URL) ; .env ignore par git.
- infra/sauvegarde/pg_dump.sh : sauvegarde des bases tabibi et keycloak (pg_dump format custom compresse, fichier
  horodate par base, repertoire /var/backups/tabibi en chmod 700), rotation 14 jours, ligne cron et commande de
  restauration en en-tete.
- application-postgres.yml : datasource depuis SPRING_DATASOURCE_URL / USERNAME / PASSWORD avec les defauts locaux
  (remplace TABIBI_DB_*), server.forward-headers-strategy: ${SERVER_FORWARD_HEADERS_STRATEGY:native},
  management.endpoint.health.probes.enabled: true ; SecurityConfig ouvre /actuator/health/** (sondes liveness et
  readiness, sans detail) en plus de /actuator/health.
- docker-compose.yml de dev : service backend en option commentee (image locale, cles Keycloak lues en interne).
- README : section Deploiement (image, orchestration, variables, lancement, verification de /actuator/health,
  mise a jour, sauvegardes), Configuration (SPRING_PROFILES_ACTIVE, SPRING_DATASOURCE_*,
  SERVER_FORWARD_HEADERS_STRATEGY), Persistance, Lancer en local, Journal des acces (proxy).
- Tests : SecuriteWebTest (la sante et ses sondes liveness / readiness ne repondent jamais 401 ni 403 sans jeton,
  le reste de la supervision reste protege).

