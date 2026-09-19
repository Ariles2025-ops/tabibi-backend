package dz.tabibi.backend.ordonnances;

import dz.tabibi.backend.ordonnances.adapter.OpenPdfGenerateurOrdonnance;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.StatutOrdonnance;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'adaptateur OpenPDF + ZXing produit un vrai document PDF, entierement en memoire, pour une
 * ordonnance de plusieurs lignes comme pour une ordonnance annulee.
 */
class OpenPdfGenerateurOrdonnanceTest {

    private static final String URL = "https://tabibi.example/verifier?code=AB23CD45";

    private final OpenPdfGenerateurOrdonnance generateur = new OpenPdfGenerateurOrdonnance();

    private static Ordonnance ordonnance(StatutOrdonnance statut) {
        return new Ordonnance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                List.of(new LigneOrdonnance("Paracetamol 1 g", "1 comprime matin et soir", "5 jours"),
                        new LigneOrdonnance("Amoxicilline 500 mg", "1 gelule toutes les 8 heures", "7 jours")),
                Instant.parse("2026-09-18T10:00:00Z"), "AB23CD45", statut);
    }

    @Test
    void produit_un_document_pdf_complet() {
        byte[] pdf = generateur.generer(ordonnance(StatutOrdonnance.EMISE), "Dr Amina Belkacem", "Nadia Saidi", URL);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1024); // texte, tableau, police et image du QR code
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1).trim()).endsWith("%%EOF");
    }

    @Test
    void supporte_une_ordonnance_annulee_et_des_champs_absents() {
        Ordonnance annulee = new Ordonnance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                List.of(new LigneOrdonnance("Ibuprofene 400 mg", null, null)),
                Instant.parse("2026-09-18T10:00:00Z"), "ZZZZZZZ2", StatutOrdonnance.ANNULEE);

        byte[] pdf = generateur.generer(annulee, "Medecin", "Patient", "http://localhost:4200/verifier?code=ZZZZZZZ2");

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1024);
    }

    @Test
    void deux_ordonnances_donnent_deux_documents_distincts() {
        byte[] premier = generateur.generer(ordonnance(StatutOrdonnance.EMISE), "Medecin", "Patient", URL);
        byte[] second = generateur.generer(ordonnance(StatutOrdonnance.EMISE), "Medecin", "Autre patient", URL);

        assertThat(premier).isNotEqualTo(second);
    }
}
