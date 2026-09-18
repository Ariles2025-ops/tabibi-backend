package dz.tabibi.backend.ordonnances.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;

import java.util.List;

/**
 * Serialise les lignes d'une ordonnance en JSON pour la colonne {@code ordonnance.lignes_json}.
 * Les lignes ne contiennent que des chaines : l'ObjectMapper de Spring suffit, sans module.
 */
public final class LignesOrdonnanceJson {

    private static final TypeReference<List<LigneOrdonnance>> LISTE_DE_LIGNES = new TypeReference<>() { };

    private final ObjectMapper mapper;

    public LignesOrdonnanceJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String versJson(List<LigneOrdonnance> lignes) {
        try {
            return mapper.writeValueAsString(lignes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Lignes d'ordonnance impossibles a serialiser.", e);
        }
    }

    /** Une colonne vide ou nulle vaut "aucune ligne". */
    public List<LigneOrdonnance> depuisJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, LISTE_DE_LIGNES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Lignes d'ordonnance illisibles en base.", e);
        }
    }
}
