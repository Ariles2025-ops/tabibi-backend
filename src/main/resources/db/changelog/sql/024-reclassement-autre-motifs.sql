-- 024 : suite de 023. Reclasse davantage de fiches « autre » en lisant la specialite dans le dernier segment
-- apres la virgule du nom (virgule latine « , » ou arabe « ، »), avec des variantes et fautes courantes
-- en francais (cardio, gyneco, kine, ORL, orl, « chirurgien-dentiste »...) et en arabe (طبيب أسنان, طبيب أطفال...).
--   « Benali Karim, Gynéco-Obstétrique À Alger »  ->  « Benali Karim », gynecologue, « Gynécologue ».
--
-- GARANTIES (« sans jamais se tromper » : en cas de doute la fiche reste « autre ») :
--   * seules les lignes specialite_slug = 'autre' sont modifiees (aucune autre ligne, aucun DELETE) ;
--   * les slugs ci-dessous sont EXACTEMENT ceux deja utilises par 023 (referentiel du site) ;
--   * une fiche qui correspond a PLUSIEURS specialites differentes reste « autre » ;
--   * pharmacies, laboratoires, opticiens, cliniques, hopitaux, centres : jamais touches par 024 (liste de veto) ;
--   * veto aussi : chirurgiens (hors chirurgien-dentiste), infirmiers, techniciens, prothesistes, psychologues,
--     internes/residents/stagiaires, neuro-chirurgie/neuro-pediatrie, pedopsychiatrie, etc. ;
--   * le segment doit faire 8 mots au plus (sinon ce n'est pas un simple titre) ;
--   * le motif doit etre un MOT entier (pas de sous-chaine : « cardio » ne reconnait pas « cardiop... »).
-- Pas de slug dans le referentiel pour : psychologue, laboratoire d'analyses, chirurgien-dentiste est mappe a « dentiste »
-- (comme 023) -> psychologues et laboratoires restent « autre » (voulu).
--
-- LIGNES ATTENDUES : non estimable sans acces a la base de production (~20 000 fiches « autre » restantes).
-- Pour compter a blanc avant deploiement, remplacer « update medecin m set ... from » par « select count(*) from »
-- en gardant les CTE et la jointure. Ordre de grandeur espere : quelques milliers au plus ; verifier le chiffre reel.
-- Idempotente : rejouee, elle ne change plus rien (les fiches reclassees ne sont plus « autre »).
with motifs(motif, slug, nom) as (values
    ('cardio|cardiologue|cardiologues|cardiologie|cardiolog', 'cardiologue', 'Cardiologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?قلب', 'cardiologue', 'Cardiologue'),
    ('gyneco|gynecologue|gynecologues|gynecologie|gynecolog|obstetricien|obstetricienne', 'gynecologue', 'Gynécologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?(نساء|نسائي|نسائيه)', 'gynecologue', 'Gynécologue'),
    ('ophtalmo|ophtalmologue|ophtalmologues|ophtalmologie|ophtalmologiste|ophtalmologistes', 'ophtalmologue', 'Ophtalmologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?عيون', 'ophtalmologue', 'Ophtalmologue'),
    ('orl|oto ?rhino( ?laryngolog(iste|istes|ue|ie))?', 'ORL', 'ORL'),
    ('(ال)?انف و (ال)?اذن( و (ال)?حنجره)?', 'ORL', 'ORL'),
    ('pediatre|pediatres|pediatrie', 'pediatre', 'Pédiatre'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?اطفال', 'pediatre', 'Pédiatre'),
    ('dentiste|dentistes', 'dentiste', 'Dentiste'),
    ('(جراح|جراحه|طبيب|طبيبه|اخصائي|اخصائيه|طب)( في)? (ال)?اسنان', 'dentiste', 'Dentiste'),
    ('kine|kines|kinesitherapeute|kinesitherapeutes|kinesitherapie|physiotherapeute', 'kinesitherapeute', 'Kinésithérapeute'),
    ('(اخصائي|اخصائيه|طبيب|طبيبه)( في)? علاج طبيعي|علاج طبيعي', 'kinesitherapeute', 'Kinésithérapeute'),
    ('psychiatre|psychiatres|psychiatrie', 'psychiatre', 'Psychiatre'),
    ('(امراض|طب) (ال)?عقليه', 'psychiatre', 'Psychiatre'),
    ('dermato|dermatologue|dermatologues|dermatologie|dermatologiste|venerologue', 'dermatologue', 'Dermatologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?جلد(يه)?', 'dermatologue', 'Dermatologue'),
    ('radiologue|radiologues|radiologie|radiologiste', 'radiologue', 'Radiologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب)( في)? (ال)?(اشعه|راديولوجيا)|راديولوجي|راديولوجيا', 'radiologue', 'Radiologue'),
    ('nephro|nephrologue|nephrologues|nephrologie', 'nephrologue', 'Néphrologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?(كلي|كليه)', 'nephrologue', 'Néphrologue'),
    ('pneumo|pneumologue|pneumologues|pneumologie|phtisiologue|pneumo phtisiologue', 'pneumologue', 'Pneumologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?(صدر|رئه)', 'pneumologue', 'Pneumologue'),
    ('gastro|gastro enterologue|gastroenterologue|gastro enterologie|gastroenterologie|hepato gastro', 'gastro-enterologue', 'Gastro-entérologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?جهاز (ال)?هضمي', 'gastro-enterologue', 'Gastro-entérologue'),
    ('neuro|neurologue|neurologues|neurologie', 'neurologue', 'Neurologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?(اعصاب|عصبيه)', 'neurologue', 'Neurologue'),
    ('rhumato|rhumatologue|rhumatologues|rhumatologie', 'rhumatologue', 'Rhumatologue'),
    ('(طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?(روماتيزم|مفاصل)', 'rhumatologue', 'Rhumatologue'),
    ('endocrino|endocrino diabeto|endocrinologue|endocrinologues|endocrinologie', 'endocrinologue', 'Endocrinologue'),
    ('غدد صماء|(ال)?غدد (ال)?صماء', 'endocrinologue', 'Endocrinologue'),
    ('urologue|urologues|urologie', 'urologue', 'Urologue'),
    ('(جراح|جراحه|طبيب|طبيبه|اخصائي|اخصائيه|طب|امراض)( في)?( امراض)? (ال)?مسالك (ال)?بوليه', 'urologue', 'Urologue'),
    ('orthodontiste|orthodontistes|orthodontie', 'orthodontiste', 'Orthodontiste'),
    ('تقويم (ال)?اسنان', 'orthodontiste', 'Orthodontiste'),
    ('sage femme|sages femmes|sagefemme|sagesfemmes', 'sage-femme', 'Sage-femme'),
    ('قابله|قابلات', 'sage-femme', 'Sage-femme')
),
brut as (
    select m.id,
           m.nom_complet,
           btrim(regexp_replace(m.nom_complet, '\s*[,،][^,،]*$', '')) as nom,
           -- dernier segment apres la virgule, sans « À <lieu> »
           regexp_replace(substring(m.nom_complet from '[,،]([^,،]*)$'), '\s+[àÀaA]\s+.*$', '') as seg,
           m.nom_complet as tout
    from medecin m
    where m.specialite_slug = 'autre'
      and m.nom_complet ~ '[,،]'
),
norm as (
    select b.id, b.nom,
           -- minuscules sans accents, hamza/ta marbuta/alif maqsura arabes unifies, tout non-alphanumerique -> espace,
           -- « chirurgien(-)dentiste » ramene a « dentiste » (seul chirurgien accepte)
           ' ' || btrim(regexp_replace(regexp_replace(
               translate(translate(lower(translate(b.seg, 'ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ', 'àâäéèêëîïôöùûüç')),
                   'àâäéèêëîïôöùûüç', 'aaaeeeeiioouuuc'), 'أإآةىـ', 'اااهي'),
               '[^a-z0-9ء-ي]+', ' ', 'g'),
               'chirurgiens? dentistes?|chirurgie dentaire', 'dentiste', 'g')) || ' ' as s,
           ' ' || btrim(regexp_replace(regexp_replace(
               translate(translate(lower(translate(b.tout, 'ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ', 'àâäéèêëîïôöùûüç')),
                   'àâäéèêëîïôöùûüç', 'aaaeeeeiioouuuc'), 'أإآةىـ', 'اااهي'),
               '[^a-z0-9ء-ي]+', ' ', 'g'),
               'chirurgiens? dentistes?|chirurgie dentaire', 'dentiste', 'g')) || ' ' as tout
    from brut b
),
sains as (
    select n.*
    from norm n
    where length(n.nom) >= 2
      and array_length(regexp_split_to_array(btrim(n.s), ' '), 1) <= 8
      -- veto : non-medecins, chirurgiens, etudiants, pharmacie/labo/optique/etablissements, surspecialites sans slug
      and n.tout !~ ('chirurg|infirmi|assistant|technicien|prothesi|secretaire|aide soign|manipulateur|preparateur|ambulan'
          || '|psycholog|orthopt|psychomot|dietet|interne|resident|stagiaire|etudiant|pedo ?psy|neuro ?(pedi|psy)|infanto'
          || '|neonat|geriat|andro|sexo|legal|legiste|nucleaire|esthetique|plastique|reanim|urgence|pharmac|laborat|analyses'
          || '|optiq|opticien|clinique|hopital|centre|institut|etablissement|(^| )(chu|ehs|epsp|ets)( |$)'
          || '|ممرض|مساعد|تقني|مخبر|صيدل|مستشفي|مركز|معهد|نفس|طالب|متربص|مقيم|بصريات|نظارات'
          || '|جراح (ال)?(قلب|اعصاب|مخ|صدر|عظام|تجميل|كلي|مسالك)')
),
retenus as (
    -- exactement UNE specialite distincte ; plusieurs -> la fiche reste « autre »
    select s.id, s.nom, min(mo.slug) as slug, min(mo.nom) as specialite_fr
    from sains s
    join motifs mo on s.s ~ (' (' || mo.motif || ') ')
    group by s.id, s.nom
    having count(distinct mo.slug) = 1
)
update medecin m
set specialite_slug = r.slug,
    specialite_fr   = r.specialite_fr,
    nom_complet     = r.nom,
    nom_tri         = lower(regexp_replace(r.nom, '^[^[:alpha:]]+', ''))
from retenus r
where m.id = r.id
  and m.specialite_slug = 'autre';
