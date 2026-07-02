package org.acme.DTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrégat performance d'un article (les 4 périodes 7j/4sem/6mois/2ans en une seule
 * réponse) — pensé pour un usage admin/comptable : totaux, vélocité de vente, alerte
 * si ça ralentit. Volontairement plus léger que SellerDetailDTO — pas de découpage
 * horaire ni par bucket, ce niveau de détail n'est pas utile ici.
 */
public record ArticleDetailDTO(
    String articleId,
    /** Null si rien à signaler (vélocité stable ou en hausse). */
    ArticleAlertDTO alert,
    List<ArticlePeriodDTO> periods,
    String defaultPeriodKey
) {
    public record ArticleAlertDTO(
        /** "warning" — pas de "critical" ici, contrairement aux vendeurs : un article qui ne se vend
         * plus se voit déjà en bas du classement, pas besoin d'un second signal. */
        String level,
        String message
    ) {}

    public record ArticlePeriodDTO(
        String key,
        String label,
        BigDecimal totalCa,
        BigDecimal totalBenefice,
        /** Bénéfice / CA, en % — un CA élevé peut cacher une marge faible. */
        BigDecimal marginRatePct,
        BigDecimal quantitySold,
        int transactionsCount,
        /** Quantité vendue / nombre de jours de la période — le signal de réassort. */
        BigDecimal velocityPerDay,
        /** Qui vend cet article, triés par CA généré descendant. */
        List<SellerBreakdownDTO> bySeller,
        /** Produits achetés dans les mêmes commandes, avec un lien statistique réel (lift > 1) — pas
         * juste "souvent vendu aussi", triés par lift descendant. */
        List<AssociatedProductDTO> topAssociatedProducts
    ) {}

    public record SellerBreakdownDTO(
        String email,
        String sellerName,
        BigDecimal quantitySold,
        BigDecimal totalCa
    ) {}

    /**
     * Association statistique entre deux articles, mesurée par le lift — pas juste un
     * comptage de co-occurrences (ça favoriserait les produits populaires par hasard).
     * lift = confidence(A→B) / support(B) : > 1 veut dire un vrai lien, pas une coïncidence.
     */
    public record AssociatedProductDTO(
        String articleId,
        String name,
        int coOccurrenceCount,
        /** % des commandes (toute la période) qui contiennent A ET B. */
        BigDecimal supportPct,
        /** % des commandes contenant A qui contiennent aussi B. */
        BigDecimal confidencePct,
        BigDecimal lift
    ) {}
}
