package org.acme.Entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une ligne d'une gamme : un article + le prix et la quantité choisis par l'admin pour ce
 * flyer précis — pas forcément le prix catalogue (ex: petit tarif groupé). La marge est
 * toujours recalculée à la lecture depuis le vrai coût de revient actuel de l'article, jamais
 * stockée ici (elle doit refléter le coût réel du moment, pas celui du jour de création).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GammeItem {

    private String articleId;

    private BigDecimal price;

    private int quantity;
}
