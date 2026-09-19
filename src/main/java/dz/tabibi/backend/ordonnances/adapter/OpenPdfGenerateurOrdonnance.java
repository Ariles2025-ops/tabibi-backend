package dz.tabibi.backend.ordonnances.adapter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.ordonnances.domain.GenerateurPdfOrdonnance;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Adaptateur de sortie du port GenerateurPdfOrdonnance : une page A4 (OpenPDF) avec l'en-tete
 * Tabibi, le medecin, le patient, la date d'emission, le tableau des lignes (medicament, posologie,
 * duree), le code de verification en gros caracteres et un QR code (ZXing) qui encode l'adresse
 * publique de verification. Sans etat, sans fichier temporaire : tout est produit en memoire.
 */
@Component
public class OpenPdfGenerateurOrdonnance implements GenerateurPdfOrdonnance {

    /** Cote du QR code en points (1 pt = 1/72 pouce) : lisible au telephone une fois imprime. */
    static final int TAILLE_QR = 140;

    private static final String NOM_PLATEFORME = "Tabibi";
    private static final Color GRIS_CLAIR = new Color(235, 235, 235);
    private static final Color GRIS = new Color(110, 110, 110);
    private static final Color VERT = new Color(20, 110, 80);

    private static final Font TITRE = new Font(Font.HELVETICA, 24, Font.BOLD, VERT);
    private static final Font SOUS_TITRE = new Font(Font.HELVETICA, 10, Font.NORMAL, GRIS);
    private static final Font ENTETE_TABLEAU = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 11, Font.NORMAL);
    private static final Font GRAS = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font CODE = new Font(Font.COURIER, 26, Font.BOLD);
    private static final Font PETIT = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);

    @Override
    public byte[] generer(Ordonnance ordonnance, String nomMedecin, String nomPatient, String urlVerification) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, sortie);
            document.addTitle("Ordonnance " + ordonnance.codeVerification());
            document.addAuthor(NOM_PLATEFORME);
            document.addCreator(NOM_PLATEFORME);
            document.open();

            document.add(enTete());
            document.add(identites(ordonnance, nomMedecin, nomPatient));
            document.add(lignes(ordonnance));
            document.add(codeDeVerification(ordonnance));
            document.add(qrCode(urlVerification));
            document.add(mentionVerification(urlVerification));

            document.close();
        } catch (DocumentException | IOException | WriterException e) {
            throw new IllegalStateException("Impossible de produire le PDF de l'ordonnance.", e);
        }
        return sortie.toByteArray();
    }

    private static Paragraph enTete() {
        Paragraph titre = new Paragraph(NOM_PLATEFORME, TITRE);
        titre.add(new Chunk("   Ordonnance medicale", SOUS_TITRE));
        titre.setSpacingAfter(14);
        return titre;
    }

    private static PdfPTable identites(Ordonnance ordonnance, String nomMedecin, String nomPatient) {
        PdfPTable tableau = new PdfPTable(2);
        tableau.setWidthPercentage(100);
        tableau.addCell(champ("Medecin", nomMedecin));
        tableau.addCell(champ("Date d'emission", FormatDate.lisible(ordonnance.emiseLe())));
        tableau.addCell(champ("Patient", nomPatient));
        tableau.addCell(champ("Statut", ordonnance.estValide() ? "Emise" : "Annulee"));
        tableau.setSpacingAfter(16);
        return tableau;
    }

    private static PdfPCell champ(String libelle, String valeur) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(libelle + " : ", GRAS));
        phrase.add(new Chunk(valeur == null ? "" : valeur, NORMAL));
        PdfPCell cellule = new PdfPCell(phrase);
        cellule.setBorder(PdfPCell.NO_BORDER);
        cellule.setPadding(4);
        return cellule;
    }

    private static PdfPTable lignes(Ordonnance ordonnance) throws DocumentException {
        PdfPTable tableau = new PdfPTable(3);
        tableau.setWidthPercentage(100);
        tableau.setWidths(new float[] {3f, 4f, 2f});
        tableau.setHeaderRows(1);
        for (String colonne : new String[] {"Medicament", "Posologie", "Duree"}) {
            PdfPCell entete = new PdfPCell(new Phrase(colonne, ENTETE_TABLEAU));
            entete.setBackgroundColor(GRIS_CLAIR);
            entete.setPadding(6);
            tableau.addCell(entete);
        }
        for (LigneOrdonnance ligne : ordonnance.lignes()) {
            tableau.addCell(cellule(ligne.medicament()));
            tableau.addCell(cellule(ligne.posologie()));
            tableau.addCell(cellule(ligne.duree()));
        }
        tableau.setSpacingAfter(22);
        return tableau;
    }

    private static PdfPCell cellule(String texte) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte == null ? "" : texte, NORMAL));
        cellule.setPadding(6);
        return cellule;
    }

    private static Paragraph codeDeVerification(Ordonnance ordonnance) {
        Paragraph paragraphe = new Paragraph();
        paragraphe.setAlignment(Element.ALIGN_CENTER);
        paragraphe.add(new Chunk("Code de verification\n", SOUS_TITRE));
        paragraphe.add(new Chunk(ordonnance.codeVerification(), CODE));
        paragraphe.setSpacingAfter(10);
        return paragraphe;
    }

    /** QR code de TAILLE_QR points de cote, marge minimale, correction d'erreur moyenne (impression). */
    private static Image qrCode(String urlVerification) throws WriterException, IOException, DocumentException {
        BitMatrix matrice = new QRCodeWriter().encode(urlVerification, BarcodeFormat.QR_CODE, TAILLE_QR, TAILLE_QR,
                Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrice, "PNG", png);
        Image image = Image.getInstance(png.toByteArray());
        image.scaleAbsolute(TAILLE_QR, TAILLE_QR);
        image.setAlignment(Element.ALIGN_CENTER);
        return image;
    }

    private static Paragraph mentionVerification(String urlVerification) {
        Paragraph mention = new Paragraph("Verifiez cette ordonnance sur " + urlVerification, PETIT);
        mention.setAlignment(Element.ALIGN_CENTER);
        mention.setSpacingBefore(6);
        return mention;
    }
}
