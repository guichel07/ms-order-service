package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gamme "libre" définie à la main par l'admin (ex: "Femme enceinte", "Petit-déj enfant") — un
 * flyer composé de lignes {article, prix, quantité}, pas une catégorie ni une fenêtre de dates.
 * L'admin reconnaît un pattern humain (souvent après avoir vu les suggestions de lift) et
 * compose ce menu à la main, en ajustant prix/quantité pour équilibrer sa marge.
 */
@MongoEntity(collection = "Gammes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Gamme extends PanacheMongoEntity {

    private String title;

    private List<GammeItem> items;

    /** Texte marketing généré pour le flyer client — null tant que jamais généré. */
    private String marketingText;

    /** Pattern reconnu par l'IA à partir de la composition (ex: "petit-déjeuner congolais"). */
    private String patternDescription;

    private Instant contentGeneratedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
