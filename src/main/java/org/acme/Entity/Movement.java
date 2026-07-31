package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un mouvement de trésorerie (entrée ou sortie de liquidité), horodaté à l'enregistrement. */
@MongoEntity(collection = "Movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Movement extends PanacheMongoEntity {

    /** entree | sortie. */
    private String type;

    private BigDecimal amount;

    private String label;

    private Instant date;

    /** Photo/scan du justificatif encodé en data URI, optionnel. */
    private String proofImage;
}
