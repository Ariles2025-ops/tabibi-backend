package dz.tabibi.backend.messagerie.domain;

/** Une conversation vue par l'un de ses participants, avec le nombre de messages qu'il n'a pas encore lus. */
public record ConversationAvecNonLus(Conversation conversation, long nonLus) {}
