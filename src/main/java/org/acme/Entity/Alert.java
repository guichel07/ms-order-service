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
 * Une alerte est levée automatiquement (le "Gendarme") quand une vente est enregistrée à un prix
 * inférieur au coût de revient réel de l'article — un bénéfice négatif. La vente n'est jamais
 * bloquée : l'alerte n'est qu'un avertissement a posteriori, résolu manuellement par un admin.
 * Voir OrderServiceImpl.register() pour le point de détection.
 */
@MongoEntity(collection = "Alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Alert extends PanacheMongoEntity {

    private String orderId;

    private String articleId;

    private String vendor;

    private String productName;

    private BigDecimal attemptedPrice;

    /** Coût de revient réel de l'article au moment de la vente (pas un seuil configuré). */
    private BigDecimal costPrice;

    /** attemptedPrice - costPrice, à l'unité — toujours négatif ou nul pour déclencher une alerte. */
    private BigDecimal margin;

    private BigDecimal quantity;

    private Instant detectedAt;

    private boolean resolved;
}
