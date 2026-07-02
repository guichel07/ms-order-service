package org.acme.DTO;

import java.time.Instant;

/**
 * Enveloppe combinant le bundle calculé et le texte généré par l'IA — ce que consomme le PDF
 * marketing. Stocké en base pour être pré-généré par le job planifié plutôt que recalculé/appelé
 * à chaque affichage.
 */
public record BundleContentDTO(
    String key,
    String label,
    ArticleBundleDTO bundle,
    String marketingText,
    String usageTip,
    Instant generatedAt
) {}
