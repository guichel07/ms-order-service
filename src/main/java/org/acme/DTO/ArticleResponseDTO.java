package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import org.acme.Entity.Article;
import org.acme.Entity.PriceReview;
import org.acme.Entity.Unit;

/**
 * Représentation d'un article renvoyée par l'API — jamais l'entité Mongo directement.
 */
public record ArticleResponseDTO(
    String id,

    String name,

    String icon,

    String color,

    String category,

    BigDecimal price,

    BigDecimal minPrice,

    BigDecimal costPrice,

    BigDecimal maxPrice,

    BigDecimal marketPrice,

    BigDecimal quantity,

    Unit unit,

    BigDecimal purchasePrice,

    BigDecimal transport,

    BigDecimal misc,

    BigDecimal purchasedQuantity,

    BigDecimal criticalStock,

    boolean locked,

    boolean priceManuallySet,

    /** Null si rien à signaler. */
    PriceReviewDTO priceReview,

    boolean archived,

    /** Null si l'article n'est pas périssable. */
    Instant expirationDate
) {
    public record PriceReviewDTO(
        BigDecimal previousPrice,
        BigDecimal newUnitCost,
        BigDecimal recommendedPrice,
        BigDecimal marginDrop
    ) {
        public static PriceReviewDTO fromEntity(PriceReview review) {
            if (review == null) {
                return null;
            }
            return new PriceReviewDTO(
                review.getPreviousPrice(),
                review.getNewUnitCost(),
                review.getRecommendedPrice(),
                review.getMarginDrop()
            );
        }
    }

    public static ArticleResponseDTO fromEntity(Article article) {
        if (article == null) {
            return null;
        }
        return new ArticleResponseDTO(
            article.id.toHexString(),
            article.getName(),
            article.getIcon(),
            article.getColor(),
            article.getCategory(),
            article.getPrice(),
            article.getMinPrice(),
            article.getCostPrice(),
            article.getMaxPrice(),
            article.getMarketPrice(),
            article.getQuantity(),
            article.getUnit(),
            article.getPurchasePrice(),
            article.getTransport(),
            article.getMisc(),
            article.getPurchasedQuantity(),
            article.getCriticalStock(),
            article.isLocked(),
            article.isPriceManuallySet(),
            PriceReviewDTO.fromEntity(article.getPriceReview()),
            article.isArchived(),
            article.getExpirationDate()
        );
    }
}
