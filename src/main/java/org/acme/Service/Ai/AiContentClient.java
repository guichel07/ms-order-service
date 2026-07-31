package org.acme.Service.Ai;

import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.GammeResponseDTO;

/**
 * Génère le texte marketing + conseil d'usage à partir d'un bundle déjà calculé — jamais l'inverse,
 * l'IA ne décide jamais quels articles vont ensemble, elle habille un résultat déjà déterminé par
 * le calcul de lift.
 */
public interface AiContentClient {
    AiGeneratedContentDTO generate(ArticleBundleDTO bundle);

    /**
     * Même principe pour une gamme composée à la main : l'IA ne décide jamais des lignes (prix,
     * quantité, articles), elle rédige le texte marketing et reconnaît un pattern humain dans la
     * composition déjà figée par l'admin (ex: "petit-déjeuner congolais").
     */
    AiGeneratedContentDTO generateForGamme(GammeResponseDTO gamme);
}
