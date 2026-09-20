package dz.tabibi.backend.referentiel.adapter;

import dz.tabibi.backend.referentiel.domain.ReferentielRepository;
import dz.tabibi.backend.referentiel.domain.Specialite;
import dz.tabibi.backend.referentiel.domain.Wilaya;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Donnees de reference de demonstration (hors profil « supabase »). */
@Repository
@Profile("!supabase")
public class EnMemoireReferentielRepository implements ReferentielRepository {

    @Override
    public List<Wilaya> wilayas() {
        return List.of(
                new Wilaya("16", "Alger"),
                new Wilaya("31", "Oran"),
                new Wilaya("25", "Constantine"),
                new Wilaya("9", "Blida"),
                new Wilaya("6", "Bejaia"));
    }

    @Override
    public List<Specialite> specialites() {
        return List.of(
                new Specialite("generaliste", "Medecin generaliste"),
                new Specialite("cardiologue", "Cardiologue"),
                new Specialite("dermatologue", "Dermatologue"),
                new Specialite("pediatre", "Pediatre"),
                new Specialite("dentiste", "Dentiste"),
                new Specialite("gynecologue", "Gynecologue"));
    }
}
