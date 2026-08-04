package org.acme.Entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Palier de conditionnement/vente pour un article (ex: "Sac de 50 verres").
 * ratio = nombre d'unités atomiques ({@link Article#getAtomicUnit()}) que représente
 * ce palier — informatif, ne sert jamais à recalculer price : le prix d'un palier est
 * toujours celui saisi tel quel, indépendant de ratio × prix de l'unité atomique,
 * pour permettre des tarifs promo/volume qui cassent la linéarité.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackagingLevel {

    private String label;
    private BigDecimal ratio;
    private BigDecimal price;
}
