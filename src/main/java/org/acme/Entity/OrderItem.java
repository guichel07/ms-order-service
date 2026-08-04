package org.acme.Entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    private String articleId;
    private String name;
    private BigDecimal price;
    private BigDecimal quantityOrdered;

    /**
     * Coût unitaire de l'article au moment de la vente, snapshoté comme le prix.
     * Absent (null) sur les commandes enregistrées avant l'introduction de ce champ —
     * voir la migration de backfill dans OrderService. Setter explicite (pas dans le
     * constructeur ci-dessous) pour ne pas casser les call sites existants.
     */
    private BigDecimal unitCost;

    /**
     * Libellé du palier de vente choisi (ex: "Carton de 12"), ou l'unité atomique de
     * l'article si vendu à l'unité de base — snapshoté ici car packagingLevels peut
     * évoluer/disparaître après la vente sans que l'historique des commandes bouge.
     */
    private String soldAsLabel;

    /** Ratio (nb d'unités atomiques) du palier choisi au moment de la vente — 1 si vente à l'unité atomique. */
    private BigDecimal soldAsRatio;

    public OrderItem(String articleId, String name, BigDecimal price, BigDecimal quantityOrdered) {
        this.articleId = articleId;
        this.name = name;
        this.price = price;
        this.quantityOrdered = quantityOrdered;
    }
}
