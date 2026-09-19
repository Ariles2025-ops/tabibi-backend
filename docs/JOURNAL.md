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
