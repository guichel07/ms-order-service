package org.acme.DTO;

import java.math.BigDecimal;

/**
 * Une ligne du classement catalogue pour une période — le front en extrait les
 * best-sellers (haut de liste) et les flops (bas de liste), même logique que le
 * classement vendeurs déjà en place côté ms-section-stats.
 */
public record ArticleRankingEntryDTO(
    String articleId,
    String name,
    BigDecimal totalCa,
    BigDecimal totalBenefice,
    BigDecimal quantitySold
) {}
