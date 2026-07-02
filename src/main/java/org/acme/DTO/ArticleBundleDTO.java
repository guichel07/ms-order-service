package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Paires d'articles souvent achetés ensemble sur une tranche horaire donnée (matin/midi/soir),
 * pour alimenter les PDF marketing — même calcul de lift que
 * ArticleDetailDTO.topAssociatedProducts, mais appliqué à tout le catalogue au lieu d'un seul
 * article, et scopé sur l'heure de la commande plutôt que sur sa date.
 */
public record ArticleBundleDTO(
    String periodKey,
    String label,
    List<BundleDTO> bundles
) {
    public record BundleDTO(
        BundleItemDTO first,
        BundleItemDTO second,
        int coOccurrenceCount,
        /** % des commandes de la tranche horaire qui contiennent les deux articles. */
        BigDecimal supportPct,
        /** % des commandes contenant "first" qui contiennent aussi "second". */
        BigDecimal confidencePct,
        /** lift = support / (support(first) * support(second)) — > 1 = vrai lien d'achat. */
        BigDecimal lift,
        /** Somme brute des prix actuels — pas de remise (choix retenu). */
        BigDecimal totalPrice,
        /** Somme des marges unitaires actuelles (price - costPrice) des deux articles. */
        BigDecimal totalMargin
    ) {}

    public record BundleItemDTO(
        String articleId,
        String name,
        BigDecimal price,
        int stock,
        /** Null si l'article n'est pas périssable — à afficher sur le PDF imprimé. */
        Instant expirationDate
    ) {}
}
