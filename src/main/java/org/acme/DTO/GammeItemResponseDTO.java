package org.acme.DTO;

import java.math.BigDecimal;

/**
 * Ligne enrichie avec les vraies données article au moment de la lecture — nom/icône pour
 * l'affichage, coût de revient actuel et marge recalculée à partir de lui (jamais figée à la
 * date de création de la gamme, pour refléter le vrai coût du moment).
 */
public record GammeItemResponseDTO(
    String articleId,
    String name,
    String icon,
    BigDecimal price,
    int quantity,
    /** Coût de revient à l'unité, actuel — pour calculer la marge de cette ligne. */
    BigDecimal unitCost,
    BigDecimal stock,
    BigDecimal lineTotal,
    BigDecimal lineMargin
) {}
