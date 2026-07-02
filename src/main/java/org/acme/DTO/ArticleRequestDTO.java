package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import org.acme.Entity.Unit;

/**
 * costPrice n'est volontairement pas un champ d'entrée — il est toujours recalculé
 * côté back depuis purchasePrice/transport/misc/purchasedQuantity (voir
 * ArticleServiceImpl), jamais reçu tel quel du front.
 */
public record ArticleRequestDTO(
    String name,

    String icon,

    String color,

    String category,

    BigDecimal price,

    BigDecimal minPrice,

    BigDecimal maxPrice,

    BigDecimal marketPrice,

    BigDecimal purchasePrice,

    BigDecimal transport,

    BigDecimal misc,

    BigDecimal purchasedQuantity,

    BigDecimal quantity,

    BigDecimal criticalStock,

    Unit unit,

    boolean locked,

    boolean priceManuallySet,

    /** Null si l'article n'est pas périssable. */
    Instant expirationDate
) {}
