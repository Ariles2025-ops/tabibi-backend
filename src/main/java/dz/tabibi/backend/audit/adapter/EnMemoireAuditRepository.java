package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.domain.AuditRepository;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Adaptateur de persistance en memoire du journal des acces (dev/tests, hors profil postgres),
 * borne : au-dela de la capacite, les entrees les plus anciennes sont oubliees.
 */
@Repository
@Profile("!postgres")
public class EnMemoireAuditRepository implements AuditRepository {

    public static final int CAPACITE_DEFAUT = 10_000;

    /** De la plus recente a la plus ancienne (tri stable : a date egale, l'ordre d'arrivee inverse est conserve). */
    private static final Comparator<EntreeAudit> PLUS_RECENTE_D_ABORD =
            Comparator.comparing(EntreeAudit::horodatage, Comparator.reverseOrder());

    private final int capacite;
    private final Deque<EntreeAudit> entrees = new ArrayDeque<>();

    public EnMemoireAuditRepository() {
        this(CAPACITE_DEFAUT);
    }

    public EnMemoireAuditRepository(int capacite) {
        if (capacite < 1) {
            throw new IllegalArgumentException("La capacite du journal doit etre d'au moins une entree.");
        }
        this.capacite = capacite;
    }

    @Override
    public synchronized EntreeAudit enregistrer(EntreeAudit entree) {
        entrees.addLast(entree);
        while (entrees.size() > capacite) {
            entrees.removeFirst();
        }
        return entree;
    }

    @Override
    public synchronized List<EntreeAudit> recents(int limite) {
        return plusRecentesDAbord().limit(limite).toList();
    }

    @Override
    public synchronized List<EntreeAudit> parSujet(UUID sujet, int limite) {
        return plusRecentesDAbord().filter(e -> sujet.equals(e.sujet())).limit(limite).toList();
    }

    private Stream<EntreeAudit> plusRecentesDAbord() {
        List<EntreeAudit> copie = new ArrayList<>(entrees);
        Collections.reverse(copie);
        copie.sort(PLUS_RECENTE_D_ABORD);
        return copie.stream();
    }
}
