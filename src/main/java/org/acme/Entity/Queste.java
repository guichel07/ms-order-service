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
 * Une Quête est la campagne d'achat en cours d'un acheteur — elle porte le transport réel et la
 * prime, et regroupe les Missions unitaires (une par article acheté) saisies au fil de l'eau sur
 * le terrain. Cycle de vie : en_cours -> en_attente_validation (transport saisi, en attente que
 * l'admin valide l'injection en stock) -> cloturee (stock/coût de revient mis à jour).
 */
@MongoEntity(collection = "Questes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Queste extends PanacheMongoEntity {

    private int number;

    private String buyerName;

    /** Libellé libre optionnel — ex : "Produits laitiers", ou vide pour une quête générique. */
    private String objectif;

    private Instant createdAt;

    private String status;

    /** Frais de transport réels de toute la quête — saisis à la clôture, ventilés ensuite au prorata par article. */
    private BigDecimal transportReel;

    private BigDecimal primePercent;

    /** Somme des économies des missions terminées (coût de référence - prix payé), calculée à la clôture. */
    private BigDecimal economieAchats;

    private BigDecimal prime;

    private Instant closedAt;

    private Instant validatedAt;
}
