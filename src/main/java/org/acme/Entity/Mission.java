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
 * Une Mission est l'achat unitaire d'UN article auprès d'UN fournisseur, saisi au moment où il a
 * lieu sur le terrain (pas une prévision) — nom/icône/coût de référence snapshotés à la création,
 * même convention historique que les autres documents transactionnels de l'app. Rattachée à une
 * Queste via questeId.
 */
@MongoEntity(collection = "Missions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mission extends PanacheMongoEntity {

    private String questeId;

    private String articleId;

    private String name;

    private String icon;

    private String iconColor;

    private String supplierId;

    private String supplierName;

    private int qty;

    /** Montant réel payé pour la ligne entière (pas un prix unitaire). */
    private BigDecimal pricePaid;

    /** Coût de revient à l'unité snapshoté depuis Article.costPrice à la création — sert de référence pour l'économie, jamais recalculé après coup. */
    private BigDecimal refUnitCost;

    /** terminee | en_attente | ecartee. */
    private String status;

    private Instant createdAt;

    /** Prix marché observé sur le terrain au moment de cet achat précis — répercuté sur Article.marketPrice, mais gardé ici pour l'historique. */
    private BigDecimal marketPrice;
}
