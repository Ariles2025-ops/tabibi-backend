package dz.tabibi.backend.messagerie.domain;

/** Resultat de l'ouverture d'une conversation : celle du couple, creee a cette occasion ou preexistante. */
public record ResultatOuverture(Conversation conversation, boolean creee) {}
