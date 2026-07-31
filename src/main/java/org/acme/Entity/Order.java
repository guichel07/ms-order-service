package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@MongoEntity(collection = "Orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order extends PanacheMongoEntity {

    private String receiptNumber;
    private Date receiptDate;
    private String sellerName;
    private String email;

    /**
     * Id du Client, déjà résolu côté front (recherche ou création préalable, ou fiche
     * "anonyme" par catégorie d'âge — voir ClientService.getOrCreateAnonymous) avant
     * l'envoi de la commande. Figé une fois pour toutes : reste valable même si le
     * client change de numéro plus tard.
     */
    private String clientId;

    /** Snapshot du téléphone du Client (clientId) au moment de la vente — pour affichage. */
    private String clientPhone;
    private BigDecimal dailySummary;
    private Instant saleDate;
    private ArrayList<OrderItem> articles;

    /**
     * Bénéfice de la commande (prix de vente - coût de revient, à la date de la vente).
     * Toujours calculé côté back — jamais reçu du client (voir OrderRequestDTO, qui ne
     * l'expose pas en entrée). Calculé dans OrderServiceImpl.register().
     */
    private BigDecimal delta;
}
