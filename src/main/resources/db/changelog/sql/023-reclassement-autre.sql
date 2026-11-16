-- 023 : reclasse les fiches « autre » dont la specialite figure en fin de nom importe :
--   « Abbassen Malika, Cardiologue À Tizi Ouzou »  ->  « Abbassen Malika », cardiologue, « Cardiologue ».
-- Seuls les libelles qui correspondent exactement a une specialite du site sont reclasses ; les autres
-- (ex. « Médecin Interniste ») restent « autre », inchanges. Non destructif : UPDATE de 4 colonnes, aucune
-- ligne supprimee. Comparaison sans casse, sans accents, tirets et espaces confondus.
with libelles(libelle, slug, nom) as (values
    ('medecin generaliste', 'medecin-generaliste', 'Médecin généraliste'),
    ('cardiologue', 'cardiologue', 'Cardiologue'),
    ('gynecologue', 'gynecologue', 'Gynécologue'),
    ('chirurgien dentiste', 'dentiste', 'Dentiste'),
    ('dentiste', 'dentiste', 'Dentiste'),
    ('ophtalmologue', 'ophtalmologue', 'Ophtalmologue'),
    ('psychiatre', 'psychiatre', 'Psychiatre'),
    ('orl', 'ORL', 'ORL'),
    ('pediatre', 'pediatre', 'Pédiatre'),
    ('urologue', 'urologue', 'Urologue'),
    ('gastro enterologue', 'gastro-enterologue', 'Gastro-entérologue'),
    ('gastroenterologue', 'gastro-enterologue', 'Gastro-entérologue'),
    ('allergologue', 'allergologue', 'Allergologue'),
    ('anesthesiste', 'anesthesiste', 'Anesthésiste-réanimateur'),
    ('anesthesiste reanimateur', 'anesthesiste', 'Anesthésiste-réanimateur'),
    ('chirurgien', 'chirurgien', 'Chirurgien général'),
    ('chirurgien general', 'chirurgien', 'Chirurgien général'),
    ('chirurgien orthopediste', 'chirurgien-orthopediste', 'Chirurgien orthopédiste'),
    ('dermatologue', 'dermatologue', 'Dermatologue'),
    ('endocrinologue', 'endocrinologue', 'Endocrinologue'),
    ('hematologue', 'hematologue', 'Hématologue'),
    ('kinesitherapeute', 'kinesitherapeute', 'Kinésithérapeute'),
    ('medecin du sport', 'medecin-sport', 'Médecin du sport'),
    ('medecin du travail', 'medecin-travail', 'Médecin du travail'),
    ('neurologue', 'neurologue', 'Neurologue'),
    ('nutritionniste', 'nutritionniste', 'Nutritionniste'),
    ('nephrologue', 'nephrologue', 'Néphrologue'),
    ('oncologue', 'oncologue', 'Oncologue'),
    ('opticien', 'opticien', 'Opticien'),
    ('orthodontiste', 'orthodontiste', 'Orthodontiste'),
    ('orthophoniste', 'orthophoniste', 'Orthophoniste'),
    ('orthopediste', 'orthopediste', 'Orthopédiste'),
    ('pneumologue', 'pneumologue', 'Pneumologue'),
    ('podologue', 'podologue', 'Podologue'),
    ('radiologue', 'radiologue', 'Radiologue'),
    ('rhumatologue', 'rhumatologue', 'Rhumatologue'),
    ('sage femme', 'sage-femme', 'Sage-femme')
),
candidats as (
    select m.id,
           btrim(regexp_replace(m.nom_complet, '\s*,[^,]*$', '')) as nom,
           btrim(regexp_replace(
               translate(lower(translate(
                   -- dernier segment apres la virgule, sans « À <lieu> »
                   regexp_replace(substring(m.nom_complet from ',([^,]*)$'), '\s+[àÀaA]\s+.*$', ''),
                   'ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ', 'àâäéèêëîïôöùûüç')),
                   'àâäéèêëîïôöùûüç', 'aaaeeeeiioouuuc'),
               '[\s-]+', ' ', 'g')) as libelle
    from medecin m
    where m.specialite_slug = 'autre'
      and m.nom_complet like '%,%'
)
update medecin m
set specialite_slug = l.slug,
    specialite_fr   = l.nom,
    nom_complet     = c.nom,
    nom_tri         = lower(regexp_replace(c.nom, '^[^[:alpha:]]+', ''))
from candidats c
join libelles l on l.libelle = c.libelle
where m.id = c.id
  and length(c.nom) >= 2;
