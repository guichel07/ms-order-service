package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Article
 */
@MongoEntity(collection = "Articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Article extends PanacheMongoEntity {

    private String name;

    private String icon;

    private String color;

    private String category;

    private BigDecimal price;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** Coût de revient à l'unité — toujours recalculé côté back depuis purchasePrice/transport/misc/purchasedQuantity, jamais reçu tel quel. */
    private BigDecimal costPrice;

    private BigDecimal marketPrice;

    private int quantity;

    /** Prix d'achat total du dernier lot reçu. */
    private BigDecimal purchasePrice;

    /** Transport total du lot. */
    private BigDecimal transport;

    /** Frais divers du lot. */
    private BigDecimal misc;

    /** Quantité achetée dans ce lot — sert de diviseur pour costPrice. */
    private int purchasedQuantity;

    /** Seuil de stock en dessous duquel une alerte de réassort a du sens. */
    private int criticalStock;

    /** Si vrai, le prix de vente ne suit pas automatiquement costPrice — déclenche priceReview si le coût bouge. */
    private boolean locked;

    /** Vrai si l'admin a explicitement choisi ce prix plutôt que de suivre la cible calculée. */
    private boolean priceManuallySet;

    /** Null tant que rien à signaler — voir PriceReview. */
    private PriceReview priceReview;

    /** Retiré du catalogue actif sans perdre l'historique — pas de suppression définitive. */
    private boolean archived;

    /** Date de péremption du lot actuel — null si l'article n'est pas périssable. */
    private Instant expirationDate;
}
