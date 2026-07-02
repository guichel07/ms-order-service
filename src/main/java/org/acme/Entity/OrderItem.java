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
    private int quantityOrdered;

    /**
     * Coût unitaire de l'article au moment de la vente, snapshoté comme le prix.
     * Absent (null) sur les commandes enregistrées avant l'introduction de ce champ —
     * voir la migration de backfill dans OrderService. Setter explicite (pas dans le
     * constructeur ci-dessous) pour ne pas casser les call sites existants.
     */
    private BigDecimal unitCost;

    public OrderItem(String articleId, String name, BigDecimal price, int quantityOrdered) {
        this.articleId = articleId;
        this.name = name;
        this.price = price;
        this.quantityOrdered = quantityOrdered;
    }
}
