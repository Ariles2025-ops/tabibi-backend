-- 027 : reclasse les fiches « autre » d'apres un mot-cle lu dans TOUT le nom (023/024 ne lisaient que le segment
-- apres la virgule et n'ont presque rien reclasse : ~20 000 fiches « autre » restaient).
-- Exemples reels : « Pharmacie HEBA » -> pharmacie ; « EPH SIDI AISSA » -> hopital ; « Cabinet Dentaire Vieux Kouba »
-- -> dentiste ; « Dr bouayad cardiologue » -> cardiologue ; « CENTRE D'HEMODIALYSE SIDI BRAHIM » -> centre-sante.
--
-- GARANTIES :
--   * seules les lignes specialite_slug = 'autre' changent (aucun DELETE, nom_complet jamais modifie) ;
--   * slugs et libelles = ceux deja en base (referentiel du site) ;
--   * veto : tout ce qui n'est pas un lieu de soin (scanner AUTO, association, institut/ecole de formation...) reste « autre » ;
--   * une fiche de praticien qui cite PLUSIEURS specialites differentes reste « autre » ;
--     pour un etablissement (pharmacie, labo, hopital, clinique...), on garde le type le plus precis (ordre « prio »).
-- Compte a blanc sur la base de production le 17/11/2026 : 3 278 fiches reclassees, ~16 800 restent « autre »
-- (simples noms de medecin sans indice : a completer plus tard avec une autre source).
-- Idempotente : rejouee, elle ne change plus rien.
with r(prio, slug, nom, etab, motif) as (values
    (1,  'pharmacie',          'Pharmacie',                true,  'pharmac'),
    (2,  'laboratoire',        'Laboratoire d''analyses',  true,  '\ylaboratoire\y|analyses m[eé]dicales|\ylabo\y|biologie m[eé]dicale'),
    (3,  'opticien',           'Opticien',                 true,  'optique|opticien'),
    (4,  'orthodontiste',      'Orthodontiste',            false, 'orthodont'),
    (5,  'dentiste',           'Dentiste',                 false, 'dentaire|dentiste|stomatolog|odontolog'),
    (6,  'hopital',            'Hôpital',                  true,  'h[oô]pital|\yeph\y|\ychu\y|\yehs\y|\yepsp\y'),
    (7,  'centre-sante',       'Centre de santé',          true,  'h[eé]modialy|dialyse|centre de sant[eé]|salle de soins?\y|\ypmi\y|\ycrrf\y'),
    (8,  'clinique-privee',    'Clinique privée',          true,  'clinique'),
    (9,  'radiologue',         'Radiologue',               false, 'radiolog|imagerie m[eé]dicale'),
    (10, 'kinesitherapeute',   'Kinésithérapeute',         false, 'kin[eé]sith|\ykin[eé]\y|physioth|r[eé][eé]ducation fonctionnelle'),
    (11, 'orthophoniste',      'Orthophoniste',            false, 'orthophon'),
    (12, 'chirurgien',         'Chirurgien général',       false, 'chirurgie g[eé]n[eé]rale|chirurgien g[eé]n[eé]ral'),
    (13, 'gynecologue',        'Gynécologue',              false, 'gyn[eé]co|obst[eé]tri'),
    (14, 'pediatre',           'Pédiatre',                 false, 'p[eé]diat'),
    (15, 'ophtalmologue',      'Ophtalmologue',            false, 'ophtalm'),
    (16, 'cardiologue',        'Cardiologue',              false, 'cardiolog|\ycardio\y'),
    (17, 'psychiatre',         'Psychiatre',               false, 'psychiat'),
    (18, 'ORL',                'ORL',                      false, '\yorl\y|oto.?rhino'),
    (19, 'dermatologue',       'Dermatologue',             false, 'dermato'),
    (20, 'endocrinologue',     'Endocrinologue',           false, 'endocrin|diab[eé]to'),
    (21, 'neurologue',         'Neurologue',               false, 'neurolog'),
    (22, 'urologue',           'Urologue',                 false, 'urolog'),
    (23, 'pneumologue',        'Pneumologue',              false, 'pneumolog|\ypneumo\y'),
    (24, 'rhumatologue',       'Rhumatologue',             false, 'rhumato'),
    (25, 'gastro-enterologue', 'Gastro-entérologue',       false, 'gastro'),
    (26, 'nephrologue',        'Néphrologue',              false, 'n[eé]phrolog'),
    (27, 'orthopediste',       'Orthopédiste',             false, 'orthop[eé]di|traumatolog'),
    (28, 'nutritionniste',     'Nutritionniste',           false, 'nutritionn|di[eé]t[eé]ticien'),
    (29, 'medecin-generaliste','Médecin généraliste',      false, 'm[eé]decine g[eé]n[eé]rale|g[eé]n[eé]raliste')
),
candidats as (
    select m.id, r.prio, r.slug, r.nom, r.etab
    from medecin m
    join r on lower(m.nom_complet) ~ r.motif
    where m.specialite_slug = 'autre'
      and lower(m.nom_complet) !~ '\y(auto|automobile|voiture|association|institut|formation|[eé]cole|universit[eé]|facult[eé]|syndicat|v[eé]t[eé]rinaire)\y'
),
choix as (
    select id,
           (array_agg(slug order by prio))[1] as slug,
           (array_agg(nom order by prio))[1] as nom,
           bool_or(etab) as etab,
           count(distinct slug) filter (where not etab) as nb_specialites
    from candidats
    group by id
)
update medecin m
set specialite_slug = choix.slug,
    specialite_fr = choix.nom
from choix
where m.id = choix.id
  and m.specialite_slug = 'autre'
  and (choix.etab or choix.nb_specialites = 1);
