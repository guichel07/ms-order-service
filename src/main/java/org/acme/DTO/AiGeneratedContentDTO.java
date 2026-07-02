package org.acme.DTO;

/**
 * Texte généré par l'IA à partir d'un bundle déjà calculé — la sélection des produits reste un
 * calcul pur (lift), l'IA ne fait que rédiger le texte marketing et un conseil d'usage contextualisé.
 */
public record AiGeneratedContentDTO(
    String marketingText,
    /** Conseil d'usage/recette, contextualisé (ex: Congo) — optionnel selon le produit. */
    String usageTip
) {}
