package org.acme.Entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bandeau "article à ajuster" — posé sur un article verrouillé (locked) quand son coût
 * de revient a bougé de plus de PRICE_REVIEW_THRESHOLD depuis la dernière fois, sans
 * que le prix de vente n'ait suivi. Null tant que rien n'est à signaler.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceReview {

    private BigDecimal previousPrice;
    private BigDecimal newUnitCost;
    private BigDecimal recommendedPrice;
    private BigDecimal marginDrop;
}
