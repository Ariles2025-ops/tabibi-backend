package dz.tabibi.backend.avis.domain;

/**
 * Cycle de vie d'un avis : publie a son depot, signale par le medecin concerne, masque par
 * l'administrateur (qui peut aussi le retablir). Seuls les avis publies sont visibles du public.
 */
public enum StatutAvis {
    PUBLIE,
    SIGNALE,
    MASQUE
}
